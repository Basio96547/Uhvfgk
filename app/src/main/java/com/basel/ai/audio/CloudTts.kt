package com.basel.ai.audio

import com.basel.ai.core.AppStrings

/**
 * A hosted speech service.
 *
 * These exist because the honest answer to "I want a voice that sounds human
 * in Arabic" is that the best ones are not on the phone. Using one means the
 * text of every reply leaves the device, which is the opposite of everything
 * else here — so it is off unless the user turns it on, needs their own key,
 * and says plainly what it sends.
 */
enum class CloudTtsProvider {
    /**
     * Azure Neural TTS. Voice names are documented and stable, and it can
     * return raw PCM, so no audio decoder is needed on this side.
     */
    AZURE,

    /** ElevenLabs. Voices are opaque ids the user copies from their account. */
    ELEVENLABS,
    ;

    fun label(s: AppStrings): String = when (this) {
        AZURE -> s.cloudAzure
        ELEVENLABS -> s.cloudElevenLabs
    }
}

/** Everything needed to make one request, resolved from settings. */
data class CloudTtsRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    /** Sample rate of the PCM the service will return. */
    val sampleRateHz: Int,
)

/**
 * Builds hosted-TTS requests.
 *
 * Pure: the URL, the headers, the SSML and its escaping are the parts that
 * silently break, and they are all testable without a network or a key.
 */
object CloudTts {

    /** Both services can return signed 16-bit mono PCM at this rate. */
    const val SAMPLE_RATE_HZ = 24_000

    /**
     * Azure's Arabic neural voices, by region.
     *
     * Documented names, not guesses. Saudi first because that is this app's
     * primary audience; Egyptian and Emirati follow because dialect preference
     * is personal and a single "Arabic" voice serves nobody well.
     */
    val AZURE_ARABIC_VOICES = listOf(
        "ar-SA-HamedNeural",
        "ar-SA-ZariyahNeural",
        "ar-EG-SalmaNeural",
        "ar-EG-ShakirNeural",
        "ar-AE-FatimaNeural",
        "ar-AE-HamdanNeural",
    )

    const val DEFAULT_AZURE_VOICE = "ar-SA-HamedNeural"

    /** Multilingual model; the monolingual ones do not speak Arabic. */
    const val ELEVENLABS_MODEL = "eleven_multilingual_v2"

    /** XML-escapes text for SSML. An unescaped `&` makes the whole request 400. */
    fun escapeXml(text: String): String = buildString(text.length + 16) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    /** The language a voice speaks, taken from its own name. */
    fun localeOfAzureVoice(voice: String): String {
        val parts = voice.split('-')
        return if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "ar-SA"
    }

    /**
     * Azure speaking-rate as SSML wants it: a signed percentage against
     * normal, rather than the multiplier the rest of the app uses.
     */
    fun ratePercent(multiplier: Float): String {
        val percent = ((multiplier.coerceIn(0.5f, 2.0f) - 1f) * 100).toInt()
        return if (percent >= 0) "+$percent%" else "$percent%"
    }

    fun azure(
        region: String,
        apiKey: String,
        voice: String,
        text: String,
        speakingRate: Float = 1.0f,
    ): CloudTtsRequest {
        val locale = localeOfAzureVoice(voice)
        return CloudTtsRequest(
            url = "https://${region.trim()}.tts.speech.microsoft.com/cognitiveservices/v1",
            headers = mapOf(
                "Ocp-Apim-Subscription-Key" to apiKey.trim(),
                "Content-Type" to "application/ssml+xml",
                // Raw PCM rather than MP3: the app already plays PCM, and
                // pulling in a decoder to save bandwidth would be a poor trade.
                "X-Microsoft-OutputFormat" to "raw-24khz-16bit-mono-pcm",
            ),
            body = buildString {
                append("<speak version='1.0' xml:lang='")
                append(locale)
                append("'><voice name='")
                append(voice)
                append("'><prosody rate='")
                append(ratePercent(speakingRate))
                append("'>")
                append(escapeXml(text))
                append("</prosody></voice></speak>")
            },
            sampleRateHz = SAMPLE_RATE_HZ,
        )
    }

    fun elevenLabs(
        apiKey: String,
        voiceId: String,
        text: String,
    ): CloudTtsRequest = CloudTtsRequest(
        url = "https://api.elevenlabs.io/v1/text-to-speech/${voiceId.trim()}" +
            "?output_format=pcm_24000",
        headers = mapOf(
            "xi-api-key" to apiKey.trim(),
            "Content-Type" to "application/json",
        ),
        body = buildString {
            append("{\"text\":")
            append(jsonString(text))
            append(",\"model_id\":\"")
            append(ELEVENLABS_MODEL)
            append("\"}")
        },
        sampleRateHz = SAMPLE_RATE_HZ,
    )

    /** Minimal JSON string escaping — enough for a text field, no dependency. */
    fun jsonString(text: String): String = buildString(text.length + 16) {
        append('"')
        for (ch in text) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }

    /**
     * Whether the settings are complete enough to try.
     *
     * Checked before the request so the user gets "you haven't set a region"
     * rather than an opaque 401.
     */
    fun missingSetting(
        provider: CloudTtsProvider,
        apiKey: String?,
        region: String?,
        voice: String?,
        s: AppStrings,
    ): String? {
        if (apiKey.isNullOrBlank()) return s.cloudNeedsKey
        return when (provider) {
            CloudTtsProvider.AZURE ->
                if (region.isNullOrBlank()) s.cloudNeedsRegion else null
            CloudTtsProvider.ELEVENLABS ->
                if (voice.isNullOrBlank()) s.cloudNeedsVoiceId else null
        }
    }
}
