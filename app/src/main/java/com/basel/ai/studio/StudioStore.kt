package com.basel.ai.studio

import android.content.Context
import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One saved page. The HTML *is* the project — there is nothing else to keep. */
data class StudioProject(
    val id: String,
    val name: String,
    val code: String,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("code", code)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): StudioProject? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            return StudioProject(
                id = id,
                name = json.optString("name"),
                code = json.optString("code"),
                updatedAt = json.optLong("updatedAt"),
            )
        }
    }
}

/**
 * Saved Studio projects, in one JSON file in app storage.
 *
 * A file rather than SharedPreferences because a page runs to tens of
 * kilobytes and preferences are loaded into memory whole; and one file rather
 * than one per project because the list is short and an atomic rewrite is
 * simpler to reason about than a directory that can half-fail.
 */
class StudioStore(context: Context) {

    private val file = File(context.filesDir, "studio-projects.json")

    private val _projects = MutableStateFlow(read())
    val projects: StateFlow<List<StudioProject>> = _projects.asStateFlow()

    private fun read(): List<StudioProject> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.let(StudioProject::fromJson) }
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            ErrorLog.report("Studio", "Saved projects are unreadable", e, Severity.WARNING)
            emptyList()
        }
    }

    private fun write(projects: List<StudioProject>) {
        try {
            val array = JSONArray()
            projects.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
        } catch (e: Exception) {
            ErrorLog.report("Studio", "Could not save projects", e)
        }
    }

    /** Inserts or replaces by id, newest first. */
    fun save(project: StudioProject) {
        val updated = (listOf(project) + _projects.value.filterNot { it.id == project.id })
            .sortedByDescending { it.updatedAt }
        _projects.value = updated
        write(updated)
    }

    fun remove(id: String) {
        val updated = _projects.value.filterNot { it.id == id }
        _projects.value = updated
        write(updated)
    }
}
