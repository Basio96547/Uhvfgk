package com.example.ondevicellm.audio

import com.example.ondevicellm.core.Localization
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Voice input via Android's on-device speech recognizer.
 *
 * This uses the platform recognizer with `EXTRA_PREFER_OFFLINE`, so with an
 * offline language pack installed transcription stays on the device — matching
 * the app's offline-first goal.
 */
class SpeechInput(private val context: Context) {

    interface Callbacks {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onEndOfSpeech()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** True when the device can transcribe without a network connection. */
    val supportsOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(languageTag: String = Locale.getDefault().toLanguageTag(), callbacks: Callbacks) {
        if (listening) return
        if (!isAvailable) {
            callbacks.onError(Localization.strings.speechUnavailable)
            return
        }

        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = engine
        listening = true

        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                callbacks.onEndOfSpeech()
            }

            override fun onError(error: Int) {
                listening = false
                callbacks.onError(describeError(error))
                release()
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                callbacks.onFinal(text)
                release()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotEmpty()) callbacks.onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Keep transcription on-device when a local language pack exists.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        engine.startListening(intent)
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        listening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun describeError(error: Int): String {
        val s = Localization.strings
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> s.speechErrorAudio
            SpeechRecognizer.ERROR_CLIENT -> s.speechErrorClient
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> s.speechErrorPermission
            SpeechRecognizer.ERROR_NETWORK -> s.speechErrorNetwork
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> s.speechErrorTimeout
            SpeechRecognizer.ERROR_NO_MATCH -> s.speechErrorNoMatch
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> s.speechErrorBusy
            SpeechRecognizer.ERROR_SERVER -> s.speechErrorServer
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> s.speechErrorNoSpeech
            else -> s.speechErrorOther(error)
        }
    }
}
