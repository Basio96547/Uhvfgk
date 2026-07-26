// JNI bridge to llama.cpp, giving the app GGUF support alongside the
// MediaPipe .task path.
//
// The surface is deliberately small: load a model, generate with streaming
// callbacks, reset the KV cache, free. Everything stateful lives in Session,
// whose address is handed to Kotlin as an opaque long.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <string>
#include <vector>

#include "ggml-cpu.h"
#include "llama.h"

#define LOG_TAG "llamabridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Externally linked so tools/verify-native.sh can test it directly — it is the
// fix for a process-killing crash and deserves more than "it compiled".
namespace llamabridge {

/// Length of the longest prefix of [bytes] that is complete UTF-8.
///
/// This is the fix for a hard crash. A BPE token is a run of *bytes*, not
/// characters: Arabic letters are two bytes and emoji four, and the tokenizer
/// splits them across pieces all the time. Handing such a fragment to
/// NewStringUTF makes ART abort the whole process —
///   "JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8"
/// — which is why the app died mid-reply. Anything incomplete is held back
/// until the following token completes it.
size_t utf8_complete_prefix(const std::string &bytes) {
    size_t i = 0;
    while (i < bytes.size()) {
        const unsigned char lead = static_cast<unsigned char>(bytes[i]);
        size_t len;
        if (lead < 0x80) {
            len = 1;
        } else if ((lead & 0xE0) == 0xC0) {
            len = 2;
        } else if ((lead & 0xF0) == 0xE0) {
            len = 3;
        } else if ((lead & 0xF8) == 0xF0) {
            len = 4;
        } else {
            // Not a valid lead byte. Pass it through rather than stalling
            // forever waiting for a continuation that will never arrive.
            len = 1;
        }
        if (i + len > bytes.size()) break;
        i += len;
    }
    return i;
}

} // namespace llamabridge

namespace {

using llamabridge::utf8_complete_prefix;

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    // Set from another thread to interrupt the decode loop.
    std::atomic<bool> stop{false};
    // Position of the next token in the KV cache, so multi-turn chat continues
    // instead of re-processing the whole conversation each time.
    llama_pos n_past = 0;
};

// Kept so the last failure can be reported to Kotlin after a null return.
std::string g_last_error;

// Why the last generation ended. A reply cut off by the token cap looks
// identical to a short answer from the outside, which is precisely the kind of
// silent failure that reads as the model being stupid.
enum StopReason { STOP_END_OF_TURN = 0, STOP_TOKEN_CAP = 1, STOP_CANCELLED = 2 };
std::atomic<int> g_stop_reason{STOP_END_OF_TURN};

std::string jstring_to_utf8(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

/// Converts one token to text. Returns an empty string for tokens that render
/// to nothing.
std::string token_to_text(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    const int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, /*special=*/false);
    if (n < 0) {
        // Piece longer than the stack buffer: retry with an exact allocation.
        std::vector<char> heap(-n);
        const int32_t m = llama_token_to_piece(
            vocab, token, heap.data(), static_cast<int32_t>(heap.size()), 0, false);
        if (m <= 0) return {};
        return std::string(heap.data(), m);
    }
    return std::string(buf, n);
}

std::vector<llama_token> tokenize(
    const llama_vocab *vocab, const std::string &text, bool add_special) {
    // Negative return = required size.
    const int32_t needed = -llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        nullptr, 0, add_special, /*parse_special=*/true);

    std::vector<llama_token> tokens(needed > 0 ? needed : 0);
    if (tokens.empty()) return tokens;

    const int32_t n = llama_tokenize(
        vocab, text.c_str(), static_cast<int32_t>(text.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()),
        add_special, /*parse_special=*/true);
    if (n < 0) return {};
    tokens.resize(n);
    return tokens;
}

