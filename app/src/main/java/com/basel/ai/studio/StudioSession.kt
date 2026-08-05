package com.basel.ai.studio

import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Localization
import com.basel.ai.llm.TextEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StudioUiState(
    val projectId: String? = null,
    val name: String = "",
    /** The code that has been committed — what the preview renders. */
    val code: String = "",
    /** Raw model output while a build is in flight, so progress is visible. */
    val streaming: String = "",
    val isBuilding: Boolean = false,
    val showCode: Boolean = false,
    val notice: String? = null,
    /** Bumped on each successful build so the preview reloads exactly once. */
    val previewRevision: Int = 0,
)

/**
 * Drives one Studio project against the loaded model.
 *
 * Kept out of `ChatViewModel` so neither grows unreadable, but deliberately
 * *not* given its own engine: there is one model in memory and generation has
 * to stay serialised, so the caller passes the engine in and owns the busy
 * flag.
 */
class StudioSession(
    private val store: StudioStore,
    private val newId: () -> String,
    private val now: () -> Long,
) {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    val projects: StateFlow<List<StudioProject>> get() = store.projects

    fun toggleView() = _state.update { it.copy(showCode = !it.showCode) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** Hand-editing the code is a first-class path, not a fallback. */
    fun editCode(code: String) = _state.update { it.copy(code = code) }

    /** Re-renders the preview from whatever the code currently is. */
    fun run() = _state.update {
        it.copy(showCode = false, previewRevision = it.previewRevision + 1)
    }

    fun newProject() {
        _state.value = StudioUiState()
    }

    fun open(project: StudioProject) {
        _state.value = StudioUiState(
            projectId = project.id,
            name = project.name,
            code = project.code,
            previewRevision = 1,
        )
    }

    fun delete(id: String) {
        store.remove(id)
        if (_state.value.projectId == id) newProject()
    }

    fun save() {
        val current = _state.value
        if (current.code.isBlank()) return
        val strings = Localization.strings
        val name = current.name.ifBlank {
            CodeExtractor.titleOf(current.code).ifBlank { strings.studioUntitled }
        }
        val project = StudioProject(
            id = current.projectId ?: newId(),
            name = name,
            code = current.code,
            updatedAt = now(),
        )
        store.save(project)
        _state.update {
            it.copy(projectId = project.id, name = name, notice = strings.studioSaved)
        }
    }

    /**
     * Sends [request] to the model and replaces the code with what comes back.
     *
     * The session is reset first: a Studio turn is stateless, because the file
     * already carries every previous instruction. Threading it as a chat would
     * put three copies of the page in the context after two edits.
     */
    fun build(engine: TextEngine, request: String, onFinished: () -> Unit) {
        val strings = Localization.strings
        val current = _state.value
        if (request.isBlank() || current.isBuilding) {
            onFinished()
            return
        }
        if (StudioPrompt.isTooLargeToEdit(current.code)) {
            _state.update { it.copy(notice = strings.studioTooLarge) }
            onFinished()
            return
        }

        _state.update { it.copy(isBuilding = true, streaming = "", notice = null) }
        engine.resetSession()

        val builder = StringBuilder()
        try {
            engine.generate(
                prompt = StudioPrompt.build(current.code, request, strings),
                systemPrompt = strings.studioSystemPrompt,
                // Code, not deliberation. A chain of thought here spends the
                // whole budget before a single tag is written.
                thinkingEnabled = false,
                maxTokens = BUILD_TOKENS,
            ) { _, answer, done ->
                if (answer.isNotEmpty()) {
                    builder.append(answer)
                    _state.update { it.copy(streaming = builder.toString()) }
                }
                if (done) {
                    finish(builder.toString())
                    onFinished()
                }
            }
        } catch (e: Throwable) {
            ErrorLog.report("Studio", "Build failed", e)
            _state.update {
                it.copy(isBuilding = false, streaming = "", notice = e.message)
            }
            onFinished()
        }
    }

    private fun finish(raw: String) {
        val strings = Localization.strings
        val extracted = CodeExtractor.extract(raw)
        if (extracted.isBlank()) {
            _state.update {
                it.copy(isBuilding = false, streaming = "", notice = strings.studioNoCodeBack)
            }
            return
        }

        val document = CodeExtractor.asDocument(extracted)
        _state.update {
            it.copy(
                code = document,
                name = it.name.ifBlank { CodeExtractor.titleOf(document) },
                streaming = "",
                isBuilding = false,
                showCode = false,
                previewRevision = it.previewRevision + 1,
            )
        }
    }

    private companion object {
        /**
         * A page is long. This is well above a chat reply's budget and is the
         * reason Studio turns are stateless — the context has to hold the old
         * file *and* the new one.
         */
        const val BUILD_TOKENS = 3072
    }
}
