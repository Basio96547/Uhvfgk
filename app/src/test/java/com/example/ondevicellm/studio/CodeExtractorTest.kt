package com.example.ondevicellm.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shape a small model actually replies in.
 *
 * These are not hypotheticals: a 4B model asked for a page will do all of
 * them, and each one that isn't handled shows the user a blank preview with no
 * explanation.
 */
class CodeExtractorTest {

    private val page = "<!doctype html>\n<html><body><h1>Hi</h1></body></html>"

    @Test
    fun `a labelled fence with prose around it`() {
        val raw = """
            Sure! Here is the page you asked for:

            ```html
            $page
            ```

            Let me know if you want changes.
        """.trimIndent()
        assertEquals(page, CodeExtractor.extract(raw))
    }

    @Test
    fun `an unlabelled fence`() {
        assertEquals(page, CodeExtractor.extract("```\n$page\n```"))
    }

    @Test
    fun `a fence labelled something else`() {
        assertEquals(page, CodeExtractor.extract("```HTML\n$page\n```"))
    }

    @Test
    fun `a fence that was never closed because the reply was cut off`() {
        // Keeping the partial page is deliberate: it shows the user how far it
        // got, which is more use than an empty screen.
        val partial = "<!doctype html>\n<html><body><h1>Hi"
        assertEquals(partial, CodeExtractor.extract("```html\n$partial"))
    }

    @Test
    fun `raw html with no fence at all`() {
        assertEquals(page, CodeExtractor.extract(page))
    }

    @Test
    fun `prose with no code is not mistaken for code`() {
        assertEquals("", CodeExtractor.extract("I would build that with a form and a button."))
        assertEquals("", CodeExtractor.extract(""))
        assertEquals("", CodeExtractor.extract("   \n  "))
    }

    @Test
    fun `four backticks are still a fence`() {
        assertEquals(page, CodeExtractor.extract("````html\n$page\n````"))
    }

    // ---- wrapping a fragment ----------------------------------------------

    @Test
    fun `a full document is left alone`() {
        assertEquals(page, CodeExtractor.asDocument(page))
        val noDoctype = "<html><body>x</body></html>"
        assertEquals(noDoctype, CodeExtractor.asDocument(noDoctype))
    }

    @Test
    fun `a bare fragment is wrapped so it renders properly`() {
        // A WebView renders a loose div as unstyled text in the corner, which
        // looks like the model failed when it didn't.
        val wrapped = CodeExtractor.asDocument("<div>hello</div>")
        assertTrue(wrapped.startsWith("<!doctype html>"))
        assertTrue(wrapped.contains("<div>hello</div>"))
        assertTrue("a phone page needs a viewport", wrapped.contains("viewport"))
        assertTrue(wrapped.contains("charset=\"utf-8\""))
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals("", CodeExtractor.asDocument("   "))
    }

    // ---- naming ------------------------------------------------------------

    @Test
    fun `the title comes from the document`() {
        assertEquals(
            "My Page",
            CodeExtractor.titleOf("<html><head><title>My Page</title></head></html>"),
        )
    }

    @Test
    fun `an arabic title survives`() {
        assertEquals("آلة حاسبة", CodeExtractor.titleOf("<title>آلة حاسبة</title>"))
    }

    @Test
    fun `a heading is the fallback, with its tags stripped`() {
        assertEquals("Timer", CodeExtractor.titleOf("<body><h1>Timer</h1></body>"))
        assertEquals(
            "Big Timer",
            CodeExtractor.titleOf("<h1><span>Big</span> Timer</h1>"),
        )
    }

    @Test
    fun `no title and no heading invents nothing`() {
        assertEquals("", CodeExtractor.titleOf("<div>just a div</div>"))
    }

    // ---- document detection ------------------------------------------------

    @Test
    fun `document detection does not fire on prose`() {
        assertFalse(CodeExtractor.looksLikeDocument("Here is how I would do it."))
        assertFalse(CodeExtractor.looksLikeDocument("2 < 3 and 4 > 1"))
        assertTrue(CodeExtractor.looksLikeDocument("<div>x</div>"))
    }
}
