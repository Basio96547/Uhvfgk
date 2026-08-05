package com.basel.ai.chat

/** A run of text with the emphasis that applies to it. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

/** One block of a reply. */
sealed interface Block {
    data class Paragraph(val spans: List<Span>) : Block
    data class Bullet(val spans: List<Span>) : Block
    data class Numbered(val number: Int, val spans: List<Span>) : Block
    data class Heading(val level: Int, val spans: List<Span>) : Block

    /** A fenced block. [language] may be empty. */
    data class Code(val language: String, val code: String) : Block
    data object Rule : Block
}

/**
 * Turns a model's reply into something to lay out.
 *
 * The chat was showing raw Markdown. A model writes `**مهم**` and `- بند` and
 * fenced code because that is what it was trained to write, and the bubble
 * printed the asterisks and the dashes. Long replies looked broken, and the
 * one thing emphasis exists for — telling the reader which part matters — was
 * doing the opposite.
 *
 * Deliberately small. This renders what a chat reply actually contains and
 * nothing else: emphasis, code, lists, headings, rules. No tables, no images,
 * no reference links, no HTML — every one of those would be more parser than
 * the thing is worth, and a half-working table is worse than a visible pipe.
 *
 * Two rules throughout:
 *
 *  - **An unclosed marker is literal text.** A reply cut off mid-sentence
 *    leaves a dangling `**`, and swallowing the rest of the message into bold
 *    because of it would turn a truncated answer into an unreadable one.
 *  - **Nothing is dropped.** Every character of the input appears in the
 *    output, styled or plain. A parser that silently eats text it did not
 *    understand is worse than one that shows the marker.
 *
 * Pure, so all of that is tested against real model output rather than
 * discovered on a phone.
 */
object Markdown {

    private val FENCE = Regex("^\\s{0,3}```+\\s*([A-Za-z0-9+#._-]*)\\s*$")
    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s{0,3}[-*+•]\\s+(.*)$")
    private val NUMBERED = Regex("^\\s{0,3}(\\d{1,3})[.)]\\s+(.*)$")
    private val RULE = Regex("^\\s{0,3}(?:[-*_]\\s*){3,}$")

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val lines = text.lines()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            // Joined with a space, not a newline: a model hard-wraps prose and
            // honouring those breaks makes the bubble ragged for no reason.
            blocks += Block.Paragraph(spans(paragraph.joinToString(" ").trim()))
            paragraph.clear()
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val fence = FENCE.find(line)

            if (fence != null) {
                flushParagraph()
                val language = fence.groupValues[1]
                val body = mutableListOf<String>()
                index++
                // An unterminated fence takes the rest: a truncated reply ends
                // inside its code block, and that is still a code block.
                while (index < lines.size && FENCE.find(lines[index]) == null) {
                    body += lines[index]
                    index++
                }
                if (index < lines.size) index++  // the closing fence
                blocks += Block.Code(language, body.joinToString("\n").trimEnd())
                continue
            }

            when {
                line.isBlank() -> flushParagraph()

                RULE.matches(line) -> {
                    flushParagraph()
                    blocks += Block.Rule
                }

                HEADING.find(line) != null -> {
                    flushParagraph()
                    val match = HEADING.find(line)!!
                    blocks += Block.Heading(
                        level = match.groupValues[1].length,
                        spans = spans(match.groupValues[2].trim()),
                    )
                }

                BULLET.find(line) != null -> {
                    flushParagraph()
                    blocks += Block.Bullet(spans(BULLET.find(line)!!.groupValues[1].trim()))
                }

                NUMBERED.find(line) != null -> {
                    flushParagraph()
                    val match = NUMBERED.find(line)!!
                    blocks += Block.Numbered(
                        number = match.groupValues[1].toIntOrNull() ?: 1,
                        spans = spans(match.groupValues[2].trim()),
                    )
                }

                else -> paragraph += line
            }
            index++
        }
        flushParagraph()
        return blocks
    }

    /**
     * Splits a line into styled runs.
     *
     * Scanned rather than matched with a regex, because the failure that
     * matters is the *unclosed* marker: a regex either misses it or, worse,
     * pairs it with one three sentences away. Here a marker with no partner is
     * emitted as the character it is.
     */
    fun spans(line: String): List<Span> {
        if (line.isEmpty()) return emptyList()
        val out = mutableListOf<Span>()
        val plain = StringBuilder()
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out += Span(plain.toString())
                plain.setLength(0)
            }
        }

        while (i < line.length) {
            val rest = line.length - i

            // Inline code wins over emphasis: `**` inside backticks is code.
            if (line[i] == '`') {
                val close = line.indexOf('`', i + 1)
                if (close > i + 1) {
                    flushPlain()
                    out += Span(line.substring(i + 1, close), code = true)
                    i = close + 1
                    continue
                }
            }

            if (rest >= 2 && (line.startsWith("**", i) || line.startsWith("__", i))) {
                val marker = line.substring(i, i + 2)
                val close = line.indexOf(marker, i + 2)
                if (close > i + 2) {
                    flushPlain()
                    out += Span(line.substring(i + 2, close), bold = true)
                    i = close + 2
                    continue
                }
            }

            if (line[i] == '*' || line[i] == '_') {
                val marker = line[i]
                val close = line.indexOf(marker, i + 1)
                // A lone underscore inside a word (snake_case, a file name) is
                // not emphasis, and treating it as such mangles identifiers.
                val insideWord = marker == '_' &&
                    i > 0 && line[i - 1].isLetterOrDigit()
                if (!insideWord && close > i + 1) {
                    flushPlain()
                    out += Span(line.substring(i + 1, close), italic = true)
                    i = close + 1
                    continue
                }
            }

            // `[text](url)` keeps the text: an address in a chat bubble is
            // untappable noise, and the model rarely means it as the answer.
            if (line[i] == '[') {
                val closeText = line.indexOf(']', i + 1)
                if (closeText > i && closeText + 1 < line.length && line[closeText + 1] == '(') {
                    val closeUrl = line.indexOf(')', closeText + 2)
                    if (closeUrl > closeText) {
                        flushPlain()
                        out += spans(line.substring(i + 1, closeText))
                        i = closeUrl + 1
                        continue
                    }
                }
            }

            plain.append(line[i])
            i++
        }
        flushPlain()
        return out
    }

    /** The plain text of a block, for measuring and for accessibility. */
    fun plainText(spans: List<Span>): String = spans.joinToString("") { it.text }
}
