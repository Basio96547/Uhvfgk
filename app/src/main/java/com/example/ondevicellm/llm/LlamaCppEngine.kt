package com.example.ondevicellm.llm

import android.content.Context
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.Localization
import com.example.ondevicellm.core.ThermalGuard
import com.example.ondevicellm.core.ThermalLevel
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.model.BackendPref
import com.example.ondevicellm.model.ModelSpec
import java.io.File

/** Thin JNI surface. All methods are implemented in `llama_bridge.cpp`. */
internal class LlamaBridge {

    external fun nativeInit()
    external fun nativeLastError(): String

    /** SIMD extensions the compiled kernels are really using on this CPU. */
    external fun nativeCpuFeatures(): String

    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeStop(handle: Long)
    external fun nativeSetThreads(handle: Long, nThreads: Int)
    external fun nativeResetContext(handle: Long)

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        system: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        seed: Int,
        callback: TokenCallback,
    ): Boolean

    /**
     * Called from the native decode loop; return false to stop generating.
     *
     * Takes bytes rather than a String on purpose. A token is a run of bytes,
     * so a multi-byte character can straddle two of them; the native side
     * hands over only complete UTF-8, and the JVM decodes it properly —
     * including 4-byte sequences, which JNI's NewStringUTF cannot represent.
     */
    interface TokenCallback {
        fun onToken(piece: ByteArray): Boolean
    }

    companion object {
        @Volatile
        private var libraryLoaded: Boolean? = null

        /**
         * True when the native library is present and loadable. Kept optional so
         * a build without the NDK component still runs — the app then simply
         * reports that GGUF is unavailable instead of crashing at startup.
         */
        fun isAvailable(): Boolean {
            libraryLoaded?.let { return it }
            return synchronized(this) {
                libraryLoaded ?: runCatching {
                    System.loadLibrary("llamabridge")
                    true
                }.getOrDefault(false).also { libraryLoaded = it }
            }
        }
    }
}

/**
 * Runs GGUF models through llama.cpp.
 *
 * llama.cpp keeps its own KV cache, so multi-turn chat continues from where the
 * previous turn ended rather than re-processing the whole conversation.
 * Generation is synchronous on the calling thread; the repository already runs
 * it on an IO dispatcher.
 */
