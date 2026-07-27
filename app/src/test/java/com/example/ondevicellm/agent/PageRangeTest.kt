package com.example.ondevicellm.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeTest {

    @Test
    fun `a single page`() {
        assertEquals(listOf(3), PageRange.parse("3", pageCount = 10))
    }

    @Test
    fun `a range`() {
        assertEquals(listOf(2, 3, 4), PageRange.parse("2-4", pageCount = 10))
    }

    @Test
    fun `the dashes models actually emit`() {
        // En dash, em dash and the Arabic tatweel all turn up in model output.
        for (spec in listOf("2-4", "2–4", "2—4", "2ـ4")) {
            assertEquals(spec, listOf(2, 3, 4), PageRange.parse(spec, pageCount = 10))
        }
    }

    @Test
    fun `a list of pages and ranges`() {
        assertEquals(listOf(1, 4, 5, 6), PageRange.parse("1, 4-6", pageCount = 10))
        assertEquals(listOf(1, 3), PageRange.parse("1، 3", pageCount = 10))
    }

    @Test
    fun `page prefixes are ignored`() {
        assertEquals(listOf(5), PageRange.parse("p. 5", pageCount = 10))
        assertEquals(listOf(5), PageRange.parse("page 5", pageCount = 10))
    }

    @Test
    fun `arabic page labels are ignored too`() {
        assertEquals(listOf(5), PageRange.parse("صفحة 5", pageCount = 10))
        assertEquals(listOf(2, 3), PageRange.parse("ص 2-3", pageCount = 10))
    }

    @Test
    fun `a backwards range is read the right way round`() {
        assertEquals(listOf(2, 3, 4), PageRange.parse("4-2", pageCount = 10))
    }

    @Test
    fun `pages outside the document are dropped`() {
        assertEquals(listOf(9, 10), PageRange.parse("9-14", pageCount = 10))
        assertTrue(PageRange.parse("50", pageCount = 10).isEmpty())
    }

    @Test
    fun `no spec means the opening pages`() {
        assertEquals(listOf(1, 2, 3), PageRange.parse(null, pageCount = 10))
        assertEquals(listOf(1, 2), PageRange.parse("  ", pageCount = 2))
    }

    @Test
    fun `asking for the whole book is capped`() {
        // Otherwise one call returns more than the context window holds, and
        // the turn dies with no explanation.
        val pages = PageRange.parse("1-500", pageCount = 500)
        assertTrue(pages.size <= PageRange.MAX_PAGES)
    }

    @Test
    fun `an empty document yields nothing`() {
        assertTrue(PageRange.parse("1", pageCount = 0).isEmpty())
    }

    @Test
    fun `nonsense yields nothing rather than page one`() {
        // Silently answering with page 1 makes a typo look like an answer.
        assertTrue(PageRange.parse("chapter four", pageCount = 10).isEmpty())
    }

    @Test
    fun `duplicates collapse and order is kept`() {
        assertEquals(listOf(2, 3), PageRange.parse("3, 2, 3, 2-3", pageCount = 10))
    }
}
