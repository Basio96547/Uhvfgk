package com.basel.ai.audio

import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speech from a hosted service.
 *
 * The only part of this app that sends what you wrote to someone else's
 * server, and it exists because the honest answer to "I want a voice that
 * sounds human in Arabic" is that the best ones are not on the phone. It is
 * off unless switched on, needs the user's own key, and the settings screen
 * says what leaves the device.
 *
 * Both providers are asked for raw PCM so no audio decoder is needed here.
 */
class CloudTtsSynthesizer(
    private val provider: CloudTtsProvider,
    private val apiKey: String,
    private val region: String,
    private val voice: String,
) : SpeechSynthesizer {

    override val displayName: String = "Cloud voice"

    /** Nothing to load: the service is the runtime. */
    override suspend fun prepare(): Boolean = apiKey.isNotBlank()

    override suspend fun speak(text: String, options: SpeechOptions): SynthesisResult =
        withContext(Dispatchers.IO) {
            val s = Localization.strings
            if (text.isBlank()) return@withContext SynthesisResult.Failed(s.searchNothingToDo)

            CloudTts.missingSetting(provider, apiKey, region, voice, s)?.let {
                return@withContext SynthesisResult.Failed(it)
            }

            val request = when (provider) {
                CloudTtsProvider.AZURE -> CloudTts.azure(
                    region = region,
                    apiKey = apiKey,
                    voice = voice.ifBlank { CloudTts.DEFAULT_AZURE_VOICE },
                    text = text,
                    speakingRate = options.speakingRate,
                )
                CloudTtsProvider.ELEVENLABS -> CloudTts.elevenLabs(apiKey, voice, text)
            }

            try {
                val pcm = post(request)
                if (pcm.isEmpty()) return@withContext SynthesisResult.Failed(s.cloudEmptyAudio)
                SynthesisResult.Pcm(
                    samples = PcmAudio.pcm16ToFloat(pcm),
                    sampleRateHz = request.sampleRateHz,
                )
            } catch (e: Exception) {
                ErrorLog.report("Cloud speech", "Request failed", e)
                SynthesisResult.Failed(e.message ?: s.cloudRequestFailed)
            }
        }

    private fun post(request: CloudTtsRequest): ByteArray {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            request.headers.forEach(::setRequestProperty)
            setRequestProperty("User-Agent", "BaselAi")
        }

        try {
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                // The body carries the reason — a wrong key, a wrong region, a
                // voice the account cannot use. Passing the code alone would
                // make every one of those look the same.
                val detail = runCatching {
                    connection.errorStream?.bufferedReader()?.readText().orEmpty().take(300)
                }.getOrDefault("")
                throw java.io.IOException(
                    Localization.strings.cloudHttpError(connection.responseCode, detail)
                )
            }

            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(16 * 1024)
                var read = input.read(chunk)
                var total = 0
                while (read >= 0 && total < MAX_AUDIO_BYTES) {
                    buffer.write(chunk, 0, read)
                    total += read
                    read = input.read(chunk)
                }
            }
            return buffer.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    override fun stop() = Unit

    override fun release() = Unit

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000

        /** ~2 minutes of 24 kHz mono PCM; a reply is never longer than this. */
        const val MAX_AUDIO_BYTES = 6 * 1024 * 1024
    }
}
