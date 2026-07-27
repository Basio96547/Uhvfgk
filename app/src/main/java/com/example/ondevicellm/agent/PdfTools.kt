package com.example.ondevicellm.agent

import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.pdf.DocumentIndex
import com.example.ondevicellm.pdf.PdfDoc
import com.example.ondevicellm.pdf.TextSource

/**
 * Skills for documents the user has added.
 *
 * Separate from the attachment path in chat, and both are needed. Attaching a
 * PDF answers "what does this say about X" in one turn, which is what people
 * actually do. These let the model work: check a second document, find which
 * page a figure is on, come back for more after the first extract was not
 * enough. A model that can only be handed a document cannot go and look
 * something up in it.
 */

/** Pure page-range parsing, so `5-8`, `5–8` and `p. 5` all mean the same thing. */
object PageRange {

    /**
     * Pages named by [spec], 1-based and clamped to [pageCount].
     *
     * Empty when the spec names nothing valid, which the caller reports rather
     * than silently turning into "page 1".
     */
    fun parse(spec: String?, pageCount: Int, defaultCount: Int = 3): List<Int> {
        if (pageCount <= 0) return emptyList()
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return (1..minOf(defaultCount, pageCount)).toList()

        val out = linkedSetOf<Int>()
        // Split on commas so "1, 4-6" works; models produce it unprompted.
        for (part in text.split(',', '،')) {
            // Longest prefix first, and only one: stripping "p" before "page"
            // turns "page 5" into "age 5", which parses as nothing.
            val cleaned = stripPagePrefix(part.trim())
                .replace('–', '-').replace('—', '-').replace('ـ', '-')
                .trim()
            if (cleaned.isEmpty()) continue

            val dash = cleaned.indexOf('-', startIndex = 1)
            if (dash > 0) {
                val from = cleaned.substring(0, dash).trim().toIntOrNull()
                val to = cleaned.substring(dash + 1).trim().toIntOrNull()
                if (from != null && to != null) {
                    for (page in minOf(from, to)..maxOf(from, to)) {
                        if (page in 1..pageCount) out += page
                    }
                    // A range that asks for the whole book is a way to blow the
                    // context window without meaning to.
                    if (out.size >= MAX_PAGES) break
                }
            } else {
                cleaned.toIntOrNull()?.takeIf { it in 1..pageCount }?.let { out += it }
            }
            if (out.size >= MAX_PAGES) break
        }
        return out.take(MAX_PAGES).sorted()
    }

    /** The ways a page number gets labelled, longest first. */
    private val PAGE_PREFIXES = listOf("pages", "page", "pp.", "pp", "p.", "p", "صفحة", "ص")

    private fun stripPagePrefix(text: String): String {
        for (prefix in PAGE_PREFIXES) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                return text.removePrefix(text.take(prefix.length)).trim()
            }
        }
        return text
    }

    /** Pages one call may return. Beyond this the reply is longer than the context. */
    const val MAX_PAGES = 12
}

/** Finds the document a name refers to, tolerantly. */
internal fun List<PdfDoc>.match(name: String?): PdfDoc? {
    if (isEmpty()) return null
    val wanted = name?.trim()?.lowercase().orEmpty()
    if (wanted.isEmpty()) return lastOrNull()
    return firstOrNull { it.id == wanted }
        ?: firstOrNull { it.name.equals(wanted, ignoreCase = true) }
        // Models drop the extension, and users say "the report" for
        // "2025-annual-report-final.pdf".
        ?: firstOrNull { it.name.lowercase().contains(wanted) }
        ?: firstOrNull { wanted.contains(it.name.substringBeforeLast('.').lowercase()) }
}

