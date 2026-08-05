package com.basel.ai.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIndexTest {

    private fun page(number: Int, text: String) =
        PdfPageText(number, text, TextSource.EMBEDDED)

    // ------------------------------------------------------------ chunking

    @Test
    fun `short pages are grouped, and the chunk knows which pages it spans`() {
        val chunks = DocumentIndex.chunk(
            listOf(page(1, "alpha"), page(2, "beta"), page(3, "gamma")),
            targetChars = 100,
        )
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].firstPage)
        assertEquals(3, chunks[0].lastPage)
        assertEquals("1-3", chunks[0].pageLabel())
    }

    @Test
    fun `a single page label is not a range`() {
        val chunks = DocumentIndex.chunk(listOf(page(7, "x".repeat(50))), targetChars = 40)
        assertTrue(chunks.all { it.pageLabel() == "7" })
    }

    @Test
    fun `a long page is split, and every piece still points at that page`() {
        val long = (1..40).joinToString(" ") { "sentence number $it here." }
        val chunks = DocumentIndex.chunk(listOf(page(5, long)), targetChars = 120)
        assertTrue("expected several chunks, got ${chunks.size}", chunks.size > 2)
        assertTrue(chunks.all { it.firstPage == 5 && it.lastPage == 5 })
    }

    @Test
    fun `no chunk greatly exceeds the target`() {
        val long = (1..200).joinToString(" ") { "word$it" }
        val chunks = DocumentIndex.chunk(listOf(page(1, long)), targetChars = 200)
        // Boundaries are found near the target, not exactly at it; twice over
        // would mean the splitter had given up.
        assertTrue(chunks.all { it.text.length <= 400 })
    }

    @Test
    fun `text with no boundaries anywhere is still cut`() {
        // A table, or a language without spaces. Emitting one enormous chunk
        // would defeat the whole point of chunking.
        val chunks = DocumentIndex.chunk(listOf(page(1, "x".repeat(1000))), targetChars = 100)
        assertTrue(chunks.size > 5)
    }

    @Test
    fun `empty pages are skipped rather than becoming empty chunks`() {
        val chunks = DocumentIndex.chunk(
            listOf(page(1, ""), page(2, "real content here"), page(3, "   ")),
            targetChars = 100,
        )
        assertEquals(1, chunks.size)
        assertEquals(2, chunks[0].firstPage)
    }

    // ------------------------------------------------------------- tokens

    @Test
    fun `stopwords and single characters are dropped`() {
        val tokens = DocumentIndex.tokens("what is the revenue of the company")
        assertTrue("revenue" in tokens)
        assertTrue("company" in tokens)
        assertFalse("the" in tokens)
        assertFalse("is" in tokens)
    }

    @Test
    fun `arabic is normalised before matching`() {
        // "الإيرادات" and "الايرادات" are the same word written two ordinary
        // ways. Without folding, neither matches the other and Arabic search
        // over a document barely works.
        val a = DocumentIndex.tokens("ما هي الإيرادات")
        val b = DocumentIndex.tokens("الايرادات بلغت")
        assertTrue(a.intersect(b).isNotEmpty())
    }

    // ----------------------------------------------------------- selection

    @Test
    fun `the chunk that answers the question is chosen`() {
        val pages = listOf(
            page(1, "This document covers the company history and its founding in nineteen ninety."),
            page(2, "Revenue for the year reached four million dollars across all regions."),
            page(3, "The board of directors meets quarterly to review strategy and plans."),
        )
        val chunks = DocumentIndex.chunk(pages, targetChars = 90)
        val selected = DocumentIndex.select(chunks, "what was the revenue", budgetChars = 100)
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.any { it.text.contains("Revenue") })
    }

    @Test
    fun `a question with no content words falls back to the opening`() {
        // "summarise this" shares no keywords with anything. Returning nothing
        // would break the most common request people make of a document.
        val pages = (1..6).map { page(it, "Chapter $it discusses matters of some length here.") }
        val chunks = DocumentIndex.chunk(pages, targetChars = 60)
        val selected = DocumentIndex.select(chunks, "لخص لي هذا الملف", budgetChars = 120)
        assertTrue(selected.isNotEmpty())
        assertEquals(1, selected.first().firstPage)
    }

    @Test
    fun `selection is returned in reading order, not by score`() {
        // A model handed page 9 before page 2 narrates them in that order.
        val pages = listOf(
            page(1, "unrelated introduction text about nothing in particular at all"),
            page(2, "the widget specification is described in detail on this page"),
            page(3, "more filler that does not mention anything of interest here"),
            page(4, "widget pricing and widget availability are covered here too"),
        )
        val chunks = DocumentIndex.chunk(pages, targetChars = 70)
        val selected = DocumentIndex.select(chunks, "widget", budgetChars = 400)
        val pageNumbers = selected.map { it.firstPage }
        assertEquals(pageNumbers.sorted(), pageNumbers)
    }

    @Test
    fun `the budget is respected`() {
        val pages = (1..30).map { page(it, "page $it content ".repeat(20)) }
        val chunks = DocumentIndex.chunk(pages)
        val selected = DocumentIndex.select(chunks, "content", budgetChars = 2_000)
        assertTrue(selected.sumOf { it.text.length } <= 3_000)
    }

    @Test
    fun `at least one chunk comes back even when the budget is tiny`() {
        // Returning nothing because the first chunk is one character over the
        // budget would answer "I have no information about this document".
        val chunks = DocumentIndex.chunk(listOf(page(1, "a".repeat(500))), targetChars = 500)
        assertTrue(DocumentIndex.select(chunks, "anything", budgetChars = 10).isNotEmpty())
    }

    @Test
    fun `an empty document selects nothing rather than crashing`() {
        assertTrue(DocumentIndex.select(emptyList(), "question").isEmpty())
    }

    // ------------------------------------------------------------- context

    @Test
    fun `the context cites pages and tells the model what to do when the answer is absent`() {
        val chunks = listOf(DocChunk(3, 4, "The revenue was four million."))
        val context = DocumentIndex.buildContext("report.pdf", "revenue?", chunks, totalPages = 90)
        assertTrue(context.contains("report.pdf"))
        assertTrue(context.contains("90"))
        assertTrue(context.contains("[page 3-4]"))
        // The instruction that stops it inventing a figure from a page it was
        // never shown.
        assertTrue(context.contains("not in them", ignoreCase = true))
    }

    @Test
    fun `an arabic question gets an arabic frame`() {
        // An English instruction block in front of an Arabic question pulls a
        // small model into answering in English.
        val chunks = listOf(DocChunk(1, 1, "بلغت الإيرادات أربعة ملايين."))
        val context = DocumentIndex.buildContext("تقرير.pdf", "كم الإيرادات؟", chunks, 12)
        assertTrue(context.any { it in '؀'..'ۿ' })
        assertTrue(context.contains("صفحة"))
    }

    @Test
    fun `no chunks means no context, rather than an empty frame`() {
        // An empty "Extracts from…" block invites the model to answer from it.
        assertEquals("", DocumentIndex.buildContext("a.pdf", "q", emptyList(), 3))
    }
}
