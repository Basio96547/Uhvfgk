package com.example.ondevicellm.pdf

import com.example.ondevicellm.llm.QueryRouter
import com.example.ondevicellm.web.SearchQuery

/**
 * Fits a document into a context window.
 *
 * This is the part that decides whether reading a PDF works at all. A forty
 * page report is two hundred thousand characters; a 4B model has room for a
 * few thousand. Handing it the first N characters would answer questions about
 * the title page and nothing else, so the document is cut into chunks and the
 * chunks relevant to the question are chosen.
 *
 * Two decisions worth stating:
 *
 *  - **Chunks are page-aligned.** A chunk knows which pages it spans, so the
 *    model can cite "page 12" and the user can go and check. A citation nobody
 *    can verify is decoration.
 *  - **A question with no content words means "summarise".** "لخص لي هذا
 *    الملف" and "what is this about" share no keywords with anything, and
 *    scoring them returns nothing. Falling back to the opening pages is the
 *    right answer for exactly those questions, so it is the fallback rather
 *    than an error.
 *
 * Arabic matching goes through [QueryRouter.normalize], which already folds
 * أ/إ/آ, ة/ه, ى/ي and strips diacritics. Without that, keyword matching on
 * Arabic barely works: the same word written two ordinary ways does not match
 * itself.
 */
object DocumentIndex {

    /** Target size of one chunk. Small enough that several fit, big enough to hold an idea. */
    const val CHUNK_CHARS = 1_200

    /** How much of the document may go into one prompt, by default. */
    const val DEFAULT_BUDGET_CHARS = 6_000

    // ------------------------------------------------------------- chunking

