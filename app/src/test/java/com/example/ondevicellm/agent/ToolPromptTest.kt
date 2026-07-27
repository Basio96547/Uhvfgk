package com.example.ondevicellm.agent

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPromptTest {

    /** A tool with no Android behind it, so the prompt can be rendered here. */
    private class Fake(
        name: String,
        params: List<ToolParam> = emptyList(),
    ) : AgentTool {
        override val spec = ToolSpec(
            name = name,
            summary = "does $name",
            params = params,
            example = """<tool_call>{"name": "$name", "arguments": {}}</tool_call>""",
        )

        override suspend fun run(call: ToolCall) = ToolResult.ok("ran")
    }

    private fun registry(vararg names: String) = ToolRegistry(names.map { Fake(it) })

    @Test
    fun `no tools means no tools section at all`() {
        // Not an empty heading: an empty "Available tools:" invites the model
        // to invent one.
        assertEquals("", ToolPrompt.render(ToolRegistry(emptyList()), EnglishStrings))
    }

    @Test
    fun `every tool appears with its example`() {
        val prompt = ToolPrompt.render(registry("shell", "calc"), EnglishStrings)
        assertTrue(prompt.contains("shell"))
        assertTrue(prompt.contains("calc"))
        assertTrue(prompt.contains("""{"name": "shell", "arguments": {}}"""))
    }

    @Test
    fun `the call format is never translated`() {
        // `<tool_call>` is a token the model was trained on. Translating it
        // would break the one part that has to match exactly.
        for (strings in listOf<AppStrings>(EnglishStrings, ArabicStrings)) {
            val prompt = ToolPrompt.render(registry("now"), strings)
            assertTrue(prompt.contains("<tool_call>"))
            assertTrue(prompt.contains("\"arguments\""))
        }
    }

    @Test
    fun `the surrounding words are translated`() {
        val arabic = ToolPrompt.render(registry("now"), ArabicStrings)
        assertTrue(arabic.contains(ArabicStrings.toolsAvailable))
        assertTrue(arabic.any { it in '؀'..'ۿ' })
    }

    @Test
    fun `optional arguments are marked as optional`() {
        val tools = ToolRegistry(
            listOf(
                Fake(
                    "read_file",
                    listOf(
                        ToolParam("path", "where"),
                        ToolParam("lines", "how many", required = false),
                    ),
                )
            )
        )
        val prompt = ToolPrompt.render(tools, EnglishStrings)
        assertTrue(prompt.contains("path"))
        assertTrue(prompt.contains("lines (${EnglishStrings.toolsOptional})"))
        // The required one is not marked, or the marking means nothing.
        assertFalse(prompt.contains("path (${EnglishStrings.toolsOptional})"))
    }

    @Test
    fun `an observation is labelled so it is not mistaken for the user`() {
        val observation = ToolPrompt.observation(
            ToolCall("calc", mapOf("expression" to "2+2")),
            ToolResult.ok("2+2 = 4"),
            EnglishStrings,
        )
        assertTrue(observation.contains(EnglishStrings.toolResultLabel))
        assertTrue(observation.contains("calc"))
        assertTrue(observation.contains("2+2 = 4"))
        assertFalse(observation.contains(EnglishStrings.toolFailedLabel))
    }

    @Test
    fun `a failure says so, so the model does not read it as an answer`() {
        val observation = ToolPrompt.observation(
            ToolCall("shell"),
            ToolResult.failed("permission denied"),
            EnglishStrings,
        )
        assertTrue(observation.contains(EnglishStrings.toolFailedLabel))
        assertTrue(observation.contains("permission denied"))
    }

    @Test
    fun `empty output is said out loud rather than left blank`() {
        val observation =
            ToolPrompt.observation(ToolCall("shell"), ToolResult.ok("   "), EnglishStrings)
        assertTrue(observation.contains(EnglishStrings.toolNoOutput))
    }

    // ------------------------------------------------------------ registry

    @Test
    fun `a wrapped tool name still resolves`() {
        // Models emit "functions.shell" and "tool_shell" unprompted.
        val tools = registry("shell", "calc")
        assertEquals("shell", tools.find("functions.shell")?.spec?.name)
        assertEquals("shell", tools.find("shell_tool")?.spec?.name)
        assertEquals("calc", tools.find("  CALC ")?.spec?.name)
    }

    @Test
    fun `an unknown tool resolves to nothing`() {
        assertEquals(null, registry("shell").find("send_email"))
    }

    @Test
    fun `the unknown-tool message lists what does exist`() {
        val tools = registry("calc", "shell")
        val message = ToolPrompt.unknownTool("send_email", tools, EnglishStrings)
        assertTrue(message.contains("send_email"))
        assertTrue(message.contains("calc"))
        assertTrue(message.contains("shell"))
    }
}
