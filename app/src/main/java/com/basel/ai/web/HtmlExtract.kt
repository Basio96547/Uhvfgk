package com.basel.ai.web

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
    /**
     * @param region DuckDuckGo `kl` region code, e.g. `xa-ar` for Arabic. An
     *   Arabic query sent without it comes back ranked for an English-speaking
     *   audience, which is the wrong half of the web for the question asked.
     */
    /** The endpoint on its own, for a form POST that carries the query in its body. */
    const val DUCKDUCKGO_HTML_ENDPOINT = "https://html.duckduckgo.com/html/"

    fun duckDuckGoHtmlUrl(query: String, region: String = ""): String = buildString {
        append(DUCKDUCKGO_HTML_ENDPOINT).append("?q=")
        append(SearchQuery.encode(query))
        if (region.isNotBlank()) append("&kl=").append(region)
    }

    /**
     * The lite frontend, as a second try.
     *
     * Plainer markup, far less machinery in front of it, and it answers when
     * the main HTML endpoint decides a request looks automated. Same parser:
     * the class names differ but both are now handled.
     */
    const val DUCKDUCKGO_LITE_ENDPOINT = "https://lite.duckduckgo.com/lite/"

    fun duckDuckGoLiteUrl(query: String, region: String = ""): String = buildString {
        append(DUCKDUCKGO_LITE_ENDPOINT).append("?q=")
        append(SearchQuery.encode(query))
        if (region.isNotBlank()) append("&kl=").append(region)
    }

    /** Form body for a POST to either endpoint. */
    fun duckDuckGoFormBody(query: String, region: String = ""): String = buildString {
        append("q=").append(SearchQuery.encode(query))
        if (region.isNotBlank()) append("&kl=").append(region)
    }

    /**
     * Any anchor, and any cell that could be a snippet.
     *
     * Attributes are pulled out afterwards rather than matched inline. The
     * previous pattern required `class` to come *before* `href` and both to be
     * double-quoted — and DuckDuckGo's lite frontend writes
     * `<a rel="nofollow" href="…" class='result-link'>`, which satisfies
     * neither. It matched nothing there, which is one way search returns no
     * results on a page full of them.
     */
    private val ANCHOR = Regex(
        """<a\b([^>]*)>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val SNIPPET_TAG = Regex(
        """<(a|td)\b([^>]*)>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Class names the organic result link has carried across frontends. */
    private val RESULT_LINK_CLASSES = listOf("result__a", "result-link")

    private val SNIPPET_CLASSES = listOf("result__snippet", "result-snippet")

    /**
     * One attribute's value, whatever it is quoted with — or not quoted at all.
     */
    internal fun attribute(tag: String, name: String): String? {
        val pattern = Regex(
            """\b$name\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""",
            RegexOption.IGNORE_CASE,
        )
        val match = pattern.find(tag) ?: return null
        return (match.groupValues[2].takeIf { it.isNotEmpty() }
            ?: match.groupValues[3].takeIf { it.isNotEmpty() }
            ?: match.groupValues[4]).takeIf { it.isNotEmpty() }
    }

    /** True when the tag's class list contains any of [names]. */
    internal fun hasClass(tag: String, names: List<String>): Boolean {
        val classes = attribute(tag, "class") ?: return false
        return names.any { classes.contains(it, ignoreCase = true) }
    }

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
     * A title takes the snippet that sits between it and the next title in the
     * document. Pairing the two match lists by index instead looks simpler and
     * is wrong: anchors are dropped here — ads, tracking links, blank titles —
     * and the snippet list does not lose the matching entry, so one sponsored
     * link at the top shifted every snippet onto the wrong result. Positions
     * cannot drift that way.
     */
    fun parseDuckDuckGoResults(html: String, limit: Int = 6): List<SearchResult> {
        val anchors = ANCHOR.findAll(html)
            .filter { hasClass(it.groupValues[1], RESULT_LINK_CLASSES) }
            .toList()

        val snippets = SNIPPET_TAG.findAll(html)
            .filter { hasClass(it.groupValues[2], SNIPPET_CLASSES) }
            .toList()

        val results = mutableListOf<SearchResult>()
        for ((index, match) in anchors.withIndex()) {
            if (results.size >= limit) break

            val href = attribute(match.groupValues[1], "href") ?: continue
            val url = resolveUrl(href) ?: continue
            val title = toPlainText(match.groupValues[2])
            if (title.isBlank()) continue

            // Everything up to the next result anchor belongs to this one.
            val blockEnd = anchors.getOrNull(index + 1)?.range?.first ?: html.length
            val snippet = snippets
                .firstOrNull { it.range.first in (match.range.last + 1) until blockEnd }
                ?.let { toPlainText(it.groupValues[3]) }
                .orEmpty()

            results += SearchResult(
                title = title,
                snippet = snippet,
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
