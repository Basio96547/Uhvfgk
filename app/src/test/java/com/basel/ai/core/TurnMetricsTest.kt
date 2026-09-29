package com.basel.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnMetricsTest {

    @Test
    fun `tokens per second is tokens over the decode window`() {
        val turn = TurnMetrics(tokens = 40, decodeMs = 2_000)
        assertEquals(20.0, turn.tokensPerSecond!!, 1e-6)
    }

    @Test
    fun `no measurement is null, never zero`() {
        // "0.0 tok/s" reads as a measurement. None was taken.
        assertNull(TurnMetrics().tokensPerSecond)
        assertNull(TurnMetrics(tokens = 10, decodeMs = 0).tokensPerSecond)
        assertNull(TurnMetrics(tokens = 0, decodeMs = 500).tokensPerSecond)
    }

    @Test
    fun `memory used is the drop in free memory, never negative`() {
        assertEquals(
            100L,
            TurnMetrics(freeBytesBefore = 500, freeBytesAfter = 400).memoryUsedBytes,
        )
        // Memory can go *up* mid-turn if something else exits. That is not a
        // negative cost.
        assertEquals(
            0L,
            TurnMetrics(freeBytesBefore = 400, freeBytesAfter = 500).memoryUsedBytes,
        )
        assertNull(TurnMetrics().memoryUsedBytes)
    }

    // ------------------------------------------------------------ tool use

    @Test
    fun `the parse rate separates a model that cannot from a parser that will not`() {
        // Zero attempts means the model never tried — a model limitation.
        assertNull(TurnMetrics().toolParseRate)
        // Attempts the parser could not read is a parser bug, and it should
        // not look like the same thing.
        assertEquals(0.5, TurnMetrics(toolCallsSeen = 4, toolCallsParsed = 2).toolParseRate!!, 1e-9)
    }

    @Test
    fun `session rates aggregate rather than average the averages`() {
        val turns = listOf(
            TurnMetrics(toolCallsSeen = 3, toolCallsParsed = 3, toolCallsSucceeded = 1),
            TurnMetrics(toolCallsSeen = 1, toolCallsParsed = 0, toolCallsSucceeded = 0),
        )
        assertEquals(0.75, MetricsSummary.toolParseRate(turns)!!, 1e-9)
        assertEquals(1.0 / 3, MetricsSummary.toolSuccessRate(turns)!!, 1e-9)
    }

    @Test
    fun `no attempts across the session is null, not zero`() {
        assertNull(MetricsSummary.toolParseRate(listOf(TurnMetrics(tokens = 5))))
        assertNull(MetricsSummary.toolSuccessRate(listOf(TurnMetrics(tokens = 5))))
    }

    // -------------------------------------------------------------- median

    @Test
    fun `the median ignores a single stalled turn`() {
        // One thermal pause should not define the number a user is shown.
        val turns = listOf(
            TurnMetrics(tokens = 100, decodeMs = 5_000),   // 20/s
            TurnMetrics(tokens = 100, decodeMs = 5_000),   // 20/s
            TurnMetrics(tokens = 10, decodeMs = 20_000),   // 0.5/s — the stall
        )
        assertEquals(20.0, MetricsSummary.medianTokensPerSecond(turns)!!, 1e-6)
    }

    @Test
    fun `an even number of turns takes the middle pair`() {
        val turns = listOf(
            TurnMetrics(tokens = 10, decodeMs = 1_000),  // 10/s
            TurnMetrics(tokens = 20, decodeMs = 1_000),  // 20/s
        )
        assertEquals(15.0, MetricsSummary.medianTokensPerSecond(turns)!!, 1e-6)
    }

    @Test
    fun `nothing measured means no median`() {
        assertNull(MetricsSummary.medianTokensPerSecond(emptyList()))
        assertNull(MetricsSummary.medianTokensPerSecond(listOf(TurnMetrics())))
    }

    @Test
    fun `total tokens counts every turn`() {
        assertEquals(
            30,
            MetricsSummary.totalTokens(listOf(TurnMetrics(tokens = 10), TurnMetrics(tokens = 20))),
        )
    }

    // ------------------------------------------------------------ wording

    @Test
    fun `a formatted line says the rate, the processor and the tools`() {
        val line = MetricsSummary.format(
            TurnMetrics(
                tokens = 40,
                decodeMs = 2_000,
                onGpu = true,
                toolCallsSeen = 2,
                toolCallsParsed = 2,
                toolCallsSucceeded = 1,
            ),
            EnglishStrings,
        )
        assertTrue(line.contains("20.0"))
        assertTrue(line.contains(EnglishStrings.metricsOnGpu))
        assertTrue(line.contains("1/2"))
    }

    @Test
    fun `a turn with no tool attempts does not mention tools`() {
        val line = MetricsSummary.format(TurnMetrics(tokens = 5, decodeMs = 500), EnglishStrings)
        assertTrue(!line.contains("tools"))
    }

    @Test
    fun `an unmeasured turn says so rather than showing a zero`() {
        val line = MetricsSummary.format(TurnMetrics(), EnglishStrings)
        assertTrue(line.contains(EnglishStrings.metricsNoRate))
    }

    @Test
    fun `arabic formats too`() {
        val line = MetricsSummary.format(TurnMetrics(tokens = 10, decodeMs = 1_000), ArabicStrings)
        assertTrue(line.any { it in '؀'..'ۿ' })
    }
}
