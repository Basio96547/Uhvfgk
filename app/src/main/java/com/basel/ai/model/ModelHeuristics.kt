package com.basel.ai.model

/**
 * Filename-based guesses used when a model is first added, so the user starts
 * from a sensible type instead of always "Text". Every guess is editable on the
 * Models screen — these are conveniences, not decisions.
 */
object ModelHeuristics {

    private val ASR_HINTS = listOf(
        "whisper", "asr", "wav2vec", "moonshine", "conformer", "stt", "speech2text",
    )

    private val TTS_HINTS = listOf(
        "tts", "kokoro", "piper", "vits", "tacotron", "outetts", "speecht5",
        "vocoder", "melgan", "hifigan", "text2speech", "styletts",
    )

    private val MULTIMODAL_HINTS = listOf("3n", "vision", "-vl", "omni", "multimodal")

    private val THINKING_HINTS = listOf("qwen3", "r1", "deepseek", "think", "reason", "cot")

    fun guessKind(fileName: String): ModelKind {
        val name = fileName.lowercase()
        return when {
            // TTS is checked before ASR: names like "speecht5_tts" match both,
            // and the more specific intent is the one in the filename.
            TTS_HINTS.any { name.contains(it) } -> ModelKind.TTS
            ASR_HINTS.any { name.contains(it) } -> ModelKind.ASR
            MULTIMODAL_HINTS.any { name.contains(it) } -> ModelKind.MULTIMODAL
            else -> ModelKind.TEXT
        }
    }

    fun guessThinking(fileName: String): Boolean {
        val name = fileName.lowercase()
        return THINKING_HINTS.any { name.contains(it) }
    }

    /** Default output rate for a TTS model, inferred from well-known families. */
    fun guessTtsSampleRate(fileName: String): Int {
        val name = fileName.lowercase()
        return when {
            name.contains("kokoro") -> 24_000
            name.contains("piper") || name.contains("vits") -> 22_050
            name.contains("speecht5") -> 16_000
            else -> 22_050
        }
    }
}
