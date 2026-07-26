package com.example.ondevicellm.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingStreamParserTest {

    private fun collect(parser: ThinkingStreamParser, chunks: List<String>): Pair<String, String> {
        val thinking = StringBuilder()
        val answer = StringBuilder()
        chunks.forEach { chunk ->
            val delta = parser.consume(chunk)
            thinking.append(delta.thinking)
            answer.append(delta.answer)
        }
        val tail = parser.flush()
        thinking.append(tail.thinking)
        answer.append(tail.answer)
        return thinking.toString() to answer.toString()
    }

    @Test
    fun `splits thinking from answer in a single chunk`() {
        val (thinking, answer) = collect(
            ThinkingStreamParser(),
            listOf("<think>weighing options</think>Hello!"),
        )
        assertEquals("weighing options", thinking)
        assertEquals("Hello!", answer)
    }

    @Test
    fun `handles a tag split across chunk boundaries`() {
        val (thinking, answer) = collect(
            ThinkingStreamParser(),
            listOf("<thi", "nk>step one", " step two</thi", "nk>Answer"),
        )
        assertEquals("step one step two", thinking)
        assertEquals("Answer", answer)
    }

    @Test
    fun `never leaks partial tag text into the answer`() {
        val (thinking, answer) = collect(
            ThinkingStreamParser(),
            listOf("<think>a</think>Result <", "not-a-tag"),
        )
        assertEquals("a", thinking)
        assertEquals("Result <not-a-tag", answer)
    }

    @Test
    fun `treats plain output with no tags as answer only`() {
        val parser = ThinkingStreamParser()
        val (thinking, answer) = collect(parser, listOf("Just ", "a normal ", "reply."))
        assertEquals("", thinking)
        assertEquals("Just a normal reply.", answer)
        assertFalse(parser.sawThinking)
    }

    @Test
    fun `supports templates that start inside the thinking block`() {
        val parser = ThinkingStreamParser(startInsideThinking = true)
        val (thinking, answer) = collect(parser, listOf("reasoning here</think>Done"))
        assertEquals("reasoning here", thinking)
        assertEquals("Done", answer)
        assertTrue(parser.thinkingFinished)
    }

    @Test
    fun `flushes unterminated thinking as thinking`() {
        val parser = ThinkingStreamParser()
        val (thinking, answer) = collect(parser, listOf("<think>cut off mid-thought"))
        assertEquals("cut off mid-thought", thinking)
        assertEquals("", answer)
        assertFalse(parser.thinkingFinished)
    }
}
