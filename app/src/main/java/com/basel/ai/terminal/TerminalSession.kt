package com.basel.ai.terminal

import com.basel.ai.agent.CommandPolicy
import com.basel.ai.core.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class TerminalLineKind {
    /** What was typed, echoed with the prompt. */
    COMMAND,
    OUTPUT,
    ERROR,

    /** The app talking, not the shell — a refusal, a note about a limit. */
    NOTE,

    /** A command the model ran, marked so it is never mistaken for the user's. */
    AGENT,
}

data class TerminalLine(
    val id: Long,
    val kind: TerminalLineKind,
    val text: String,
)

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = false,
    /** Shown in the prompt, relative to the workspace. */
    val prompt: String = "~",
    /** Commands typed this session, newest last, for the recall buttons. */
    val history: List<String> = emptyList(),
)

/**
 * The terminal page, and the same shell the model's `shell` tool uses.
 *
 * One shell, deliberately: if the model runs `cd logs && ls`, the user should
 * be able to open the terminal and be standing in the same directory, looking
 * at the same output. Two shells would make the page a demonstration of the
 * model's work rather than a window onto it.
 */
class TerminalSession(root: File) {

    val shell = Shell(root)

    private val _state = MutableStateFlow(TerminalUiState(prompt = promptFor()))
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    private var nextLineId = 0L

    /** Runs a command the user typed. */
    suspend fun submit(command: String) {
        val text = command.trim()
        if (text.isEmpty() || _state.value.isRunning) return

        val s = Localization.strings
        append(TerminalLineKind.COMMAND, "${_state.value.prompt} $text")
        _state.update {
            it.copy(
                isRunning = true,
                history = (it.history - text) + text,
            )
        }

        CommandPolicy.refusal(text, s)?.let { reason ->
            append(TerminalLineKind.NOTE, reason)
            _state.update { it.copy(isRunning = false) }
            return
        }

        val result = shell.run(text)
        emit(result)
        _state.update { it.copy(isRunning = false, prompt = promptFor()) }
    }

    /**
     * Records a command the model ran, so the page is a log of everything that
     * happened on this device and not only of what the user did.
     */
    fun recordAgentRun(command: String, result: ShellResult) {
        append(TerminalLineKind.AGENT, "${promptFor()} $command")
        emit(result)
        _state.update { it.copy(prompt = promptFor()) }
    }

    fun recordAgentRefusal(command: String, reason: String) {
        append(TerminalLineKind.AGENT, "${promptFor()} $command")
        append(TerminalLineKind.NOTE, reason)
    }

    fun clear() {
        _state.update { it.copy(lines = emptyList()) }
    }

    private fun emit(result: ShellResult) {
        val s = Localization.strings
        if (result.stdout.isNotBlank()) append(TerminalLineKind.OUTPUT, result.stdout)
        if (result.stderr.isNotBlank()) append(TerminalLineKind.ERROR, result.stderr)

        when {
            result.timedOut -> append(TerminalLineKind.NOTE, s.terminalTimedOut)
            // A silent failure reads as "nothing happened", which is the one
            // thing it did not do.
            result.exitCode != 0 && result.stderr.isBlank() && result.stdout.isBlank() ->
                append(TerminalLineKind.NOTE, s.terminalExitCode(result.exitCode))
            result.exitCode == Shell.LAUNCH_FAILED_EXIT_CODE ->
                append(TerminalLineKind.NOTE, s.terminalCouldNotStart)
        }
        if (result.truncated) append(TerminalLineKind.NOTE, s.terminalTruncated)
    }

    private fun append(kind: TerminalLineKind, text: String) {
        _state.update { state ->
            val line = TerminalLine(nextLineId++, kind, text)
            // The log is a scrollback, not a record: an old `find` is not worth
            // the memory once a thousand lines have gone by.
            val lines = (state.lines + line).takeLast(MAX_LINES)
            state.copy(lines = lines)
        }
    }

    private fun promptFor(): String {
        val relative = shell.workingDirectory.path.removePrefix(shell.root.path)
        return if (relative.isBlank()) "~" else "~$relative"
    }

    private companion object {
        const val MAX_LINES = 400
    }
}
