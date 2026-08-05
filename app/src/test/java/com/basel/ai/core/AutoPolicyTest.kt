package com.basel.ai.core

import com.basel.ai.web.SearchDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every threshold here is a decision the user no longer has to make, which
 * means every one of them is a decision nobody will notice being wrong.
 * They are pinned rather than believed.
 */
class AutoPolicyTest {

    private val s = EnglishStrings

    private fun gb(n: Double): Long = (n * 1024 * 1024 * 1024).toLong()

    private fun situation(
        thermal: ThermalLevel = ThermalLevel.NORMAL,
        percent: Int = 80,
        charging: Boolean = false,
        saving: Boolean = false,
        connection: Connection = Connection.UNMETERED,
        available: Long = gb(4.0),
        cores: Int = 8,
        gpuLayers: Int = 0,
    ) = Situation(
        thermal = thermal,
        power = PowerSnapshot(percent, charging, saving),
        connection = connection,
        availableBytes = available,
        cpuCores = cores,
        gpuLayers = gpuLayers,
    )

    // ------------------------------------------------------------- context

    @Test
    fun `context grows with free memory`() {
        val small = AutoPolicy.contextTokens(gb(1.0), modelWants = 2_048, s = s).value
        val medium = AutoPolicy.contextTokens(gb(3.0), modelWants = 2_048, s = s).value
        val large = AutoPolicy.contextTokens(gb(8.0), modelWants = 2_048, s = s).value
        assertTrue("$small < $medium", small < medium)
        assertTrue("$medium < $large", medium < large)
    }

    @Test
    fun `a roomy phone gets more than the old fixed 6144`() {
        // The constant this replaces. On a phone with room it was leaving a
        // reasoning turn to overflow and silently forget the conversation.
        assertTrue(AutoPolicy.contextTokens(gb(8.0), 2_048, s).value > 6_144)
    }

    @Test
    fun `unknown memory falls back to the old default rather than guessing`() {
        assertEquals(6_144, AutoPolicy.contextTokens(0, 2_048, s).value)
    }

    @Test
    fun `the reason says how much memory was seen`() {
        val decision = AutoPolicy.contextTokens(gb(4.0), 2_048, s)
        assertTrue(decision.reason.contains("4.0"))
        assertTrue(decision.reason.isNotBlank())
    }

    // ------------------------------------------------------------- threads

    @Test
    fun `a cool charged phone gets the full thermal budget`() {
        val decision = AutoPolicy.threads(situation(charging = true), s)
        assertEquals(ThermalPolicy.threadBudget(8, ThermalLevel.NORMAL), decision.value)
    }

    @Test
    fun `a nearly flat phone gets fewer threads than a full one`() {
        val full = AutoPolicy.threads(situation(percent = 90), s).value
        val low = AutoPolicy.threads(situation(percent = 15), s).value
        val critical = AutoPolicy.threads(situation(percent = 5), s).value
        assertTrue("$low < $full", low < full)
        assertTrue("$critical <= $low", critical <= low)
    }

    @Test
    fun `charging cancels the battery trim`() {
        // Plugged in at 5% is not the same situation as 5% on the move.
        val plugged = AutoPolicy.threads(situation(percent = 5, charging = true), s).value
        val unplugged = AutoPolicy.threads(situation(percent = 5), s).value
        assertTrue("$plugged > $unplugged", plugged > unplugged)
    }

    @Test
    fun `power save mode is obeyed even at full charge`() {
        // The user asked. That is reason enough.
        val saving = AutoPolicy.threads(situation(percent = 100, saving = true), s).value
        val normal = AutoPolicy.threads(situation(percent = 100), s).value
        assertTrue("$saving < $normal", saving < normal)
    }

    @Test
    fun `the GPU path needs fewer CPU threads`() {
        val onGpu = AutoPolicy.threads(situation(gpuLayers = 32), s).value
        val onCpu = AutoPolicy.threads(situation(), s).value
        assertTrue("$onGpu < $onCpu", onGpu < onCpu)
        assertTrue("still needs some", onGpu >= 1)
    }

    @Test
    fun `never fewer than one thread, never more than there are cores`() {
        for (cores in 1..12) {
            for (thermal in ThermalLevel.entries) {
                val n = AutoPolicy.threads(
                    situation(thermal = thermal, percent = 2, cores = cores), s
                ).value
                assertTrue("cores=$cores $thermal -> $n", n in 1..cores)
            }
        }
    }

    // -------------------------------------------------------------- search

    @Test
    fun `deep search only on an unmetered connection`() {
        assertEquals(
            SearchDepth.DEEP,
            AutoPolicy.searchDepth(situation(connection = Connection.UNMETERED), s).value
        )
        assertEquals(
            SearchDepth.QUICK,
            AutoPolicy.searchDepth(situation(connection = Connection.METERED), s).value
        )
        assertEquals(
            SearchDepth.QUICK,
            AutoPolicy.searchDepth(situation(connection = Connection.NONE), s).value
        )
    }

    @Test
    fun `a flat phone does not read three full pages`() {
        assertEquals(
            SearchDepth.QUICK,
            AutoPolicy.searchDepth(situation(percent = 12), s).value
        )
    }

    @Test
    fun `every search decision explains itself`() {
        for (connection in Connection.entries) {
            val decision = AutoPolicy.searchDepth(situation(connection = connection), s)
            assertTrue(connection.name, decision.reason.isNotBlank())
        }
    }

