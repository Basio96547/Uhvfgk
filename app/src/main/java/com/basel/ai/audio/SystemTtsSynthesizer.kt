package com.basel.ai.audio

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
    /** Which engine package the live instance was created with, if any. */
    @Volatile
    private var activeEngine: String? = null
    /**
     * The engine the caller last asked for explicitly.
     *
     * Without this, the no-argument [prepare] means "the system default", so
     * every internal call — speaking, saving to a file — would tear down the
     * engine the user chose in Settings and rebuild it as the default. The
     * chosen engine would then never actually be the one that speaks.
     */
    @Volatile
    private var preferredEngine: String? = null
    private val utteranceCounter = AtomicLong(0)

    override suspend fun prepare(): Boolean = prepare(preferredEngine)

    /**
     * Starts the engine, optionally a specific one by package name.
     *
     * Which *engine* is used matters more for Arabic than which voice: a phone
     * often ships a vendor engine as the default while a markedly better one
     * (Google's, typically) sits installed and unused. The system default is
     * only ever a default.
     */
    suspend fun prepare(enginePackage: String?): Boolean {
        preferredEngine = enginePackage
        if (ready && activeEngine == enginePackage) return true
        // Switching engines means a new instance; the old one holds the audio.
        if (ready) release()

        return suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts = engine
                    activeEngine = enginePackage
                } else {
                    runCatching { engine?.shutdown() }
                }
                if (continuation.isActive) continuation.resume(ready)
            }
            engine = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context.applicationContext, listener)
            } else {
                TextToSpeech(context.applicationContext, listener, enginePackage)
            }
            continuation.invokeOnCancellation { runCatching { engine?.shutdown() } }
        }
    }

    /** Speech engines installed on this device. */
    fun availableEngines(): List<TtsEngineOption> = runCatching {
        tts?.engines.orEmpty().map { TtsEngineOption(it.name, it.label) }
    }.getOrDefault(emptyList())

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


    /** Every installed voice, as plain data the picker can rank. */
    fun availableVoices(): List<VoiceOption> = runCatching {
        tts?.voices.orEmpty().mapNotNull { voice ->
            val tag = voice.locale?.toLanguageTag() ?: return@mapNotNull null
            VoiceOption(
                name = voice.name,
                languageTag = tag,
                quality = voice.quality,
                needsNetwork = voice.isNetworkConnectionRequired,
            )
        }
    }.getOrDefault(emptyList())

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

        // setLanguage alone takes the engine's default voice for that locale,
        // which is routinely the oldest and most robotic one installed. Pick
        // deliberately: the user's chosen voice if it is still there, else the
        // best-ranked one for the language.
        runCatching {
            val installed = availableVoices()
            val chosen = options.voiceName
                ?.let { wanted -> installed.firstOrNull { it.name == wanted } }
                ?: VoicePicker.best(installed, options.languageTag)

            if (chosen != null) {
                engine.voices?.firstOrNull { it.name == chosen.name }?.let(engine::setVoice)
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
        activeEngine = null
        ready = false
    }
}
