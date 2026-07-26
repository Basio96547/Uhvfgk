package com.example.ondevicellm.llm

import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.model.BackendPref
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * Result of mapping a user's [BackendPref] onto a backend the runtime can
 * really use, plus an explanation of any substitution that happened.
 */
data class ResolvedBackend(
    val requested: BackendPref,
    val actual: LlmInference.Backend,
    /** User-facing explanation. Empty when the request was honoured as-is. */
    val note: String,
) {
    val actualLabel: String
        get() = when (actual) {
            LlmInference.Backend.GPU -> "GPU"
            LlmInference.Backend.CPU -> "CPU"
            else -> actual.name
        }

    val wasSubstituted: Boolean get() = note.isNotEmpty()
}

/**
 * Chooses the execution backend.
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
 *  2. When NPU is requested, it runs on the GPU — the fastest backend actually
 *     available — and says so plainly instead of silently pretending.
 *
 * The [NpuRuntime] hook below is the integration point for wiring in QNN later.
 */
object BackendResolver {

    fun resolve(pref: BackendPref, device: DeviceSnapshot): ResolvedBackend = when (pref) {
        BackendPref.CPU -> ResolvedBackend(pref, LlmInference.Backend.CPU, "")

        BackendPref.GPU -> if (device.accelerators.vulkanAvailable ||
            device.accelerators.openClAvailable
        ) {
            ResolvedBackend(pref, LlmInference.Backend.GPU, "")
        } else {
            ResolvedBackend(
                pref,
                LlmInference.Backend.CPU,
                "No Vulkan/OpenCL driver detected — running on CPU.",
            )
        }

        BackendPref.NPU -> resolveNpu(device)

        BackendPref.AUTO -> autoSelect(device)
    }

    private fun resolveNpu(device: DeviceSnapshot): ResolvedBackend {
        val npu = device.accelerators
        val note = when {
            NpuRuntime.isAvailable() ->
                "NPU runtime is linked — delegating to the vendor NPU."

            npu.hasNpuRuntime -> buildString {
                append("Qualcomm NPU runtime detected on this device")
                npu.hexagonVersion?.let { append(" (Hexagon $it)") }
                append(", but the MediaPipe LLM API exposes only CPU/GPU. ")
                append("Running on GPU. See BackendResolver docs to wire up QNN.")
            }

            else ->
                "No vendor NPU runtime found on this device — running on GPU."
        }

        val backend = if (npu.vulkanAvailable || npu.openClAvailable) {
            LlmInference.Backend.GPU
        } else {
            LlmInference.Backend.CPU
        }
        return ResolvedBackend(BackendPref.NPU, backend, note)
    }

    private fun autoSelect(device: DeviceSnapshot): ResolvedBackend {
        // GPU is the best generally-available backend for LLM decode on
        // Snapdragon; fall back to CPU when no GPU compute driver is present.
        val gpuCapable = device.accelerators.vulkanAvailable ||
            device.accelerators.openClAvailable
        return if (gpuCapable) {
            ResolvedBackend(BackendPref.AUTO, LlmInference.Backend.GPU, "")
        } else {
            ResolvedBackend(
                BackendPref.AUTO,
                LlmInference.Backend.CPU,
                "No GPU compute driver detected — running on CPU.",
            )
        }
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
