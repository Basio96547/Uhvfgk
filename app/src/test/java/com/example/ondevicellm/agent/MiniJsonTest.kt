package com.example.ondevicellm.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `objects, arrays, and every scalar`() {
        val root = MiniJson.parseObject(
            """{"s": "x", "n": 3, "f": 1.5, "b": true, "z": null, "a": [1, 2]}"""
        )!!
        assertEquals("x", root["s"])
        assertEquals(3.0, root["n"] as Double, 1e-9)
        assertEquals(1.5, root["f"] as Double, 1e-9)
        assertEquals(true, root["b"])
        assertNull(root["z"])
        assertEquals(2, (root["a"] as List<*>).size)
    }

    @Test
    fun `nesting`() {
        val root = MiniJson.parseObject("""{"a": {"b": {"c": "deep"}}}""")!!
        @Suppress("UNCHECKED_CAST")
        val a = root["a"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val b = a["b"] as Map<String, Any?>
        assertEquals("deep", b["c"])
    }

    @Test
    fun `escapes`() {
        val root = MiniJson.parseObject("""{"a": "line\nnext\ttab \"quoted\" \\ A"}""")!!
        assertEquals("line\nnext\ttab \"quoted\" \\ A", root["a"])
    }

    @Test
    fun `an unknown escape is the character itself`() {
        // Models emit \/ and \' routinely; both mean the character.
        assertEquals("a/b", MiniJson.parseObject("""{"x": "a\/b"}""")!!["x"])
    }

    @Test
    fun `arabic passes through`() {
        assertEquals("مرحبا", MiniJson.parseObject("""{"x": "مرحبا"}""")!!["x"])
    }

    // ---------------------------------------------------------- forgiveness

    @Test
    fun `trailing commas`() {
        assertEquals("y", MiniJson.parseObject("""{"x": "y",}""")!!["x"])
        assertEquals(2, (MiniJson.parse("""[1, 2,]""") as List<*>).size)
    }

    @Test
    fun `unquoted keys and single quotes`() {
        assertEquals("y", MiniJson.parseObject("""{x: 'y'}""")!!["x"])
    }

    @Test
    fun `whitespace anywhere`() {
        assertEquals("y", MiniJson.parseObject("  {\n  \"x\"  :  \"y\"\n}  ")!!["x"])
    }

    // ----------------------------------------------------- what must fail

    @Test
    fun `malformed input is null, never a guess`() {
        assertNull(MiniJson.parse("""{"x": }"""))
        assertNull(MiniJson.parse("""{"x" "y"}"""))
        assertNull(MiniJson.parse("""{"x": "unterminated"""))
        assertNull(MiniJson.parse("not json at all"))
        assertNull(MiniJson.parse(""))
    }

    @Test
    fun `a non-object is not read as an object`() {
        assertNull(MiniJson.parseObject("""[1, 2]"""))
        assertNull(MiniJson.parseObject(""""just a string""""))
    }

    // -------------------------------------------------------------- asText

    @Test
    fun `whole numbers lose the decimal tail`() {
        // "50.0" is how a model starts treating a line count as a decimal.
        assertEquals("50", MiniJson.asText(50.0))
        assertEquals("1.5", MiniJson.asText(1.5))
        assertEquals("", MiniJson.asText(null))
        assertEquals("true", MiniJson.asText(true))
    }

    @Test
    fun `a list becomes something a model can read`() {
        assertTrue(MiniJson.asText(listOf("a", "b")).contains("a"))
        assertTrue(MiniJson.asText(listOf("a", "b")).contains("b"))
    }
}
