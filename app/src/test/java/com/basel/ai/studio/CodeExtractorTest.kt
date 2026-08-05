package com.basel.ai.studio

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
    fun `a full document keeps its own markup`() {
        // Not byte-identical: a document without a viewport gains one, because
        // otherwise the WebView lays it out at 980px and shrinks it. Everything
        // the model wrote is preserved.
        listOf(page, "<html><body>x</body></html>").forEach { document ->
            val result = CodeExtractor.asDocument(document)
            assertTrue(result.contains("<body>"))
            assertFalse("must not be wrapped a second time", result.contains("<html", ignoreCase = true)
                .let { _ -> Regex("<html", RegexOption.IGNORE_CASE).findAll(result).count() > 1 })
            assertTrue(CodeExtractor.hasViewport(result))
        }
        assertTrue(CodeExtractor.asDocument(page).contains("<h1>Hi</h1>"))
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

    // ---- laying out at the device width -----------------------------------

    @Test
    fun `a document without a viewport gets one`() {
        val bare = "<!doctype html><html><head><title>x</title></head><body>hi</body></html>"
        assertFalse(CodeExtractor.hasViewport(bare))
        val fixed = CodeExtractor.asDocument(bare)
        assertTrue(CodeExtractor.hasViewport(fixed))
        assertTrue("the page itself is untouched", fixed.contains("<title>x</title>"))
        assertTrue(fixed.contains("width=device-width"))
    }

    @Test
    fun `a document that already has one is left alone`() {
        val withViewport =
            "<html><head><meta name=\"viewport\" content=\"width=device-width\"></head></html>"
        assertEquals(withViewport, CodeExtractor.asDocument(withViewport))
    }

    @Test
    fun `single quotes and odd spacing still count as a viewport`() {
        assertTrue(CodeExtractor.hasViewport("<meta name = 'viewport' content='x'>"))
        assertTrue(CodeExtractor.hasViewport("<META NAME=\"VIEWPORT\" CONTENT=\"x\">"))
    }

    @Test
    fun `a document with no head still gets a viewport`() {
        val noHead = "<html><body>hi</body></html>"
        val fixed = CodeExtractor.ensureViewport(noHead)
        assertTrue(CodeExtractor.hasViewport(fixed))
        assertTrue(fixed.contains("<body>hi</body>"))
    }

    @Test
    fun `a bare fragment with no html tag still gets a viewport`() {
        val fixed = CodeExtractor.ensureViewport("<div>hi</div>")
        assertTrue(CodeExtractor.hasViewport(fixed))
        assertTrue(fixed.contains("<div>hi</div>"))
    }

    @Test
    fun `nothing in, nothing invented`() {
        assertEquals("", CodeExtractor.ensureViewport(""))
    }
}
