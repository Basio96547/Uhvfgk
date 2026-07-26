// JNI bridge to llama.cpp, giving the app GGUF support alongside the
// MediaPipe .task path.
//
// The surface is deliberately small: load a model, generate with streaming
// callbacks, reset the KV cache, free. Everything stateful lives in Session,
// whose address is handed to Kotlin as an opaque long.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "llamabridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    // Set from another thread to interrupt the decode loop.
    std::atomic<bool> stop{false};
    // Position of the next token in the KV cache, so multi-turn chat continues
    // instead of re-processing the whole conversation each time.
    llama_pos n_past = 0;
    std::string last_error;
};

// Kept so the last failure can be reported to Kotlin after a null return.
std::string g_last_error;

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
std::string apply_chat_template(
    llama_model *model, const std::string &system_prompt, const std::string &user) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) {
        // Base model with no template: fall back to the raw prompt.
        if (system_prompt.empty()) return user;
        return system_prompt + "\n\n" + user;
    }

    std::vector<llama_chat_message> messages;
    if (!system_prompt.empty()) {
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
        return system_prompt.empty() ? user : system_prompt + "\n\n" + user;
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

JNIEXPORT jlong JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeLoadModel(
    JNIEnv *env, jobject, jstring path_, jint n_ctx, jint n_threads) {

    g_last_error.clear();
    const std::string path = jstring_to_utf8(env, path_);

    llama_model_params mparams = llama_model_default_params();
    // No GPU offload: Android GPU backends aren't built here, so all layers
    // stay on the CPU. mmap keeps resident memory down and lets the kernel
    // page weights in and out, which matters on a phone.
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

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

JNIEXPORT jint JNICALL
Java_com_example_ondevicellm_llm_LlamaBridge_nativeContextSize(JNIEnv *, jobject, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr) return 0;
    return static_cast<jint>(llama_n_ctx(session->ctx));
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

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
    if (on_token == nullptr) {
        g_last_error = "Internal error: token callback not found.";
        return JNI_FALSE;
    }

    const std::string user = jstring_to_utf8(env, prompt_);
    const std::string system_prompt = jstring_to_utf8(env, system_);
    const std::string formatted = apply_chat_template(session->model, system_prompt, user);

    // Only the first turn carries BOS; later turns continue the same sequence.
    const bool first_turn = session->n_past == 0;
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
        tokens = tokenize(session->vocab, formatted, true);
    }

    // Prompt ingestion.
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(session->ctx, batch) != 0) {
        g_last_error = "Failed to process the prompt (context may be too small).";
        return JNI_FALSE;
    }
    session->n_past += static_cast<llama_pos>(tokens.size());

    // Sampler chain: top-k -> top-p -> temperature -> distribution.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sparams);
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
    for (int32_t generated = 0; generated < max_tokens; ++generated) {
        if (session->stop.load()) break;

        const llama_token token = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(session->vocab, token)) break;

        const std::string piece = token_to_text(session->vocab, token);
        if (!piece.empty()) {
            jstring jpiece = env->NewStringUTF(piece.c_str());
            const jboolean keep_going = env->CallBooleanMethod(callback, on_token, jpiece);
            env->DeleteLocalRef(jpiece);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                ok = false;
                break;
            }
            if (keep_going == JNI_FALSE) break;
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

    llama_sampler_free(sampler);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
