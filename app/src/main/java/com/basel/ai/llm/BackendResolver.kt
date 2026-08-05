package com.basel.ai.llm

import com.basel.ai.core.DeviceSnapshot
import com.basel.ai.core.Localization
import com.basel.ai.model.BackendPref
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * Result of mapping a user's [BackendPref] onto a backend the runtime can
 * really use, plus an explanation of any substitution that happened.
 */
data class ResolvedBackend(
    /** What actually runs, e.g. "GPU" or "CPU · llama.cpp". */
    val actualLabel: String,
    /** User-facing explanation. Empty when the request was honoured as-is. */
    val note: String,
)

/** MediaPipe-specific resolution: the enum it needs, plus what to show the user. */
data class MediaPipeBackend(
    val backend: LlmInference.Backend,
    val resolved: ResolvedBackend,
)

/**
 * Adapts [BackendPlanner]'s decision to MediaPipe's two-value backend enum.
 *
 * **On NPU support — read this before filing a bug.** The MediaPipe LLM
 * Inference API used here exposes exactly two backends: `CPU` and `GPU`. There
 * is no public NPU backend in `tasks-genai`. Running an LLM on the Snapdragon
 * 8 Elite's Hexagon NPU requires Qualcomm's AI Engine Direct (QNN) / Genie
 * runtime from the Qualcomm AI Hub, with a model compiled into a QNN context
 * binary for that specific Hexagon architecture — a different SDK and a
 * different model artifact, neither of which is a public Maven dependency.
 *
 * So this resolver does two honest things:
 *  1. It *detects* whether a vendor NPU runtime exists on the device and
 *     surfaces that in the UI (see `DeviceCapabilities`).
 *  2. When NPU is requested, it falls back to the fastest backend that really
 *     is available, and says so plainly instead of silently pretending.
 *
 * The [NpuRuntime] hook below is the integration point for wiring in QNN later.
 */
object BackendResolver {

    /**
     * @param modelBytes size of the model file; 0 when unknown. Passing it is
     *   what makes AUTO a real decision rather than "GPU if there's a driver".
     */
    fun resolve(
        pref: BackendPref,
        device: DeviceSnapshot,
        modelBytes: Long = 0L,
    ): MediaPipeBackend {
        val plan = BackendPlanner.plan(
            pref = pref,
            gpuCapable = device.accelerators.vulkanAvailable ||
                device.accelerators.openClAvailable,
            npuRuntimePresent = device.accelerators.hasNpuRuntime,
            npuLinked = NpuRuntime.isAvailable(),
            modelBytes = modelBytes,
            availableBytes = device.memory.effectiveAvailableBytes,
            socLabel = device.socModel,
            s = Localization.strings,
        )

        val backend = when (plan.target) {
            ComputeTarget.GPU -> LlmInference.Backend.GPU
            ComputeTarget.CPU -> LlmInference.Backend.CPU
        }
        return MediaPipeBackend(
            backend = backend,
            resolved = ResolvedBackend(
                actualLabel = if (plan.target == ComputeTarget.GPU) "GPU" else "CPU",
                note = plan.note,
            ),
        )
    }
}

/**
 * Integration point for a real NPU runtime (Qualcomm QNN / Genie).
 *
 * To enable true NPU execution on a Snapdragon 8 Elite device:
 *  1. Obtain the QNN / Genie runtime `.so` files and the Java/JNI bindings from
 *     the Qualcomm AI Hub, and drop them into `app/src/main/jniLibs/arm64-v8a`.
 *  2. Compile your model into a QNN context binary targeting the device's
 *     Hexagon architecture (reported on the Device screen).
 *  3. Implement [isAvailable] to return true once those libraries load, and
 *     route generation through the Genie API instead of MediaPipe in
 *     `InferenceEngine`.
 *
 * Kept as a stub so the rest of the app already models "NPU" as a first-class
 * choice rather than needing a refactor later.
 */
object NpuRuntime {
    /** True only when a real NPU runtime has been linked into the app. */
    fun isAvailable(): Boolean = false
}
