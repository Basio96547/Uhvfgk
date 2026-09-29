package com.basel.ai.chat

import com.basel.ai.llm.QueryRouter

/** One saved conversation. */
data class Conversation(
    val id: String,
    /** Derived from the first thing asked, unless the user renamed it. */
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage> = emptyList(),
) {
    val isEmpty: Boolean get() = messages.none { it.text.isNotBlank() }

    /** The last thing said, for the list. */
    fun preview(): String =
        messages.lastOrNull { it.text.isNotBlank() }?.text?.trim().orEmpty()
}

/**
 * Naming, ordering, searching and exporting conversations.
 *
 * Kept apart from the storage because these are the parts that are wrong in a
 * way nobody reports: a title that cuts a word in half, a search that cannot
 * find an Arabic word because it was written with a different alef, a list
 * that reorders itself unpredictably. Storage either works or throws.
 */
object ConversationIndex {

    /** Longest a derived title may be before it stops fitting a list row. */
    const val MAX_TITLE = 42

    /**
     * A title from the first thing asked.
     *
     * Cut at a word boundary, never mid-word: a list of conversations called
     * "كيف أستخد…" and "what is the diff…" is harder to scan than one with
     * slightly uneven lengths.
     */
    fun titleFor(firstMessage: String, fallback: String): String {
        val clean = firstMessage
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
            // A pasted document or a code block makes a terrible title.
            .replace(Regex("[`*#>]"), "")
            .replace(Regex("\\s+"), " ")

        if (clean.isBlank()) return fallback
        if (clean.length <= MAX_TITLE) return clean

        val cut = clean.lastIndexOf(' ', MAX_TITLE)
        return if (cut > MAX_TITLE / 2) clean.take(cut) + "…" else clean.take(MAX_TITLE) + "…"
    }

    /** Newest first. That is the only order anyone looks for. */
    fun sort(conversations: List<Conversation>): List<Conversation> =
        conversations.sortedByDescending { it.updatedAt }

    /**
     * Whether a conversation matches a search.
     *
     * Through the same normaliser the router and the document index use, so
     * أ/إ/آ, ة/ه and ى/ي fold and diacritics go. Without it, searching Arabic
     * for a word written one ordinary way misses the same word written
     * another, which reads as the search being broken.
     */
    fun matches(conversation: Conversation, query: String): Boolean {
        val needle = QueryRouter.normalize(query).trim()
        if (needle.isEmpty()) return true
        if (QueryRouter.normalize(conversation.title).contains(needle)) return true
        return conversation.messages.any {
            QueryRouter.normalize(it.text).contains(needle)
        }
    }

    fun search(conversations: List<Conversation>, query: String): List<Conversation> =
        sort(conversations.filter { matches(it, query) })

    /**
     * A conversation as Markdown, for sharing.
     *
     * Markdown rather than plain text because that is what the model wrote in
     * the first place, and because it survives being pasted anywhere. Thinking
     * is left out — it is working, not the answer, and pasting it into a
     * message to someone else would be quoting a draft.
     */
    fun toMarkdown(conversation: Conversation, youLabel: String, modelLabel: String): String =
        buildString {
            appendLine("# ${conversation.title}")
            appendLine()
            for (message in conversation.messages) {
                if (message.text.isBlank()) continue
                appendLine("**${if (message.author == Author.USER) youLabel else modelLabel}**")
                appendLine()
                appendLine(message.text.trim())
                appendLine()
                if (message.sources.isNotEmpty()) {
                    for ((index, source) in message.sources.withIndex()) {
                        appendLine("> [${index + 1}] ${source.title} — ${source.url}")
                    }
                    appendLine()
                }
            }
        }.trimEnd() + "\n"
}