    /**
     * Cuts [pages] into chunks of roughly [targetChars].
     *
     * Pages are kept together while they fit, so a short page is not a chunk
     * of its own, and a long page is split at a paragraph break rather than
     * mid-sentence.
     */
    fun chunk(pages: List<PdfPageText>, targetChars: Int = CHUNK_CHARS): List<DocChunk> {
        val out = mutableListOf<DocChunk>()
        val buffer = StringBuilder()
        var first = -1
        var last = -1

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty() && first > 0) out += DocChunk(first, last, text)
            buffer.setLength(0)
            first = -1
            last = -1
        }

        for (page in pages) {
            val text = page.text.trim()
            if (text.isEmpty()) continue

            for (piece in splitLong(text, targetChars)) {
                if (buffer.isNotEmpty() && buffer.length + piece.length > targetChars) flush()
                if (first < 0) first = page.number
                last = page.number
                if (buffer.isNotEmpty()) buffer.append("\n")
                buffer.append(piece)
                if (buffer.length >= targetChars) flush()
            }
        }
        flush()
        return out
    }

    /** Splits one long page at paragraph, then sentence, then hard boundaries. */
    internal fun splitLong(text: String, targetChars: Int): List<String> {
        if (text.length <= targetChars) return listOf(text)

        val out = mutableListOf<String>()
        var rest = text
        while (rest.length > targetChars) {
            val window = rest.substring(0, targetChars)
            val cut = window.lastIndexOf("\n\n")
                .takeIf { it > targetChars / 2 }
                ?: window.lastIndexOfAny(SENTENCE_ENDS).takeIf { it > targetChars / 2 }
                ?: window.lastIndexOf(' ').takeIf { it > targetChars / 2 }
                // No boundary anywhere in the window — a table, or a language
                // without spaces. Cut it rather than emit one huge chunk.
                ?: targetChars
            val end = (cut + 1).coerceAtMost(rest.length)
            out += rest.substring(0, end).trim()
            rest = rest.substring(end)
        }
        if (rest.isNotBlank()) out += rest.trim()
        return out.filter { it.isNotEmpty() }
    }

    private val SENTENCE_ENDS = charArrayOf('.', '!', '?', '؟', '۔', '\n')

    // ------------------------------------------------------------ selection

    /**
     * Content words of [text], normalised and with stopwords removed.
     *
     * Single characters go too: an Arabic preposition or an English "a" match
     * everywhere and rank nothing.
     */
    fun tokens(text: String): Set<String> =
        QueryRouter.normalize(text)
            .split(' ')
            .filter { it.length > 1 && it !in STOPWORDS }
            .toSet()

    /**
     * How well a chunk answers a question.
     *
     * Distinct coverage, not raw hits: a chunk that says one query word forty
     * times is a table of contents, and one that says four of them once each
     * is the passage wanted.
     */
    fun score(chunk: DocChunk, questionTokens: Set<String>): Float {
        if (questionTokens.isEmpty()) return 0f
        val chunkTokens = tokens(chunk.text)
        var covered = 0
        for (token in questionTokens) {
            if (token in chunkTokens) covered++
        }
        if (covered == 0) return 0f
        return covered.toFloat() / questionTokens.size
    }

    /**
     * The chunks to put in front of the model, in reading order.
     *
     * Chosen by score, then re-sorted by page: a model handed page 9 before
     * page 2 narrates them in that order, and the answer reads as though the
     * document were shuffled.
     */
    fun select(
        chunks: List<DocChunk>,
        question: String,
        budgetChars: Int = DEFAULT_BUDGET_CHARS,
    ): List<DocChunk> {
        if (chunks.isEmpty()) return emptyList()

        val questionTokens = tokens(question)
        val scored = chunks
            .map { it to score(it, questionTokens) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }

        // No content words, or nothing matched: the question is about the
        // document as a whole, so the opening is the right answer.
        val ordered = if (scored.isEmpty()) chunks else scored.map { it.first }

        val taken = mutableListOf<DocChunk>()
        var used = 0
        for (chunk in ordered) {
            if (used + chunk.text.length > budgetChars && taken.isNotEmpty()) continue
            taken += chunk
            used += chunk.text.length
            if (used >= budgetChars) break
        }
        return taken.sortedWith(compareBy({ it.firstPage }, { it.lastPage }))
    }

    // -------------------------------------------------------------- context

    /**
     * The block handed to the model.
     *
     * Written in the question's own language, for the same reason the web
     * grounding is: an English instruction block in front of an Arabic
     * question pulls a small model into answering in English, and the
     * grounding ends up costing the user their language.
     */
    fun buildContext(
        documentName: String,
        question: String,
        chunks: List<DocChunk>,
        totalPages: Int,
    ): String {
        if (chunks.isEmpty()) return ""
        val arabic = SearchQuery.isArabic(question) || SearchQuery.isArabic(chunks.first().text)

        return buildString {
            appendLine(
                if (arabic) "مقتطفات من الملف «$documentName» ($totalPages صفحة):"
                else "Extracts from \"$documentName\" ($totalPages pages):"
            )
            appendLine()
            for (chunk in chunks) {
                appendLine(
                    if (arabic) "[صفحة ${chunk.pageLabel()}]"
                    else "[page ${chunk.pageLabel()}]"
                )
                appendLine(chunk.text)
                appendLine()
            }
            append(
                if (arabic) {
                    "أجب اعتمادًا على هذه المقتطفات وحدها، وأشر إلى رقم الصفحة " +
                        "هكذا [صفحة 3]. وإن لم تكن الإجابة فيها فقل ذلك صراحةً " +
                        "بدل التخمين — فقد تكون في صفحة لم تُعرض عليك."
                } else {
                    "Answer from these extracts only, citing pages as [page 3]. " +
                        "If the answer is not in them, say so instead of guessing — " +
                        "it may be on a page you were not shown."
                }
            )
        }
    }

    /**
     * Words too common to rank anything.
     *
     * Arabic first and longer, because Arabic glues its particles to words and
     * normalisation leaves a lot of two-letter debris behind.
     */
    private val STOPWORDS = setOf(
        // Arabic
        "في", "من", "الى", "على", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي",
        "الذي", "ما", "هو", "هي", "ان", "انا", "انت", "كان", "كانت", "قد", "لقد",
        "كل", "بعض", "او", "ثم", "لكن", "حتى", "اذا", "كما", "بين", "عند", "لا",
        "يا", "ايضا", "وهو", "وهي", "به", "له", "لي", "بها", "منه", "عليه",
        "هل", "كيف", "متى", "اين", "لماذا", "كم", "الملف", "الوثيقه", "الصفحه",
        // English
        "the", "of", "and", "to", "in", "is", "it", "for", "on", "with", "as",
        "at", "by", "an", "be", "this", "that", "from", "or", "are", "was",
        "what", "how", "when", "where", "why", "who", "which", "do", "does",
        "can", "you", "me", "my", "please", "tell", "about", "file", "document",
        "page", "pdf",
    )
}
