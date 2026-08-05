package com.basel.ai.core

/**
 * What the phone's state *is*, separated from how it is read.
 *
 * These used to live beside their Android readers, which meant the policy
 * built on them could not be compiled — let alone tested — without an Android
 * runtime. The readings need a `Context`; the meaning of a reading does not,
 * and the meaning is the part that decides what the app does.
 */

/**
 * How hot the device is, normalised across API levels.
 *
 * Android's own thermal ladder has seven rungs; the bottom four are what an
 * app can meaningfully react to, so they are collapsed into something the UI
 * can show and the inference loop can act on.
 */
enum class ThermalLevel {
    /** Nothing to do. Full speed. */
    NORMAL,

    /** Warm. Back off a little before the system starts throttling for us. */
    WARM,

    /** Hot. Run on the minimum useful number of threads. */
    HOT,

    /** Too hot to keep generating — the system is actively throttling. */
    CRITICAL,
    ;

    fun label(s: AppStrings): String = when (this) {
        NORMAL -> s.thermalNormal
        WARM -> s.thermalWarm
        HOT -> s.thermalHot
        CRITICAL -> s.thermalCritical
    }
}

/** What the battery is doing. */
data class PowerSnapshot(
    /** 0–100, or -1 when the platform will not say. */
    val percent: Int,
    val isCharging: Boolean,
    /** The user has asked the system to conserve power. */
    val isPowerSaveMode: Boolean,
) {
    /** Below this, a long decode is a real cost to the person holding the phone. */
    val isLow: Boolean get() = !isCharging && percent in 0..LOW_PERCENT

    val isCritical: Boolean get() = !isCharging && percent in 0..CRITICAL_PERCENT

    /**
     * True when work should be trimmed: the user asked for it, or the battery
     * is low enough that a five-minute generation is not a fair trade.
     */
    val shouldConserve: Boolean get() = isPowerSaveMode || isLow

    companion object {
        const val LOW_PERCENT = 20
        const val CRITICAL_PERCENT = 10

        /**
         * Unknown, so nothing is trimmed on a guess.
         *
         * A percentage of -1 means the platform declined to answer, and
         * throttling a healthy phone on a missing reading is worse than not
         * throttling a flat one.
         */
        val UNKNOWN = PowerSnapshot(percent = -1, isCharging = false, isPowerSaveMode = false)
    }
}

/** What kind of connection there is, if any. */
enum class Connection {
    /** Nothing usable. Anything needing the network should not be attempted. */
    NONE,

    /** Mobile data, a metered hotspot — the user pays per megabyte. */
    METERED,

    /** Wi-Fi or ethernet with no metering flag. */
    UNMETERED,
    ;

    val isOnline: Boolean get() = this != NONE
}

/** Thread budgets. Pure, because the arithmetic is what actually decides speed. */
object ThermalPolicy {

    /**
     * Threads to use for token generation.
     *
     * Decode is memory-bandwidth bound, not compute bound: past roughly four
     * threads the extra cores mostly add heat rather than tokens per second.
     * Snapdragon 8 Elite has no efficiency cores — every core is a big one —
     * so loading all eight heats the device unusually fast. Hence a ceiling
     * well below the core count even when cool.
     */
    fun threadBudget(coreCount: Int, level: ThermalLevel): Int {
        val ceiling = when (level) {
            ThermalLevel.NORMAL -> 6
            ThermalLevel.WARM -> 4
            ThermalLevel.HOT -> 2
            ThermalLevel.CRITICAL -> 2
        }
        // Always leave headroom for the UI thread and audio.
        return minOf(ceiling, (coreCount - 2).coerceAtLeast(1))
    }

    /** True when generation should not be started or continued. */
    fun shouldPause(level: ThermalLevel): Boolean = level == ThermalLevel.CRITICAL
}
