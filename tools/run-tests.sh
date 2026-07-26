#!/usr/bin/env bash
#
# Compiles and runs the Android-free unit tests on the host, with **exactly**
# the classpath CI gives them — nothing more.
#
# Why the "nothing more" matters: StringsTest uses kotlin.reflect, which was on
# the sandbox classpath by accident and is not a project dependency. The suite
# passed here and failed to compile in CI. A harness that is more generous than
# the real build tells you nothing, so this one reads its dependencies from
# app/build.gradle.kts and refuses to add any of its own.
#
# Tests that touch Android (or org.json) are skipped: they need the SDK, and CI
# runs the full suite anyway. This is the fast loop, not a replacement.
#
# Usage:  tools/run-tests.sh
# Needs:  a JDK, and Gradle's bundled kotlin compiler + junit jars.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_LIB=/opt/gradle-8.14.3/lib
if ! ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar >/dev/null 2>&1; then
    echo "No kotlin-compiler-embeddable jar under $GRADLE_LIB — skipping." >&2
    exit 0
fi

# --- the test classpath, derived from the build file, not invented here ------
DEPS=("$GRADLE_LIB/kotlin-stdlib-2.0.21.jar")
add_if_declared() {  # $1 = gradle coordinate fragment, $2 = jar
    if grep -q "testImplementation(\"$1" "$REPO_ROOT/app/build.gradle.kts"; then
        [ -f "$2" ] && DEPS+=("$2")
    fi
}
add_if_declared "junit:junit"                       "$GRADLE_LIB/junit-4.13.2.jar"
add_if_declared "junit:junit"                       "$GRADLE_LIB/hamcrest-core-1.3.jar"
add_if_declared "org.jetbrains.kotlin:kotlin-reflect" "$GRADLE_LIB/kotlin-reflect-2.0.21.jar"

TEST_CP="$(IFS=:; echo "${DEPS[*]}")"
COMPILER_CP="$(ls "$GRADLE_LIB"/kotlin-*.jar "$GRADLE_LIB"/kotlinx-*.jar \
                  "$GRADLE_LIB"/annotations-*.jar "$GRADLE_LIB"/trove4j*.jar 2>/dev/null | tr '\n' ':')"

# --- sources: everything that doesn't need the Android SDK -------------------
SOURCES=(
    app/src/main/java/com/example/ondevicellm/core/Strings.kt
    app/src/main/java/com/example/ondevicellm/llm/QueryRouter.kt
    app/src/main/java/com/example/ondevicellm/llm/BackendPlanner.kt
    app/src/main/java/com/example/ondevicellm/llm/ThinkingStreamParser.kt
    app/src/main/java/com/example/ondevicellm/web/SearchModels.kt
    app/src/main/java/com/example/ondevicellm/web/HtmlExtract.kt
    app/src/test/java/com/example/ondevicellm/core/StringsTest.kt
    app/src/test/java/com/example/ondevicellm/llm/QueryRouterTest.kt
    app/src/test/java/com/example/ondevicellm/llm/BackendPlannerTest.kt
    app/src/test/java/com/example/ondevicellm/llm/ThinkingStreamParserTest.kt
    app/src/test/java/com/example/ondevicellm/web/SearchQueryTest.kt
    app/src/test/java/com/example/ondevicellm/web/HtmlExtractTest.kt
)
CLASSES=(
    com.example.ondevicellm.core.StringsTest
    com.example.ondevicellm.llm.QueryRouterTest
    com.example.ondevicellm.llm.BackendPlannerTest
    com.example.ondevicellm.llm.ThinkingStreamParserTest
    com.example.ondevicellm.web.SearchQueryTest
    com.example.ondevicellm.web.HtmlExtractTest
)

# BackendPlanner references BackendPref, which lives in a file that imports
# org.json. A minimal stand-in keeps the SDK out of this loop.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cat > "$WORK/BackendPref.kt" <<'EOF'
package com.example.ondevicellm.model
enum class BackendPref { AUTO, CPU, GPU, NPU }
EOF

cd "$REPO_ROOT"
echo "==> compiling with the CI test classpath"
if ! java -Dfile.encoding=UTF-8 -cp "$COMPILER_CP" \
        org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        "$WORK/BackendPref.kt" "${SOURCES[@]}" \
        -classpath "$TEST_CP" -d "$WORK/out" -nowarn 2>&1 \
        | grep -vE "^warning:|^Picked up" | tee "$WORK/compile.log"
then :; fi
if grep -q "error:" "$WORK/compile.log"; then
    echo ">>> COMPILE FAILED"
    exit 1
fi

echo "==> running"
java -Dfile.encoding=UTF-8 -cp "$WORK/out:$TEST_CP" \
    org.junit.runner.JUnitCore "${CLASSES[@]}" 2>&1 | grep -v "^Picked up"
