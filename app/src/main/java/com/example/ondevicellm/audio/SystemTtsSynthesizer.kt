package com.example.ondevicellm.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Speech output via Android's built-in engine.
 *
 * This is the default because it works with no extra downloads and, once a
 * voice pack is installed, runs entirely offline — matching the app's
 * offline-first goal. Use [ModelTtsSynthesizer] when you want a specific voice
 * from your own model file.
 */
class SystemTtsSynthesizer(private val context: Context) : SpeechSynthesizer {

    override val displayName: String = "System engine"

    private var tts: TextToSpeech? = null
    @Volatile
    private var ready = false
    private val utteranceCounter = AtomicLong(0)

    override val isReady: Boolean get() = ready

    override suspend fun prepare(): Boolean {
        if (ready) return true
        return suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts = engine
                } else {
                    runCatching { engine?.shutdown() }
                }
                if (continuation.isActive) continuation.resume(ready)
            }
            continuation.invokeOnCancellation { runCatching { engine?.shutdown() } }
        }
    }

    override suspend fun speak(text: String, options: SpeechOptions): SynthesisResult {
        if (text.isBlank()) return SynthesisResult.Failed("Nothing to speak.")
        if (!prepare()) return SynthesisResult.Failed("Text-to-speech engine unavailable.")
        val engine = tts ?: return SynthesisResult.Failed("Text-to-speech engine unavailable.")

        applyVoice(engine, options)

        val utteranceId = "utt-${utteranceCounter.incrementAndGet()}"
        return suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resume(SynthesisResult.PlayedDirectly)
                    }
                }

                @Deprecated("Superseded by onError(String, int)")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resume(SynthesisResult.Failed("Speech synthesis failed."))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resume(
                            SynthesisResult.Failed("Speech synthesis failed (code $errorCode).")
                        )
                    }
                }
            })

            continuation.invokeOnCancellation { runCatching { engine.stop() } }

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resume(SynthesisResult.Failed("Could not queue speech."))
            }
        }
    }

    /** Renders [text] to a WAV file instead of playing it. */
    suspend fun synthesizeToFile(
        text: String,
        options: SpeechOptions,
        target: File,
    ): Boolean {
        if (!prepare()) return false
        val engine = tts ?: return false
        applyVoice(engine, options)
        target.parentFile?.mkdirs()

        val utteranceId = "file-${utteranceCounter.incrementAndGet()}"
        return suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(true)
                }

                @Deprecated("Superseded by onError(String, int)")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(false)
                }
            })

            val result = engine.synthesizeToFile(text, Bundle(), target, utteranceId)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    /** Language tags the installed engine can speak without a network. */
    fun availableLanguages(): List<String> = tts?.let { engine ->
        runCatching {
            engine.availableLanguages
                ?.map { it.toLanguageTag() }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())
    }.orEmpty()

    private fun applyVoice(engine: TextToSpeech, options: SpeechOptions) {
        runCatching {
            val locale = Locale.forLanguageTag(options.languageTag)
            val status = engine.setLanguage(locale)
            if (status == TextToSpeech.LANG_MISSING_DATA ||
                status == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.getDefault())
            }
        }
        engine.setSpeechRate(options.speakingRate.coerceIn(0.1f, 3.0f))
        engine.setPitch(options.pitch.coerceIn(0.1f, 2.0f))
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
