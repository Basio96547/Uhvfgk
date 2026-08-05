package com.basel.ai.agent

/**
 * Finds a tool call in whatever the model actually emitted.
 *
 * There is no single format. Qwen writes `<tool_call>` tags, Llama writes a
 * bare JSON object, others fence it as ```json, and a 4B model does all three
 * across one conversation — sometimes with a sentence of commentary wrapped
 * around it. A parser that accepts only the documented shape rejects most real
 * calls, so this accepts every shape that is unambiguous and gives up loudly
 * on the ones that are not.
 *
 * Pure, so all of that is tested against saved model output rather than
 * discovered on a phone.
 */
object ToolCallParser {

    /** Qwen and several fine-tunes: `<tool_call>{...}</tool_call>`. */
    private val TAGGED = Regex(
        """<tool[ _]?call>\s*(\{.*?\})\s*</tool[ _]?call>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** A fenced block, with or without a language tag. */
    private val FENCED = Regex(
        """```(?:json|tool_call|tool)?\s*(\{.*?\})\s*```""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Keys that have been used for "which tool", across model families. */
    private val NAME_KEYS = listOf("name", "tool", "tool_name", "function", "action")

    /** Keys that have been used for "with what". */
    private val ARG_KEYS = listOf("arguments", "args", "parameters", "params", "input")

    /**
     * The first tool call in [text], or null.
     *
     * First, not all: a small model that emits two calls has usually emitted
     * the same call twice, and running both is worse than running one and
     * letting it try again with the result in hand.
     */
    fun parse(text: String): ToolCall? {
        for (candidate in candidates(text)) {
            val call = fromJson(candidate.json, candidate.raw)
            if (call != null) return call
        }
        return null
    }

    /** True when [text] contains something that was meant to be a tool call. */
    fun looksLikeCall(text: String): Boolean = parse(text) != null

    /**
     * [text] with the tool call removed.
     *
     * The call is machinery, not an answer. Left in, it shows up in the chat
     * bubble as a block of JSON, and gets read aloud by the speech engine.
     */
    fun strip(text: String): String {
        val call = parse(text) ?: return text
        if (call.raw.isEmpty()) return text
        return text.replace(call.raw, "").trim()
    }

    private data class Candidate(val json: String, val raw: String)

    /** Every substring that might be a call, most explicit first. */
    private fun candidates(text: String): List<Candidate> {
        val out = mutableListOf<Candidate>()
        TAGGED.findAll(text).forEach { out += Candidate(it.groupValues[1], it.value) }
        FENCED.findAll(text).forEach { out += Candidate(it.groupValues[1], it.value) }
        // Bare objects last: they are the most likely to be something else,
        // like a model explaining what JSON it *would* send.
        braceSpans(text).forEach { out += Candidate(it, it) }
        return out
    }

    /**
     * Balanced `{...}` spans at the top level, ignoring braces inside strings.
     *
     * A regex cannot do this: `{"a": {"b": 1}}` needs counting, and a shell
     * command in an argument routinely contains braces of its own.
     */
    internal fun braceSpans(text: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var quote = ' '
        var escaped = false

        for (i in text.indices) {
            val ch = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == quote -> inString = false
                }
                continue
            }
            when (ch) {
                '"', '\'' -> if (depth > 0) {
                    inString = true
                    quote = ch
                }
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            out += text.substring(start, i + 1)
                            start = -1
                        }
                    }
                }
            }
        }
        return out
    }

    private fun fromJson(json: String, raw: String): ToolCall? {
        val root = MiniJson.parseObject(json) ?: return null

        val name = NAME_KEYS.firstNotNullOfOrNull { key ->
            (root[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } ?: return null

        val argsValue = ARG_KEYS.firstNotNullOfOrNull { root[it] }
        val args = when (argsValue) {
            is Map<*, *> -> argsValue.entries
                .mapNotNull { (k, v) -> (k as? String)?.let { it to MiniJson.asText(v) } }
                .toMap()

            // Some models flatten: {"name": "shell", "command": "ls"}. Take
            // everything that is not the name key as an argument.
            null -> root
                .filterKeys { it !in NAME_KEYS }
                .mapValues { MiniJson.asText(it.value) }

            // A single positional value, e.g. {"tool": "calc", "args": "2+2"}.
            else -> mapOf("value" to MiniJson.asText(argsValue))
        }

        return ToolCall(name = name.lowercase(), args = args, raw = raw)
    }
}
