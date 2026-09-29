package com.basel.ai.core

import com.basel.ai.web.SearchDepth

/**
 * Everything about the phone's current state that should change a decision.
 *
 * One object rather than six parameters threaded through six call sites: the
 * point of this is that decisions are made from the *same* picture, and a
 * picture assembled separately at each call site drifts.
 */
data class Situation(
    val thermal: ThermalLevel = ThermalLevel.NORMAL,
    val power: PowerSnapshot = PowerSnapshot.UNKNOWN,
    val connection: Connection = Connection.NONE,
    /** RAM plus free extended memory, as the loader counts it. */
    val availableBytes: Long = 0,
    val cpuCores: Int = 8,
    /** Non-zero when the loaded model has layers on the GPU. */
    val gpuLayers: Int = 0,
) {
    val onGpu: Boolean get() = gpuLayers > 0

    /** Hot, flat, or the user asked to conserve — trim work either way. */
    val isConstrained: Boolean
        get() = thermal >= ThermalLevel.WARM || power.shouldConserve

    val isSeverelyConstrained: Boolean
        get() = thermal >= ThermalLevel.HOT || power.isCritical
}

/** A decision, and the sentence that explains it. */
data class Decision<T>(val value: T, val reason: String)

/**
 * Turns the situation into settings, so the user does not have to.
 *
 * The app had eight things to configure and every one of them defaulted to a
 * fixed number chosen once, on no evidence, for a phone in no particular
 * state. A 6144-token context whether there is 1 GB free or 8. Deep search
 * that reads three full pages on a mobile plan. Three tool steps — three full
 * generations — at 8% battery. None of those are wrong *settings*; they are
 * the wrong *kind* of thing to settle in advance.
 *
 * Two rules this follows, and they are what separate "automatic" from
 * "unpredictable":
 *
 *  - **Every decision carries its reason.** The UI can always answer "why is
 *    it doing that", and an automatic system that cannot be interrogated is
 *    just an opaque one.
 *  - **An explicit choice always wins.** These are consulted for AUTO and
 *    nothing else. A user who picks DEEP gets DEEP on mobile data, because
 *    they said so and they can see their own signal bar.
 *
 * Pure. Every threshold below is a test rather than a belief.
 */
object AutoPolicy {

    // ------------------------------------------------------------- context

    /**
     * Context window, sized from free memory rather than fixed.
     *
     * The KV cache is what this actually buys, and it grows with the window —
     * roughly linearly. A phone with 6 GB free can hold a conversation that a
     * phone with 1 GB cannot, and the old constant served neither: too small
     * to keep a reasoning turn on the roomy phone, too large on the tight one,
     * where it turned into a failed load the user read as a broken model.
     */
    fun contextTokens(availableBytes: Long, modelWants: Int, s: AppStrings): Decision<Int> {
        val gb = availableBytes.toDouble() / (1024 * 1024 * 1024)
        val fromMemory = when {
            gb >= 6.0 -> 16_384
            gb >= 4.0 -> 12_288
            gb >= 2.5 -> 8_192
            gb >= 1.5 -> 6_144
            gb > 0 -> 4_096
            // Memory unknown: the old default, which at least never surprised.
            else -> 6_144
        }
        // A model that declares a bigger budget than the phone can hold still
        // has to fit; the smaller of the two is the honest answer.
        val value = maxOf(fromMemory, minOf(modelWants * 2, fromMemory))
        return Decision(value, s.autoContextReason(value, gb))
    }

    // ------------------------------------------------------------- threads

    /**
     * Decode threads.
     *
     * Thermal already had a budget; power did not, and a flat phone deserves
     * the same restraint as a hot one. On the GPU the CPU is feeding the
     * pipeline rather than doing the matmuls, so it needs fewer.
     */
    fun threads(situation: Situation, s: AppStrings): Decision<Int> {
        val cores = situation.cpuCores.coerceAtLeast(1)
        var budget = ThermalPolicy.threadBudget(cores, situation.thermal)
        val reason = StringBuilder()

        if (situation.onGpu) {
            budget = minOf(budget, maxOf(2, cores / 4))
            reason.append(s.autoThreadsGpu)
        } else if (situation.power.isCritical) {
            budget = maxOf(1, budget / 2)
            reason.append(s.autoThreadsCritical)
        } else if (situation.power.shouldConserve) {
            budget = maxOf(1, (budget * 2) / 3)
            reason.append(s.autoThreadsSaving)
        } else if (situation.thermal >= ThermalLevel.WARM) {
            reason.append(situation.thermal.label(s))
        } else {
            reason.append(s.autoThreadsFull)
        }

        return Decision(budget.coerceIn(1, cores), s.autoThreadsReason(budget, cores, reason.toString()))
    }

