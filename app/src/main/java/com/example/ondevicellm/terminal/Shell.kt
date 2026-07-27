package com.example.ondevicellm.terminal

import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** What one command did. */
data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    /** True when output was cut at the cap. */
    val truncated: Boolean,
    /** True when the command was killed for running too long. */
    val timedOut: Boolean,
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut

    /** stdout, or stderr when there is nothing else to show. */
    val text: String
        get() = when {
            stdout.isNotBlank() -> stdout
            stderr.isNotBlank() -> stderr
            else -> ""
        }
}

/**
 * Runs shell commands as this app's own user.
 *
 * Real, with real limits, and the limits are worth stating plainly because
 * they are permanent rather than unfinished work:
 *
 *  - **No root.** Commands run as the app's uid inside its sandbox. `su` is
 *    not there, and `pm`, `settings` and most of `dumpsys` answer with
 *    permission denied. That is Android, not a gap here.
 *  - **A small toolbox.** Android ships one multi-call binary (toybox) rather
 *    than GNU coreutils, so flags differ from a desktop and there is no
 *    `bash`, `python`, `curl` or `git` unless the user installed one.
 *  - **No persistent shell.** Each command is its own process, so `cd` does
 *    not carry over. The working directory is a property of this object and is
 *    changed with [changeDirectory], which is why `cd` is handled here rather
 *    than passed through.
 *
 * Everything else — the pipes, redirects, globs, `&&` — is a real `sh`, so it
 * behaves the way it looks.
 */
class Shell(root: File) {

    /**
     * One command at a time.
     *
     * The terminal page and the model's `shell` tool share this object and both
     * dispatch on IO, so a `cd` from one could land between another's
     * `directory(workingDirectory)` and its `start()` — starting a process in
     * a directory nobody asked for, sometimes.
     */
    private val lock = Mutex()

    /** Where commands run. Starts at the workspace and moves with `cd`. */
    @Volatile
    var workingDirectory: File = root
        private set

    val root: File = root

    init {
        runCatching { root.mkdirs() }
    }

    /**
     * `cd` is stateful and a process is not, so it is interpreted here.
     * Returns null on success, or the reason it failed.
     */
    suspend fun changeDirectory(path: String): String? = lock.withLock {
        changeDirectoryLocked(path)
    }

    /** Caller already holds [lock]; Mutex is not reentrant. */
    private fun changeDirectoryLocked(path: String): String? {
        val target = when {
            path.isBlank() || path == "~" -> root
            path.startsWith("/") -> File(path)
            else -> File(workingDirectory, path)
        }
        val resolved = runCatching { target.canonicalFile }.getOrNull() ?: target
        if (!resolved.isDirectory) return "cd: ${resolved.path}: not a directory"
        if (!resolved.canRead()) return "cd: ${resolved.path}: permission denied"
        workingDirectory = resolved
        return null
    }

    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        withContext(Dispatchers.IO) {
            lock.withLock { runLocked(command, timeoutMs) }
        }

    private fun runLocked(command: String, timeoutMs: Long): ShellResult {
            val started = System.currentTimeMillis()

            // `cd` alone, or `cd x && rest`, has to be handled before exec or
            // it changes a directory in a process that is about to exit.
            val trimmed = command.trim()
            if (trimmed == "cd" || trimmed.startsWith("cd ")) {
                val problem = changeDirectoryLocked(trimmed.removePrefix("cd").trim())
                return ShellResult(
                    command = command,
                    exitCode = if (problem == null) 0 else 1,
                    stdout = if (problem == null) workingDirectory.path else "",
                    stderr = problem.orEmpty(),
                    durationMs = System.currentTimeMillis() - started,
                    truncated = false,
                    timedOut = false,
                )
            }

            var process: Process? = null
            try {
                process = ProcessBuilder(SHELL, "-c", command)
                    .directory(workingDirectory)
                    .redirectErrorStream(false)
                    .apply {
                        environment()["HOME"] = root.path
                        environment()["PWD"] = workingDirectory.path
                        environment()["TMPDIR"] = File(root, "tmp").also { it.mkdirs() }.path
                    }
                    .start()

                // Both streams have to be drained concurrently. Reading stdout
                // to the end first deadlocks the moment a command writes more
                // to stderr than the pipe buffer holds — which `find /` does
                // immediately, with a page of permission-denied lines.
                val out = StringBuilder()
                val err = StringBuilder()
                val outReader = drain(process.inputStream, out)
                val errReader = drain(process.errorStream, err)

                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) process.destroyForcibly()

                outReader.join(STREAM_JOIN_MS)
                errReader.join(STREAM_JOIN_MS)

                val truncated = out.length >= MAX_OUTPUT_CHARS || err.length >= MAX_OUTPUT_CHARS
                return ShellResult(
                    command = command,
                    exitCode = if (finished) process.exitValue() else TIMEOUT_EXIT_CODE,
                    stdout = out.toString().trimEnd(),
                    stderr = err.toString().trimEnd(),
                    durationMs = System.currentTimeMillis() - started,
                    truncated = truncated,
                    timedOut = !finished,
                )
            } catch (e: Throwable) {
                ErrorLog.report("Terminal", "Could not run: $command", e, Severity.WARNING)
                return ShellResult(
                    command = command,
                    exitCode = LAUNCH_FAILED_EXIT_CODE,
                    stdout = "",
                    stderr = e.message ?: e::class.java.simpleName,
                    durationMs = System.currentTimeMillis() - started,
                    truncated = false,
                    timedOut = false,
                )
            } finally {
                runCatching { process?.destroy() }
            }
    }

    /** Reads a stream on its own thread, stopping at the cap. */
    private fun drain(stream: java.io.InputStream, into: StringBuilder): Thread =
        Thread {
            runCatching {
                stream.bufferedReader().use { reader ->
                    val buffer = CharArray(8 * 1024)
                    while (into.length < MAX_OUTPUT_CHARS) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        synchronized(into) { into.append(buffer, 0, read) }
                    }
                }
            }
        }.apply { isDaemon = true; start() }

    companion object {
        const val SHELL = "/system/bin/sh"
        const val DEFAULT_TIMEOUT_MS = 15_000L

        /**
         * Output cap.
         *
         * Not about memory — about the context window. A `find /` writes
         * megabytes, and feeding even a fraction of that back to a 4B model
         * fills its whole context with directory names and leaves no room for
         * the answer.
         */
        const val MAX_OUTPUT_CHARS = 24_000

        /** How long to wait for the reader threads after the process ends. */
        private const val STREAM_JOIN_MS = 1_000L

        const val TIMEOUT_EXIT_CODE = 124
        const val LAUNCH_FAILED_EXIT_CODE = 127
    }
}
