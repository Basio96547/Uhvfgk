package com.example.ondevicellm.audio

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hosted-TTS request builder.
 *
 * Everything here is a thing that fails silently in production and loudly in a
 * test: an unescaped ampersand that 400s the whole request, a rate the service
 * reads as a literal string, a voice name that quietly resolves to English.
 * None of it needs a key or a network to check.
 */
class CloudTtsTest {

    // ------------------------------------------------------------- escaping

    @Test
    fun `xml escaping covers every character that breaks SSML`() {
        assertEquals(
            "&amp;&lt;&gt;&quot;&apos;",
            CloudTts.escapeXml("&<>\"'")
        )
    }

    @Test
    fun `arabic text passes through xml escaping unchanged`() {
        val text = "مرحبا، كيف حالك؟"
        assertEquals(text, CloudTts.escapeXml(text))
    }

    @Test
    fun `an ampersand in the reply does not leak into the ssml`() {
        val body = CloudTts.azure("westeurope", "k", "ar-SA-HamedNeural", "Tom & Jerry").body
        assertTrue(body.contains("Tom &amp; Jerry"))
        // The only bare & in the document would be the one we just escaped.
        assertTrue(Regex("&(?!amp;|lt;|gt;|quot;|apos;)").find(body) == null)
    }

    @Test
    fun `json escaping handles quotes backslashes and control characters`() {
        assertEquals("\"a\\\"b\"", CloudTts.jsonString("a\"b"))
        assertEquals("\"a\\\\b\"", CloudTts.jsonString("a\\b"))
        assertEquals("\"a\\nb\"", CloudTts.jsonString("a\nb"))
        assertTrue(CloudTts.jsonString("ab").contains("\\u0001"))
    }

    // ---------------------------------------------------------------- azure

    @Test
    fun `azure url carries the region`() {
        val request = CloudTts.azure("eastus", "key", CloudTts.DEFAULT_AZURE_VOICE, "hi")
        assertEquals(
            "https://eastus.tts.speech.microsoft.com/cognitiveservices/v1",
            request.url
        )
    }

    @Test
    fun `azure region and key are trimmed`() {
        // Pasting a key from a web console routinely brings whitespace with it,
        // and a trailing space in the host makes the URL unresolvable.
        val request = CloudTts.azure(" eastus ", " key ", CloudTts.DEFAULT_AZURE_VOICE, "hi")
        assertTrue(request.url.startsWith("https://eastus."))
        assertEquals("key", request.headers["Ocp-Apim-Subscription-Key"])
    }

    @Test
    fun `azure asks for raw pcm at the rate the player is told to expect`() {
        val request = CloudTts.azure("eastus", "key", CloudTts.DEFAULT_AZURE_VOICE, "hi")
        assertEquals("raw-24khz-16bit-mono-pcm", request.headers["X-Microsoft-OutputFormat"])
        assertEquals(24_000, request.sampleRateHz)
    }

    @Test
    fun `azure ssml names the voice and its own locale`() {
        val request = CloudTts.azure("eastus", "key", "ar-EG-SalmaNeural", "مرحبا")
        assertTrue(request.body.contains("xml:lang='ar-EG'"))
        assertTrue(request.body.contains("name='ar-EG-SalmaNeural'"))
        assertTrue(request.body.contains("مرحبا"))
    }

    @Test
    fun `locale comes from the voice name, not from a guess`() {
        assertEquals("ar-SA", CloudTts.localeOfAzureVoice("ar-SA-HamedNeural"))
        assertEquals("ar-EG", CloudTts.localeOfAzureVoice("ar-EG-SalmaNeural"))
        // Nonsense input falls back to Arabic rather than to the JVM default.
        assertEquals("ar-SA", CloudTts.localeOfAzureVoice("garbage"))
    }