    // -------------------------------------------------------------- search

    /**
     * How hard to search.
     *
     * Deep search opens the top three pages and reads them in full — the
     * difference between a two-line snippet and an answer. It is also several
     * hundred kilobytes and a few seconds, which is a fair trade on Wi-Fi and
     * a poor one on a mobile plan at 15% battery.
     */
    fun searchDepth(situation: Situation, s: AppStrings): Decision<SearchDepth> = when {
        situation.connection == Connection.NONE ->
            Decision(SearchDepth.QUICK, s.autoDepthOffline)

        situation.connection == Connection.METERED ->
            Decision(SearchDepth.QUICK, s.autoDepthMetered)

        situation.power.shouldConserve ->
            Decision(SearchDepth.QUICK, s.autoDepthSaving)

        else -> Decision(SearchDepth.DEEP, s.autoDepthDeep)
    }

    /** Whether the network is worth using at all right now. */
    fun networkUsable(situation: Situation): Boolean = situation.connection.isOnline

    // --------------------------------------------------------------- tools

    /**
     * Tool calls allowed before the model must answer.
     *
     * Each step is a whole generation, so this is the main thing standing
     * between "a few seconds" and "a few minutes" — and the cost lands on a
     * battery and a thermal budget that may not have room for it.
     */
    fun toolSteps(situation: Situation, s: AppStrings): Decision<Int> = when {
        situation.isSeverelyConstrained -> Decision(1, s.autoStepsHot)
        situation.isConstrained -> Decision(2, s.autoStepsConstrained)
        situation.onGpu -> Decision(4, s.autoStepsGpu)
        else -> Decision(3, s.autoStepsNormal)
    }

    // ------------------------------------------------------------- answers

    /**
     * Trims a reply's token budget when the phone cannot afford the full one.
     *
     * Cutting the budget rather than refusing: a shorter answer now beats a
     * complete one that arrives after the phone has throttled itself to a
     * crawl, and beats a thermal pause mid-sentence.
     */
    fun maxTokens(requested: Int, situation: Situation, s: AppStrings): Decision<Int> = when {
        situation.isSeverelyConstrained ->
            Decision(minOf(requested, 512), s.autoTokensHot)

        situation.isConstrained ->
            Decision(minOf(requested, 1_024), s.autoTokensConstrained)

        else -> Decision(requested, s.autoTokensFull)
    }

    /**
     * Whether to send page images for reading.
     *
     * A scanned document is one upload per unreadable page. Doing forty of
     * those on a mobile plan without asking is the kind of helpfulness nobody
     * wants twice.
     */
    fun mayUploadImages(situation: Situation, s: AppStrings): Decision<Boolean> = when {
        situation.connection == Connection.NONE -> Decision(false, s.autoOcrOffline)
        situation.connection == Connection.METERED -> Decision(false, s.autoOcrMetered)
        situation.power.isCritical -> Decision(false, s.autoOcrBattery)
        else -> Decision(true, s.autoOcrReady)
    }

    /**
     * Whether the GPU should be used when one is available.
     *
     * Offload is not free: the weights move into GPU-visible memory, so a
     * phone already short of it does worse, not better. And a GPU at full tilt
     * is a heat source, which is the one thing a phone already throttling does
     * not need.
     */
    fun useGpu(situation: Situation, gpuPresent: Boolean, modelBytes: Long, s: AppStrings):
        Decision<Boolean> = when {
        !gpuPresent -> Decision(false, s.autoGpuAbsent)
        situation.thermal >= ThermalLevel.HOT -> Decision(false, s.autoGpuHot)
        // Headroom, not just fit: landing exactly on the limit means the next
        // allocation fails instead of this one.
        modelBytes > 0 && situation.availableBytes < modelBytes * 12 / 10 ->
            Decision(false, s.autoGpuMemory)

        else -> Decision(true, s.autoGpuReady)
    }
}
