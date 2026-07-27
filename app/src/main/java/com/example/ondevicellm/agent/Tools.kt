package com.example.ondevicellm.agent

import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.terminal.Shell
import com.example.ondevicellm.terminal.TerminalSession
import com.example.ondevicellm.web.SearchDepth
import com.example.ondevicellm.web.SearchQuery
import com.example.ondevicellm.web.WebSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The skills.
 *
 * The set is chosen from what a local model is actually bad at, not from what
 * is impressive in a list:
 *
 *  - It cannot count or multiply → [CalcTool].
 *  - It does not know what day it is, and will invent one → [ClockTool].
 *  - It knows nothing after its training cut-off → search and page reading.
 *  - It cannot see the device it is running on → [DeviceTool].
 *  - It cannot remember anything between turns → the file tools.
 *  - And it cannot do anything at all outside the conversation → [ShellTool].
 *
 * Each one is small on purpose. A tool that takes six arguments is a tool a 4B
 * model calls wrongly.
 */

// ------------------------------------------------------------------ shell

/**
 * Runs a command.
 *
 * The broadest skill and the one with the real teeth, so it is also the one
 * with a policy in front of it and a visible log behind it: every command the
 * model runs appears on the terminal page, marked as the model's.
 */
class ShellTool(
    private val session: TerminalSession,
    private val strings: () -> AppStrings,
    /** Whether a command of this risk may run without asking. */
    private val permit: (CommandRisk) -> Boolean,
) : AgentTool {

    override val spec = ToolSpec(
        name = "shell",
        summary = strings().toolShellSummary,
        params = listOf(
            ToolParam("command", strings().toolShellCommandParam),
        ),
        example = """<tool_call>{"name": "shell", "arguments": {"command": "ls -la"}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val s = strings()
        val command = call.arg("command")
            ?: call.arg("cmd")
            ?: call.arg("value")
            ?: return ToolResult.failed(s.toolMissingArg("command"))

        CommandPolicy.refusal(command, s)?.let {
            session.recordAgentRefusal(command, it)
            return ToolResult.failed(it)
        }

        val risk = CommandPolicy.classify(command)
        if (!permit(risk)) {
            val reason = s.toolNotPermitted(risk.name.lowercase())
            session.recordAgentRefusal(command, reason)
            return ToolResult.failed(reason)
        }

        val result = session.shell.run(command)
        session.recordAgentRun(command, result)

        val body = buildString {
            appendLine("exit ${result.exitCode}")
            if (result.stdout.isNotBlank()) appendLine(result.stdout)
            if (result.stderr.isNotBlank()) {
                appendLine("stderr:")
                appendLine(result.stderr)
            }
            if (result.timedOut) appendLine(s.terminalTimedOut)
            if (result.truncated) appendLine(s.terminalTruncated)
        }.trim()

        return ToolResult(ok = result.ok, output = body)
    }
}

// ------------------------------------------------------------------- files

/** Reads a file. Anywhere the OS allows — `/proc/cpuinfo` is a fair question. */
class ReadFileTool(
    private val shell: Shell,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "read_file",
        summary = strings().toolReadFileSummary,
        params = listOf(
            ToolParam("path", strings().toolPathParam),
            ToolParam("lines", strings().toolLinesParam, required = false),
        ),
        example = """<tool_call>{"name": "read_file", "arguments": {"path": "notes.txt"}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val s = strings()
        val path = call.arg("path") ?: call.arg("file") ?: call.arg("value")
            ?: return@withContext ToolResult.failed(s.toolMissingArg("path"))

        val file = if (path.startsWith("/")) File(path) else File(shell.workingDirectory, path)
        if (!file.exists()) return@withContext ToolResult.failed(s.toolNoSuchFile(path))
        if (file.isDirectory) return@withContext ToolResult.failed(s.toolIsDirectory(path))

        val limit = call.intArg("lines", DEFAULT_LINES).coerceIn(1, MAX_LINES)
        try {
            val lines = file.bufferedReader().useLines { it.take(limit + 1).toList() }
            val shown = lines.take(limit)
            val body = buildString {
                append(shown.joinToString("\n").take(MAX_CHARS))
                if (lines.size > limit) {
                    appendLine()
                    append(s.toolMoreLines)
                }
            }
            ToolResult.ok(body)
        } catch (e: Exception) {
            // Permission denied is the common one, and it is information: the
            // model should say "I can't read that" rather than invent contents.
            ToolResult.failed(e.message ?: s.toolReadFailed(path))
        }
    }

    private companion object {
        const val DEFAULT_LINES = 200
        const val MAX_LINES = 2_000
        const val MAX_CHARS = 20_000
    }
}

/** Writes a file. Confined to the workspace — see [SandboxPaths]. */
class WriteFileTool(
    private val shell: Shell,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "write_file",
        summary = strings().toolWriteFileSummary,
        params = listOf(
            ToolParam("path", strings().toolWritePathParam),
            ToolParam("content", strings().toolContentParam),
            ToolParam("append", strings().toolAppendParam, required = false),
        ),
        example = """<tool_call>{"name": "write_file", "arguments": {"path": "notes.txt", "content": "..."}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val s = strings()
        val path = call.arg("path") ?: call.arg("file")
            ?: return@withContext ToolResult.failed(s.toolMissingArg("path"))
        val content = call.args["content"] ?: call.args["text"] ?: call.args["value"]
            ?: return@withContext ToolResult.failed(s.toolMissingArg("content"))

        val resolved = SandboxPaths.resolve(shell.root.path, path)
            ?: return@withContext ToolResult.failed(s.toolOutsideWorkspace(path))

        val append = call.arg("append")?.lowercase() in setOf("true", "yes", "1")
        try {
            val file = File(resolved)
            file.parentFile?.mkdirs()
            if (append) file.appendText(content) else file.writeText(content)
            ToolResult.ok(
                s.toolWroteBytes(
                    SandboxPaths.display(shell.root.path, resolved),
                    content.toByteArray().size,
                )
            )
        } catch (e: Exception) {
            ToolResult.failed(e.message ?: s.toolWriteFailed(path))
        }
    }
}

/** Lists a directory. The first thing anything does when it is lost. */
class ListFilesTool(
    private val shell: Shell,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "list_files",
        summary = strings().toolListFilesSummary,
        params = listOf(ToolParam("path", strings().toolListPathParam, required = false)),
        example = """<tool_call>{"name": "list_files", "arguments": {"path": "."}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val s = strings()
        val path = call.arg("path") ?: call.arg("value") ?: "."
        val dir = if (path.startsWith("/")) File(path) else File(shell.workingDirectory, path)

        if (!dir.isDirectory) return@withContext ToolResult.failed(s.toolNotADirectory(path))
        val entries = dir.listFiles()
            ?: return@withContext ToolResult.failed(s.toolCannotList(path))

        if (entries.isEmpty()) return@withContext ToolResult.ok(s.toolEmptyDirectory)

        val body = entries
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            .take(MAX_ENTRIES)
            .joinToString("\n") { entry ->
                if (entry.isDirectory) "${entry.name}/" else "${entry.name}  ${entry.length()}B"
            }
        ToolResult.ok(body)
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}

// -------------------------------------------------------------------- web

/** Searches the web. The model's only route to anything after its cut-off. */
class WebSearchTool(
    private val service: WebSearchService,
    private val languageTag: () -> String,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "web_search",
        summary = strings().toolWebSearchSummary,
        params = listOf(ToolParam("query", strings().toolQueryParam)),
        example = """<tool_call>{"name": "web_search", "arguments": {"query": "..."}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val s = strings()
        val query = call.arg("query") ?: call.arg("q") ?: call.arg("value")
            ?: return ToolResult.failed(s.toolMissingArg("query"))

        val outcome = runCatching {
            service.search(query, languageTag(), SearchDepth.QUICK)
        }.getOrNull() ?: return ToolResult.failed(s.toolSearchFailed)

        if (outcome.results.isEmpty()) {
            return ToolResult.failed(outcome.problem ?: s.searchNoResults)
        }
        return ToolResult.ok(SearchQuery.buildContext(query, outcome.results))
    }
}

// ------------------------------------------------------------------ facts

/**
 * The date and time.
 *
 * The single cheapest correction available. Asked what day it is, a model
 * answers with a date from its training data and sounds completely sure.
 */
class ClockTool(private val strings: () -> AppStrings) : AgentTool {

    override val spec = ToolSpec(
        name = "now",
        summary = strings().toolNowSummary,
        example = """<tool_call>{"name": "now", "arguments": {}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        // Fixed locale: the model reads this, and a localised month name is
        // one more thing for it to get wrong. The user never sees this string.
        val format = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss zzz", Locale.US)
        return ToolResult.ok(format.format(Date()))
    }
}

/** Arithmetic. See [Calc] for why this is not optional. */
class CalcTool(private val strings: () -> AppStrings) : AgentTool {

    override val spec = ToolSpec(
        name = "calc",
        summary = strings().toolCalcSummary,
        params = listOf(ToolParam("expression", strings().toolExpressionParam)),
        example = """<tool_call>{"name": "calc", "arguments": {"expression": "25 * 17"}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val s = strings()
        val expression = call.arg("expression") ?: call.arg("expr") ?: call.arg("value")
            ?: return ToolResult.failed(s.toolMissingArg("expression"))
        val value = Calc.eval(expression) ?: return ToolResult.failed(s.toolBadExpression(expression))
        return ToolResult.ok("$expression = ${Calc.format(value)}")
    }
}

/** What it is running on. Turns "your phone" from a guess into a fact. */
class DeviceTool(
    private val snapshot: () -> DeviceSnapshot,
    private val strings: () -> AppStrings,
) : AgentTool {

    override val spec = ToolSpec(
        name = "device_info",
        summary = strings().toolDeviceSummary,
        example = """<tool_call>{"name": "device_info", "arguments": {}}</tool_call>""",
    )

    override suspend fun run(call: ToolCall): ToolResult {
        val device = snapshot()
        val memory = device.memory
        return ToolResult.ok(
            buildString {
                appendLine("device: ${device.manufacturer} ${device.deviceModel}")
                appendLine("soc: ${device.socManufacturer} ${device.socModel}")
                appendLine("cpu cores: ${device.cpuCores}")
                appendLine("abis: ${device.supportedAbis.joinToString(", ")}")
                appendLine("ram total: ${memory.totalRamBytes / (1024 * 1024)} MB")
                appendLine("ram available: ${memory.availableRamBytes / (1024 * 1024)} MB")
                append("swap free: ${memory.swapFreeBytes / (1024 * 1024)} MB")
            }
        )
    }
}
