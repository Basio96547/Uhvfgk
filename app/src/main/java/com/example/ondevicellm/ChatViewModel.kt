package com.example.ondevicellm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ondevicellm.audio.AudioPlayer
import com.example.ondevicellm.audio.ModelTtsSynthesizer
import com.example.ondevicellm.audio.SpeechInput
import com.example.ondevicellm.audio.SpeechOptions
import com.example.ondevicellm.audio.SpeechSynthesizer
import com.example.ondevicellm.audio.SynthesisResult
import com.example.ondevicellm.audio.SystemTtsSynthesizer
import com.example.ondevicellm.audio.WavWriter
import com.example.ondevicellm.core.AppSettings
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.MemorySnapshot
import com.example.ondevicellm.core.SettingsStore
import com.example.ondevicellm.core.TtsEngine
import com.example.ondevicellm.llm.EngineFactory
import com.example.ondevicellm.llm.ResolvedBackend
import com.example.ondevicellm.llm.TextEngine
import com.example.ondevicellm.model.ModelImporter
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
    /** Id of the message currently being spoken, if any. */
    val speakingMessageId: Long? = null,
    val isSynthesizing: Boolean = false,
    val importState: ImportState? = null,
    val notice: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val registry = ModelRegistry(app)
    private val importer = ModelImporter(app, registry)
    private val settingsStore = SettingsStore(app)
    private val speech = SpeechInput(app)
    private val audioPlayer = AudioPlayer()
    private val systemTts = SystemTtsSynthesizer(app)
    private var modelTts: ModelTtsSynthesizer? = null

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val models: StateFlow<List<ModelSpec>> = registry.models

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _device = MutableStateFlow(DeviceCapabilities.snapshot(app))
    val device: StateFlow<DeviceSnapshot> = _device.asStateFlow()

    private val _memory = MutableStateFlow(DeviceCapabilities.readMemory(app))
    val memory: StateFlow<MemorySnapshot> = _memory.asStateFlow()

    private var engine: TextEngine? = null
    private var loadJob: Job? = null
    private var importJob: Job? = null
    private var speakJob: Job? = null
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
                val loaded = EngineFactory.load(getApplication<Application>(), spec, _device.value)
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
                if (spec.kind.isConversational && registry.selectedTextModel?.id == spec.id) {
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
        if (done) {
            refreshMemory()
            if (settingsStore.settings.value.autoSpeakReplies) {
                val reply = _uiState.value.messages.firstOrNull { it.id == id }
                if (reply != null && reply.text.isNotBlank()) speak(id, reply.text)
            }
        }
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

    // --------------------------------------------------------- speech output

    /**
     * Speaks [message]'s text. Tapping the same message again stops playback.
     */
    fun toggleSpeak(messageId: Long) {
        if (_uiState.value.speakingMessageId == messageId) {
            stopSpeaking()
            return
        }

        val text = _uiState.value.messages
            .firstOrNull { it.id == messageId }
            ?.text
            ?.takeIf { it.isNotBlank() }
            ?: return

        speak(messageId, text)
    }

    private fun speak(messageId: Long, text: String) {
        speakJob?.cancel()
        stopSpeaking()

        speakJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(speakingMessageId = messageId, isSynthesizing = true)
            }

            val synthesizer = resolveSynthesizer()
            if (synthesizer == null) {
                _uiState.update {
                    it.copy(
                        speakingMessageId = null,
                        isSynthesizing = false,
                        notice = speechUnavailableReason(),
                    )
                }
                return@launch
            }

            val current = settingsStore.settings.value
            val options = SpeechOptions(
                languageTag = current.voiceLanguageTag,
                speakingRate = current.speakingRate,
                pitch = current.pitch,
                speakerId = registry.selectedTtsModel?.ttsSpeakerId ?: 0,
            )

            when (val result = synthesizer.speak(text, options)) {
                is SynthesisResult.Pcm -> {
                    _uiState.update { it.copy(isSynthesizing = false) }
                    runCatching { audioPlayer.play(result.samples, result.sampleRateHz) }
                        .onFailure { error ->
                            _uiState.update { it.copy(notice = error.message) }
                        }
                }

                is SynthesisResult.PlayedDirectly -> Unit

                is SynthesisResult.Failed -> _uiState.update {
                    it.copy(notice = result.message)
                }
            }

            _uiState.update { it.copy(speakingMessageId = null, isSynthesizing = false) }
        }
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        audioPlayer.stop()
        systemTts.stop()
        modelTts?.stop()
        _uiState.update { it.copy(speakingMessageId = null, isSynthesizing = false) }
    }

    /**
     * Renders a reply to a WAV file in the app's external files dir so it can be
     * shared or inspected.
     */
    fun saveMessageAudio(messageId: Long) {
        val text = _uiState.value.messages
            .firstOrNull { it.id == messageId }
            ?.text
            ?.takeIf { it.isNotBlank() }
            ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val dir = java.io.File(app.getExternalFilesDir(null), "speech").apply { mkdirs() }
            val target = java.io.File(dir, "reply-$messageId.wav")

            val current = settingsStore.settings.value
            val options = SpeechOptions(
                languageTag = current.voiceLanguageTag,
                speakingRate = current.speakingRate,
                pitch = current.pitch,
                speakerId = registry.selectedTtsModel?.ttsSpeakerId ?: 0,
            )

            val saved = when (current.ttsEngine) {
                TtsEngine.SYSTEM -> systemTts.synthesizeToFile(text, options, target)
                TtsEngine.MODEL -> {
                    when (val result = resolveSynthesizer()?.speak(text, options)) {
                        is SynthesisResult.Pcm -> {
                            WavWriter.write(target, result.samples, result.sampleRateHz)
                            true
                        }
                        else -> false
                    }
                }
            }

            _uiState.update {
                it.copy(
                    notice = if (saved) "Saved to ${target.absolutePath}"
                    else "Could not save audio."
                )
            }
        }
    }

    /**
     * Returns the synthesizer for the current settings, loading the user's TTS
     * model on demand. Null when MODEL is selected but no TTS model is set.
     */
    private suspend fun resolveSynthesizer(): SpeechSynthesizer? {
        return when (settingsStore.settings.value.ttsEngine) {
            TtsEngine.SYSTEM -> systemTts.takeIf { it.prepare() }

            TtsEngine.MODEL -> {
                if (!ModelTtsSynthesizer.isRuntimeAvailable()) return null
                val spec = registry.selectedTtsModel ?: return null
                // Reuse the loaded interpreter unless the user switched models
                // or changed settings that affect synthesis.
                val existing = modelTts
                if (existing != null && existing.spec == spec) {
                    return existing.takeIf { it.prepare() }
                }
                existing?.release()
                val created = ModelTtsSynthesizer(spec)
                modelTts = created
                if (created.prepare()) created else null
            }
        }
    }

    /** True when speech output can work right now with the current settings. */
    val canSpeak: Boolean
        get() = when (settingsStore.settings.value.ttsEngine) {
            TtsEngine.SYSTEM -> true
            TtsEngine.MODEL -> ModelTtsSynthesizer.isRuntimeAvailable() &&
                registry.selectedTtsModel != null
        }

    /** True when custom TTS models can run in this build. See ModelTtsSynthesizer. */
    val ttsRuntimeAvailable: Boolean get() = ModelTtsSynthesizer.isRuntimeAvailable()

    /** Explains, in the user's terms, why speech output is unavailable. */
    fun speechUnavailableReason(): String = when {
        settingsStore.settings.value.ttsEngine == TtsEngine.SYSTEM ->
            "The system text-to-speech engine is unavailable. Install or enable a " +
                "TTS engine in system settings."

        !ModelTtsSynthesizer.isRuntimeAvailable() ->
            ModelTtsSynthesizer.RUNTIME_MISSING_MESSAGE

        registry.selectedTtsModel == null ->
            "No text-to-speech model selected. Add one on the Models screen and " +
                "set its type to \"Text → Speech\"."

        else -> "Could not load the selected text-to-speech model."
    }

    // -------------------------------------------------------------- settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun refreshMemory() {
        _memory.value = DeviceCapabilities.readMemory(getApplication<Application>())
    }

    fun refreshDevice() {
        _device.value = DeviceCapabilities.snapshot(getApplication<Application>())
        refreshMemory()
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        speech.release()
        audioPlayer.release()
        systemTts.release()
        modelTts?.release()
        modelTts = null
        engine?.close()
        engine = null
    }
}
