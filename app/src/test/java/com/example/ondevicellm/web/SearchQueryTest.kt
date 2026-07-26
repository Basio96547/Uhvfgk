package com.example.ondevicellm.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `escapes queries into URLs`() {
        assertTrue(SearchQuery.duckDuckGoUrl("hello world").contains("q=hello+world"))
        assertTrue(SearchQuery.encode("مرحبا").startsWith("%"))
        assertTrue(SearchQuery.duckDuckGoUrl("x").startsWith("https://"))
    }

    @Test
    fun `maps a language tag onto a Wikipedia host`() {
        assertEquals("ar.wikipedia.org", SearchQuery.wikiHost("ar-SA"))
        assertEquals("en.wikipedia.org", SearchQuery.wikiHost("en"))
    }

    @Test
    fun `falls back to English for an unusable tag`() {
        // A malformed tag must not produce a host that doesn't resolve.
        assertEquals("en.wikipedia.org", SearchQuery.wikiHost(""))
        assertEquals("en.wikipedia.org", SearchQuery.wikiHost("12-34"))
    }

    @Test
    fun `uses underscores in article titles`() {
        assertTrue(SearchQuery.wikipediaSummaryUrl("en", "Alan Turing").endsWith("Alan_Turing"))
    }

    @Test
    fun `cleans snippets`() {
        assertEquals("bold text", SearchQuery.trimSnippet("<b>bold</b> text"))
        assertEquals("a b c", SearchQuery.trimSnippet("a   b\n\nc"))
        assertEquals("short", SearchQuery.trimSnippet("short", 100))
    }

    @Test
    fun `truncates long snippets on a word boundary`() {
        val long = "x".repeat(50) + " " + "y".repeat(50)
        assertTrue(SearchQuery.trimSnippet(long, 60).endsWith("…"))
    }

    private val sample = listOf(
        SearchResult("A", "snippet A", "https://a.com/", "P"),
        SearchResult("A dup", "snippet A2", "https://a.com", "P"),
        SearchResult("B", "", "https://b.com", "P"),
        SearchResult("C", "snippet C", "", "P"),
        SearchResult("D", "snippet D", "https://d.com", "P"),
    )

    @Test
    fun `dedupes by URL and drops unusable entries`() {
        val deduped = SearchQuery.dedupe(sample)
        assertEquals(2, deduped.size)
        // Trailing slashes must not create a phantom second result.
        assertEquals("A", deduped[0].title)
        assertTrue(deduped.none { it.title == "B" || it.title == "C" })
    }

    @Test
    fun `builds a citable context block`() {
        val context = SearchQuery.buildContext("who is X", SearchQuery.dedupe(sample))
        assertTrue(context.contains("[1]"))
        assertTrue(context.contains("[2]"))
        assertTrue(context.contains("https://a.com/"))
        // Without this instruction the model answers from weights and still cites.
        assertTrue(context.contains("say so"))
    }

    @Test
    fun `produces no context when nothing was found`() {
        assertEquals("", SearchQuery.buildContext("q", emptyList()))
    }

    @Test
    fun `numbers sources from one to match the citations`() {
        val sources = SearchQuery.toSources(SearchQuery.dedupe(sample))
        assertEquals(2, sources.size)
        assertEquals(1, sources[0].index)
    }
}
