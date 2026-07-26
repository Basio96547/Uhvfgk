package com.example.ondevicellm.web

import java.net.URLDecoder

/**
 * HTML scraping and cleanup, kept pure so it can be unit tested.
 *
 * The Instant Answer API only returns definitions and disambiguation pages, so
 * it answers almost nothing a person actually asks. Real results come from
 * DuckDuckGo's HTML endpoint, which means parsing markup. That parsing is the
 * most brittle part of the feature, so it lives here with tests rather than
 * inline in the networking code.
 */
object HtmlExtract {

    /** DuckDuckGo's keyless HTML endpoint. Real results, no API key. */
    fun duckDuckGoHtmlUrl(query: String): String =
        "https://html.duckduckgo.com/html/?q=${SearchQuery.encode(query)}"

    // Result anchors carry one of these classes depending on which frontend
    // answered; both shapes are accepted so a change to one doesn't kill search.
    private val RESULT_ANCHOR = Regex(
        """<a[^>]*class="[^"]*result(?:__a|-link)[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val SNIPPET = Regex(
        """<(?:a|td)[^>]*class="[^"]*result(?:__snippet|-snippet)[^"]*"[^>]*>(.*?)</(?:a|td)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val SCRIPT_OR_STYLE = Regex(
        """<(script|style|noscript|svg)[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val BLOCK_END = Regex(
        """</(p|div|li|h[1-6]|tr|section|article|br)\s*>|<br\s*/?>""",
        RegexOption.IGNORE_CASE,
    )

    private val TAG = Regex("<[^>]*>")
    private val MULTI_SPACE = Regex("[ \\t]+")
    private val MULTI_NEWLINE = Regex("\n{3,}")

    /**
     * Pulls organic results out of a DuckDuckGo HTML response.
     *
     * Titles and snippets are paired by position: the endpoint emits them in
     * matching order, and pairing by index survives markup changes that
     * renaming-based parsing would not.
     */
    fun parseDuckDuckGoResults(html: String, limit: Int = 6): List<SearchResult> {
        val anchors = RESULT_ANCHOR.findAll(html).toList()
        val snippets = SNIPPET.findAll(html).map { toPlainText(it.groupValues[1]) }.toList()

        val results = mutableListOf<SearchResult>()
        for ((index, match) in anchors.withIndex()) {
            if (results.size >= limit) break

            val url = resolveUrl(match.groupValues[1]) ?: continue
            val title = toPlainText(match.groupValues[2])
            if (title.isBlank()) continue

            results += SearchResult(
                title = title,
                snippet = snippets.getOrNull(index).orEmpty(),
                url = url,
                provider = "DuckDuckGo",
            )
        }
        return results
    }

    /**
     * Unwraps DuckDuckGo's click-tracking redirect and normalises the scheme.
     * Returns null for links that aren't usable results (ads, internal pages).
     */
    fun resolveUrl(href: String): String? {
        val raw = href.trim()
        if (raw.isEmpty()) return null

        // Results are wrapped as //duckduckgo.com/l/?uddg=<encoded>&rut=…
        val uddg = Regex("[?&]uddg=([^&]+)").find(raw)?.groupValues?.get(1)
        val target = if (uddg != null) {
            try {
                URLDecoder.decode(uddg, "UTF-8")
            } catch (_: Exception) {
                return null
            }
        } else {
            raw
        }

        val absolute = when {
            target.startsWith("http://") || target.startsWith("https://") -> target
            target.startsWith("//") -> "https:$target"
            else -> return null
        }

        // Ad and telemetry links are not results.
        if (absolute.contains("duckduckgo.com/y.js") ||
            absolute.contains("/ad_domain") ||
            absolute.contains("bing.com/aclick")
        ) {
            return null
        }
        return absolute
    }

    /**
     * Strips markup to readable text for a *fragment* — no block-level spacing,
     * so titles and snippets stay on one line.
     */
    fun toPlainText(fragment: String): String =
        decodeEntities(TAG.replace(fragment, ""))
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Reduces a full page to readable text.
     *
     * Scripts and styles go first — otherwise their contents survive tag
     * stripping and swamp the result. Block ends become newlines so paragraphs
     * don't run together into one unreadable line.
     */
    fun articleText(html: String, maxChars: Int = 4000): String {
        var text = SCRIPT_OR_STYLE.replace(html, " ")
        text = BLOCK_END.replace(text, "\n")
        text = TAG.replace(text, " ")
        text = decodeEntities(text)
        text = MULTI_SPACE.replace(text, " ")
        text = text.lineSequence().map { it.trim() }.joinToString("\n")
        text = MULTI_NEWLINE.replace(text, "\n\n").trim()

        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val boundary = cut.lastIndexOf('\n').takeIf { it > maxChars / 2 }
            ?: cut.lastIndexOf(' ').takeIf { it > maxChars / 2 }
            ?: maxChars
        return cut.take(boundary).trimEnd() + "…"
    }

    /** Decodes the entities that actually show up in search output. */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        var out = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")

        // Numeric references, decimal and hex.
        out = Regex("&#(\\d{1,7});").replace(out) { match ->
            match.groupValues[1].toIntOrNull()
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        out = Regex("&#[xX]([0-9a-fA-F]{1,6});").replace(out) { match ->
            match.groupValues[1].toIntOrNull(16)
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        return out
    }
}
