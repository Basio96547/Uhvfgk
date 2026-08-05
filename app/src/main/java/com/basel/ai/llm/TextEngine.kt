package com.basel.ai.llm

import com.basel.ai.model.ModelSpec

/**
 * A loaded text-generation model.
 *
 * Two runtimes implement this: [InferenceEngine] (MediaPipe, `.task` bundles)
 * and [LlamaCppEngine] (llama.cpp, `.gguf` files). The rest of the app is
 * written against this interface so the format a user picks is not visible
 * above the `llm` package.
 */
interface TextEngine {

    val spec: ModelSpec

    /** Which runtime and accelerator this model actually ended up on. */
    val backend: ResolvedBackend

    /**
     * Streams a reply to [prompt].
     *
     * [onDelta] receives reasoning and answer text separately; reasoning is
     * extracted from `<think>` blocks when the model emits them. It is called
     * with `done = true` exactly once, at the end.
     */
    fun generate(
        prompt: String,
        systemPrompt: String?,
        thinkingEnabled: Boolean,
        /** Cap for this turn; the router lowers it for trivial messages. */
        maxTokens: Int,
        onDelta: (thinking: String, answer: String, done: Boolean) -> Unit,
    )

    /** Interrupts an in-flight generation. Safe to call when idle. */
    fun stop()

    /** Clears conversation history. */
    fun resetSession()

    fun close()
}

/** Raised when a model cannot be loaded, carrying a message meant for the user. */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