    // --------------------------------------------------------------- tools

    @Test
    fun `tool steps shrink as the phone struggles`() {
        val normal = AutoPolicy.toolSteps(situation(), s).value
        val warm = AutoPolicy.toolSteps(situation(thermal = ThermalLevel.WARM), s).value
        val hot = AutoPolicy.toolSteps(situation(thermal = ThermalLevel.HOT), s).value
        assertTrue("$hot < $warm", hot < warm)
        assertTrue("$warm < $normal", warm < normal)
        assertTrue("at least one step, or tools are simply off", hot >= 1)
    }

    @Test
    fun `the GPU buys an extra step`() {
        assertTrue(
            AutoPolicy.toolSteps(situation(gpuLayers = 32), s).value >
                AutoPolicy.toolSteps(situation(), s).value
        )
    }

    @Test
    fun `a critical battery is as limiting as a hot phone`() {
        assertEquals(
            AutoPolicy.toolSteps(situation(thermal = ThermalLevel.HOT), s).value,
            AutoPolicy.toolSteps(situation(percent = 5), s).value,
        )
    }

    // ------------------------------------------------------------- answers

    @Test
    fun `answers are trimmed under pressure and never lengthened`() {
        val asked = 2_048
        assertEquals(asked, AutoPolicy.maxTokens(asked, situation(), s).value)
        assertTrue(AutoPolicy.maxTokens(asked, situation(percent = 15), s).value < asked)
        assertTrue(
            AutoPolicy.maxTokens(asked, situation(thermal = ThermalLevel.HOT), s).value <
                AutoPolicy.maxTokens(asked, situation(percent = 15), s).value
        )
    }

    @Test
    fun `a small request is never inflated to the cap`() {
        // The trim is a ceiling, not a target.
        assertEquals(64, AutoPolicy.maxTokens(64, situation(thermal = ThermalLevel.HOT), s).value)
    }

    // ----------------------------------------------------------------- ocr

    @Test
    fun `page images are not uploaded on mobile data`() {
        assertFalse(AutoPolicy.mayUploadImages(situation(connection = Connection.METERED), s).value)
        assertFalse(AutoPolicy.mayUploadImages(situation(connection = Connection.NONE), s).value)
        assertTrue(AutoPolicy.mayUploadImages(situation(), s).value)
    }

    @Test
    fun `a nearly dead battery postpones scanned pages`() {
        assertFalse(AutoPolicy.mayUploadImages(situation(percent = 4), s).value)
    }

    // ----------------------------------------------------------------- gpu

    @Test
    fun `no GPU device means no offload, whatever else is true`() {
        assertFalse(AutoPolicy.useGpu(situation(), gpuPresent = false, modelBytes = 0, s = s).value)
    }

    @Test
    fun `a hot phone does not add the GPU to its own heat`() {
        assertFalse(
            AutoPolicy.useGpu(
                situation(thermal = ThermalLevel.HOT), gpuPresent = true, modelBytes = gb(2.0), s = s
            ).value
        )
    }

    @Test
    fun `offload needs headroom, not just a bare fit`() {
        // Landing exactly on the limit means the next allocation fails.
        val bare = AutoPolicy.useGpu(
            situation(available = gb(2.0)), gpuPresent = true, modelBytes = gb(2.0), s = s
        )
        assertFalse(bare.value)

        val roomy = AutoPolicy.useGpu(
            situation(available = gb(4.0)), gpuPresent = true, modelBytes = gb(2.0), s = s
        )
        assertTrue(roomy.value)
    }

    @Test
    fun `every gpu answer says why`() {
        val cases = listOf(
            AutoPolicy.useGpu(situation(), false, 0, s),
            AutoPolicy.useGpu(situation(thermal = ThermalLevel.HOT), true, gb(1.0), s),
            AutoPolicy.useGpu(situation(available = gb(1.0)), true, gb(2.0), s),
            AutoPolicy.useGpu(situation(), true, gb(1.0), s),
        )
        for (case in cases) assertTrue(case.reason.isNotBlank())
    }

    // ------------------------------------------------------------ snapshot

    @Test
    fun `an unknown battery level trims nothing`() {
        // -1 means the platform declined to answer. Throttling a healthy phone
        // on a missing reading is worse than not throttling a flat one.
        assertFalse(PowerSnapshot.UNKNOWN.isLow)
        assertFalse(PowerSnapshot.UNKNOWN.shouldConserve)
        assertFalse(PowerSnapshot.UNKNOWN.isCritical)
    }

    @Test
    fun `charging is never low`() {
        assertFalse(PowerSnapshot(3, isCharging = true, isPowerSaveMode = false).isLow)
        assertTrue(PowerSnapshot(3, isCharging = false, isPowerSaveMode = false).isCritical)
    }

    @Test
    fun `only a real connection counts as online`() {
        assertFalse(Connection.NONE.isOnline)
        assertTrue(Connection.METERED.isOnline)
        assertTrue(Connection.UNMETERED.isOnline)
    }

    @Test
    fun `constraint is either heat or power, not both required`() {
        assertTrue(situation(thermal = ThermalLevel.WARM).isConstrained)
        assertTrue(situation(percent = 10).isConstrained)
        assertFalse(situation(charging = true).isConstrained)
    }
}
