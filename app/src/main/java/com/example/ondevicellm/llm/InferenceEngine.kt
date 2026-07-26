package com.example.ondevicellm.llm

import android.content.Context
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.model.ModelSpec
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/**
 * Wraps one loaded model. Owns the MediaPipe engine plus the current session,
 * and is responsible for closing both.
 *
 * Instances are created by [InferenceEngine.load] and are **not** thread-safe:
 * the repository serialises access.
 */
class InferenceEngine private constructor(
    private val llmInference: LlmInference,
    val spec: ModelSpec,
    val backend: ResolvedBackend,
) {

    private var session: LlmInferenceSession = newSession()
    private var closed = false

    private fun newSession(): LlmInferenceSession {
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(spec.topK)
            .setTopP(spec.topP)
            .setTemperature(spec.temperature)
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, options)
    }

    /**
     * Streams a response to [prompt].
     *
     * [onDelta] receives reasoning and answer text separately — reasoning is
     * extracted from `<think>` blocks when the model emits them.
     */
    fun generate(
        prompt: String,
        systemPrompt: String?,
        thinkingEnabled: Boolean,
        onDelta: (thinking: String, answer: String, done: Boolean) -> Unit,
    ) {
        check(!closed) { "Engine already closed" }

        val parser = if (spec.supportsThinking && thinkingEnabled) {
            ThinkingStreamParser()
        } else {
            null
        }

        val fullPrompt = buildString {
            if (!systemPrompt.isNullOrBlank()) {
                append(systemPrompt.trim())
                append("\n\n")
            }
            append(prompt)
        }

        session.addQueryChunk(fullPrompt)
        session.generateResponseAsync { partial, done ->
            if (parser == null) {
                onDelta("", partial, done)
                return@generateResponseAsync
            }

            val delta = parser.consume(partial)
            if (!delta.isEmpty) {
                onDelta(delta.thinking, delta.answer, false)
            }
            if (done) {
                val tail = parser.flush()
                onDelta(tail.thinking, tail.answer, true)
            }
        }
    }

    /** Clears conversation history by starting a fresh session. */
    fun resetSession() {
        if (closed) return
        session.close()
        session = newSession()
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { session.close() }
        runCatching { llmInference.close() }
    }

    class ModelLoadException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    companion object {

        /**
         * Loads [spec] and returns a ready engine.
         *
         * @throws ModelLoadException with an actionable message when the file is
         *   missing/unreadable, memory is clearly insufficient, or the runtime
         *   rejects the bundle.
         */
        fun load(context: Context, spec: ModelSpec, device: DeviceSnapshot): InferenceEngine {
            val file = File(spec.path)
            if (!file.exists()) {
                throw ModelLoadException("Model file not found:\n${spec.path}")
            }
            if (!file.canRead()) {
                throw ModelLoadException(
                    "Model file is not readable by this app:\n${spec.path}\n\n" +
                        "Re-import it through \"Add model\" so it lives in app storage."
                )
            }

            assertEnoughMemory(context, file.length())

            val backend = BackendResolver.resolve(spec.backend, device)

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(spec.path)
                .setMaxTokens(spec.maxTokens)
                .setPreferredBackend(backend.actual)
                .build()

            val inference = try {
                LlmInference.createFromOptions(context, options)
            } catch (e: Throwable) {
                throw ModelLoadException(
                    "The runtime could not load this model.\n\n" +
                        "${e.message}\n\n" +
                        "Check that it is a MediaPipe-compatible .task bundle and " +
                        "that the selected backend (${backend.actualLabel}) is supported.",
                    e,
                )
            }

            return InferenceEngine(inference, spec, backend)
        }

        /**
         * Rejects loads that cannot possibly fit.
         *
         * Model weights are memory-mapped, so pages can be backed by extended
         * memory (Samsung RAM Plus / zram, reported as swap) rather than physical
         * RAM. The budget therefore counts available RAM **plus** free swap.
         */
        private fun assertEnoughMemory(context: Context, modelBytes: Long) {
            val memory = DeviceCapabilities.readMemory(context)
            val budget = memory.effectiveAvailableBytes

            if (budget in 1 until modelBytes) {
                throw ModelLoadException(
                    buildString {
                        append("Not enough memory to load this model.\n\n")
                        append("Model: ${modelBytes.formatBytes()}\n")
                        append("Available RAM: ${memory.availableRamBytes.formatBytes()}\n")
                        if (memory.hasExtendedMemory) {
                            append("Free extended memory (RAM Plus/zram): ")
                            append("${memory.swapFreeBytes.formatBytes()}\n")
                        } else {
                            append("Extended memory (RAM Plus): not enabled\n")
                        }
                        append("\nClose background apps, enable RAM Plus in ")
                        append("Settings › Device care › Memory, or use a smaller ")
                        append("quantized model.")
                    }
                )
            }
        }
    }
}
