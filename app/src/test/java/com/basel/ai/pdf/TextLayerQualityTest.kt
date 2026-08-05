package com.basel.ai.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that decides whether a page goes to OCR.
 *
 * The case it exists for is not the empty page — that one is obvious. It is
 * the page that yields *characters* that are not text: a PDF whose fonts are
 * subsetted with no `ToUnicode` map extracts as control codes or unrelated
 * glyphs, "succeeds", and hands a model gibberish it will answer from.
 */
class TextLayerQualityTest {

    @Test
    fun `ordinary prose is usable`() {
        assertTrue(
            TextLayerQuality.isUsable(
                "This is a normal paragraph of text from a page of a report."
            )
        )
    }

    @Test
    fun `arabic prose is usable`() {
        assertTrue(
            TextLayerQuality.isUsable(
                "هذا نص عربي عادي من صفحة في تقرير، وفيه كلمات كافية للحكم عليه."
            )
        )
    }

    @Test
    fun `an empty or nearly empty page is not usable`() {
        assertFalse(TextLayerQuality.isUsable(""))
        assertFalse(TextLayerQuality.isUsable("   \n  \n "))
        // A page number alone is what a scanned page's text layer often holds.
        assertFalse(TextLayerQuality.isUsable("12"))
    }

    @Test
    fun `a broken font map is not usable`() {
        // What a subsetted font with no ToUnicode actually extracts as.
        val broken = "" +
            ""
        assertFalse(TextLayerQuality.isUsable(broken))
    }

    @Test
    fun `a page of replacement characters is not usable`() {
        assertFalse(TextLayerQuality.isUsable("�".repeat(60)))
    }

    @Test
    fun `private-use glyphs are not text`() {
        // Another shape of the same failure: the glyphs render, and mean nothing.
        assertFalse(TextLayerQuality.isUsable((0xE000..0xE040).map { it.toChar() }.joinToString("")))
    }

    @Test
    fun `stray single letters are not prose`() {
        // Long enough and "meaningful" by character, but no words in it.
        assertFalse(TextLayerQuality.isUsable("a b c d e f g h i j k l m n o p q r s t u v"))
    }

    @Test
    fun `a page of numbers and punctuation is usable`() {
        // A table of figures is a real page, and the text layer is exact.
        assertTrue(
            TextLayerQuality.isUsable(
                "Region North 1,240 12.4% Region South 3,180 31.8% Region East 900 9.0%"
            )
        )
    }

    @Test
    fun `the meaningful ratio counts letters, digits and ordinary punctuation`() {
        assertTrue(TextLayerQuality.meaningfulRatio("Hello, world! (2025)") > 0.95f)
        assertTrue(TextLayerQuality.meaningfulRatio("") < 0.1f)
        assertTrue(TextLayerQuality.meaningfulRatio("مرحبا، كيف حالك؟") > 0.95f)
    }

    @Test
    fun `word counting needs runs of letters`() {
        assertTrue(TextLayerQuality.wordCount("one two three") >= 3)
        assertTrue(TextLayerQuality.wordCount("a b c") == 0)
        assertTrue(TextLayerQuality.wordCount("كلمة أخرى ثالثة") >= 3)
    }

    @Test
    fun `a mostly unreadable document is called scanned`() {
        val pages = List(10) { if (it < 3) "Readable page of real prose text here." else "" }
        assertTrue(TextLayerQuality.looksScanned(pages))
    }

    @Test
    fun `a document with a text layer is not called scanned`() {
        val pages = List(10) { "Readable page of real prose text goes here for page $it." }
        assertFalse(TextLayerQuality.looksScanned(pages))
        assertFalse(TextLayerQuality.looksScanned(emptyList()))
    }
}
