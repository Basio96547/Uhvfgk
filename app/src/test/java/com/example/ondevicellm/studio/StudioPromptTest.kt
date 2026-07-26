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
}
