package com.example.ondevicellm.llm

import com.example.ondevicellm.model.BackendPref

/** Where a model ends up running. */
enum class ComputeTarget { CPU, GPU }

/** A chosen target plus the reasoning behind it, in the user's words. */
data class BackendPlan(val target: ComputeTarget, val note: String)

/**
 * Picks CPU or GPU for a MediaPipe `.task` model.
 *
 * The previous version asked one question — "is there a GPU driver?" — and
 * always answered GPU. That is not a decision, and on a phone it is often the
 * wrong one: the MediaPipe GPU path holds the whole model in a
 * driver-allocated buffer, and a multi-gigabyte bundle either fails to
 * allocate or thrashes, while the same model runs fine on eight Oryon cores.
 * So the size of the model and the memory actually free are inputs too, and
 * every choice comes with a sentence explaining itself.
 *
 * Pure Kotlin with no Android or MediaPipe types, so the policy is unit-tested
 * rather than only observed on a device.
 */
object BackendPlanner {

    /**
     * Above this, the GPU path stops being a good bet on a phone: the driver
     * allocation is one contiguous region, and Adreno rejects or thrashes on
     * bundles this size while the CPU path memory-maps happily.
     */
    const val GPU_MODEL_LIMIT_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** GPU needs the weights resident, so demand real headroom over the file. */
    private const val GPU_MEMORY_HEADROOM = 1.35

    fun plan(
        pref: BackendPref,
        gpuCapable: Boolean,
        /** A vendor NPU runtime exists on the device (detected, not usable). */
        npuRuntimePresent: Boolean,
        /** An NPU runtime is linked into *this build* and can actually run a model. */
        npuLinked: Boolean,
        modelBytes: Long,
        /** RAM plus free extended memory, as the loader counts it. */
        availableBytes: Long,
        /** e.g. "Snapdragon 8 Elite"; empty when unknown. */
        socLabel: String,
    ): BackendPlan = when (pref) {
        BackendPref.CPU -> BackendPlan(
            ComputeTarget.CPU,
            if (socLabel.isNotEmpty()) "Running on the $socLabel CPU, as requested." else "",
        )

        BackendPref.GPU -> if (gpuCapable) {
            val fit = gpuFit(modelBytes, availableBytes)
            if (fit == null) {
                BackendPlan(ComputeTarget.GPU, "")
            } else {
                // Requested explicitly, so it is honoured — but say what to expect.
                BackendPlan(ComputeTarget.GPU, "$fit Switch to CPU if loading fails.")
            }
        } else {
            BackendPlan(ComputeTarget.CPU, "No Vulkan or OpenCL driver here — running on CPU.")
        }

        BackendPref.NPU -> planNpu(
            gpuCapable, npuRuntimePresent, npuLinked, modelBytes, availableBytes,
        )

        BackendPref.AUTO -> planAuto(gpuCapable, modelBytes, availableBytes, socLabel)
    }

    private fun planAuto(
        gpuCapable: Boolean,
        modelBytes: Long,
        availableBytes: Long,
        socLabel: String,
    ): BackendPlan {
        val soc = socLabel.ifEmpty { "this device" }
        if (!gpuCapable) {
            return BackendPlan(
                ComputeTarget.CPU,
                "Auto: no GPU compute driver on $soc, so the CPU it is.",
            )
        }
        val problem = gpuFit(modelBytes, availableBytes)
        return if (problem == null) {
            BackendPlan(ComputeTarget.GPU, "Auto: GPU — this model is small enough to fit.")
        } else {
            BackendPlan(ComputeTarget.CPU, "Auto: CPU. $problem")
        }
    }

    private fun planNpu(
        gpuCapable: Boolean,
        npuRuntimePresent: Boolean,
        npuLinked: Boolean,
        modelBytes: Long,
        availableBytes: Long,
    ): BackendPlan {
        if (npuLinked) {
            return BackendPlan(ComputeTarget.GPU, "NPU runtime is linked — delegating to it.")
        }
        val fallback = planAuto(gpuCapable, modelBytes, availableBytes, "")
        val lead = if (npuRuntimePresent) {
            "This phone has a vendor NPU, but no public API exposes it to a " +
                "general GGUF/.task model, so it can't be used here."
        } else {
            "No usable vendor NPU runtime on this device."
        }
        return BackendPlan(fallback.target, "$lead ${fallback.note}".trim())
    }

    /**
     * Returns null when the GPU is a good fit, or a sentence saying why it
     * isn't.
     */
    private fun gpuFit(modelBytes: Long, availableBytes: Long): String? {
        if (modelBytes <= 0L) return null
        if (modelBytes > GPU_MODEL_LIMIT_BYTES) {
            return "This model is too large for a phone GPU allocation; " +
                "the CPU path memory-maps it instead."
        }
        if (availableBytes > 0 && availableBytes < modelBytes * GPU_MEMORY_HEADROOM) {
            return "Not enough free memory to keep the weights resident on the GPU."
        }
        return null
    }
}
