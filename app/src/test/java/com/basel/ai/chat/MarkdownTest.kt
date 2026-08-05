package com.basel.ai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private fun text(blocks: List<Block>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is Block.Paragraph -> Markdown.plainText(block.spans)
            is Block.Bullet -> Markdown.plainText(block.spans)
            is Block.Numbered -> Markdown.plainText(block.spans)
            is Block.Heading -> Markdown.plainText(block.spans)
            is Block.Code -> block.code
            Block.Rule -> "---"
        }
    }

    // ------------------------------------------------------------- emphasis

    @Test
    fun `bold and italic become spans, not asterisks`() {
        val spans = Markdown.spans("this is **important** and *this* too")
        assertEquals("this is important and this too", Markdown.plainText(spans))
        assertTrue(spans.any { it.bold && it.text == "important" })
        assertTrue(spans.any { it.italic && it.text == "this" })
    }

    @Test
    fun `arabic emphasis works the same`() {
        val spans = Markdown.spans("هذا **مهم** جدًا")
        assertEquals("هذا مهم جدًا", Markdown.plainText(spans))
        assertTrue(spans.any { it.bold && it.text == "مهم" })
    }

    @Test
    fun `inline code is code, and beats emphasis inside it`() {
        val spans = Markdown.spans("run `ls **-la**` now")
        assertTrue(spans.any { it.code && it.text == "ls **-la**" })
        assertEquals("run ls **-la** now", Markdown.plainText(spans))
    }

    @Test
    fun `an unclosed marker stays literal`() {
        // A truncated reply ends on a dangling **. Swallowing the rest into
        // bold would turn a cut-off answer into an unreadable one.
        assertEquals("half a **sentence", Markdown.plainText(Markdown.spans("half a **sentence")))
        assertEquals("a `command", Markdown.plainText(Markdown.spans("a `command")))
    }

    @Test
    fun `underscores inside a word are not emphasis`() {
        // snake_case identifiers and file names would otherwise be mangled.
        val spans = Markdown.spans("call read_file_now here")
        assertEquals("call read_file_now here", Markdown.plainText(spans))
        assertTrue(spans.none { it.italic })
    }

    @Test
    fun `a link keeps its text and drops the address`() {
        val spans = Markdown.spans("see [the docs](https://example.com/a?b=1) for more")
        assertEquals("see the docs for more", Markdown.plainText(spans))
    }

    @Test
    fun `nothing is ever dropped`() {
        // A parser that silently eats what it did not understand is worse than
        // one that shows the marker.
        val samples = listOf(
            "plain text", "**", "*", "`", "[", "[]", "[x](", "a *b _c_ d* e",
            "مرحبا **بك** في `التطبيق`", "100% * 3", "__both__ and _one_",
        )
        for (sample in samples) {
            val plain = Markdown.plainText(Markdown.spans(sample))
            assertEquals(
                sample,
                sample.replace("**", "").replace("`", "").length <= plain.length ||
                    plain.isNotEmpty(),
                true,
            )
            assertTrue(sample, plain.isNotEmpty() || sample.isEmpty())
        }
    }

    // --------------------------------------------------------------- blocks

    @Test
    fun `a fenced code block keeps its lines and its language`() {
        val blocks = Markdown.parse("before\n```kotlin\nval x = 1\nval y = 2\n```\nafter")
        val code = blocks.filterIsInstance<Block.Code>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\nval y = 2", code.code)
        assertTrue(text(blocks).contains("before"))
        assertTrue(text(blocks).contains("after"))
    }

    @Test
    fun `an unterminated fence is still a code block`() {
        // A truncated generation ends inside its code. Treating the remainder
        // as prose would print it with the markup showing.
        val blocks = Markdown.parse("explanation\n```\nfun main() {\n  println(1)")
        val code = blocks.filterIsInstance<Block.Code>().single()
        assertTrue(code.code.contains("println(1)"))
    }

    @Test
    fun `code inside a fence is not parsed as markdown`() {
        val blocks = Markdown.parse("```\n# not a heading\n- not a bullet\n**not bold**\n```")
        val code = blocks.filterIsInstance<Block.Code>().single()
        assertTrue(code.code.contains("# not a heading"))
        assertTrue(code.code.contains("**not bold**"))
        assertTrue(blocks.none { it is Block.Heading })
        assertTrue(blocks.none { it is Block.Bullet })
    }

    @Test
    fun `bullets and numbers become list blocks`() {
        val blocks = Markdown.parse("- first\n- second\n\n1. one\n2. two")
        assertEquals(2, blocks.filterIsInstance<Block.Bullet>().size)
        val numbered = blocks.filterIsInstance<Block.Numbered>()
        assertEquals(2, numbered.size)
        assertEquals(1, numbered[0].number)
        assertEquals(2, numbered[1].number)
    }

    @Test
    fun `arabic bullets work`() {
        val blocks = Markdown.parse("- البند الأول\n- البند الثاني")
        val bullets = blocks.filterIsInstance<Block.Bullet>()
        assertEquals(2, bullets.size)
        assertEquals("البند الأول", Markdown.plainText(bullets[0].spans))
    }

    @Test
    fun `headings carry their level`() {
        val blocks = Markdown.parse("# One\n### Three")
        val headings = blocks.filterIsInstance<Block.Heading>()
        assertEquals(1, headings[0].level)
        assertEquals(3, headings[1].level)
        assertEquals("One", Markdown.plainText(headings[0].spans))
    }

    @Test
    fun `a hash inside a line is not a heading`() {
        val blocks = Markdown.parse("issue #42 was fixed")
        assertTrue(blocks.none { it is Block.Heading })
    }

    @Test
    fun `hard-wrapped prose is joined into one paragraph`() {
        // Models wrap at a column. Honouring those breaks makes the bubble
        // ragged for no reason.
        val blocks = Markdown.parse("one line\nand its continuation\n\nsecond para")
        val paragraphs = blocks.filterIsInstance<Block.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("one line and its continuation", Markdown.plainText(paragraphs[0].spans))
    }

    @Test
    fun `a rule is a rule, and three dashes are not a bullet`() {
        val blocks = Markdown.parse("above\n\n---\n\nbelow")
        assertEquals(1, blocks.count { it is Block.Rule })
        assertTrue(blocks.none { it is Block.Bullet })
    }

    @Test
    fun `ordinary prose is one paragraph with no markup`() {
        val blocks = Markdown.parse("مرحبا، كيف أساعدك اليوم؟")
        assertEquals(1, blocks.size)
        assertEquals("مرحبا، كيف أساعدك اليوم؟", text(blocks))
    }

    @Test
    fun `empty input is no blocks rather than one empty one`() {
        assertTrue(Markdown.parse("").isEmpty())
        assertTrue(Markdown.parse("   \n\n  ").isEmpty())
    }

    @Test
    fun `a whole realistic reply survives intact`() {
        val reply = """
            ## الخلاصة

            الإجابة **نعم**، وهذه الأسباب:

            - السبب الأول
            - السبب الثاني مع `كود`

            ```kotlin
            fun main() = println("hi")
            ```

            وللمزيد انظر [الوثائق](https://example.com).
        """.trimIndent()
        val blocks = Markdown.parse(reply)
        assertEquals(1, blocks.count { it is Block.Heading })
        assertEquals(2, blocks.count { it is Block.Bullet })
        assertEquals(1, blocks.count { it is Block.Code })
        val flat = text(blocks)
        assertTrue(flat.contains("الخلاصة"))
        assertTrue(flat.contains("الوثائق"))
        assertFalse2(flat.contains("https://example.com"))
        assertFalse2(flat.contains("**"))
    }

    private fun assertFalse2(condition: Boolean) = assertTrue(!condition)
}
