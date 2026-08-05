package com.basel.ai.audio

/**
 * One installed system voice, reduced to the facts that decide whether it is
 * worth using.
 */
data class VoiceOption(
    /** The engine's own identifier, passed back to select it. */
    val name: String,
    val languageTag: String,
    /** The engine's own 1–500 quality scale; higher is better. */
    val quality: Int,
    /** True when the voice only works with a network connection. */
    val needsNetwork: Boolean,
)

/** One speech engine installed on the device. */
data class TtsEngineOption(
    /** Package name, e.g. com.google.android.tts. */
    val packageName: String,
    /** The engine's own display label. */
    val label: String,
)

/**
 * Chooses which installed voice to speak Arabic with.
 *
 * The app used to call `setLanguage("ar-SA")` and take whatever fell out.
 * Android ships several voices per language at different quality tiers, and
 * the default is routinely the oldest and most robotic one installed — which
 * is why the speech sounded mechanical when a better voice was sitting on the
 * same phone unused.
 *
 * Offline wins over quality on a tie, and by more than a tie: this is an
 * offline-first app, and a voice that goes silent without a signal is worse
 * than one that is slightly flatter and always works.
 *
 * Pure Kotlin — `VoiceOption` is a plain record, so the ranking is unit-tested
 * rather than judged by ear on one device.
 */
object VoicePicker {

    /**
     * How much quality a network-only voice has to win by before it is worth
     * depending on a connection. Roughly one full tier on Android's scale.
     */
    private const val NETWORK_PENALTY = 150

    /** Language part of a BCP-47 tag, lowercased. `ar-SA` → `ar`. */
    fun languageOf(tag: String): String =
        tag.substringBefore('-').substringBefore('_').lowercase()

    /** Voices that speak [languageTag]'s language, best first. */
    fun candidatesFor(voices: List<VoiceOption>, languageTag: String): List<VoiceOption> {
        val wanted = languageOf(languageTag)
        return voices
            .filter { languageOf(it.languageTag) == wanted }
            .sortedWith(
                compareByDescending<VoiceOption> { score(it) }
                    // Same score: prefer the exact region the user asked for,
                    // so ar-SA beats ar-EG for a Saudi user.
                    .thenByDescending { it.languageTag.equals(languageTag, ignoreCase = true) }
                    .thenBy { it.name }
            )
    }

    /** The voice to use, or null when nothing installed speaks the language. */
    fun best(voices: List<VoiceOption>, languageTag: String): VoiceOption? =
        candidatesFor(voices, languageTag).firstOrNull()

    /** Ranking score: quality, less a penalty for needing the network. */
    fun score(voice: VoiceOption): Int =
        voice.quality - if (voice.needsNetwork) NETWORK_PENALTY else 0

    /**
     * True when a noticeably better voice exists than the one in use, so the
     * app can point at it instead of leaving the user to wonder.
     */
    fun hasBetterThan(voices: List<VoiceOption>, current: String?, languageTag: String): Boolean {
        val best = best(voices, languageTag) ?: return false
        if (current == null) return true
        val inUse = voices.firstOrNull { it.name == current } ?: return true
        return score(best) > score(inUse)
    }
}
