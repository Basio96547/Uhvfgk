package com.example.ondevicellm.llm

import com.example.ondevicellm.model.BackendPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendPlannerTest {

    private val gb = 1024L * 1024 * 1024

    private fun plan(
        pref: BackendPref = BackendPref.AUTO,
        gpuCapable: Boolean = true,
        npuRuntimePresent: Boolean = true,
        npuLinked: Boolean = false,
        modelBytes: Long = gb,
        availableBytes: Long = 8 * gb,
        socLabel: String = "Snapdragon 8 Elite",
    ) = BackendPlanner.plan(
        pref, gpuCapable, npuRuntimePresent, npuLinked, modelBytes, availableBytes, socLabel,
    )

    // ---- AUTO is a decision, not a coin flip ------------------------------

    @Test
    fun `auto picks GPU for a small model with room`() {
        assertEquals(ComputeTarget.GPU, plan(modelBytes = 900 * 1024 * 1024).target)
    }

    @Test
    fun `auto picks CPU for a model too big for a phone GPU`() {
        // Qwen3-4B-Q8_0 is around 4 GB — the case that started this.
        val p = plan(modelBytes = 4 * gb)
        assertEquals(ComputeTarget.CPU, p.target)
        assertTrue(p.note.contains("too large", ignoreCase = true))
    }

    @Test
    fun `auto picks CPU when memory is tight even for a small model`() {
        val p = plan(modelBytes = gb, availableBytes = gb)
        assertEquals(ComputeTarget.CPU, p.target)
        assertTrue(p.note.contains("memory", ignoreCase = true))
    }

    @Test
    fun `auto falls back to CPU with no GPU driver`() {
        val p = plan(gpuCapable = false)
        assertEquals(ComputeTarget.CPU, p.target)
        assertTrue(p.note.contains("Snapdragon 8 Elite"))
    }

    @Test
    fun `every auto decision explains itself`() {
        listOf(
            plan(modelBytes = 500 * 1024 * 1024),
            plan(modelBytes = 6 * gb),
            plan(gpuCapable = false),
        ).forEach { assertTrue(it.note.isNotBlank()) }
    }

    // ---- explicit preferences are honoured --------------------------------

    @Test
    fun `CPU is honoured without argument`() {
        assertEquals(ComputeTarget.CPU, plan(pref = BackendPref.CPU, modelBytes = 100).target)
    }

    @Test
    fun `an explicit GPU request is honoured even for a big model`() {
        val p = plan(pref = BackendPref.GPU, modelBytes = 5 * gb)
        assertEquals(ComputeTarget.GPU, p.target)
        // Honoured, but the user is told what to expect.
        assertTrue(p.note.contains("CPU"))
    }

    @Test
    fun `GPU request without a driver becomes CPU`() {
        val p = plan(pref = BackendPref.GPU, gpuCapable = false)
        assertEquals(ComputeTarget.CPU, p.target)
        assertTrue(p.note.contains("Vulkan"))
    }

    // ---- NPU ---------------------------------------------------------------

    @Test
    fun `NPU on a phone with a detected but unusable runtime says so`() {
        val p = plan(pref = BackendPref.NPU, npuRuntimePresent = true)
        assertTrue(p.note.contains("vendor NPU"))
        // Falls back to the same target AUTO would have chosen.
        assertEquals(plan(pref = BackendPref.AUTO).target, p.target)
    }

    @Test
    fun `NPU falls back to CPU for a large model, not blindly to GPU`() {
        assertEquals(
            ComputeTarget.CPU,
            plan(pref = BackendPref.NPU, modelBytes = 5 * gb).target,
        )
    }

    @Test
    fun `a linked NPU runtime is used`() {
        val p = plan(pref = BackendPref.NPU, npuLinked = true)
        assertTrue(p.note.contains("linked"))
    }

    @Test
    fun `no NPU runtime at all is reported honestly`() {
        val p = plan(pref = BackendPref.NPU, npuRuntimePresent = false)
        assertTrue(p.note.contains("No usable vendor NPU"))
    }

    // ---- edges -------------------------------------------------------------

    @Test
    fun `unknown model size does not block the GPU`() {
        assertEquals(ComputeTarget.GPU, plan(modelBytes = 0).target)
    }
}