    @Test
    fun `every listed azure voice is arabic and well formed`() {
        assertTrue(CloudTts.AZURE_ARABIC_VOICES.isNotEmpty())
        for (voice in CloudTts.AZURE_ARABIC_VOICES) {
            assertTrue(voice, voice.startsWith("ar-"))
            assertTrue(voice, voice.endsWith("Neural"))
            assertEquals(voice, 3, voice.split('-').size)
        }
        assertTrue(CloudTts.DEFAULT_AZURE_VOICE in CloudTts.AZURE_ARABIC_VOICES)
    }

    // ----------------------------------------------------------------- rate

    @Test
    fun `speaking rate becomes a signed percentage`() {
        assertEquals("+0%", CloudTts.ratePercent(1.0f))
        assertEquals("+50%", CloudTts.ratePercent(1.5f))
        assertEquals("-25%", CloudTts.ratePercent(0.75f))
    }

    @Test
    fun `speaking rate is clamped to what the service accepts`() {
        assertEquals("+100%", CloudTts.ratePercent(9f))
        assertEquals("-50%", CloudTts.ratePercent(0f))
    }

    // ----------------------------------------------------------- elevenlabs

    @Test
    fun `elevenlabs asks for pcm and the multilingual model`() {
        val request = CloudTts.elevenLabs("key", "voice-id", "مرحبا")
        assertTrue(request.url.contains("/voice-id?"))
        assertTrue(request.url.contains("output_format=pcm_24000"))
        assertTrue(request.body.contains(CloudTts.ELEVENLABS_MODEL))
        assertEquals("key", request.headers["xi-api-key"])
        assertEquals(24_000, request.sampleRateHz)
    }

    @Test
    fun `elevenlabs body is valid json around the text`() {
        val request = CloudTts.elevenLabs("key", "v", "say \"hi\"")
        assertTrue(request.body.startsWith("{\"text\":\""))
        assertTrue(request.body.contains("\\\"hi\\\""))
        assertTrue(request.body.endsWith("}"))
    }

    // ------------------------------------------------------- pre-flight check

    @Test
    fun `a missing key is reported before anything is sent`() {
        val s = EnglishStrings
        assertEquals(
            s.cloudNeedsKey,
            CloudTts.missingSetting(CloudTtsProvider.AZURE, "", "eastus", "v", s)
        )
        assertEquals(
            s.cloudNeedsKey,
            CloudTts.missingSetting(CloudTtsProvider.ELEVENLABS, null, null, "v", s)
        )
    }

    @Test
    fun `each provider asks for the field it actually needs`() {
        val s = EnglishStrings
        assertEquals(
            s.cloudNeedsRegion,
            CloudTts.missingSetting(CloudTtsProvider.AZURE, "k", " ", "v", s)
        )
        // Azure does not need a voice id — it has a default.
        assertNull(CloudTts.missingSetting(CloudTtsProvider.AZURE, "k", "eastus", null, s))

        assertEquals(
            s.cloudNeedsVoiceId,
            CloudTts.missingSetting(CloudTtsProvider.ELEVENLABS, "k", null, "", s)
        )
        // ElevenLabs does not need a region.
        assertNull(CloudTts.missingSetting(CloudTtsProvider.ELEVENLABS, "k", null, "v", s))
    }

    @Test
    fun `the arabic strings answer too`() {
        // The pre-flight message is the first thing a user sees when setup is
        // incomplete; in Arabic it must not fall back to English.
        val message = CloudTts.missingSetting(
            CloudTtsProvider.AZURE, "", "eastus", "v", ArabicStrings
        )
        assertNotNull(message)
        assertEquals(ArabicStrings.cloudNeedsKey, message)
        assertTrue(message!!.any { it in '؀'..'ۿ' })
    }

    // --------------------------------------------------------------- labels

    @Test
    fun `both providers have a label in both languages`() {
        for (provider in CloudTtsProvider.entries) {
            assertTrue(provider.label(EnglishStrings).isNotBlank())
            assertTrue(provider.label(ArabicStrings).isNotBlank())
        }
    }
}
