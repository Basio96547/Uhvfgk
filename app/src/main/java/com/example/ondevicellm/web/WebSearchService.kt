package com.example.ondevicellm.web

import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Localization
import com.example.ondevicellm.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** How much work a search should do before answering. */
enum class SearchDepth {
    /** Snippets only. One request, fastest. */
    QUICK,

    /** Also opens the top pages and reads them. Slower, much better answers. */
    DEEP,
}

/**
 * Web search and retrieval.
 *
 * Three sources, deliberately layered:
 *  - **DuckDuckGo HTML** — real organic results. This is the actual search;
 *    the Instant Answer API alone returns nothing for most real questions.
 *  - **DuckDuckGo Instant Answer** — the direct answer box when one exists.
 *  - **Wikipedia** — encyclopaedic depth in the user's own language.
 *
 * In [SearchDepth.DEEP] the top results are then fetched and reduced to
 * readable text, so the model reasons over page content rather than a
 * two-line snippet.
 *
 * Everything uses `HttpURLConnection` and `org.json` from the platform, so
 * grounding adds no dependency that could clash with the inference runtimes.
 */
class WebSearchService {

    data class Outcome(
        val results: List<SearchResult>,
        /** Non-null when nothing usable came back, phrased for the user. */
        val problem: String? = null,
    )

    /**
     * Runs the providers concurrently, merges, and optionally reads the top
     * pages.
     *
     * @param languageTag BCP-47 tag; only its language part is used, to pick a
     *   Wikipedia edition.
     */
    suspend fun search(
        query: String,
        languageTag: String,
        depth: SearchDepth = SearchDepth.QUICK,
    ): Outcome = coroutineScope {
        if (query.isBlank()) {
            return@coroutineScope Outcome(emptyList(), Localization.strings.searchNothingToDo)
        }

        // The script the question is written in wins over the app's language:
        // an Arabic question deserves ar.wikipedia.org and Arabic-ranked
        // results even when the interface is in English.
        val effectiveTag = SearchQuery.searchLanguageTag(query, languageTag)
        val region = SearchQuery.regionFor(effectiveTag)

        val organic = async(Dispatchers.IO) { attempt("Web results") { webResults(query, region) } }
        val instant = async(Dispatchers.IO) { attempt("Instant answer") { duckDuckGo(query) } }
        val wiki = async(Dispatchers.IO) { attempt("Wikipedia") { wikipedia(query, effectiveTag) } }

        val organicResults = organic.await()
        val instantResults = instant.await()
        val wikiResults = wiki.await()

        if (organicResults == null && instantResults == null && wikiResults == null) {
            return@coroutineScope Outcome(
                emptyList(),
                Localization.strings.searchUnreachable,
            )
        }

        // Instant answers and Wikipedia lead: when they have something it is
        // usually the direct answer. Organic results follow for coverage.
        val merged = SearchQuery.dedupe(
            instantResults.orEmpty() + wikiResults.orEmpty() + organicResults.orEmpty()
        )

        if (merged.isEmpty()) {
            return@coroutineScope Outcome(emptyList(), Localization.strings.searchNoResults)
        }

        val enriched = if (depth == SearchDepth.DEEP) readPages(merged) else merged
        Outcome(enriched)
    }