/// Formats a turn with the model's own chat template when it has one, so
/// instruction-tuned models (Qwen, Llama, Gemma…) behave as intended.
///
/// [include_system] is false for every turn after the first. The KV cache
/// already holds the whole conversation, so re-emitting the system block each
/// turn stacked a second and third copy of it into the context behind the
/// replies already there — the model was reading a transcript that restarted
/// mid-conversation, and answered accordingly.
std::string apply_chat_template(
    llama_model *model, const std::string &system_prompt, const std::string &user,
    bool include_system) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) {
        // Base model with no template: fall back to the raw prompt.
        if (system_prompt.empty() || !include_system) return user;
        return system_prompt + "\n\n" + user;
    }

    std::vector<llama_chat_message> messages;
    if (include_system && !system_prompt.empty()) {
        messages.push_back({"system", system_prompt.c_str()});
    }
    messages.push_back({"user", user.c_str()});

    std::vector<char> buf(std::max<size_t>(1024, (system_prompt.size() + user.size()) * 4));
    int32_t n = llama_chat_apply_template(
        tmpl, messages.data(), messages.size(), /*add_ass=*/true,
        buf.data(), static_cast<int32_t>(buf.size()));

    if (n > static_cast<int32_t>(buf.size())) {
        buf.resize(n);
        n = llama_chat_apply_template(
            tmpl, messages.data(), messages.size(), true,
            buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (n <= 0) {
        if (system_prompt.empty() || !include_system) return user;
        return system_prompt + "\n\n" + user;
    }
    return std::string(buf.data(), n);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeInit(JNIEnv *, jobject) {
    static std::atomic<bool> initialised{false};
    bool expected = false;
    if (initialised.compare_exchange_strong(expected, true)) {
        llama_backend_init();
        // ggml is chatty at info level; keep logcat usable.
        llama_log_set([](ggml_log_level level, const char *text, void *) {
            if (level == GGML_LOG_LEVEL_ERROR) LOGE("%s", text);
        }, nullptr);
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeLastError(JNIEnv *env, jobject) {
    return env->NewStringUTF(g_last_error.c_str());
}

/// The SIMD extensions this build is actually using, comma-separated.
///
/// Compile-time *and* runtime: ggml only reports a feature when the kernels
/// were built for it and the CPU advertises it. So this is what the model is
/// really running on, not what the phone could theoretically do.
/// 0 = the model finished its turn, 1 = cut off by the token cap, 2 = stopped.
JNIEXPORT jint JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeLastStopReason(JNIEnv *, jobject) {
    return static_cast<jint>(g_stop_reason.load());
}

JNIEXPORT jstring JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeCpuFeatures(JNIEnv *env, jobject) {
    std::string features;
    const auto add = [&features](const char *name, int enabled) {
        if (!enabled) return;
        if (!features.empty()) features += ", ";
        features += name;
    };
    add("NEON", ggml_cpu_has_neon());
    add("dotprod", ggml_cpu_has_dotprod());
    add("fp16", ggml_cpu_has_fp16_va());
    add("i8mm", ggml_cpu_has_matmul_int8());
    add("SVE", ggml_cpu_has_sve());
    add("SME", ggml_cpu_has_sme());
    return env->NewStringUTF(features.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(
    JNIEnv *env, jobject, jstring path_, jint n_ctx, jint n_threads) {

    g_last_error.clear();
    const std::string path = jstring_to_utf8(env, path_);

    llama_model_params mparams = llama_model_default_params();
    // No GPU offload: Android GPU backends aren't built here, so all layers
    // stay on the CPU.
    mparams.n_gpu_layers = 0;
    // Memory-map rather than mlock: keeps resident memory down and lets the
    // kernel page weights in and out, which is what makes a multi-gigabyte
    // model usable on a phone — and is why free RAM Plus counts toward the
    // load budget in LlamaCppEngine.
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;

    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        g_last_error = "llama.cpp could not read this GGUF file. It may be "
                       "corrupt, truncated, or use an unsupported quantisation.";
        LOGE("failed to load model: %s", path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        llama_model_free(model);
        g_last_error = "Not enough memory to create a context for this model. "
                       "Try a smaller context size or a smaller model.";
        return 0;
    }

    auto *session = new Session();
    session->model = model;
    session->ctx = ctx;
    session->vocab = llama_model_get_vocab(model);

    LOGI("loaded %s (n_ctx=%u)", path.c_str(), llama_n_ctx(ctx));
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return;
    if (session->ctx) llama_free(session->ctx);
    if (session->model) llama_model_free(session->model);
    delete session;
}

/// Retunes the thread count on a live context so the app can back off as the
/// device heats up, without unloading the model.
JNIEXPORT void JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeSetThreads(
    JNIEnv *, jobject, jlong handle, jint n_threads) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr) return;
    if (n_threads < 1) return;
    llama_set_n_threads(session->ctx, n_threads, n_threads);
}

JNIEXPORT void JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeStop(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session) session->stop.store(true);
}

/// Drops the conversation history so the next turn starts fresh.
JNIEXPORT void JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeResetContext(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr) return;
    llama_memory_clear(llama_get_memory(session->ctx), /*data=*/true);
    session->n_past = 0;
}


/**
 * Generates a reply, invoking callback.onToken(String) for each piece.
 * The callback returns false to request an early stop.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeGenerate(
    JNIEnv *env, jobject, jlong handle,
    jstring prompt_, jstring system_,
    jint max_tokens, jfloat temperature, jint top_k, jfloat top_p, jint seed,
    jobject callback) {

    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr) {
        g_last_error = "Model is not loaded.";
        return JNI_FALSE;
    }

    session->stop.store(false);
    g_last_error.clear();
    g_stop_reason.store(STOP_TOKEN_CAP);

    jclass callback_class = env->GetObjectClass(callback);
    // Bytes, not String: the JVM decodes them as real UTF-8, which handles
    // 4-byte sequences (emoji) that NewStringUTF's Modified UTF-8 cannot.
    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "([B)Z");
    if (on_token == nullptr) {
        g_last_error = "Internal error: token callback not found.";
        return JNI_FALSE;
    }

    const std::string user = jstring_to_utf8(env, prompt_);
    const std::string system_prompt = jstring_to_utf8(env, system_);

    // Only the first turn carries BOS and the system block; later turns append
    // to a cache that already holds both.
    const bool first_turn = session->n_past == 0;
    const std::string formatted =
        apply_chat_template(session->model, system_prompt, user, first_turn);

    std::vector<llama_token> tokens = tokenize(session->vocab, formatted, first_turn);
    if (tokens.empty()) {
        g_last_error = "The prompt produced no tokens.";
        return JNI_FALSE;
    }

    const uint32_t n_ctx = llama_n_ctx(session->ctx);
    if (session->n_past + static_cast<llama_pos>(tokens.size()) + max_tokens >
        static_cast<llama_pos>(n_ctx)) {
        // Simplest correct policy: start over rather than silently truncating
        // mid-conversation and confusing the model.
        llama_memory_clear(llama_get_memory(session->ctx), true);
        session->n_past = 0;
        // Starting over means the system block has to go back in.
        const std::string restart =
            apply_chat_template(session->model, system_prompt, user, true);
        tokens = tokenize(session->vocab, restart, true);
    }

    // Prompt ingestion, in n_batch-sized chunks. A single decode larger than
    // n_batch is rejected by llama.cpp, and web-search grounding routinely
    // pushes a prompt well past 512 tokens.
    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(session->ctx));
    for (size_t offset = 0; offset < tokens.size();) {
        const int32_t chunk = std::min<int32_t>(n_batch, tokens.size() - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, chunk);
        if (llama_decode(session->ctx, batch) != 0) {
            g_last_error = "Failed to process the prompt (context may be too small).";
            return JNI_FALSE;
        }
        offset += static_cast<size_t>(chunk);
        session->n_past += chunk;
    }

    // Sampler chain: penalties -> top-k -> top-p -> temperature -> distribution.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
    // There was no repetition penalty at all, which is llama.cpp's single most
    // common cause of a model looping a phrase or padding an answer with
    // restatements. These are llama-cli's own defaults.
    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_penalties(
            /*penalty_last_n=*/64, /*penalty_repeat=*/1.1f,
            /*penalty_freq=*/0.0f, /*penalty_present=*/0.0f));
    if (top_k > 0) llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    if (top_p > 0.0f && top_p < 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    }
    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    bool ok = true;
    // Holds the tail of a multi-byte character split across two tokens.
    std::string pending;
    // The token that ends the assistant's turn, so the transcript in the cache
    // stays well-formed for the next one.
    llama_token closing_token = LLAMA_TOKEN_NULL;

    for (int32_t generated = 0; generated < max_tokens; ++generated) {
        if (session->stop.load()) {
            g_stop_reason.store(STOP_CANCELLED);
            break;
        }

        const llama_token token = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(session->vocab, token)) {
            closing_token = token;
            g_stop_reason.store(STOP_END_OF_TURN);
            break;
        }

        pending += token_to_text(session->vocab, token);
        const size_t emit = utf8_complete_prefix(pending);
        if (emit > 0) {
            jbyteArray chunk = env->NewByteArray(static_cast<jsize>(emit));
            if (chunk == nullptr) {
                g_last_error = "Out of memory while streaming the reply.";
                ok = false;
                break;
            }
            env->SetByteArrayRegion(
                chunk, 0, static_cast<jsize>(emit),
                reinterpret_cast<const jbyte *>(pending.data()));
            const jboolean keep_going = env->CallBooleanMethod(callback, on_token, chunk);
            env->DeleteLocalRef(chunk);
            pending.erase(0, emit);

            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                ok = false;
                break;
            }
            if (keep_going == JNI_FALSE) {
                g_stop_reason.store(STOP_CANCELLED);
                break;
            }
        }

        llama_sampler_accept(sampler, token);

        llama_token next = token;
        llama_batch step = llama_batch_get_one(&next, 1);
        if (llama_decode(session->ctx, step) != 0) {
            g_last_error = "Generation stopped: the context is full.";
            ok = false;
            break;
        }
        session->n_past += 1;
    }

    // Close the assistant turn in the KV cache. Without this the reply ran
    // straight into the next user turn with no <|im_end|> between them, and the
    // model spent every turn after the first reading a malformed transcript.
    if (closing_token == LLAMA_TOKEN_NULL) {
        // Stopped on the token cap or by the user, so the model never emitted
        // an end marker; supply the vocabulary's own.
        closing_token = llama_vocab_eot(session->vocab);
        if (closing_token == LLAMA_TOKEN_NULL) {
            closing_token = llama_vocab_eos(session->vocab);
        }
    }
    if (closing_token != LLAMA_TOKEN_NULL) {
        llama_batch closing = llama_batch_get_one(&closing_token, 1);
        if (llama_decode(session->ctx, closing) == 0) {
            session->n_past += 1;
        }
    }

    llama_sampler_free(sampler);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
