package com.example.ondevicellm.audio

/** Tunables applied to a synthesis request. */
data class SpeechOptions(
    val languageTag: String = "en-US",
    /** 1.0 = the model/engine's natural speed. */
    val speakingRate: Float = 1.0f,
    /** 1.0 = natural pitch. Only the system engine honours this. */
    val pitch: Float = 1.0f,
    /** Multi-speaker models select a voice by index. */
    val speakerId: Int = 0,
)

/** What a synthesizer produced. */
sealed interface SynthesisResult {
    /**
     * Raw mono audio the caller owns — play it with [AudioPlayer] or save it
     * with [WavWriter].
     */
    data class Pcm(val samples: FloatArray, val sampleRateHz: Int) : SynthesisResult {
        val durationMs: Long get() = PcmAudio.durationMs(samples.size, sampleRateHz)

        // FloatArray gives identity equals/hashCode by default, which is wrong
        // for a value class that Compose may compare.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pcm) return false
            return sampleRateHz == other.sampleRateHz && samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRateHz
    }

    /** The engine handled playback itself; there is no buffer to hand back. */
    data object PlayedDirectly : SynthesisResult

    data class Failed(val message: String) : SynthesisResult
}

/**
 * Turns text into speech.
 *
 * Two implementations ship with the app:
 *  - [SystemTtsSynthesizer] — Android's built-in engine. Works out of the box
 *    and stays offline once a voice pack is installed.
 *  - [ModelTtsSynthesizer] — runs a TTS model file the user added on the Models
 *    screen, for full control over the voice.
 */
interface SpeechSynthesizer {

    /** Human-readable name shown in settings. */
    val displayName: String

    /** True once the engine is ready to synthesize. */
    val isReady: Boolean

    /** Loads whatever the engine needs. Safe to call more than once. */
    suspend fun prepare(): Boolean

    suspend fun speak(text: String, options: SpeechOptions): SynthesisResult

    fun stop()

    fun release()
}
