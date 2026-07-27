package com.example.ondevicellm.ocr

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOcrTest {

    @Test
    fun `the request names the dense-page feature`() {
        // TEXT_DETECTION finds signs in photographs; DOCUMENT_TEXT_DETECTION
        // reads a page in reading order, which is the whole point here.
        val request = CloudOcr.request("KEY", "BASE64", "ar-SA")
        assertTrue(request.body.contains("DOCUMENT_TEXT_DETECTION"))
        assertTrue(request.body.contains("BASE64"))
    }

    @Test
    fun `the key rides in the query string and is trimmed`() {
        val request = CloudOcr.request("  KEY  ", "X", "en-US")
        assertEquals("${CloudOcr.ENDPOINT}?key=KEY", request.url)
    }

    @Test
    fun `arabic gets an arabic hint`() {
        val hints = CloudOcr.languageHints("ar-SA")
        assertEquals("ar", hints.first())
        // English alongside, because Arabic pages carry Latin page numbers and
        // URLs, and without the pair they come back transliterated.
        assertTrue("en" in hints)
    }

    @Test
    fun `an unknown tag still yields a usable hint`() {
        assertTrue(CloudOcr.languageHints("").isNotEmpty())
        assertEquals("fr", CloudOcr.languageHints("fr-FR").first())
    }

    @Test
    fun `text comes out of a real response shape`() {
        val json = """
            {"responses":[{"fullTextAnnotation":{"text":"صفحة من كتاب\nسطر ثانٍ"}}]}
        """.trimIndent()
        assertEquals("صفحة من كتاب\nسطر ثانٍ", CloudOcr.textOf(json))
    }

    @Test
    fun `a blank page is null text, not an error`() {
        // A page can be a photograph of a wall. Calling that a failure would
        // make the whole document look broken.
        assertNull(CloudOcr.textOf("""{"responses":[{}]}"""))
        assertNull(CloudOcr.errorOf("""{"responses":[{}]}"""))
    }

    @Test
    fun `a top-level error is surfaced`() {
        val json = """{"error":{"code":403,"message":"API key not valid"}}"""
        assertEquals("API key not valid", CloudOcr.errorOf(json))
        assertNull(CloudOcr.textOf(json))
    }

    @Test
    fun `a per-request error is surfaced too`() {
        // A bad key and a bad image are different faults with different fixes,
        // and they arrive in different places in the response.
        val json = """{"responses":[{"error":{"message":"Bad image data"}}]}"""
        assertEquals("Bad image data", CloudOcr.errorOf(json))
    }

    @Test
    fun `garbage in the response is not read as text`() {
        assertNull(CloudOcr.textOf("<html>502 Bad Gateway</html>"))
        assertNull(CloudOcr.errorOf("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `a missing key is caught before anything is sent`() {
        assertEquals(EnglishStrings.ocrNeedsKey, CloudOcr.missingSetting("", EnglishStrings))
        assertNull(CloudOcr.missingSetting("KEY", EnglishStrings))

        val arabic = CloudOcr.missingSetting(null, ArabicStrings)
        assertNotNull(arabic)
        assertTrue(arabic!!.any { it in '؀'..'ۿ' })
    }
}
