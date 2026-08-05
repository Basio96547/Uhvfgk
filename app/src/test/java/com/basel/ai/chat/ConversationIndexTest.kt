package com.basel.ai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIndexTest {

    private fun conversation(
        id: String = "1",
        title: String = "t",
        updatedAt: Long = 0,
        vararg texts: Pair<Author, String>,
    ) = Conversation(
        id = id,
        title = title,
        updatedAt = updatedAt,
        messages = texts.mapIndexed { i, (author, text) ->
            ChatMessage(id = i.toLong(), author = author, text = text)
        },
    )

    // --------------------------------------------------------------- titles

    @Test
    fun `a title comes from the first thing asked`() {
        assertEquals("كيف أضيف نموذجًا", ConversationIndex.titleFor("كيف أضيف نموذجًا", "—"))
    }

    @Test
    fun `a long title is cut at a word, never mid-word`() {
        val question = "what is the difference between quantisation formats and which should I use"
        val title = ConversationIndex.titleFor(question, "—")
        assertTrue(title.length <= ConversationIndex.MAX_TITLE + 1)
        assertTrue(title.endsWith("…"))
        // The cut lands on a space, so no half word is left behind.
        assertFalse(title.removeSuffix("…").endsWith(" "))
        assertTrue(question.startsWith(title.removeSuffix("…")))
    }

    @Test
    fun `an arabic question is cut at a word too`() {
        val question = "ما الفرق بين صيغ التكميم المختلفة وأيها ينبغي أن أستعمل على هاتفي"
        val title = ConversationIndex.titleFor(question, "—")
        assertTrue(title.endsWith("…"))
        assertTrue(question.startsWith(title.removeSuffix("…")))
    }

    @Test
    fun `markup does not end up in the title`() {
        // A pasted code block or a heading makes a terrible title.
        val title = ConversationIndex.titleFor("## **مهم** `code`", "—")
        assertFalse(title.contains("#"))
        assertFalse(title.contains("*"))
        assertFalse(title.contains("`"))
    }

    @Test
    fun `only the first line is used`() {
        assertEquals("first line", ConversationIndex.titleFor("first line\nsecond\nthird", "—"))
    }

    @Test
    fun `nothing to name falls back`() {
        assertEquals("New chat", ConversationIndex.titleFor("", "New chat"))
        assertEquals("New chat", ConversationIndex.titleFor("   \n  ", "New chat"))
    }

    // ---------------------------------------------------------------- order

    @Test
    fun `newest first`() {
        val list = listOf(
            conversation(id = "old", updatedAt = 100),
            conversation(id = "new", updatedAt = 300),
            conversation(id = "mid", updatedAt = 200),
        )
        assertEquals(listOf("new", "mid", "old"), ConversationIndex.sort(list).map { it.id })
    }

    // --------------------------------------------------------------- search

    @Test
    fun `search looks in the title and in the messages`() {
        val chat = conversation(
            title = "Model formats",
            texts = arrayOf(Author.USER to "what about quantisation"),
        )
        assertTrue(ConversationIndex.matches(chat, "formats"))
        assertTrue(ConversationIndex.matches(chat, "quantisation"))
        assertFalse(ConversationIndex.matches(chat, "elephant"))
    }

    @Test
    fun `arabic search survives the ordinary spelling differences`() {
        // "الإيرادات" and "الايرادات" are the same word written two normal
        // ways. Missing one reads as the search being broken.
        val chat = conversation(texts = arrayOf(Author.USER to "كم بلغت الإيرادات"))
        assertTrue(ConversationIndex.matches(chat, "الايرادات"))
        assertTrue(ConversationIndex.matches(chat, "الإيرادات"))
    }

    @Test
    fun `an empty query matches everything`() {
        val chat = conversation(texts = arrayOf(Author.USER to "anything"))
        assertTrue(ConversationIndex.matches(chat, ""))
        assertTrue(ConversationIndex.matches(chat, "   "))
    }

    @Test
    fun `search returns newest first`() {
        val list = listOf(
            conversation(id = "a", updatedAt = 1, texts = arrayOf(Author.USER to "kotlin")),
            conversation(id = "b", updatedAt = 9, texts = arrayOf(Author.USER to "kotlin")),
        )
        assertEquals(listOf("b", "a"), ConversationIndex.search(list, "kotlin").map { it.id })
    }

    // ---------------------------------------------------------------- state

    @Test
    fun `a conversation with nothing said is empty`() {
        assertTrue(conversation().isEmpty)
        assertTrue(conversation(texts = arrayOf(Author.USER to "   ")).isEmpty)
        assertFalse(conversation(texts = arrayOf(Author.USER to "hi")).isEmpty)
    }

    @Test
    fun `the preview is the last thing said`() {
        val chat = conversation(
            texts = arrayOf(Author.USER to "first", Author.MODEL to "last"),
        )
        assertEquals("last", chat.preview())
    }

    // --------------------------------------------------------------- export

    @Test
    fun `export is markdown with both sides labelled`() {
        val chat = conversation(
            title = "Quantisation",
            texts = arrayOf(Author.USER to "which format", Author.MODEL to "Q4_K_M"),
        )
        val out = ConversationIndex.toMarkdown(chat, "You", "باسل Ai")
        assertTrue(out.startsWith("# Quantisation"))
        assertTrue(out.contains("**You**"))
        assertTrue(out.contains("**باسل Ai**"))
        assertTrue(out.contains("which format"))
        assertTrue(out.contains("Q4_K_M"))
    }

    @Test
    fun `thinking is not exported`() {
        // It is working, not the answer. Pasting it to someone else would be
        // quoting a draft.
        val chat = Conversation(
            id = "1", title = "t", updatedAt = 0,
            messages = listOf(
                ChatMessage(1, Author.MODEL, "the answer", thinking = "a long private ramble")
            ),
        )
        val out = ConversationIndex.toMarkdown(chat, "You", "باسل Ai")
        assertTrue(out.contains("the answer"))
        assertFalse(out.contains("ramble"))
    }

    @Test
    fun `empty messages are skipped rather than exported as blanks`() {
        val chat = conversation(
            texts = arrayOf(Author.USER to "asked", Author.MODEL to "   "),
        )
        val out = ConversationIndex.toMarkdown(chat, "You", "باسل Ai")
        assertEquals(1, Regex("\\*\\*").findAll(out).count() / 2)
    }
}
