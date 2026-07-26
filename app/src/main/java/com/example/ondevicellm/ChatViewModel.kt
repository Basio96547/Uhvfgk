package com.example.ondevicellm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ondevicellm.audio.SpeechInput
import com.example.ondevicellm.core.AppSettings
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.MemorySnapshot
import com.example.ondevicellm.core.SettingsStore
import com.example.ondevicellm.llm.InferenceEngine
import com.example.ondevicellm.llm.ResolvedBackend
import com.example.ondevicellm.model.ModelImporter
import com.example.ondevicellm.model.ModelKind
import com.example.ondevicellm.model.ModelRegistry
import com.example.ondevicellm.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Author { USER, MODEL }

data class ChatMessage(
    val id: Long,
    val author: Author,
    val text: String,
    val thinking: String = "",
    val isGenerating: Boolean = false,
    val thinkingExpanded: Boolean = false,
)

enum class ModelStatus { NONE, LOADING, READY, ERROR }

data class ImportState(val fileName: String, val fraction: Float)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: ModelStatus = ModelStatus.NONE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val activeModel: ModelSpec? = null,
    val backend: ResolvedBackend? = null,
    val isListening: Boolean = false,
    val voiceDraft: String = "",
    val importState: ImportState? = null,
    val notice: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val registry = ModelRegistry(app)
    private val importer = ModelImporter(app, registry)
    private val settingsStore = SettingsStore(app)
    private val speech = SpeechInput(app)

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val models: StateFlow<List<ModelSpec>> = registry.models

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _device = MutableStateFlow(DeviceCapabilities.snapshot(app))
    val device: StateFlow<DeviceSnapshot> = _device.asStateFlow()

    private val _memory = MutableStateFlow(DeviceCapabilities.readMemory(app))
    val memory: StateFlow<MemorySnapshot> = _memory.asStateFlow()

    private var engine: InferenceEngine? = null
    private var loadJob: Job? = null
    private var importJob: Job? = null
    private var nextId = 0L

    val speechAvailable: Boolean get() = speech.isAvailable
    val speechOnDevice: Boolean get() = speech.supportsOnDevice

    init {
        // Pick up anything already side-loaded so first run isn't empty.
        viewModelScope.launch(Dispatchers.IO) {
            registry.pruneMissing()
            registry.scanSideloadedModels()
            registry.selectedTextModel?.let { loadModel(it) }
        }
    }

    // ---------------------------------------------------------------- models

    fun loadModel(spec: ModelSpec) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(status = ModelStatus.LOADING, errorMessage = null, activeModel = spec)
            }
            engine?.close()
            engine = null

            try {
                val loaded = InferenceEngine.load(getApplication(), spec, _device.value)
                engine = loaded
                registry.select(spec)
                _uiState.update {
                    it.copy(
                        status = ModelStatus.READY,
                        activeModel = spec,
                        backend = loaded.backend,
                        errorMessage = null,
                        messages = emptyList(),
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        status = ModelStatus.ERROR,
                        errorMessage = e.message ?: "Failed to load the model.",
                    )
                }
            }
            refreshMemory()
        }
    }

    fun retryLoad() {
        _uiState.value.activeModel?.let(::loadModel)
    }

    fun importModel(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val spec = importer.import(uri) { progress ->
                    _uiState.update {
                        it.copy(
                            importState = ImportState(
                                fileName = "Copying model…",
                                fraction = progress.fraction,
                            )
                        )
                    }
                }
                _uiState.update {
                    it.copy(importState = null, notice = "Added \"${spec.displayName}\".")
                }
                if (spec.kind != ModelKind.AUDIO && registry.selectedTextModel?.id == spec.id) {
                    loadModel(spec)
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        importState = null,
                        errorMessage = e.message ?: "Import failed.",
                    )
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        _uiState.update { it.copy(importState = null, notice = "Import cancelled.") }
    }

    /** Registers an already-on-disk model (e.g. `adb push`ed) without copying it. */
    fun registerModelPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val spec = importer.registerInPlace(path.trim())
                _uiState.update { it.copy(notice = "Added \"${spec.displayName}\".") }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Could not register that path.")
                }
            }
        }
    }

    fun scanForModels() {
        viewModelScope.launch(Dispatchers.IO) {
            registry.pruneMissing()
            val found = registry.scanSideloadedModels()
            _uiState.update {
                it.copy(
                    notice = if (found.isEmpty()) {
                        "No new models found in /data/local/tmp/llm or Downloads."
                    } else {
                        "Found ${found.size} model(s)."
                    }
                )
            }
        }
    }

    fun updateModel(spec: ModelSpec) {
        registry.update(spec)
        if (_uiState.value.activeModel?.id == spec.id) {
            _uiState.update { it.copy(activeModel = spec) }
        }
    }

    fun removeModel(spec: ModelSpec) {
        if (_uiState.value.activeModel?.id == spec.id) {
            engine?.close()
            engine = null
            _uiState.update {
                it.copy(status = ModelStatus.NONE, activeModel = null, backend = null)
            }
        }
        registry.remove(spec)
    }

    // ------------------------------------------------------------------ chat

    fun sendMessage(text: String) {
        val prompt = text.trim()
        val active = engine
        if (prompt.isEmpty() || active == null || _uiState.value.isBusy) return

        val userMessage = ChatMessage(nextId++, Author.USER, prompt)
        val replyId = nextId++
        val placeholder = ChatMessage(replyId, Author.MODEL, "", isGenerating = true)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + placeholder,
                isBusy = true,
                voiceDraft = "",
            )
        }

        val current = settingsStore.settings.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                active.generate(
                    prompt = prompt,
                    systemPrompt = current.systemPrompt,
                    thinkingEnabled = current.thinkingEnabled,
                ) { thinking, answer, done ->
                    appendDelta(replyId, thinking, answer, done)
                }
            } catch (e: Throwable) {
                appendDelta(replyId, "", "\n[error: ${e.message}]", done = true)
            }
        }
    }

    private fun appendDelta(id: Long, thinking: String, answer: String, done: Boolean) {
        val budget = _uiState.value.activeModel?.thinkingBudgetChars ?: 0
        _uiState.update { state ->
            val messages = state.messages.map { message ->
                if (message.id != id) return@map message
                val newThinking = if (thinking.isEmpty()) {
                    message.thinking
                } else {
                    val combined = message.thinking + thinking
                    if (budget > 0 && combined.length > budget) combined.take(budget) else combined
                }
                message.copy(
                    thinking = newThinking,
                    text = message.text + answer,
                    isGenerating = !done,
                )
            }
            state.copy(messages = messages, isBusy = if (done) false else state.isBusy)
        }
        if (done) refreshMemory()
    }

    fun toggleThinkingExpanded(id: Long) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(thinkingExpanded = !it.thinkingExpanded) else it
                }
            )
        }
    }

    fun clearConversation() {
        if (_uiState.value.isBusy) return
        engine?.resetSession()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    // ----------------------------------------------------------------- voice

    fun startListening() {
        if (_uiState.value.isListening) return
        _uiState.update { it.copy(isListening = true, voiceDraft = "") }

        speech.start(settingsStore.settings.value.voiceLanguageTag, object : SpeechInput.Callbacks {
            override fun onPartial(text: String) {
                _uiState.update { it.copy(voiceDraft = text) }
            }

            override fun onFinal(text: String) {
                _uiState.update { it.copy(isListening = false, voiceDraft = text) }
            }

            override fun onError(message: String) {
                _uiState.update {
                    it.copy(isListening = false, notice = message)
                }
            }

            override fun onEndOfSpeech() {
                _uiState.update { it.copy(isListening = false) }
            }
        })
    }

    fun stopListening() {
        speech.stop()
        _uiState.update { it.copy(isListening = false) }
    }

    // -------------------------------------------------------------- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun refreshMemory() {
        _memory.value = DeviceCapabilities.readMemory(getApplication())
    }

    fun refreshDevice() {
        _device.value = DeviceCapabilities.snapshot(getApplication())
        refreshMemory()
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        speech.release()
        engine?.close()
        engine = null
    }
}
