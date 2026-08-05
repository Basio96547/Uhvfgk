package com.basel.ai.agent

import com.basel.ai.core.AppStrings

/**
 * Turns the live tool registry into the text the model reads.
 *
 * Generated, never hand-written. A prompt that lists tools by hand goes stale
 * the first time one is renamed, and the failure is silent: the model calls a
 * tool that no longer exists and the turn dies with no explanation. Here the
 * list cannot disagree with the code, because it is the code.
 *
 * The wording around it is translated; the call format is not. `<tool_call>`
 * is a token the model was trained on — translating it would break the one
 * part that has to be exact.
 */
object ToolPrompt {

    /** The shape every call must take. Kept identical to what Qwen was trained on. */
    const val CALL_FORMAT = """<tool_call>{"name": "TOOL", "arguments": {"KEY": "VALUE"}}</tool_call>"""

    /** The whole tools section of the system prompt, or "" when there are none. */
    fun render(registry: ToolRegistry, s: AppStrings): String {
        if (registry.isEmpty) return ""

        return buildString {
            appendLine(s.toolsHeader)
            appendLine()
            appendLine(CALL_FORMAT)
            appendLine()
            appendLine(s.toolsRules)
            appendLine()
            appendLine(s.toolsAvailable)
            for (tool in registry.tools) {
                val spec = tool.spec
                appendLine()
                appendLine("- ${spec.name} — ${spec.summary}")
                for (param in spec.params) {
                    val mark = if (param.required) "" else " (${s.toolsOptional})"
                    appendLine("    · ${param.name}$mark — ${param.description}")
                }
                appendLine("    ${spec.example}")
            }
        }.trim()
    }

    /**
     * What the model is shown after a tool ran.
     *
     * Fed back as a user turn, because that is the only role the chat template
     * reliably has. It is labelled so the model does not mistake it for the
     * person talking — a 4B model that thinks the user said `exit 0` will
     * apologise to them for it.
     */
    fun observation(call: ToolCall, result: ToolResult, s: AppStrings): String = buildString {
        appendLine("${s.toolResultLabel}: ${call.name}")
        if (!result.ok) appendLine(s.toolFailedLabel)
        appendLine(result.output.ifBlank { s.toolNoOutput })
        appendLine()
        append(s.toolContinueInstruction)
    }

    /** Told to the model when it asks for a tool that is not there. */
    fun unknownTool(name: String, registry: ToolRegistry, s: AppStrings): String =
        "${s.toolUnknown(name)} ${s.toolsAvailable} ${registry.names().joinToString(", ")}"
}
