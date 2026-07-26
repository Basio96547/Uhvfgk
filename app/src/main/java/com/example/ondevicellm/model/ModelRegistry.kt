package com.example.ondevicellm.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persistent list of models the user has added, stored as JSON in the app's
 * private files dir. Also knows the conventional adb-push locations so models
 * side-loaded with `adb push` can be discovered without a file picker.
 */
class ModelRegistry(private val context: Context) {

    private val storeFile: File get() = File(context.filesDir, STORE_NAME)

    /** Where the importer copies models that the user picks via the file picker. */
    val managedDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    private val _models = MutableStateFlow<List<ModelSpec>>(emptyList())
    val models: StateFlow<List<ModelSpec>> = _models.asStateFlow()

    private val _selectedTextModelId = MutableStateFlow<String?>(null)
    val selectedTextModelId: StateFlow<String?> = _selectedTextModelId.asStateFlow()

    private val _selectedAsrModelId = MutableStateFlow<String?>(null)
    val selectedAsrModelId: StateFlow<String?> = _selectedAsrModelId.asStateFlow()

    private val _selectedTtsModelId = MutableStateFlow<String?>(null)
    val selectedTtsModelId: StateFlow<String?> = _selectedTtsModelId.asStateFlow()

    init {
        load()
    }

    val selectedTextModel: ModelSpec?
        get() = _models.value.firstOrNull { it.id == _selectedTextModelId.value }

    val selectedTtsModel: ModelSpec?
        get() = _models.value.firstOrNull { it.id == _selectedTtsModelId.value }

    fun add(spec: ModelSpec) {
        _models.value = _models.value.filterNot { it.path == spec.path } + spec
        autoSelect(spec)
        persist()
    }

    fun update(spec: ModelSpec) {
        _models.value = _models.value.map { if (it.id == spec.id) spec else it }
        persist()
    }

    /** Removes a model; deletes the file too when the app owns it. */
    fun remove(spec: ModelSpec) {
        if (spec.managed) {
            runCatching { File(spec.path).delete() }
        }
        _models.value = _models.value.filterNot { it.id == spec.id }
        if (_selectedTextModelId.value == spec.id) {
            _selectedTextModelId.value = _models.value
                .firstOrNull { it.kind.isConversational }?.id
        }
        if (_selectedAsrModelId.value == spec.id) _selectedAsrModelId.value = null
        if (_selectedTtsModelId.value == spec.id) _selectedTtsModelId.value = null
        persist()
    }

    fun select(spec: ModelSpec) {
        when (spec.kind) {
            ModelKind.ASR -> _selectedAsrModelId.value = spec.id
            ModelKind.TTS -> _selectedTtsModelId.value = spec.id
            else -> _selectedTextModelId.value = spec.id
        }
        persist()
    }

    private fun autoSelect(spec: ModelSpec) {
        when (spec.kind) {
            ModelKind.ASR -> if (_selectedAsrModelId.value == null) {
                _selectedAsrModelId.value = spec.id
            }
            ModelKind.TTS -> if (_selectedTtsModelId.value == null) {
                _selectedTtsModelId.value = spec.id
            }
            else -> if (_selectedTextModelId.value == null) {
                _selectedTextModelId.value = spec.id
            }
        }
    }

    /**
     * Looks for `.task` / `.bin` bundles in the well-known side-load
     * directories and registers any that are not already known.
     *
     * @return the specs that were newly added.
     */
    fun scanSideloadedModels(): List<ModelSpec> {
        val known = _models.value.map { it.path }.toSet()
        val found = mutableListOf<ModelSpec>()

        for (dir in SCAN_DIRS.map(::File) + managedDir) {
            val files = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (file in files) {
                if (!file.isFile || file.path in known) continue
                if (MODEL_EXTENSIONS.none { file.name.endsWith(it, ignoreCase = true) }) continue

                found += ModelSpec(
                    id = UUID.randomUUID().toString(),
                    displayName = file.nameWithoutExtension,
                    path = file.path,
                    kind = guessKind(file.name),
                    supportsThinking = guessThinking(file.name),
                    managed = file.parentFile == managedDir,
                    sizeBytes = file.length(),
                )
            }
        }

        found.forEach(::add)
        return found
    }

    /** Drops entries whose backing file has disappeared. */
    fun pruneMissing(): Int {
        val before = _models.value.size
        _models.value = _models.value.filter { File(it.path).exists() }
        if (_models.value.size != before) persist()
        return before - _models.value.size
    }

    private fun load() {
        val file = storeFile
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("models") ?: JSONArray()
            val list = buildList {
                for (i in 0 until array.length()) {
                    runCatching { ModelSpec.fromJson(array.getJSONObject(i)) }
                        .getOrNull()?.let(::add)
                }
            }
            _models.value = list
            _selectedTextModelId.value = root.optString("selectedTextModelId").ifBlank { null }
            // "selectedAudioModelId" is the pre-TTS key name for the ASR slot.
            _selectedAsrModelId.value = root.optString("selectedAsrModelId")
                .ifBlank { root.optString("selectedAudioModelId") }
                .ifBlank { null }
            _selectedTtsModelId.value = root.optString("selectedTtsModelId").ifBlank { null }
        }
    }

    private fun persist() {
        runCatching {
            val root = JSONObject().apply {
                put("models", JSONArray().apply {
                    _models.value.forEach { put(it.toJson()) }
                })
                put("selectedTextModelId", _selectedTextModelId.value ?: "")
                put("selectedAsrModelId", _selectedAsrModelId.value ?: "")
                put("selectedTtsModelId", _selectedTtsModelId.value ?: "")
            }
            storeFile.writeText(root.toString())
        }
    }

    private companion object {
        const val STORE_NAME = "models.json"

        val MODEL_EXTENSIONS = listOf(".task", ".gguf", ".litertlm", ".tflite", ".bin")

        /**
         * `/data/local/tmp/llm` is the path used by Google's official MediaPipe
         * samples — writable via adb without root, and readable by the app.
         */
        val SCAN_DIRS = listOf(
            "/data/local/tmp/llm",
            "/sdcard/Download",
            "/storage/emulated/0/Download",
        )

        fun guessKind(name: String) = ModelHeuristics.guessKind(name)

        fun guessThinking(name: String) = ModelHeuristics.guessThinking(name)
    }
}
