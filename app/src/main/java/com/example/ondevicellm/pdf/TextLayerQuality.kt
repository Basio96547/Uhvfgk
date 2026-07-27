package com.example.ondevicellm.pdf

/**
 * Decides whether a page's text layer is worth having, or whether the page has
 * to be rendered and read as an image.
 *
 * This is the hinge of the whole feature. Two failure modes it exists for:
 *
 *  - **A scan.** The page is a photograph of paper. The text layer is empty or
 *    nearly so, and without OCR there is nothing to answer from.
 *  - **A broken text layer**, which is worse, because it is invisible. A PDF
 *    with subsetted fonts and no `ToUnicode` map still yields *characters* —
 *    they are just the wrong ones, often control codes or a run of unrelated
 *    Latin. Extraction "succeeds", the model is handed gibberish, and it
 *    answers from it confidently. Catching that here is the difference between
 *    a wrong answer and an honest "this page is an image, let me read it".
 *
 * Pure, so both cases are tested against real shapes of bad extraction rather
 * than discovered on someone's scanned book.
 */
object TextLayerQuality {

    /** Below this, a page is empty for practical purposes. */
    const val MIN_CHARS = 24

    /** Share of characters that must be letters, digits or ordinary punctuation. */
    const val MIN_MEANINGFUL_RATIO = 0.65f

    /** A page needs at least this many word-like runs to be prose rather than debris. */
    const val MIN_WORDS = 4

    /**
     * True when the extracted text can be used as-is.
     *
     * Deliberately conservative in one direction only: a page wrongly sent to
     * OCR costs time, a page wrongly trusted costs the answer.
     */
    fun isUsable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < MIN_CHARS) return false
        if (meaningfulRatio(trimmed) < MIN_MEANINGFUL_RATIO) return false
        if (wordCount(trimmed) < MIN_WORDS) return false
        return true
    }

    /**
     * Share of characters that carry meaning.
     *
     * Letters, digits, spaces and the punctuation that appears in real prose.
     * Control codes, replacement characters and private-use glyphs — the
     * signature of a missing `ToUnicode` map — count against it.
     */
    fun meaningfulRatio(text: String): Float {
        if (text.isEmpty()) return 0f
        var good = 0
        for (ch in text) {
            if (isMeaningful(ch)) good++
        }
        return good.toFloat() / text.length
    }

    private fun isMeaningful(ch: Char): Boolean = when {
        ch.isLetterOrDigit() -> true
        ch.isWhitespace() -> true
        ch == '�' -> false                    // replacement char
        ch.code in 0xE000..0xF8FF -> false         // private use area
        ch.code < 0x20 -> false                    // control codes
        ch in ORDINARY_PUNCTUATION -> true
        else -> false
    }

    private const val ORDINARY_PUNCTUATION = ".,;:!?'\"()[]{}-–—/\\%$#@&*+=<>|~^_`" +
        "،؛؟…«»‹›“”‘’"

    /** Runs of two or more letters. A page of single stray glyphs has none. */
    fun wordCount(text: String): Int {
        var words = 0
        var run = 0
        for (ch in text) {
            if (ch.isLetter()) {
                run++
                if (run == 2) words++
            } else {
                run = 0
            }
        }
        return words
    }

    /**
     * Whether the document as a whole looks scanned.
     *
     * Used to explain the wait before it starts, rather than after: OCR over
     * forty pages takes minutes, and a progress bar that appears without a
     * reason reads as the app having hung.
     */
    fun looksScanned(pageTexts: List<String>): Boolean {
        if (pageTexts.isEmpty()) return false
        val unusable = pageTexts.count { !isUsable(it) }
        return unusable * 2 > pageTexts.size
    }
}
