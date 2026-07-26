package com.example.ondevicellm.model

import com.example.ondevicellm.core.AppStrings

/** How comfortably a size class fits in the memory this device actually has. */
enum class Fit { COMFORTABLE, TIGHT, TOO_BIG }

/**
 * One weight class the user could run, with an honest verdict.
 *
 * [approxBytes] is the size of a real GGUF file of that class, not a
 * calculation from parameter counts — quantised files carry embeddings and
 * metadata that a naive bits-per-weight estimate misses by a wide margin.
 */
data class ModelOption(
    val parameters: String,
    val quantisation: String,
    val approxBytes: Long,
    val fit: Fit,
)

/**
 * Says what this phone can actually run, and why a bigger model at a coarser
 * quantisation beats a smaller one at a fine quantisation.
 *
 * This exists because of a real report: the answers were coherent and on time,
 * but the model plainly did not understand what was being asked. That is not a
 * bug in the app — it is the ceiling of a 4B model, and no amount of prompt
 * work moves it. The lever the user actually has is which weights they load,
 * and until now the app never said so.
 *
 * The advice that matters most: **Q4_K_M of a larger model beats Q8_0 of a
 * smaller one at the same memory.** Going from 8-bit to 4-bit costs a few
 * percent on benchmarks; doubling the parameter count is worth far more than
 * that, and the two cost about the same to load.
 *
 * Pure Kotlin, so the arithmetic is unit-tested rather than eyeballed.
 */
object ModelAdvisor {

    private const val GB = 1024L * 1024 * 1024

    /**
     * Room the runtime needs beyond the weights: the KV cache for a 6k context
     * plus the app itself. Measured against a 4B at 6144 tokens, rounded up.
     */
    private const val RUNTIME_OVERHEAD_BYTES = 1_200L * 1024 * 1024

    /** Below this margin a load will technically succeed and then thrash. */
    private const val COMFORT_MARGIN_BYTES = 1_500L * 1024 * 1024

    /**
     * Real GGUF file sizes, in the order a user should prefer them: the most
     * capable model that still fits comes first.
     */
    private val CLASSES = listOf(
        Triple("30B+", "Q4_K_M", 18_500L * 1024 * 1024),
        Triple("12–14B", "Q4_K_M", 8_500L * 1024 * 1024),
        Triple("7–9B", "Q5_K_M", 5_800L * 1024 * 1024),
        Triple("7–9B", "Q4_K_M", 4_900L * 1024 * 1024),
        Triple("4B", "Q8_0", 4_300L * 1024 * 1024),
        Triple("4B", "Q4_K_M", 2_500L * 1024 * 1024),
        Triple("1–2B", "Q4_K_M", 1_100L * 1024 * 1024),
    )

    fun optionsFor(budgetBytes: Long): List<ModelOption> = CLASSES.map { (params, quant, size) ->
        val needed = size + RUNTIME_OVERHEAD_BYTES
        ModelOption(
            parameters = params,
            quantisation = quant,
            approxBytes = size,
            fit = when {
                budgetBytes <= 0L -> Fit.TIGHT
                budgetBytes >= needed + COMFORT_MARGIN_BYTES -> Fit.COMFORTABLE
                budgetBytes >= needed -> Fit.TIGHT
                else -> Fit.TOO_BIG
            },
        )
    }

    /** The largest class that fits comfortably, or null when nothing does. */
    fun best(budgetBytes: Long): ModelOption? =
        optionsFor(budgetBytes).firstOrNull { it.fit == Fit.COMFORTABLE }

    /**
     * One sentence naming the best class this device can hold, or a warning
     * when even the smallest won't fit.
     */
    fun headline(budgetBytes: Long, s: AppStrings): String {
        val best = best(budgetBytes) ?: return s.advisorNothingFits
        return s.advisorHeadline("${best.parameters} · ${best.quantisation}")
    }
}
