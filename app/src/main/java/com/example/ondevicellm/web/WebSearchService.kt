package com.example.ondevicellm.web

import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches web context for a question.
 *
 * Two providers, both keyless and both returning structured JSON rather than
 * scraped HTML: DuckDuckGo's Instant Answer API for definitions and direct
 * answers, and Wikipedia for encyclopaedic depth in the user's own language.
 * They fail independently — one being unreachable still yields useful context.
 *
 * Uses `HttpURLConnection` and `org.json` from the platform, so grounding adds
 * no dependency and nothing that could clash with the inference runtimes.
 */
class WebSearchService {

    data class Outcome(
        val results: List<SearchResult>,
        /** Non-null when nothing usable came back, phrased for the user. */
        val problem: String? = null,
    )

    /**
     * Runs both providers concurrently and merges what comes back.
     *
     * @param languageTag BCP-47 tag; only its language part is used, to pick a
     *   Wikipedia edition.
     */
    suspend fun search(query: String, languageTag: String): Outcome = coroutineScope {
        if (query.isBlank()) return@coroutineScope Outcome(emptyList(), "Nothing to search for.")

        val duck = async(Dispatchers.IO) {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                try {
                    duckDuckGo(query)
                } catch (e: Exception) {
                    ErrorLog.report("Web search", "DuckDuckGo failed", e, Severity.WARNING)
                    null
                }
            }
        }
        val wiki = async(Dispatchers.IO) {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                try {
                    wikipedia(query, languageTag)
                } catch (e: Exception) {
                    ErrorLog.report("Web search", "Wikipedia failed", e, Severity.WARNING)
                    null
                }
            }
        }

        val duckResults = duck.await()
        val wikiResults = wiki.await()

        if (duckResults == null && wikiResults == null) {
            return@coroutineScope Outcome(
                emptyList(),
                "Couldn't reach the web. Check your connection, or turn search off " +
                    "to answer from the model alone.",
            )
        }

        val merged = SearchQuery.dedupe(duckResults.orEmpty() + wikiResults.orEmpty())
        if (merged.isEmpty()) {
            Outcome(emptyList(), "No useful web results for that question.")
        } else {
            Outcome(merged)
        }
    }

    // ------------------------------------------------------------- providers

    private fun duckDuckGo(query: String): List<SearchResult> {
        val json = JSONObject(fetch(SearchQuery.duckDuckGoUrl(query)))
        val results = mutableListOf<SearchResult>()

        // The headline answer, when DuckDuckGo has one.
        val abstract = json.optString("AbstractText")
        if (abstract.isNotBlank()) {
            results += SearchResult(
                title = json.optString("Heading").ifBlank { query },
                snippet = abstract,
                url = json.optString("AbstractURL"),
                provider = "DuckDuckGo",
            )
        }

        // RelatedTopics mixes leaf results with nested category groups.
        val topics = json.optJSONArray("RelatedTopics") ?: return results
        for (i in 0 until topics.length()) {
            if (results.size >= MAX_PER_PROVIDER) break
            val entry = topics.optJSONObject(i) ?: continue
            val text = entry.optString("Text")
            val url = entry.optString("FirstURL")
            if (text.isBlank() || url.isBlank()) continue
            results += SearchResult(
                title = text.substringBefore(" - ").take(90),
                snippet = text,
                url = url,
                provider = "DuckDuckGo",
            )
        }
        return results
    }

    private fun wikipedia(query: String, languageTag: String): List<SearchResult> {
        val host = SearchQuery.wikiHost(languageTag)
        val search = JSONObject(fetch(SearchQuery.wikipediaSearchUrl(languageTag, query)))
        val hits = search.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()

        val results = mutableListOf<SearchResult>()
        for (i in 0 until minOf(hits.length(), MAX_PER_PROVIDER)) {
            val hit = hits.optJSONObject(i) ?: continue
            val title = hit.optString("title")
            if (title.isBlank()) continue

            // The REST summary gives a clean intro paragraph; the search
            // snippet is HTML-marked and truncated mid-sentence.
            val summary = try {
                JSONObject(fetch(SearchQuery.wikipediaSummaryUrl(languageTag, title)))
            } catch (e: Exception) {
                // Fall back to the search snippet for this one article.
                ErrorLog.report(
                    "Web search", "No summary for \"$title\"", e, Severity.INFO,
                )
                null
            }

            val extract = summary?.optString("extract").orEmpty()
                .ifBlank { hit.optString("snippet") }
            if (extract.isBlank()) continue

            val pageUrl = summary
                ?.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                .orEmpty()
                .ifBlank { "https://$host/wiki/${SearchQuery.encode(title).replace("+", "_")}" }

            results += SearchResult(
                title = title,
                snippet = extract,
                url = pageUrl,
                provider = "Wikipedia",
            )
        }
        return results
    }

    // ------------------------------------------------------------------ http

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            // Wikipedia's API rejects requests without an identifying agent.
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_PER_PROVIDER = 4
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 8_000
        const val REQUEST_TIMEOUT_MS = 12_000L
        const val USER_AGENT = "OnDeviceLLM/1.0 (Android; offline-first LLM chat)"
    }
}

/** Convenience wrapper used by the ViewModel. */
suspend fun WebSearchService.contextFor(
    query: String,
    languageTag: String,
): Pair<String, List<SearchSource>> = withContext(Dispatchers.IO) {
    val outcome = search(query, languageTag)
    SearchQuery.buildContext(query, outcome.results) to SearchQuery.toSources(outcome.results)
}