class LlamaCppEngine private constructor(
    override val spec: ModelSpec,
    override val backend: ResolvedBackend,
    private val bridge: LlamaBridge,
    private var handle: Long,
) : TextEngine {

    @Volatile
    private var closed = false

    /** Threads currently configured, so we only cross JNI when it changes. */
    private var activeThreads = 0

    /**
     * Retunes the decode thread count for the current thermal state. Called
     * before each turn and whenever the device heats up mid-generation.
     */
    fun applyThermalLevel(level: ThermalLevel) {
        if (closed) return
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = ThermalGuard.threadBudget(cores, level)
        if (threads != activeThreads) {
            bridge.nativeSetThreads(handle, threads)
            activeThreads = threads
        }
    }

    override fun generate(
        prompt: String,
        systemPrompt: String?,
        thinkingEnabled: Boolean,
        maxTokens: Int,
        onDelta: (thinking: String, answer: String, done: Boolean) -> Unit,
    ) {
        check(!closed) { "Engine already closed" }

        // Always parse, never conditionally. A model that ignores /no_think
        // still wraps its reasoning in <think>…</think>, and with no parser
        // that block landed verbatim in the reply — reasoning and answer
        // jumbled together in the bubble, with thinking supposedly off.
        val parser = ThinkingStreamParser()
        // Reasoning was not asked for this turn, so any trace that arrives
        // anyway is dropped rather than shown.
        val keepThinking = thinkingEnabled

        val callback = object : LlamaBridge.TokenCallback {
            override fun onToken(piece: ByteArray): Boolean {
                if (closed) return false
                val delta = parser.consume(String(piece, Charsets.UTF_8))
                if (!delta.isEmpty) {
                    onDelta(if (keepThinking) delta.thinking else "", delta.answer, false)
                }
                return true
            }
        }

        val ok = try {
            bridge.nativeGenerate(
                handle = handle,
                // Reasoning models read /think and /no_think from the user turn;
                // without it a Qwen3-class model deliberates over "hello".
                prompt = if (spec.supportsThinking) {
                    prompt + QueryRouter.thinkingDirective(thinkingEnabled)
                } else {
                    prompt
                },
                system = systemPrompt.orEmpty(),
                maxTokens = maxTokens,
                temperature = spec.temperature,
                topK = spec.topK,
                topP = spec.topP,
                // Varying the seed per call keeps repeated questions from
                // producing identical wording.
                seed = (System.nanoTime() and 0x7FFFFFFF).toInt(),
                callback = callback,
            )
        } catch (e: Throwable) {
            onDelta("", "\n[error: ${e.message}]", true)
            return
        }

        val tail = parser.flush()
        val failureNote = if (!ok) {
            bridge.nativeLastError().takeIf { it.isNotBlank() }?.let { "\n[$it]" }.orEmpty()
        } else {
            ""
        }
        onDelta(
            if (keepThinking) tail.thinking else "",
            tail.answer + failureNote,
            true,
        )
    }

    override fun stop() {
        if (!closed) bridge.nativeStop(handle)
    }

    override fun resetSession() {
        if (!closed) bridge.nativeResetContext(handle)
    }

    override fun close() {
        if (closed) return
        closed = true
        bridge.nativeFree(handle)
        handle = 0L
    }

    companion object {

        /** Context window used when a model doesn't warrant a larger one. */
        private const val DEFAULT_CONTEXT_TOKENS = 4096

        fun load(context: Context, spec: ModelSpec, device: DeviceSnapshot): LlamaCppEngine {
            if (!LlamaBridge.isAvailable()) {
                throw ModelLoadException(Localization.strings.ggufRuntimeMissing)
            }

            val file = File(spec.path)
            if (!file.isFile || !file.canRead()) {
                throw ModelLoadException(Localization.strings.modelFileNotReadable(spec.path))
            }

            assertEnoughMemory(context, file.length())

            val bridge = LlamaBridge()
            bridge.nativeInit()

            // Deliberately below the core count: see ThermalGuard.threadBudget.
            val cores = Runtime.getRuntime().availableProcessors()
            val threads = ThermalGuard.threadBudget(cores, ThermalLevel.NORMAL)
            val contextTokens = maxOf(DEFAULT_CONTEXT_TOKENS, spec.maxTokens * 2)

            val handle = bridge.nativeLoadModel(spec.path, contextTokens, threads)
            if (handle == 0L) {
                val detail = bridge.nativeLastError().ifBlank { "Unknown error." }
                throw ModelLoadException(Localization.strings.ggufLoadFailed(detail))
            }

            // llama.cpp runs on the CPU here: no GPU backend is compiled in, so
            // report that honestly rather than echoing the user's preference —
            // but say which SIMD kernels are live, because that is what
            // actually decides how fast this model runs.
            val features = runCatching { bridge.nativeCpuFeatures() }.getOrDefault("")
            val strings = Localization.strings
            val note = buildString {
                if (spec.backend != BackendPref.CPU && spec.backend != BackendPref.AUTO) {
                    append(strings.ggufBackendIgnored(spec.backend.label(strings)))
                }
                append(strings.threadsOfCores(threads, cores))
                if (features.isNotBlank()) append(" · $features")
                append(".")
            }
            val backend = ResolvedBackend(
                requested = spec.backend,
                actualLabel = if (features.contains("i8mm") || features.contains("dotprod")) {
                    "CPU · llama.cpp (accelerated)"
                } else {
                    "CPU · llama.cpp"
                },
                note = note,
            )

            return LlamaCppEngine(spec, backend, bridge, handle)
                .also { it.activeThreads = threads }
        }

        /**
         * GGUF weights are memory-mapped, so free swap (Samsung RAM Plus) counts
         * toward what can be loaded, same as the MediaPipe path.
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
