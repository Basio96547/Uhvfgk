package com.basel.ai.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case here is something the speech engine was reading out loud.
 *
 * The complaint was that the Arabic voice sounded annoying and inaccurate. A
 * good part of that was never the voice: it was the voice saying "نجمة نجمة"
 * around every bold word and spelling out a URL character by character.
 */
class SpeechTextTest {

    private fun spoken(text: String) = SpeechText.forSpeech(text, "مقطع برمجي")

    @Test
    fun `bold and italic markers are not said`() {
        assertEquals("هذا مهم جدا", spoken("هذا **مهم** جدا"))
        assertEquals("this matters", spoken("this *matters*"))
        assertEquals("mixed emphasis", spoken("**mixed _emphasis_**"))
    }

    @Test
    fun `bullets and headings are not said`() {
        val text = """
            ## العنوان
            - أول بند
            - ثاني بند
        """.trimIndent()
        val out = spoken(text)
        assertFalse(out.contains("#"))
        assertFalse(out.contains("-"))
        assertTrue(out.contains("العنوان"))
        assertTrue(out.contains("أول بند"))
    }

    @Test
    fun `a numbered list keeps its numbers`() {
        // "1." is meaningful when heard; the bullet dash is not.
        val out = spoken("1. الأول\n2. الثاني")
        assertTrue(out.contains("1."))
        assertTrue(out.contains("الثاني"))
    }

    @Test
    fun `a code block becomes one short sentence`() {
        val text = "قبل\n```kotlin\nval x = listOf(1, 2, 3)\n```\nبعد"
        val out = spoken(text)
        assertTrue(out.contains("مقطع برمجي"))
        assertFalse(out.contains("listOf"))
        assertTrue(out.contains("قبل"))
        assertTrue(out.contains("بعد"))
    }

    @Test
    fun `an unterminated code block does not leak the rest of the reply`() {
        // A truncated generation leaves an open fence, and everything after it
        // was being read out as code.
        val out = spoken("شرح\n```\nfun main() {\n    println(1)")
        assertTrue(out.contains("مقطع برمجي"))
        assertFalse(out.contains("println"))
    }

    @Test
    fun `inline code keeps its word`() {
        assertEquals("استخدم ls هنا", spoken("استخدم `ls` هنا"))
    }

    @Test
    fun `urls are dropped, link text is kept`() {
        // Reading an address aloud is unbearable and tells the listener nothing.
        assertEquals("انظر الوثائق", spoken("انظر [الوثائق](https://example.com/a?b=1)").trim())
        assertFalse(spoken("see https://example.com/x/y here").contains("example"))
    }

    @Test
    fun `citations are for the eye`() {
        assertFalse(spoken("الإيرادات أربعة ملايين [صفحة 3]").contains("صفحة 3"))
        assertFalse(spoken("as reported [1] and [2]").contains("["))
    }

    @Test
    fun `a table is read as its cells, not its pipes`() {
        val out = spoken("| المنطقة | العدد |\n| الشمال | 12 |")
        assertFalse(out.contains("|"))
        assertTrue(out.contains("المنطقة"))
        assertTrue(out.contains("12"))
    }

    @Test
    fun `a horizontal rule is silent`() {
        val out = spoken("قبل\n\n---\n\nبعد")
        assertFalse(out.contains("---"))
        assertTrue(out.contains("قبل"))
        assertTrue(out.contains("بعد"))
    }

    @Test
    fun `ordinary arabic prose is untouched`() {
        // The normaliser must not become a second source of wrongness.
        val text = "مرحبًا، كيف حالك؟ أتمنى أن يكون يومك جميلًا."
        assertEquals(text, spoken(text))
    }

    @Test
    fun `arabic punctuation survives, because it is heard as pauses`() {
        val out = spoken("أولًا، ثم ثانيًا؛ وأخيرًا؟")
        assertTrue(out.contains("،"))
        assertTrue(out.contains("؛"))
        assertTrue(out.contains("؟"))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", spoken(""))
        assertEquals("", spoken("   \n  "))
    }

    @Test
    fun `a reply with nothing sayable is reported rather than spoken as silence`() {
        // A button that appears to do nothing reads as broken.
        assertFalse(SpeechText.hasSomethingToSay(spoken("[1] [2] ---")))
        assertTrue(SpeechText.hasSomethingToSay(spoken("نعم")))
    }

    @Test
    fun `whitespace is tidied without gluing words together`() {
        val out = spoken("كلمة    أخرى\n\n\n\nفقرة")
        assertTrue(out.contains("كلمة أخرى"))
        assertFalse(out.contains("\n\n\n"))
    }
}
