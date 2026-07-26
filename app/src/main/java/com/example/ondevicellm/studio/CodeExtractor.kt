package com.example.ondevicellm.studio

/**
 * Pulls the code out of what a model writes back.
 *
 * A small model does not reliably answer with a bare file. It prefixes
 * "Here is the page:", wraps the code in a fence, sometimes labels the fence
 * and sometimes doesn't, occasionally opens a fence and gets truncated before
 * closing it, and now and then just emits raw HTML with no fence at all. All
 * five have to end up as something the preview can render, because the
 * alternative is a blank screen and no explanation.
 *
 * Pure Kotlin so every one of those shapes is a test rather than a surprise.
 */
object CodeExtractor {

    private val FENCE = Regex("^\\s*`{3,}\\s*([A-Za-z0-9+#-]*)\\s*$")

    /**
     * Returns the code from [raw], or "" when there is nothing usable.
     *
     * Prefers a fenced block; falls back to the whole text when it already
     * looks like a document.
     */
    fun extract(raw: String): String {
        if (raw.isBlank()) return ""

        val lines = raw.lines()
        val open = lines.indexOfFirst { FENCE.matches(it) }

        if (open >= 0) {
            val rest = lines.drop(open + 1)
            val close = rest.indexOfFirst { FENCE.matches(it) }
            // An unterminated fence means the reply was cut off. Keep what
            // arrived: a half-written page still shows the user where it got to.
            val body = if (close >= 0) rest.take(close) else rest
            val code = body.joinToString("\n").trim()
            if (code.isNotEmpty()) return code
        }

        val trimmed = raw.trim()
        return if (looksLikeDocument(trimmed)) trimmed else ""
    }

    /** True when [text] is plausibly a web document rather than prose about one. */
    fun looksLikeDocument(text: String): Boolean {
        val head = text.trimStart().take(200).lowercase()
        return head.startsWith("<!doctype") ||
            head.startsWith("<html") ||
            (head.contains("<html") && text.contains("</html>", ignoreCase = true)) ||
            (head.startsWith("<") && text.contains("</", ignoreCase = true))
    }

    /**
     * Wraps a fragment so the preview always has a real document.
     *
     * Models frequently return only a `<div>` and its `<style>` when asked for
     * a change, and a WebView will render that as unstyled text at the top-left
     * of a white page — which looks like the model failed when it didn't.
     */
    fun asDocument(code: String): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ""
        val head = trimmed.take(200).lowercase()
        if (head.startsWith("<!doctype") || head.startsWith("<html")) return trimmed

        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine(
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            )
            appendLine("</head>")
            appendLine("<body>")
            appendLine(trimmed)
            appendLine("</body>")
            append("</html>")
        }
    }

    /**
     * A one-line title for a project, taken from the document itself.
     *
     * `<title>` first, then the first heading. Returns "" when neither is
     * there rather than inventing something.
     */
    fun titleOf(code: String): String {
        Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(code)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(code)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim()
            .orEmpty()
    }
}
