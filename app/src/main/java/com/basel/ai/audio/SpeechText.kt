package com.basel.ai.audio

/**
 * Turns a reply into something worth hearing.
 *
 * The app was handing raw model output straight to the speech engine, and a
 * model's output is not prose — it is Markdown. Every one of these is read out
 * loud, literally, in a flat voice:
 *
 *  - `**مهم**` → "نجمة نجمة مهم نجمة نجمة"
 *  - `- أول بند` → "شرطة أول بند"
 *  - `[صفحة 3]` → "قوس معقوف صفحة ثلاثة قوس معقوف"
 *  - `https://example.com/a/b?c=1` → every slash, dot and equals sign
 *  - a fenced code block → several minutes of punctuation
 *
 * That is most of what "the Arabic voice is annoying and inaccurate" actually
 * is. It is not the voice mispronouncing Arabic; it is the voice pronouncing
 * things that were never meant to be said.
 *
 * Pure, so every one of those cases is a test rather than something to be
 * heard and reported.
 */
object SpeechText {

    /** A fenced block. Reading code aloud is never what anyone wanted. */
    private val FENCED_CODE = Regex("```[\\s\\S]*?```")

    /** An unterminated fence — a truncated reply — takes the rest with it. */
    private val OPEN_FENCE = Regex("```[\\s\\S]*$")

    private val INLINE_CODE = Regex("`([^`\\n]*)`")

    /** `[text](url)` keeps the text and drops the address. */
    private val MARKDOWN_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")

    private val BARE_URL = Regex("""\b(?:https?://|www\.)\S+""")

    /** `**bold**`, `__bold__`, `*italic*`, `_italic_` — keep the words. */
    private val EMPHASIS = Regex("(\\*{1,3}|_{1,3})(\\S(?:.*?\\S)?)\\1")

    /** A heading marker at the start of a line. */
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s*")

    /** A bullet or a quote marker at the start of a line. */
    private val BULLET = Regex("(?m)^\\s{0,3}(?:[-*+•]|>)\\s+")

    /** `1.` at the start of a line: the number is meaningful, the dot is not. */
    private val ORDERED = Regex("(?m)^\\s{0,3}(\\d{1,2})[.)]\\s+")

    /** A rule made of dashes, underscores or asterisks. */
    private val RULE = Regex("(?m)^\\s{0,3}(?:[-*_]\\s*){3,}$")

    /** A table row. Reading pipes aloud turns a table into noise. */
    private val TABLE_ROW = Regex("(?m)^\\s*\\|.*\\|\\s*$")

    /** `[1]`, `[page 3]`, `[صفحة 3]` — citations are for the eye. */
    private val CITATION = Regex("\\[[^\\]\\n]{0,24}\\]")

    /** Anything left over that is punctuation nobody says. */
    private val LEFTOVER = Regex("[*_#`|~^<>]")

    private val BLANK_LINES = Regex("\\n{3,}")
    private val SPACES = Regex("[ \\t]{2,}")

    /**
     * The reply as it should be spoken.
     *
     * @param codeReplacement said in place of a code block, in the reader's
     *   language. Silence would make the answer sound like it skipped a step.
     */
    fun forSpeech(text: String, codeReplacement: String): String {
        if (text.isBlank()) return ""

        var out = text

        // Code first: everything inside a fence is exempt from the rules below,
        // and removing it early stops a `#` in a comment being read as a
        // heading marker.
        out = FENCED_CODE.replace(out) { "\n$codeReplacement\n" }
        out = OPEN_FENCE.replace(out) { "\n$codeReplacement\n" }
        out = INLINE_CODE.replace(out) { it.groupValues[1] }

        out = MARKDOWN_LINK.replace(out) { it.groupValues[1] }
        out = BARE_URL.replace(out) { "" }

        out = TABLE_ROW.replace(out) { row ->
            // Keep the cells, drop the pipes: a row of figures still says
            // something, a row of pipes says nothing.
            row.value.trim().trim('|').split('|').joinToString("، ") { it.trim() }
        }

        out = RULE.replace(out) { "" }
        out = HEADING.replace(out) { "" }
        out = ORDERED.replace(out) { "${it.groupValues[1]}. " }
        out = BULLET.replace(out) { "" }

        // Emphasis twice: `**bold _and_ italic**` needs two passes, and a
        // third buys nothing.
        out = EMPHASIS.replace(out) { it.groupValues[2] }
        out = EMPHASIS.replace(out) { it.groupValues[2] }

        out = CITATION.replace(out) { "" }
        out = LEFTOVER.replace(out) { "" }

        return out
            .replace(BLANK_LINES, "\n\n")
            .replace(SPACES, " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    /**
     * Whether there is anything left worth saying.
     *
     * A reply that was nothing but a code block becomes one short sentence,
     * and a reply that was nothing but a link becomes empty — speaking
     * silence looks like the button is broken.
     */
    fun hasSomethingToSay(spoken: String): Boolean =
        spoken.any { it.isLetterOrDigit() }
}
