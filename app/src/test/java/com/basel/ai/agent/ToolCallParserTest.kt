package com.basel.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case here is a shape a real model emits.
 *
 * The parser is the whole feature's single point of failure: a call it does
 * not recognise is not an error the user can see, it is a turn where the model
 * silently answered from guesswork instead of from the tool. So the tests are
 * about tolerance, and about the one thing tolerance must not cost — reading a
 * call out of text that was never one.
 */
class ToolCallParserTest {

    @Test
    fun `qwen tool_call tags`() {
        val call = ToolCallParser.parse(
            """<tool_call>{"name": "shell", "arguments": {"command": "ls -la"}}</tool_call>"""
        )
        assertEquals("shell", call?.name)
        assertEquals("ls -la", call?.arg("command"))
    }

    @Test
    fun `a fenced json block`() {
        val call = ToolCallParser.parse(
            """
            I'll check that.
            ```json
            {"name": "now", "arguments": {}}
            ```
            """.trimIndent()
        )
        assertEquals("now", call?.name)
    }

    @Test
    fun `a bare object with no wrapper`() {
        val call = ToolCallParser.parse("""{"name": "calc", "arguments": {"expression": "2+2"}}""")
        assertEquals("calc", call?.name)
        assertEquals("2+2", call?.arg("expression"))
    }

    @Test
    fun `the other key names models use`() {
        // Same call, four vocabularies. Rejecting three of them would make the
        // tool work on one model family and silently not on the rest.
        val shapes = listOf(
            """{"tool": "calc", "args": {"expression": "1+1"}}""",
            """{"tool_name": "calc", "parameters": {"expression": "1+1"}}""",
            """{"function": "calc", "params": {"expression": "1+1"}}""",
            """{"action": "calc", "input": {"expression": "1+1"}}""",
        )
        for (shape in shapes) {
            val call = ToolCallParser.parse(shape)
            assertEquals(shape, "calc", call?.name)
            assertEquals(shape, "1+1", call?.arg("expression"))
        }
    }

    @Test
    fun `flattened arguments are still arguments`() {
        val call = ToolCallParser.parse("""{"name": "shell", "command": "pwd"}""")
        assertEquals("shell", call?.name)
        assertEquals("pwd", call?.arg("command"))
    }

    @Test
    fun `numbers and booleans become strings`() {
        val call = ToolCallParser.parse(
            """{"name": "read_file", "arguments": {"path": "a.txt", "lines": 50, "raw": true}}"""
        )
        // 50, not 50.0 — a model shown "50.0" starts treating a line count as
        // a decimal and asking for 50.5 lines.
        assertEquals("50", call?.arg("lines"))
        assertEquals("true", call?.arg("raw"))
        assertEquals(50, call?.intArg("lines", 0))
    }

    @Test
    fun `a nested object in an argument survives`() {
        val call = ToolCallParser.parse(
            """<tool_call>{"name": "shell", "arguments": {"command": "awk '{print $1}' f"}}</tool_call>"""
        )
        assertEquals("awk '{print \$1}' f", call?.arg("command"))
    }

    @Test
    fun `trailing commas and unquoted keys are forgiven`() {
        // A 4B model emits both, and refusing them costs a whole turn.
        val call = ToolCallParser.parse("""{name: "now", arguments: {},}""")
        assertEquals("now", call?.name)
    }

    @Test
    fun `single quotes are forgiven`() {
        val call = ToolCallParser.parse("""{'name': 'calc', 'arguments': {'expression': '3*3'}}""")
        assertEquals("calc", call?.name)
        assertEquals("3*3", call?.arg("expression"))
    }

    @Test
    fun `the tool name is normalised`() {
        assertEquals("shell", ToolCallParser.parse("""{"name": "SHELL", "arguments": {}}""")?.name)
    }

    // ------------------------------------------------- what must NOT parse

    @Test
    fun `ordinary prose is not a tool call`() {
        assertNull(ToolCallParser.parse("Sure, I can list the files for you."))
        assertNull(ToolCallParser.parse("مرحبا، كيف أساعدك؟"))
        assertFalse(ToolCallParser.looksLikeCall("Here is some {json-ish} text."))
    }

    @Test
    fun `an object with no tool name is not a call`() {
        assertNull(ToolCallParser.parse("""{"result": 42, "unit": "kg"}"""))
    }

    @Test
    fun `a code block that merely contains braces is not a call`() {
        val text = """
            Here's the CSS:
            ```css
            body { margin: 0; }
            ```
        """.trimIndent()
        assertNull(ToolCallParser.parse(text))
    }

    // --------------------------------------------------------------- strip

    @Test
    fun `the call is taken back out of the reply`() {
        val text = """Let me check.
<tool_call>{"name": "now", "arguments": {}}</tool_call>"""
        val stripped = ToolCallParser.strip(text)
        assertEquals("Let me check.", stripped)
        assertFalse(stripped.contains("tool_call"))
    }

    @Test
    fun `stripping leaves text without a call alone`() {
        assertEquals("Just an answer.", ToolCallParser.strip("Just an answer."))
    }

    // ---------------------------------------------------------- brace spans

    @Test
    fun `brace spans count depth and ignore braces inside strings`() {
        val spans = ToolCallParser.braceSpans("""{"a": {"b": "}"}} tail {"c": 1}""")
        assertEquals(2, spans.size)
        assertEquals("""{"a": {"b": "}"}}""", spans[0])
        assertEquals("""{"c": 1}""", spans[1])
    }

    @Test
    fun `an unclosed brace yields nothing rather than half a call`() {
        assertTrue(ToolCallParser.braceSpans("""{"name": "shell" """).isEmpty())
    }

    @Test
    fun `only the first call is taken`() {
        // Two calls from a small model are almost always the same call twice.
        val call = ToolCallParser.parse(
            """<tool_call>{"name": "now", "arguments": {}}</tool_call>
               <tool_call>{"name": "calc", "arguments": {"expression": "1"}}</tool_call>"""
        )
        assertEquals("now", call?.name)
    }
}
