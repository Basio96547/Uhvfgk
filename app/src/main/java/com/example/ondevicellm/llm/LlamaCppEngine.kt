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

    /** 0 = finished the turn, 1 = hit the token cap, 2 = stopped. */
    external fun nativeLastStopReason(): Int

    /** @param nGpuLayers layers to offload; 0 keeps everything on the CPU. */
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long

    /** Compute devices ggml really registered, e.g. "CPU, GPUOpenCL". */
    external fun nativeBackends(): String

    /** Layers that actually ended up on the GPU. 0 means the load fell back. */
    external fun nativeGpuLayersUsed(): Int
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
        val note = when {
            !ok -> bridge.nativeLastError().takeIf { it.isNotBlank() }?.let { "\n[$it]" }.orEmpty()
            // A reply chopped off by the cap is indistinguishable from a short
            // answer, which is exactly how a truncation reads as stupidity.
            bridge.nativeLastStopReason() == STOP_TOKEN_CAP ->
                "\n\n[${Localization.strings.replyTruncated}]"
            else -> ""
        }
        onDelta(
            if (keepThinking) tail.thinking else "",
            tail.answer + note,
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

        /** Native stop reason meaning the token cap was reached. */
        private const val STOP_TOKEN_CAP = 1

        /**
         * Context window used when a model doesn't warrant a larger one.
         *
         * Raised from 4096: a reasoning turn spends most of its budget inside
         * <think>, and once the window fills the session restarts and the
         * conversation is silently forgotten. The KV cache for a 4B model at
         * this size is a few hundred MB more, which is what RAM Plus is for.
         */
        private const val DEFAULT_CONTEXT_TOKENS = 6144

        /**
         * "Offload everything you can."
         *
         * llama.cpp clamps this to the model's real layer count, so a number
         * far above it means "all of them" without having to read the GGUF
         * header first.
         */
        private const val ALL_LAYERS = 999

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

            // Which devices ggml registered — not what the phone could in
            // principle do. The OpenCL backend appears here only when the ICD
            // loader found a vendor driver and that driver enumerated a device.
            val devices = runCatching { bridge.nativeBackends() }.getOrDefault("")
            val gpuPresent = devices.contains("opencl", ignoreCase = true) ||
                devices.contains("gpu", ignoreCase = true)

            // Offload only when the user asked and a device is actually there.
            // AUTO stays on the CPU until the GPU path has been proven on real
            // hardware: a wrong answer fast is worse than a right answer slow.
            val wantsGpu = spec.backend == BackendPref.GPU
            val requestedLayers = if (wantsGpu && gpuPresent) ALL_LAYERS else 0

            val handle = bridge.nativeLoadModel(spec.path, contextTokens, threads, requestedLayers)
            if (handle == 0L) {
                val detail = bridge.nativeLastError().ifBlank { "Unknown error." }
                throw ModelLoadException(Localization.strings.ggufLoadFailed(detail))
            }

            // What ran, read back from the loader rather than assumed: a GPU
            // load that failed silently falls back to CPU inside the bridge,
            // and reporting the request would then be a lie.
            val gpuLayers = runCatching { bridge.nativeGpuLayersUsed() }.getOrDefault(0)
            val features = runCatching { bridge.nativeCpuFeatures() }.getOrDefault("")
            val strings = Localization.strings

            val note = buildString {
                when {
                    gpuLayers > 0 -> append(strings.ggufGpuOffload(devices))
                    wantsGpu && !gpuPresent -> append(strings.ggufNoGpuDevice)
                    wantsGpu -> append(strings.ggufGpuFellBack)
                    spec.backend != BackendPref.CPU && spec.backend != BackendPref.AUTO ->
                        append(strings.ggufBackendIgnored(spec.backend.label(strings)))
                }
                append(strings.threadsOfCores(threads, cores))
                if (features.isNotBlank()) append(" · $features")
                append(".")
            }
            val backend = ResolvedBackend(
                actualLabel = when {
                    gpuLayers > 0 -> "GPU · llama.cpp (OpenCL)"
                    features.contains("i8mm") || features.contains("dotprod") ->
                        "CPU · llama.cpp (accelerated)"
                    else -> "CPU · llama.cpp"
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
