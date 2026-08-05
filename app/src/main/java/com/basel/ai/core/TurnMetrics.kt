package com.basel.ai.core

/**
 * What one turn actually cost.
 *
 * Every performance claim in this project has been hedged — "never measured on
 * a device", "no token rate has been taken" — and that hedge is only honest
 * for as long as measuring is impossible. It is not. The app is the one thing
 * that is always present when a model runs, so it should be the thing that
 * counts.
 */
data class TurnMetrics(
    /** Callbacks from the decode loop. One per token for both engines. */
    val tokens: Int = 0,
    /** From the first token to the last, not including loading or searching. */
    val decodeMs: Long = 0,
    /** The whole turn, including search, tools and any waiting. */
    val totalMs: Long = 0,
    val freeBytesBefore: Long = 0,
    val freeBytesAfter: Long = 0,
    val thermalAtEnd: ThermalLevel = ThermalLevel.NORMAL,
    val onGpu: Boolean = false,

    // ---- tool use, which is the number that decides whether tools are real --
    /** Replies that looked like they contained a tool call. */
    val toolCallsSeen: Int = 0,
    /** Of those, the ones the parser could actually read. */
    val toolCallsParsed: Int = 0,
    /** Of those, the ones that ran and came back ok. */
    val toolCallsSucceeded: Int = 0,
) {
    /**
     * Tokens per second, or null when there is nothing to divide by.
     *
     * Null rather than zero: "0.0 tok/s" reads as a measurement, and no
     * measurement was taken.
     */
    val tokensPerSecond: Double?
        get() = if (tokens > 0 && decodeMs > 0) tokens * 1000.0 / decodeMs else null

    /** Memory the turn consumed, or null when either reading is missing. */
    val memoryUsedBytes: Long?
        get() = if (freeBytesBefore > 0 && freeBytesAfter > 0) {
            (freeBytesBefore - freeBytesAfter).coerceAtLeast(0)
        } else {
            null
        }

    /**
     * How often a reply that *tried* to call a tool actually managed to.
     *
     * The one number that says whether tool support is a feature or
     * infrastructure with no user. A model that never emits a call gives 0
     * seen; one that emits calls the parser cannot read gives a low ratio, and
     * that is a parser bug rather than a model limitation.
     */
    val toolParseRate: Double?
        get() = if (toolCallsSeen > 0) toolCallsParsed.toDouble() / toolCallsSeen else null
}

/** Running totals across a session, so one turn's oddity does not read as the norm. */
object MetricsSummary {

    /** Median rather than mean: one thermal stall should not define the number. */
    fun medianTokensPerSecond(turns: List<TurnMetrics>): Double? {
        val rates = turns.mapNotNull { it.tokensPerSecond }.sorted()
        if (rates.isEmpty()) return null
        val middle = rates.size / 2
        return if (rates.size % 2 == 1) {
            rates[middle]
        } else {
            (rates[middle - 1] + rates[middle]) / 2
        }
    }

    fun totalTokens(turns: List<TurnMetrics>): Int = turns.sumOf { it.tokens }

    /** Across the session, since a single turn is too small a sample to mean anything. */
    fun toolParseRate(turns: List<TurnMetrics>): Double? {
        val seen = turns.sumOf { it.toolCallsSeen }
        if (seen == 0) return null
        return turns.sumOf { it.toolCallsParsed }.toDouble() / seen
    }

    fun toolSuccessRate(turns: List<TurnMetrics>): Double? {
        val parsed = turns.sumOf { it.toolCallsParsed }
        if (parsed == 0) return null
        return turns.sumOf { it.toolCallsSucceeded }.toDouble() / parsed
    }

    /** One line per turn, newest first, for the diagnostics screen. */
    fun format(metrics: TurnMetrics, s: AppStrings): String = buildString {
        val rate = metrics.tokensPerSecond
        append(
            if (rate == null) s.metricsNoRate
            else s.metricsRate(String.format("%.1f", rate), metrics.tokens)
        )
        append(" · ")
        append(if (metrics.onGpu) s.metricsOnGpu else s.metricsOnCpu)
        metrics.memoryUsedBytes?.let {
            append(" · ")
            append(s.metricsMemory(megabytes(it)))
        }
        if (metrics.toolCallsSeen > 0) {
            append(" · ")
            append(
                s.metricsTools(
                    metrics.toolCallsSucceeded,
                    metrics.toolCallsParsed,
                    metrics.toolCallsSeen,
                )
            )
        }
    }

    /**
     * Megabytes, spelled out here rather than borrowed.
     *
     * The app's byte formatter lives beside Android code, and this file has to
     * compile without it — the numbers are the part worth testing.
     */
    internal fun megabytes(bytes: Long): String = "${bytes / (1024 * 1024)} MB"

    /** How many turns are worth keeping. Enough to see a trend, not a log. */
    const val HISTORY = 30
}
