#!/usr/bin/env bash
#
# Verifies the JNI bridge against the real llama.cpp — on a Linux host, with no
# Android SDK or device.
#
# Why this exists: the bridge broke twice on API drift (llama_model_params lost
# use_mmap/use_mlock), and each round trip through Android CI costs minutes.
# This does the same job in one step and proves three things:
#
#   1. llama_bridge.cpp compiles against the pinned llama.cpp headers
#   2. every llama_* symbol it calls actually exists (link, not just compile)
#   3. the JNI entry points run in a real JVM — string handling, null-handle
#      guards and the failure path are exercised, not assumed
#
# What it cannot do: run real inference. That needs a GGUF model and a device.
#
# Usage:  tools/verify-native.sh [llama.cpp-commit]
# Needs:  g++, cmake, git, a JDK (for jni.h and libjvm)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRIDGE="$REPO_ROOT/app/src/main/cpp/llama_bridge.cpp"
CMAKE_FILE="$REPO_ROOT/app/src/main/cpp/CMakeLists.txt"

# Default to the commit CMakeLists pins, so the check matches what CI builds.
PINNED="$(sed -n 's/^set(LLAMA_CPP_TAG "\([^"]*\)".*/\1/p' "$CMAKE_FILE" | head -1)"
COMMIT="${1:-$PINNED}"

WORK="${TMPDIR:-/tmp}/verify-native"
mkdir -p "$WORK"
cd "$WORK"

JAVA_BIN="$(readlink -f "$(command -v java)")"
JH="$(dirname "$(dirname "$JAVA_BIN")")"
[ -f "$JH/include/jni.h" ] || { echo "No jni.h under $JH — install a JDK."; exit 1; }

echo "==> llama.cpp @ $COMMIT"
if [ ! -d llama.cpp/.git ]; then
    git clone https://github.com/ggml-org/llama.cpp.git
fi
git -C llama.cpp fetch --quiet origin "$COMMIT" 2>/dev/null || git -C llama.cpp fetch --quiet
git -C llama.cpp checkout --quiet "$COMMIT"

echo "==> building llama.cpp (library target only)"
cmake -S llama.cpp -B build -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_COMMON=OFF \
    -DLLAMA_CURL=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_NATIVE=OFF \
    > cmake.log 2>&1
# Only the `llama` target: unrelated targets pull in headers this build skips.
cmake --build build -j"$(nproc)" --target llama > build.log 2>&1

# The NDK's logging header isn't on a Linux host.
mkdir -p stub/android
cat > stub/android/log.h <<'HEADER'
#pragma once
#include <stdarg.h>
typedef enum { ANDROID_LOG_INFO = 4, ANDROID_LOG_ERROR = 6 } android_LogPriority;
#ifdef __cplusplus
extern "C" {
#endif
int __android_log_print(int prio, const char* tag, const char* fmt, ...);
#ifdef __cplusplus
}
#endif
HEADER

cat > stub_log.c <<'IMPL'
#include <stdio.h>
#include <stdarg.h>
int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    (void)prio; (void)tag;
    va_list ap; va_start(ap, fmt);
    int n = vfprintf(stderr, fmt, ap); va_end(ap);
    return n;
}
IMPL
gcc -c stub_log.c -o stub_log.o

INCLUDES=(-I llama.cpp/include -I llama.cpp/ggml/include
          -I "$JH/include" -I "$JH/include/linux" -I stub)
LIBS=(build/src/libllama.a build/ggml/src/libggml.a
      build/ggml/src/libggml-cpu.a build/ggml/src/libggml-base.a)

echo "==> 1/3 compile"
g++ -std=gnu++17 -fsyntax-only -Wall "${INCLUDES[@]}" "$BRIDGE"
echo "    clean"

echo "==> 2/3 link + 3/3 run in a JVM"
cat > probe.cpp <<'PROBE'
#include <jni.h>
#include <cstdio>
#include <cstring>
#include <string>

namespace llamabridge { size_t utf8_complete_prefix(const std::string &); }

extern "C" {
JNIEXPORT void    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeInit(JNIEnv*, jobject);
JNIEXPORT jstring JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeLastError(JNIEnv*, jobject);
JNIEXPORT jlong   JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(JNIEnv*, jobject, jstring, jint, jint, jint);
JNIEXPORT void    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeFree(JNIEnv*, jobject, jlong);
JNIEXPORT void    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeSetThreads(JNIEnv*, jobject, jlong, jint);
JNIEXPORT void    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeStop(JNIEnv*, jobject, jlong);
JNIEXPORT void    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeResetContext(JNIEnv*, jobject, jlong);
JNIEXPORT jstring JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeCpuFeatures(JNIEnv*, jobject);
JNIEXPORT jstring JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeBackends(JNIEnv*, jobject);
JNIEXPORT jint    JNICALL Java_com_example_ondevicellm_llm_LlamaBridge_nativeGpuLayersUsed(JNIEnv*, jobject);
}
static int failures = 0;
static void check(const char* n, bool ok) { printf("%s  %s\n", ok?"PASS ":"FAIL ", n); if(!ok) failures++; }
int main() {
    JavaVM* vm=nullptr; JNIEnv* env=nullptr;
    JavaVMInitArgs a{}; a.version=JNI_VERSION_1_8; a.nOptions=0; a.ignoreUnrecognized=JNI_TRUE;
    if (JNI_CreateJavaVM(&vm,(void**)&env,&a)!=JNI_OK) { printf("no JVM\n"); return 1; }

    Java_com_example_ondevicellm_llm_LlamaBridge_nativeInit(env,nullptr);
    check("nativeInit runs", true);

    Java_com_example_ondevicellm_llm_LlamaBridge_nativeFree(env,nullptr,0);
    Java_com_example_ondevicellm_llm_LlamaBridge_nativeStop(env,nullptr,0);
    Java_com_example_ondevicellm_llm_LlamaBridge_nativeResetContext(env,nullptr,0);
    Java_com_example_ondevicellm_llm_LlamaBridge_nativeSetThreads(env,nullptr,0,4);
    check("null-handle calls are guarded", true);

    jstring bad = env->NewStringUTF("/definitely/not/a/model.gguf");
    check("missing model returns null handle",
          Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(env,nullptr,bad,2048,4,0)==0);

    jstring e = Java_com_example_ondevicellm_llm_LlamaBridge_nativeLastError(env,nullptr);
    const char* m = env->GetStringUTFChars(e,nullptr);
    check("failure sets a user-facing message", m && strlen(m)>0);
    printf("       reported: \"%s\"\n", m?m:"(null)");
    env->ReleaseStringUTFChars(e,m);

    jstring empty = env->NewStringUTF("");
    check("empty path returns null handle",
          Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(env,nullptr,empty,512,2,0)==0);

    // Asking for GPU layers on a file that does not exist must still come back
    // null rather than hanging or crashing in the fallback path.
    check("a GPU request on a missing model falls back and still fails cleanly",
          Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(env,nullptr,bad,2048,4,99)==0);

    jstring devs = Java_com_example_ondevicellm_llm_LlamaBridge_nativeBackends(env,nullptr);
    const char* devs_c = env->GetStringUTFChars(devs,nullptr);
    check("the backend registry is readable", devs_c != nullptr);
    printf("       devices: \"%s\"\n", devs_c ? devs_c : "");
    if (devs_c) env->ReleaseStringUTFChars(devs,devs_c);

    check("no layers are on the GPU after a failed load",
          Java_com_example_ondevicellm_llm_LlamaBridge_nativeGpuLayersUsed(env,nullptr)==0);

    jstring feats = Java_com_example_ondevicellm_llm_LlamaBridge_nativeCpuFeatures(env,nullptr);
    const char* fs = env->GetStringUTFChars(feats,nullptr);
    check("CPU features are reported", fs != nullptr);
    printf("       host kernels: \"%s\"\n", fs?fs:"(none)");
    env->ReleaseStringUTFChars(feats,fs);

    // --- the crash fix: a multi-byte character split across two tokens ------
    // "مرحبا" is 10 bytes; every letter is two. llama.cpp routinely hands over
    // half of one, and NewStringUTF on that fragment killed the process.
    {
        using llamabridge::utf8_complete_prefix;
        const std::string hello = "\xd9\x85\xd8\xb1\xd8\xad\xd8\xa8\xd8\xa7"; // مرحبا
        check("complete arabic passes through whole",
              utf8_complete_prefix(hello) == hello.size());
        check("a split arabic letter is held back",
              utf8_complete_prefix(hello.substr(0, 9)) == 8);
        check("a lone lead byte emits nothing",
              utf8_complete_prefix("\xd9") == 0);
        check("ascii is never withheld",
              utf8_complete_prefix("hello") == 5);
        check("emoji needs all four bytes",
              utf8_complete_prefix("\xf0\x9f\x98\x80") == 4 &&
              utf8_complete_prefix("\xf0\x9f\x98") == 0);
        check("3-byte sequences are handled",
              utf8_complete_prefix("\xe2\x9c\x93") == 3 &&
              utf8_complete_prefix("\xe2\x9c") == 0);
        check("a complete prefix is emitted before an incomplete tail",
              utf8_complete_prefix("ok\xd9\x85\xd8") == 4);
        check("an invalid lead byte never stalls the stream",
              utf8_complete_prefix("\xff\xff") == 2);
        check("empty input is a no-op", utf8_complete_prefix("") == 0);
    }

    vm->DestroyJavaVM();
    printf("\n%s\n", failures==0 ? ">>> NATIVE BRIDGE VERIFIED" : ">>> FAILURES");
    return failures;
}
PROBE

g++ -std=gnu++17 -O2 "${INCLUDES[@]}" "$BRIDGE" probe.cpp stub_log.o "${LIBS[@]}" \
    -L"$JH/lib/server" -ljvm -lpthread -ldl -lm -o probe

# ggml logs loudly to stderr on a failed load; the checks go to stdout.
LD_LIBRARY_PATH="$JH/lib/server:$JH/lib" ./probe 2>/dev/null
