package com.basel.ai.llm

import android.content.Context
import com.basel.ai.core.DeviceCapabilities
import com.basel.ai.core.DeviceSnapshot
import com.basel.ai.core.Localization
import com.basel.ai.core.formatBytes
import com.basel.ai.model.ModelFormat
import com.basel.ai.model.ModelSpec
import com.basel.ai.model.rejectionMessage
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
    override val spec: ModelSpec,
    override val backend: ResolvedBackend,
) : TextEngine {

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
    override fun generate(
        prompt: String,
        systemPrompt: String?,
        thinkingEnabled: Boolean,
        // MediaPipe fixes maxTokens when the engine is created, so a per-turn
        // cap can't be applied here. The /no_think directive still shortens
        // trivial answers for reasoning models.
        maxTokens: Int,
        onDelta: (thinking: String, answer: String, done: Boolean) -> Unit,
    ) {
        check(!closed) { "Engine already closed" }

        // Parsed unconditionally: a model that ignores /no_think still emits
        // <think>…</think>, and without a parser it showed up inline in the
        // reply even with reasoning switched off.
        val parser = ThinkingStreamParser()
        val keepThinking = thinkingEnabled

        val fullPrompt = buildString {
            if (!systemPrompt.isNullOrBlank()) {
                append(systemPrompt.trim())
                append("\n\n")
            }
            append(prompt)
            if (spec.supportsThinking) append(QueryRouter.thinkingDirective(thinkingEnabled))
        }

        session.addQueryChunk(fullPrompt)
        session.generateResponseAsync { partial, done ->
            val delta = parser.consume(partial)
            if (!delta.isEmpty) {
                onDelta(if (keepThinking) delta.thinking else "", delta.answer, false)
            }
            if (done) {
                val tail = parser.flush()
                onDelta(if (keepThinking) tail.thinking else "", tail.answer, true)
            }
        }
    }

    /**
     * MediaPipe 0.10.x exposes no cancel on an in-flight generation, so this is
     * a no-op; the UI stays responsive because decode runs off the main thread.
     */
    override fun stop() = Unit

    /** Clears conversation history by starting a fresh session. */
    override fun resetSession() {
        if (closed) return
        session.close()
        session = newSession()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { session.close() }
        runCatching { llmInference.close() }
    }

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
            val s = Localization.strings
            if (!file.exists()) {
                throw ModelLoadException(s.modelFileNotFound(spec.path))
            }
            if (!file.canRead()) {
                throw ModelLoadException(s.modelFileNotReadable(spec.path))
            }

            // Checked before anything expensive: the runtime's own failure for a
            // wrong container is an opaque native error, so identify the format
            // here and say what to download instead.
            val format = ModelFormat.detect(file)
            format.rejectionMessage(file.name)?.let { throw ModelLoadException(it) }

            assertEnoughMemory(context, file.length())

            // The file size is what turns AUTO into an actual decision.
            val backend = BackendResolver.resolve(spec.backend, device, file.length())

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(spec.path)
                .setMaxTokens(spec.maxTokens)
                .setPreferredBackend(backend.backend)
                .build()

            val inference = try {
                LlmInference.createFromOptions(context, options)
            } catch (e: Throwable) {
                throw ModelLoadException(
                    s.taskLoadFailed(e.message.orEmpty(), backend.resolved.actualLabel),
                    e,
                )
            }

            return InferenceEngine(inference, spec, backend.resolved)
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
                val s = Localization.strings
                throw ModelLoadException(
                    buildString {
                        append(s.notEnoughMemoryTitle)
                        append("\n\n")
                        appendLine(s.memoryLine(s.model, modelBytes.formatBytes()))
                        appendLine(
                            s.memoryLine(
                                s.availableRamLabel,
                                memory.availableRamBytes.formatBytes(),
                            )
                        )
                        if (memory.hasExtendedMemory) {
                            appendLine(
                                s.memoryLine(
                                    s.freeExtendedLabel,
                                    memory.swapFreeBytes.formatBytes(),
                                )
                            )
                        } else {
                            appendLine(s.ramPlusNotEnabled)
                        }
                        append("\n")
                        append(s.notEnoughMemoryAdvice)
                    }
                )
            }
        }
    }
}
