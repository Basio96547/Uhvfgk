package com.example.ondevicellm.studio

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPromptTest {

    @Test
    fun `the first request just describes what to build`() {
        val turn = StudioPrompt.build("", "a tip calculator", EnglishStrings)
        assertTrue(turn.contains("a tip calculator"))
        assertFalse("nothing to edit yet", turn.contains("current page"))
    }

    @Test
    fun `an edit carries the whole current file`() {
        val code = "<html><body>old</body></html>"
        val turn = StudioPrompt.build(code, "make the button blue", EnglishStrings)
        assertTrue(turn.contains(code))
        assertTrue(turn.contains("make the button blue"))
        // The instruction has to be explicit or a small model returns a snippet.
        assertTrue(turn.contains("whole updated file"))
    }

    @Test
    fun `an arabic request is framed in arabic`() {
        val turn = StudioPrompt.build("", "آلة حاسبة", ArabicStrings)
        assertTrue(turn.contains("آلة حاسبة"))
        assertTrue(turn.any { it in '؀'..'ۿ' })
    }

    @Test
    fun `an oversized file is truncated rather than blowing the context`() {
        val huge = "x".repeat(StudioPrompt.MAX_CODE_CHARS * 2)
        assertTrue(StudioPrompt.isTooLargeToEdit(huge))
        val turn = StudioPrompt.build(huge, "change it", EnglishStrings)
        assertTrue(turn.length < huge.length)
    }

    @Test
    fun `an ordinary page is not considered oversized`() {
        assertFalse(StudioPrompt.isTooLargeToEdit("<html>".repeat(100)))
    }

    @Test
    fun `the system prompt forbids the network, since there isn't one`() {
        listOf(EnglishStrings, ArabicStrings).forEach { s ->
            val prompt = s.studioSystemPrompt
            assertTrue(prompt.contains("CDN"))
            assertTrue(prompt.contains("html"))
        }
    }

    // ---- the rules that make a page real rather than a mock ---------------

    @Test
    fun `the prompt forbids the placeholder handlers that produce dead buttons`() {
        // A model asked for a calculator will happily emit buttons wired to
        // nothing. That is the single most common way a generated page looks
        // finished and does nothing.
        val en = EnglishStrings.studioSystemPrompt.lowercase()
        listOf("placeholder", "todo", "dummy data").forEach {
            assertTrue("should rule out \"$it\"", en.contains(it))
        }
        assertTrue(en.contains("every control must do what it looks like it does"))
    }

    @Test
    fun `the prompt asks for the empty and wrong-input cases`() {
        val en = EnglishStrings.studioSystemPrompt.lowercase()
        assertTrue(en.contains("empty case"))
        assertTrue(en.contains("happy path"))
    }

    @Test
    fun `the prompt sizes the page for a phone`() {
        listOf(EnglishStrings, ArabicStrings).forEach { s ->
            val p = s.studioSystemPrompt
            assertTrue(p.contains("viewport", ignoreCase = true))
            assertTrue("touch targets need a number", p.contains("44"))
            assertTrue("fixed pixel widths are the usual mistake", p.contains("flexbox"))
        }
    }

    @Test
    fun `the prompt asks for restraint rather than extra features`() {
        val en = EnglishStrings.studioSystemPrompt.lowercase()
        assertTrue(en.contains("exactly what was asked"))
        assertTrue(en.contains("do not add features"))
        // Comments explaining why, not restating what.
        assertTrue(en.contains("why"))
    }

    @Test
    fun `the arabic prompt says all of it in arabic`() {
        val ar = ArabicStrings.studioSystemPrompt
        assertTrue(ar.contains("واجهة وهمية"))
        assertTrue(ar.contains("لماذا"))
        assertTrue(ar.contains("الحالة الفارغة"))
    }

    @Test
    fun `the prompt stays short enough for a small model to hold`() {
        // Long enough to carry the rules, short enough to leave the context for
        // the page itself.
        listOf(EnglishStrings, ArabicStrings).forEach { s ->
            assertTrue(s.studioSystemPrompt.length in 800..2200)
        }
    }
}