    /** Runs a provider, recording failure instead of hiding it. */
    private suspend fun attempt(
        name: String,
        block: () -> List<SearchResult>,
    ): List<SearchResult>? = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
        try {
            block()
        } catch (e: Exception) {
            ErrorLog.report("Web search", "$name failed", e, Severity.WARNING)
            null
        }
    }

    // ------------------------------------------------------------- providers

    /** Organic results — the part that makes this an actual search engine. */
    private fun webResults(query: String, region: String): List<SearchResult> =
        HtmlExtract.parseDuckDuckGoResults(
            fetch(HtmlExtract.duckDuckGoHtmlUrl(query, region), accept = ACCEPT_HTML),
            limit = MAX_ORGANIC,
        )

    private fun duckDuckGo(query: String): List<SearchResult> {
        val json = JSONObject(fetch(SearchQuery.duckDuckGoUrl(query)))
        val abstract = json.optString("AbstractText")
        if (abstract.isBlank()) return emptyList()

        return listOf(
            SearchResult(
                title = json.optString("Heading").ifBlank { query },
                snippet = abstract,
                url = json.optString("AbstractURL"),
                provider = "DuckDuckGo",
            )
        )
    }

    private fun wikipedia(query: String, languageTag: String): List<SearchResult> {
        val host = SearchQuery.wikiHost(languageTag)
        val search = JSONObject(fetch(SearchQuery.wikipediaSearchUrl(languageTag, query)))
        val hits = search.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()

        val results = mutableListOf<SearchResult>()
        for (i in 0 until minOf(hits.length(), MAX_WIKI)) {
            val hit = hits.optJSONObject(i) ?: continue
            val title = hit.optString("title")
            if (title.isBlank()) continue

            // The REST summary gives a clean intro paragraph; the search
            // snippet is HTML-marked and truncated mid-sentence.
            val summary = try {
                JSONObject(fetch(SearchQuery.wikipediaSummaryUrl(languageTag, title)))
            } catch (e: Exception) {
                ErrorLog.report("Web search", "No summary for \"$title\"", e, Severity.INFO)
                null
            }

            val extract = summary?.optString("extract").orEmpty()
                .ifBlank { HtmlExtract.toPlainText(hit.optString("snippet")) }
            if (extract.isBlank()) continue

            val pageUrl = summary
                ?.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                .orEmpty()
                .ifBlank { "https://$host/wiki/${SearchQuery.encode(title).replace("+", "_")}" }

            results += SearchResult(title, extract, pageUrl, "Wikipedia")
        }
        return results
    }

    /**
     * Opens the top results and replaces their snippet with real page text.
     *
     * A snippet is two lines of marketing; the page is the answer. Fetches run
     * concurrently, and a page that fails keeps its snippet rather than
     * dropping the result.
     */
    private suspend fun readPages(results: List<SearchResult>): List<SearchResult> =
        coroutineScope {
            results.mapIndexed { index, result ->
                async(Dispatchers.IO) {
                    if (index >= MAX_PAGES_TO_READ) return@async result
                    val body = withTimeoutOrNull(PAGE_TIMEOUT_MS) {
                        try {
                            HtmlExtract.articleText(fetch(result.url, accept = ACCEPT_HTML))
                        } catch (e: Exception) {
                            ErrorLog.report(
                                "Web search",
                                "Couldn't read ${result.url}",
                                e,
                                Severity.INFO,
                            )
                            null
                        }
                    }
                    // Only replace when the page gave us more than the snippet.
                    if (body != null && body.length > result.snippet.length) {
                        result.copy(snippet = body)
                    } else {
                        result
                    }
                }
            }.awaitAll()
        }

    // ------------------------------------------------------------------ http

    private fun fetch(url: String, accept: String = ACCEPT_JSON): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            // Wikipedia rejects requests without an identifying agent, and the
            // HTML endpoint serves a stripped page to unknown clients.
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "*")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            // Cap the read: some pages are enormous and only the first part is
            // ever used.
            return connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_RESPONSE_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_ORGANIC = 6
        const val MAX_WIKI = 2
        const val MAX_PAGES_TO_READ = 3
        const val MAX_RESPONSE_CHARS = 400_000

        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 8_000
        const val REQUEST_TIMEOUT_MS = 12_000L
        const val PAGE_TIMEOUT_MS = 9_000L

        const val ACCEPT_JSON = "application/json"
        const val ACCEPT_HTML = "text/html,application/xhtml+xml"

        // A recognisable desktop agent: the HTML endpoint serves a reduced page
        // to clients it doesn't know, which the parser then finds nothing in.
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