class ListDocumentsTool(
    private val documents: () -> List<PdfDoc>,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "list_documents",
        summary = strings().toolListDocsSummary,
        example = """<tool_call>{"name": "list_documents", "arguments": {}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val docs = documents()
        if (docs.isEmpty()) return ToolResult.failed(strings().toolNoDocuments)
        return ToolResult.ok(
            docs.joinToString("\n") { doc ->
                buildString {
                    append(doc.name)
                    append(" — ${doc.pageCount} pages")
                    if (doc.ocrPageCount > 0) append(", ${doc.ocrPageCount} read as images")
                    if (doc.unreadablePageCount > 0) {
                        append(", ${doc.unreadablePageCount} unreadable")
                    }
                }
            }
        )
    }
}

class ReadPdfTool(
    private val documents: () -> List<PdfDoc>,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "read_pdf",
        summary = strings().toolReadPdfSummary,
        params = listOf(
            ToolParam("document", strings().toolDocNameParam, required = false),
            ToolParam("pages", strings().toolPagesParam, required = false),
        ),
        example = """<tool_call>{"name": "read_pdf", "arguments": {"document": "report", "pages": "2-4"}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val s = strings()
        val docs = documents()
        if (docs.isEmpty()) return ToolResult.failed(s.toolNoDocuments)

        val name = call.arg("document") ?: call.arg("name") ?: call.arg("file")
        val doc = docs.match(name) ?: return ToolResult.failed(s.toolNoSuchDocument(name.orEmpty()))

        val wanted = PageRange.parse(call.arg("pages") ?: call.arg("page"), doc.pageCount)
        if (wanted.isEmpty()) return ToolResult.failed(s.toolNoSuchDocument(doc.name))

        val body = buildString {
            appendLine("${doc.name} — pages ${wanted.first()}..${wanted.last()} of ${doc.pageCount}")
            for (number in wanted) {
                val page = doc.pages.firstOrNull { it.number == number } ?: continue
                appendLine()
                appendLine("[page $number]")
                appendLine(
                    when {
                        page.text.isNotBlank() -> page.text.take(MAX_PAGE_CHARS)
                        // Saying nothing here is how a model concludes the page
                        // is blank and answers as though it read it.
                        page.source == TextSource.NONE -> "(this page has no readable text)"
                        else -> "(empty)"
                    }
                )
            }
        }
        return ToolResult.ok(body.take(MAX_TOTAL_CHARS))
    }

    private companion object {
        const val MAX_PAGE_CHARS = 3_000
        const val MAX_TOTAL_CHARS = 12_000
    }
}

class SearchPdfTool(
    private val documents: () -> List<PdfDoc>,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "search_pdf",
        summary = strings().toolSearchPdfSummary,
        params = listOf(
            ToolParam("phrase", strings().toolPhraseParam),
            ToolParam("document", strings().toolDocNameParam, required = false),
        ),
        example = """<tool_call>{"name": "search_pdf", "arguments": {"phrase": "..."}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val s = strings()
        val docs = documents()
        if (docs.isEmpty()) return ToolResult.failed(s.toolNoDocuments)

        val phrase = call.arg("phrase") ?: call.arg("query") ?: call.arg("value")
            ?: return ToolResult.failed(s.toolMissingArg("phrase"))

        val name = call.arg("document") ?: call.arg("name")
        val doc = docs.match(name) ?: return ToolResult.failed(s.toolNoSuchDocument(name.orEmpty()))

        // Through the same normaliser the rest of the app matches Arabic with:
        // أ/إ/آ and ة/ه fold, diacritics go. Without it a word written two
        // ordinary ways does not match itself.
        val needle = DocumentIndex.tokens(phrase)
        if (needle.isEmpty()) return ToolResult.failed(s.toolMissingArg("phrase"))

        val hits = doc.pages
            .filter { page ->
                val words = DocumentIndex.tokens(page.text)
                needle.any { it in words }
            }
            .take(MAX_HITS)

        if (hits.isEmpty()) return ToolResult.failed(s.toolNoPageMatch(phrase))

        return ToolResult.ok(
            buildString {
                appendLine("${doc.name}: ${hits.size} matching pages")
                for (page in hits) {
                    appendLine()
                    appendLine("[page ${page.number}] ${excerpt(page.text, needle)}")
                }
                appendLine()
                append("Use read_pdf with these page numbers to see them in full.")
            }
        )
    }

    /** A window around the first matching word, so a hit is judgeable at a glance. */
    private fun excerpt(text: String, needle: Set<String>): String {
        val words = text.split(Regex("\\s+"))
        val at = words.indexOfFirst { word ->
            DocumentIndex.tokens(word).any { it in needle }
        }
        if (at < 0) return text.take(EXCERPT_CHARS)
        val from = (at - 12).coerceAtLeast(0)
        val to = (at + 20).coerceAtMost(words.size)
        return words.subList(from, to).joinToString(" ").take(EXCERPT_CHARS)
    }

    private companion object {
        const val MAX_HITS = 8
        const val EXCERPT_CHARS = 240
    }
}
