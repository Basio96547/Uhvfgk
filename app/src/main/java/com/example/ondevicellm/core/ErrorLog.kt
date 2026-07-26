package com.example.ondevicellm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Severity {
    /** Something worth knowing that didn't break anything. */
    INFO,

    /** A feature degraded — the app carried on with less. */
    WARNING,

    /** An operation failed and the user's request didn't complete. */
    ERROR,

    /** An uncaught exception killed the process. */
    CRASH,
    ;

    val label: String
        get() = when (this) {
            INFO -> "Info"
            WARNING -> "Warning"
            ERROR -> "Error"
            CRASH -> "Crash"
        }
}

data class AppError(
    val timeMillis: Long,
    val severity: Severity,
    /** Subsystem the failure came from, e.g. "Model", "Web search". */
    val area: String,
    val message: String,
    /** Stack trace or extra context; may be blank. */
    val detail: String = "",
) {
    val timestamp: String
        get() = TIME_FORMAT.format(Date(timeMillis))

    fun toJson(): JSONObject = JSONObject().apply {
        put("t", timeMillis)
        put("s", severity.name)
        put("a", area)
        put("m", message)
        put("d", detail)
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

        fun fromJson(json: JSONObject) = AppError(
            timeMillis = json.optLong("t"),
            severity = Severity.entries.firstOrNull { it.name == json.optString("s") }
                ?: Severity.ERROR,
            area = json.optString("a"),
            message = json.optString("m"),
            detail = json.optString("d"),
        )
    }
}

/**
 * In-app diagnostics.
 *
 * The app does a lot of work that can fail in ways the user can't see: a model
 * file that won't parse, a search provider that times out, a TTS vocabulary
 * that's malformed. Swallowing those makes the app look broken for no visible
 * reason. Everything notable is recorded here instead, surfaced in the UI, and
 * persisted so a failure that happened before the screen appeared — including a
 * crash — is still readable afterwards.
 *
 * Deliberately a singleton with no Android dependency beyond [init]: call sites
 * are deep in engines and parsers that have no business holding a Context.
 */
object ErrorLog {

    private const val MAX_ENTRIES = 200
    private const val FILE_NAME = "diagnostics.json"

    private val _entries = MutableStateFlow<List<AppError>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<AppError>> = _entries.asStateFlow()

    private val _unseenCount = MutableStateFlow(0)
    val unseenCount: StateFlow<Int> = _unseenCount.asStateFlow()

    @Volatile
    private var storeFile: File? = null

    /** Loads persisted entries. Safe to call more than once. */
    fun init(context: Context) {
        if (storeFile != null) return
        val file = File(context.filesDir, FILE_NAME)
        storeFile = file
        if (!file.exists()) return

        try {
            val array = JSONArray(file.readText())
            val loaded = buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(AppError.fromJson(it)) }
                }
            }
            _entries.value = loaded
            // Anything carried over from a previous run is by definition unseen.
            _unseenCount.value = loaded.count { it.severity >= Severity.ERROR }
        } catch (e: Exception) {
            // A corrupt log must never stop the app from starting.
            file.delete()
        }
    }

    fun report(
        area: String,
        message: String,
        throwable: Throwable? = null,
        severity: Severity = Severity.ERROR,
    ) {
        val entry = AppError(
            timeMillis = System.currentTimeMillis(),
            severity = severity,
            area = area,
            message = message.ifBlank { throwable?.message.orEmpty() }
                .ifBlank { "Unknown failure" },
            detail = throwable?.let(::stackTraceOf).orEmpty(),
        )
        add(entry)
    }

    /** Records an uncaught exception. Called from the crash handler. */
    fun reportCrash(throwable: Throwable, thread: String) {
        add(
            AppError(
                timeMillis = System.currentTimeMillis(),
                severity = Severity.CRASH,
                area = "Crash on $thread",
                message = "${throwable::class.java.simpleName}: " +
                    (throwable.message ?: "no message"),
                detail = stackTraceOf(throwable),
            )
        )
    }

    private fun add(entry: AppError) {
        synchronized(this) {
            _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            if (entry.severity >= Severity.ERROR) {
                _unseenCount.value = _unseenCount.value + 1
            }
            persist()
        }
    }

    /** Marks everything as read; the header badge clears. */
    fun markSeen() {
        _unseenCount.value = 0
    }

    fun clear() {
        synchronized(this) {
            _entries.value = emptyList()
            _unseenCount.value = 0
            storeFile?.let { runCatching { it.delete() } }
        }
    }

    /** Plain-text dump for sharing or pasting into a bug report. */
    fun exportText(): String = buildString {
        appendLine("Diagnostics — ${_entries.value.size} entries")
        appendLine()
        _entries.value.forEach { entry ->
            appendLine("[${entry.timestamp}] ${entry.severity.label} · ${entry.area}")
            appendLine(entry.message)
            if (entry.detail.isNotBlank()) appendLine(entry.detail)
            appendLine()
        }
    }

    /**
     * Writes synchronously: this also runs from the crash handler, where the
     * process is about to die and a background write would be lost.
     */
    private fun persist() {
        val file = storeFile ?: return
        try {
            val array = JSONArray()
            _entries.value.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
        } catch (e: Exception) {
            // Nothing useful to do — reporting a logging failure would recurse.
        }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        // Full traces are long and mostly framework frames.
        return writer.toString().lineSequence().take(25).joinToString("\n")
    }
}

/**
 * Runs [block], reporting any failure instead of discarding it.
 * Returns null on failure so call sites read like `runCatching{}.getOrNull()`.
 */
inline fun <T> reportingFailure(
    area: String,
    message: String,
    severity: Severity = Severity.WARNING,
    block: () -> T,
): T? = try {
    block()
} catch (e: Throwable) {
    ErrorLog.report(area, message, e, severity)
    null
}
