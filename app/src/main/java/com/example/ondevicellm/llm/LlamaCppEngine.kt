package com.example.ondevicellm.llm

import android.content.Context
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.model.BackendPref
import com.example.ondevicellm.model.ModelSpec
import java.io.File

/** Thin JNI surface. All methods are implemented in `llama_bridge.cpp`. */
internal class LlamaBridge {

    external fun nativeInit()
    external fun nativeLastError(): String
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeStop(handle: Long)
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

    /** Called from the native decode loop; return false to stop generating. */
    interface TokenCallback {
        fun onToken(piece: String): Boolean
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

    override fun generate(
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

        val callback = object : LlamaBridge.TokenCallback {
            override fun onToken(piece: String): Boolean {
                if (closed) return false
                if (parser == null) {
                    onDelta("", piece, false)
                } else {
                    val delta = parser.consume(piece)
                    if (!delta.isEmpty) onDelta(delta.thinking, delta.answer, false)
                }
                return true
            }
        }

        val ok = try {
            bridge.nativeGenerate(
                handle = handle,
                prompt = prompt,
                system = systemPrompt.orEmpty(),
                maxTokens = spec.maxTokens,
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

        val tail = parser?.flush()
        val failureNote = if (!ok) {
            bridge.nativeLastError().takeIf { it.isNotBlank() }?.let { "\n[$it]" }.orEmpty()
        } else {
            ""
        }
        onDelta(tail?.thinking.orEmpty(), tail?.answer.orEmpty() + failureNote, true)
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
                throw ModelLoadException(
                    "The GGUF runtime isn't available in this build.\n\n" +
                        "Use a MediaPipe .task model instead, or rebuild the app with " +
                        "the native component enabled."
                )
            }

            val file = File(spec.path)
            if (!file.isFile || !file.canRead()) {
                throw ModelLoadException("Model file not readable:\n${spec.path}")
            }

            assertEnoughMemory(context, file.length())

            val bridge = LlamaBridge()
            bridge.nativeInit()

            // Leave two cores for the UI and audio; more threads than physical
            // big cores usually makes decode slower, not faster.
            val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 8)
            val contextTokens = maxOf(DEFAULT_CONTEXT_TOKENS, spec.maxTokens * 2)

            val handle = bridge.nativeLoadModel(spec.path, contextTokens, threads)
            if (handle == 0L) {
                val detail = bridge.nativeLastError().ifBlank { "Unknown error." }
                throw ModelLoadException("Could not load this GGUF model.\n\n$detail")
            }

            // llama.cpp runs on the CPU here: no GPU backend is compiled in, so
            // report that honestly rather than echoing the user's preference.
            val note = when (spec.backend) {
                BackendPref.CPU, BackendPref.AUTO -> ""
                else -> "GGUF models run on the CPU in this build; " +
                    "the ${spec.backend.label} preference doesn't apply."
            }
            val backend = ResolvedBackend(
                requested = spec.backend,
                actualLabel = "CPU · llama.cpp",
                note = note,
            )

            return LlamaCppEngine(spec, backend, bridge, handle)
        }

        /**
         * GGUF weights are memory-mapped, so free swap (Samsung RAM Plus) counts
         * toward what can be loaded, same as the MediaPipe path.
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
                            append("Free RAM Plus: ${memory.swapFreeBytes.formatBytes()}\n")
                        } else {
                            append("RAM Plus: not enabled\n")
                        }
                        append("\nUse a smaller quantisation (Q4_K_M instead of Q8_0), ")
                        append("close background apps, or enable RAM Plus.")
                    }
                )
            }
        }
    }
}
