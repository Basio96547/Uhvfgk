package com.example.ondevicellm.web

import java.net.URLEncoder

/** One retrieved passage, with the page it came from. */
data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    /** Which provider returned it, shown in the UI. */
    val provider: String,
)

/** A source shown under a reply so a claim can be traced back. */
data class SearchSource(
    val index: Int,
    val title: String,
    val url: String,
)

/**
 * Query construction and prompt assembly for web-grounded answers.
 *
 * Kept free of Android and networking types so the URL escaping and the exact
 * shape of the injected context are unit tested — those are the parts that
 * quietly break retrieval when they're wrong.
 */
object SearchQuery {

    private const val MAX_SNIPPET_CHARS = 420
    private const val MAX_RESULTS_IN_PROMPT = 5

    fun encode(query: String): String =
        URLEncoder.encode(query.trim(), "UTF-8")

    /** DuckDuckGo's Instant Answer endpoint: no key, no tracking cookies. */
    fun duckDuckGoUrl(query: String): String =
        "https://api.duckduckgo.com/?q=${encode(query)}" +
            "&format=json&no_html=1&no_redirect=1&skip_disambig=1"

    /** Wikipedia full-text search, scoped to the user's language. */
    fun wikipediaSearchUrl(languageCode: String, query: String, limit: Int = 3): String =
        "https://${wikiHost(languageCode)}/w/api.php?action=query&list=search" +
            "&srsearch=${encode(query)}&srlimit=$limit&format=json&origin=*"

    /** REST summary for a single article title. */
    fun wikipediaSummaryUrl(languageCode: String, title: String): String =
        "https://${wikiHost(languageCode)}/api/rest_v1/page/summary/" +
            encode(title).replace("+", "_")

    /**
     * Maps a BCP-47 tag such as `ar-SA` onto a Wikipedia subdomain. Unknown or
     * blank tags fall back to English rather than producing a dead host.
     */
    fun wikiHost(languageTag: String): String {
        val code = languageTag.substringBefore('-').lowercase().trim()
        val valid = code.length in 2..3 && code.all { it in 'a'..'z' }
        return if (valid) "$code.wikipedia.org" else "en.wikipedia.org"
    }

    /** Collapses whitespace and cuts on a word boundary. */
    fun trimSnippet(text: String, maxChars: Int = MAX_SNIPPET_CHARS): String {
        val clean = text.replace(HTML_TAG, "").replace(WHITESPACE, " ").trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    /**
     * Drops duplicates and empty passages, keeping the first occurrence of each
     * URL so the highest-ranked provider wins.
     */
    fun dedupe(results: List<SearchResult>): List<SearchResult> {
        val seen = HashSet<String>()
        return results.filter { result ->
            result.snippet.isNotBlank() &&
                result.url.isNotBlank() &&
                seen.add(result.url.trimEnd('/'))
        }
    }

    /**
     * Builds the context block prepended to the user's question.
     *
     * The instruction is explicit about admitting ignorance: without it a small
     * model will happily answer from its weights and still cite the sources,
     * which is worse than saying the results don't cover it.
     */
    fun buildContext(query: String, results: List<SearchResult>): String {
        val used = results.take(MAX_RESULTS_IN_PROMPT)
        if (used.isEmpty()) return ""

        return buildString {
            appendLine("Web search results for \"${query.trim()}\":")
            appendLine()
            used.forEachIndexed { index, result ->
                appendLine("[${index + 1}] ${result.title}")
                appendLine(trimSnippet(result.snippet))
                appendLine("Source: ${result.url}")
                appendLine()
            }
            appendLine(
                "Answer the question using these results. Cite them inline as " +
                    "[1], [2]. If the results don't contain the answer, say so " +
                    "instead of guessing."
            )
        }
    }

    /** Numbered sources for the citation chips under a reply. */
    fun toSources(results: List<SearchResult>): List<SearchSource> =
        results.take(MAX_RESULTS_IN_PROMPT).mapIndexed { index, result ->
            SearchSource(index + 1, result.title, result.url)
        }

    private val HTML_TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")
}
