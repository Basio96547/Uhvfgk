package com.example.ondevicellm.agent

/**
 * One argument a tool takes.
 *
 * Everything is a string. Small models do not keep types straight — the same
 * model will send `{"lines": 20}` on one turn and `{"lines": "20"}` on the
 * next — so coercion happens once, here, instead of in every tool.
 */
data class ToolParam(
    val name: String,
    /** One line, written for the model, not for a docs page. */
    val description: String,
    val required: Boolean = true,
)

/** What a tool is and how to call it. This is what the model is shown. */
data class ToolSpec(
    val name: String,
    val summary: String,
    val params: List<ToolParam> = emptyList(),
    /** A complete, correct call. Models copy the example far more than they read the prose. */
    val example: String,
)

/** A parsed request from the model to run a tool. */
data class ToolCall(
    val name: String,
    val args: Map<String, String> = emptyMap(),
    /** The exact text this was parsed out of, so it can be stripped from the reply. */
    val raw: String = "",
) {
    fun arg(name: String): String? = args[name]?.takeIf { it.isNotBlank() }

    fun intArg(name: String, fallback: Int): Int =
        args[name]?.trim()?.toDoubleOrNull()?.toInt() ?: fallback

    /** How the call reads in the terminal log and in the transcript. */
    fun describe(): String = buildString {
        append(name)
        append('(')
        append(args.entries.joinToString(", ") { "${it.key}=${it.value.take(60)}" })
        append(')')
    }
}

/** What came back. [ok] is false for a failure the model should react to. */
data class ToolResult(val ok: Boolean, val output: String) {
    companion object {
        fun ok(output: String) = ToolResult(true, output)
        fun failed(reason: String) = ToolResult(false, reason)
    }
}

/**
 * A skill the model can use.
 *
 * Deliberately narrow: a name, a description the model reads, and a suspending
 * call. Tools that need Android hold their own context; tools that do not stay
 * pure and get unit tested.
 */
interface AgentTool {
    val spec: ToolSpec
    suspend fun run(call: ToolCall): ToolResult
}

/**
 * The set of tools available this turn.
 *
 * Built per turn from settings rather than once at startup, because which
 * tools exist depends on what the user has switched on — and a tool the model
 * is told about but is not allowed to use is worse than one it never saw.
 */
class ToolRegistry(tools: List<AgentTool>) {

    val tools: List<AgentTool> = tools.sortedBy { it.spec.name }

    val isEmpty: Boolean get() = tools.isEmpty()

    fun find(name: String): AgentTool? {
        val wanted = name.trim().lowercase()
        return tools.firstOrNull { it.spec.name == wanted }
            // Models routinely wrap the name: "functions.shell", "tool_shell".
            ?: tools.firstOrNull { wanted.endsWith(it.spec.name) || wanted.startsWith(it.spec.name) }
    }

    fun names(): List<String> = tools.map { it.spec.name }
}
