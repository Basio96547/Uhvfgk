package com.example.ondevicellm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ondevicellm.agent.AgentTool
import com.example.ondevicellm.agent.CalcTool
import com.example.ondevicellm.agent.ClockTool
import com.example.ondevicellm.agent.CommandRisk
import com.example.ondevicellm.agent.DeviceTool
import com.example.ondevicellm.agent.ListFilesTool
import com.example.ondevicellm.agent.ReadFileTool
import com.example.ondevicellm.agent.ShellTool
import com.example.ondevicellm.agent.ToolCallParser
import com.example.ondevicellm.agent.ToolPrompt
import com.example.ondevicellm.agent.ToolRegistry
import com.example.ondevicellm.agent.ToolResult
import com.example.ondevicellm.agent.WebSearchTool
import com.example.ondevicellm.agent.WriteFileTool
import com.example.ondevicellm.audio.AudioPlayer
import com.example.ondevicellm.audio.CloudTts
import com.example.ondevicellm.audio.CloudTtsSynthesizer
import com.example.ondevicellm.audio.ModelTtsSynthesizer
import com.example.ondevicellm.audio.SpeechInput
import com.example.ondevicellm.audio.SpeechOptions
import com.example.ondevicellm.audio.SpeechSynthesizer
import com.example.ondevicellm.audio.SynthesisResult
import com.example.ondevicellm.audio.SystemTtsSynthesizer
import com.example.ondevicellm.audio.WavWriter
import com.example.ondevicellm.core.AppSettings
import com.example.ondevicellm.core.DeviceCapabilities
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Localization
import com.example.ondevicellm.core.Severity
import com.example.ondevicellm.core.DeviceSnapshot
import com.example.ondevicellm.core.MemorySnapshot
import com.example.ondevicellm.core.SettingsStore
import com.example.ondevicellm.core.ThermalGuard
import com.example.ondevicellm.core.ThermalLevel
import com.example.ondevicellm.core.TtsEngine
import com.example.ondevicellm.llm.EngineFactory
import com.example.ondevicellm.llm.LlamaCppEngine
import com.example.ondevicellm.llm.QueryRouter
import com.example.ondevicellm.llm.RoutingDecision
import com.example.ondevicellm.llm.ResolvedBackend
import com.example.ondevicellm.llm.RoutingMode
import com.example.ondevicellm.llm.TextEngine
import com.example.ondevicellm.model.ModelImporter
import com.example.ondevicellm.model.ModelRegistry
import com.example.ondevicellm.model.ModelSpec
import com.example.ondevicellm.studio.StudioProject
import com.example.ondevicellm.studio.StudioSession
import com.example.ondevicellm.studio.StudioStore
import com.example.ondevicellm.studio.StudioUiState
import com.example.ondevicellm.terminal.TerminalSession
import com.example.ondevicellm.terminal.TerminalUiState
import com.example.ondevicellm.web.SearchQuery
import com.example.ondevicellm.web.SearchSource
import com.example.ondevicellm.web.SearchDepth
import com.example.ondevicellm.web.WebSearchService
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
    /** Pages the answer was grounded in, when web search ran. */
    val sources: List<SearchSource> = emptyList(),
    /**
     * How this reply was routed. Kept as the decision rather than a sentence
     * so the UI can word it in the reader's language.
     */
    val routing: RoutingDecision? = null,
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
    /** Non-null while a web search is running, for the status line. */
    val searchStatus: String? = null,
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val registry = ModelRegistry(app)
    private val importer = ModelImporter(app, registry)
    private val settingsStore = SettingsStore(app)
    private val speech = SpeechInput(app)
    private val thermalGuard = ThermalGuard(app)
    private val webSearch = WebSearchService()
    private val audioPlayer = AudioPlayer()
    private val systemTts = SystemTtsSynthesizer(app)

    /**
     * One shell for the whole app.
     *
     * The Terminal page and the model's `shell` tool share it on purpose: if
     * the model runs `cd logs`, opening the terminal should put you in that
     * directory, looking at the output it saw.
     */
    private val terminal = TerminalSession(java.io.File(app.filesDir, "workspace"))
    val terminalState: StateFlow<TerminalUiState> = terminal.state
    private var modelTts: ModelTtsSynthesizer? = null

    // One model in memory means generation has to stay serialised, so Studio
    // borrows the engine from here rather than owning one of its own.
    private val studioStore = StudioStore(app)
    private val studio = StudioSession(
        store = studioStore,
        newId = { java.util.UUID.randomUUID().toString() },
        now = { System.currentTimeMillis() },
    )
    val studioState: StateFlow<StudioUiState> = studio.state
    val studioProjects: StateFlow<List<StudioProject>> get() = studio.projects

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
    /** Bumped per load request; a load that finishes stale discards itself. */
    private var loadGeneration = 0

    /**
     * Bumped whenever the current reply stops being the current reply — the
     * user pressed Stop, cleared the chat, or swapped the model.
     *
     * `TextEngine.stop()` is best-effort, and for MediaPipe it is documented as
     * doing nothing at all: there is no cancel API for a generation in flight.
     * Without this, pressing Stop set `isGenerating = false` and the very next
     * token set it back to true and carried on appending text the user had
     * explicitly stopped — and because `isBusy` was already false they could
     * send again, running two generations over one non-thread-safe session.
     */
    private var replyGeneration = 0
    private var importJob: Job? = null
    private var speakJob: Job? = null
    private var nextId = 0L

    /**
     * The system prompt the current conversation was started with.
     *
     * The engines inject it once, on the first turn, so editing it mid-chat
     * would otherwise do nothing at all. Changing it restarts the session,
     * which is what "takes effect on the next message" has to mean.
     */
    private var activeSystemPrompt: String? = null

    val speechAvailable: Boolean get() = speech.isAvailable
    val speechOnDevice: Boolean get() = speech.supportsOnDevice

    val thermalSupported: Boolean get() = thermalGuard.isSupported

    init {
        thermalGuard.start()
        // Retune the running engine as the device heats up, and surface it.
        viewModelScope.launch {
            thermalGuard.level.collect { level ->
                _uiState.update { it.copy(thermalLevel = level) }
                (engine as? LlamaCppEngine)?.applyThermalLevel(level)
                if (ThermalGuard.shouldPause(level) && _uiState.value.isBusy) {
                    engine?.stop()
                    _uiState.update {
                        it.copy(
                            notice = Localization.strings.thermalPaused,
                        )
                    }
                }
            }
        }

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
        // Cancelling is not enough. EngineFactory.load blocks with no
        // suspension point, so a cancelled load runs to completion anyway and
        // then assigns itself over the newer one — reverting the UI to the
        // model the user had already moved on from and leaking the newer
        // engine's native memory, because nothing was left holding it.
        val generation = ++loadGeneration
        // The engine under the current reply is about to be closed; anything it
        // still emits belongs to a model that no longer exists.
        replyGeneration++
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    status = ModelStatus.LOADING,
                    errorMessage = null,
                    activeModel = spec,
                    isBusy = false,
                )
            }
            engine?.close()
            engine = null
            activeSystemPrompt = null

            try {
                val loaded = EngineFactory.load(getApplication<Application>(), spec, _device.value)
                if (generation != loadGeneration) {
                    // A newer load has taken over. Close what we just opened;
                    // it is gigabytes of mapped model and nothing else will.
                    runCatching { loaded.close() }
                    return@launch
                }
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
                if (generation != loadGeneration) return@launch
                ErrorLog.report("Model", "Failed to load \"${spec.displayName}\"", e)
                _uiState.update {
                    it.copy(
                        status = ModelStatus.ERROR,
                        errorMessage = e.message ?: Localization.strings.modelLoadFailedFallback,
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
                                fileName = Localization.strings.copyingModel,
                                fraction = progress.fraction,
                            )
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        importState = null,
                        notice = Localization.strings.addedModel(spec.displayName),
                    )
                }
                if (spec.kind.isConversational && registry.selectedTextModel?.id == spec.id) {
                    loadModel(spec)
                }
            } catch (e: Throwable) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    ErrorLog.report("Model import", "Import failed", e)
                }
                _uiState.update {
                    it.copy(
                        importState = null,
                        errorMessage = e.message ?: Localization.strings.importFailed,
                    )
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        _uiState.update {
            it.copy(importState = null, notice = Localization.strings.importCancelled)
        }
    }

    /** Registers an already-on-disk model (e.g. `adb push`ed) without copying it. */
    fun registerModelPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val spec = importer.registerInPlace(path.trim())
                _uiState.update { it.copy(notice = Localization.strings.addedModel(spec.displayName)) }
            } catch (e: Throwable) {
                ErrorLog.report("Model", "Could not register \"$path\"", e)
                _uiState.update {
                    it.copy(errorMessage = e.message ?: Localization.strings.couldNotRegisterPath)
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
                        Localization.strings.noNewModels
                    } else {
                        Localization.strings.foundModels(found.size)
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

        if (ThermalGuard.shouldPause(_uiState.value.thermalLevel)) {
            _uiState.update {
                it.copy(
                    notice = Localization.strings.thermalTooHot,
                )
            }
            return
        }

        val current = settingsStore.settings.value

        // Decided once, before anything expensive: searching the web for "مرحبا"
        // and reasoning about it cost half an hour and answered nothing.
        val decision = QueryRouter.route(
            message = prompt,
            // The globe toggle is the master switch; the mode says when.
            searchMode = if (current.webSearchEnabled) current.searchMode else RoutingMode.NEVER,
            thinkMode = current.thinkingMode,
            modelSupportsThinking = active.spec.supportsThinking,
            defaultMaxTokens = active.spec.maxTokens,
        )

        val userMessage = ChatMessage(nextId++, Author.USER, prompt)
        val replyId = nextId++
        val placeholder = ChatMessage(
            id = replyId,
            author = Author.MODEL,
            text = "",
            isGenerating = true,
            routing = decision,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + placeholder,
                isBusy = true,
                voiceDraft = "",
            )
        }

        val epoch = ++replyGeneration

        viewModelScope.launch(Dispatchers.IO) {
            // Match decode threads to the current thermal state before starting.
            (active as? LlamaCppEngine)?.applyThermalLevel(thermalGuard.level.value)

            var grounding = ""
            if (decision.search) {
                _uiState.update {
                    it.copy(
                        searchStatus = if (current.searchDepth == SearchDepth.DEEP) {
                            Localization.strings.searchingAndReading
                        } else {
                            Localization.strings.searchingWeb
                        }
                    )
                }
                val outcome = try {
                    webSearch.search(prompt, current.voiceLanguageTag, current.searchDepth)
                } catch (e: Throwable) {
                    ErrorLog.report("Web search", "Search failed", e)
                    null
                }

                grounding = SearchQuery.buildContext(prompt, outcome?.results.orEmpty())
                val sources = SearchQuery.toSources(outcome?.results.orEmpty())
                _uiState.update { state ->
                    state.copy(
                        searchStatus = null,
                        notice = outcome?.problem ?: state.notice,
                        messages = state.messages.map {
                            if (it.id == replyId) it.copy(sources = sources) else it
                        },
                    )
                }
            }

            // The system prompt is the part that holds for the whole
            // conversation: the language instruction and whatever the user set.
            // A multilingual model left to itself answers an Arabic question in
            // English about as often as not, so that is stated outright.
            val registry = buildToolRegistry(current)
            val fullSystem = listOf(
                Localization.strings.replyLanguageInstruction,
                if (current.guidanceEnabled) Localization.strings.assistantGuidance else "",
                // Generated from the live registry, so the model is never told
                // about a tool it cannot call.
                ToolPrompt.render(registry, Localization.strings),
                current.systemPrompt,
            )
                .filter { it.isNotBlank() }
                .joinToString("\n\n")

            // Search results belong to *this* question, not to the conversation,
            // so they ride with the user turn. Putting them in the system prompt
            // meant turn three was still answering with turn one's pages.
            val groundedPrompt = if (grounding.isBlank()) {
                prompt
            } else {
                "$grounding\n\n$prompt"
            }

            if (activeSystemPrompt != null && activeSystemPrompt != fullSystem) {
                active.resetSession()
            }
            activeSystemPrompt = fullSystem

            try {
                runTurn(
                    active = active,
                    registry = registry,
                    firstPrompt = groundedPrompt,
                    systemPrompt = fullSystem,
                    decision = decision,
                    replyId = replyId,
                    epoch = epoch,
                    maxSteps = if (registry.isEmpty) 0 else current.toolMaxSteps.coerceIn(0, 6),
                )
            } catch (e: Throwable) {
                ErrorLog.report("Generation", "Generation failed", e)
                appendDelta(replyId, "", "\n[error: ${e.message}]", done = true, epoch = epoch)
                finishTurn(replyId, epoch)
            }
        }
    }

    /**
     * One user turn, including any tool calls it takes to answer.
     *
     * The loop is here rather than inside the engine because a tool result has
     * to go back through the chat template as a turn — there is no side channel
     * in a llama.cpp or MediaPipe session, and inventing one would mean
     * maintaining a second prompt format per model family.
     *
     * The model's tool call is generated into the visible bubble and then taken
     * back out. That is deliberate: streaming into a hidden buffer and swapping
     * it in at the end would leave the screen frozen for the length of a whole
     * generation, and a user watching a spinner cannot tell that from a hang.
     */
    private suspend fun runTurn(
        active: TextEngine,
        registry: ToolRegistry,
        firstPrompt: String,
        systemPrompt: String,
        decision: RoutingDecision,
        replyId: Long,
        epoch: Int,
        maxSteps: Int,
    ) {
        var turnPrompt = firstPrompt
        var step = 0

        while (true) {
            // What the bubble already holds from earlier steps. Rewriting it
            // below has to keep this: the tool call being removed belongs to
            // the newest generation only, and replacing the whole text with
            // that generation's leftovers would erase the answer so far.
            val prefix = textOf(replyId)

            val collected = StringBuilder()
            active.generate(
                prompt = turnPrompt,
                systemPrompt = systemPrompt,
                thinkingEnabled = decision.think,
                maxTokens = decision.maxTokens,
            ) { thinking, answer, _ ->
                collected.append(answer)
                // Never final here: a turn can span several generations, and
                // finishTurn is what ends it.
                appendDelta(replyId, thinking, answer, done = false, epoch = epoch)
            }

            if (epoch != replyGeneration) return

            val reply = collected.toString()
            val call = if (step < maxSteps) ToolCallParser.parse(reply) else null
            if (call == null) {
                // No tool wanted, or no steps left. Close the turn.
                if (step >= maxSteps && ToolCallParser.looksLikeCall(reply)) {
                    // It still wanted a tool and has run out of steps. Say so
                    // rather than leaving a JSON fragment as the final answer.
                    replaceText(
                        replyId,
                        join(prefix, ToolCallParser.strip(reply)) + "\n\n" +
                            Localization.strings.toolStepLimitReached,
                    )
                }
                finishTurn(replyId, epoch)
                return
            }

            // The call itself is machinery. Left in the bubble it reads as
            // gibberish, and the speech engine reads it aloud.
            replaceText(replyId, join(prefix, ToolCallParser.strip(reply)))
            _uiState.update {
                it.copy(searchStatus = Localization.strings.toolRunning(call.name))
            }

            val tool = registry.find(call.name)
            val result = if (tool == null) {
                ToolResult.failed(ToolPrompt.unknownTool(call.name, registry, Localization.strings))
            } else {
                runCatching { tool.run(call) }.getOrElse { error ->
                    ErrorLog.report("Tool", "${call.name} failed", error, Severity.WARNING)
                    ToolResult.failed(error.message ?: call.name)
                }
            }

            _uiState.update { it.copy(searchStatus = null) }
            if (epoch != replyGeneration) return

            turnPrompt = ToolPrompt.observation(call, result, Localization.strings)
            step++
        }
    }

    /** The reply's text as it currently stands. */
    private fun textOf(id: Long): String =
        _uiState.value.messages.firstOrNull { it.id == id }?.text.orEmpty()

    /** Joins two parts of one answer without stacking blank lines. */
    private fun join(before: String, after: String): String = when {
        before.isBlank() -> after.trim()
        after.isBlank() -> before.trimEnd()
        else -> "${before.trimEnd()}\n\n${after.trim()}"
    }

    /** Replaces a reply's text wholesale — used to take a tool call back out. */
    private fun replaceText(id: Long, text: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) it.copy(text = text) else it
                }
            )
        }
    }

    private fun finishTurn(replyId: Long, epoch: Int) {
        if (epoch != replyGeneration) return
        _uiState.update { state ->
            state.copy(
                isBusy = false,
                messages = state.messages.map {
                    if (it.id == replyId) it.copy(isGenerating = false) else it
                },
            )
        }
        refreshMemory()
        if (settingsStore.settings.value.autoSpeakReplies) {
            val reply = _uiState.value.messages.firstOrNull { it.id == replyId }
            if (reply != null && reply.text.isNotBlank()) speak(replyId, reply.text)
        }
    }

    /**
     * The tools available for this message.
     *
     * Rebuilt per turn rather than held, because every switch that gates a
     * tool lives in settings and a stale registry would describe a tool the
     * user has since turned off — which the model would then call and be
     * refused by, wasting a whole generation on it.
     */
    private fun buildToolRegistry(current: AppSettings): ToolRegistry {
        if (!current.toolsEnabled) return ToolRegistry(emptyList())

        val strings = { Localization.strings }
        val tools = mutableListOf<AgentTool>(
            ClockTool(strings),
            CalcTool(strings),
            DeviceTool({ _device.value }, strings),
            ReadFileTool(terminal.shell, strings),
            ListFilesTool(terminal.shell, strings),
        )
        if (current.toolAllowWrites) tools += WriteFileTool(terminal.shell, strings)
        if (current.webSearchEnabled) {
            tools += WebSearchTool(webSearch, { settingsStore.settings.value.voiceLanguageTag }, strings)
        }
        if (current.toolShellEnabled) {
            tools += ShellTool(terminal, strings) { risk ->
                when (risk) {
                    CommandRisk.READ_ONLY -> true
                    CommandRisk.WRITES -> current.toolAllowWrites
                    CommandRisk.DANGEROUS -> current.toolAllowDangerous
                }
            }
        }
        return ToolRegistry(tools)
    }

    private fun appendDelta(
        id: Long,
        thinking: String,
        answer: String,
        done: Boolean,
        epoch: Int,
    ) {
        // A generation the user walked away from keeps emitting tokens on some
        // backends. They belong to nobody, so they are dropped here rather than
        // fought at every call site.
        if (epoch != replyGeneration) return
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
            state.copy(messages = messages)
        }
    }

    /** Interrupts the reply being generated and keeps whatever arrived so far. */
    fun stopGeneration() {
        if (!_uiState.value.isBusy) return
        // Before stop(), not after: whatever the engine emits from here on is
        // no longer this reply's, whether or not stop() could do anything.
        replyGeneration++
        engine?.stop()
        _uiState.update { state ->
            state.copy(
                isBusy = false,
                messages = state.messages.map {
                    if (it.isGenerating) it.copy(isGenerating = false) else it
                },
            )
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
        replyGeneration++
        engine?.resetSession()
        activeSystemPrompt = null
        _uiState.update { it.copy(messages = emptyList()) }
    }

    // ---------------------------------------------------------------- studio

    fun studioBuild(request: String) {
        val active = engine ?: return
        if (_uiState.value.isBusy) return

        if (ThermalGuard.shouldPause(_uiState.value.thermalLevel)) {
            _uiState.update { it.copy(notice = Localization.strings.thermalTooHot) }
            return
        }

        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            (active as? LlamaCppEngine)?.applyThermalLevel(thermalGuard.level.value)
            studio.build(active, request) {
                _uiState.update { it.copy(isBusy = false) }
                // The chat's own session was reset out from under it.
                activeSystemPrompt = null
                refreshMemory()
            }
        }
    }

    /**
     * Bumped once the speech engine has started, and again whenever it is
     * restarted on a different engine.
     *
     * Enumerating voices is a plain read off a live `TextToSpeech`, so before
     * one exists both lists below are empty — which looked to the user like a
     * phone with no voices installed. Settings watches this and re-reads.
     */
    private val _ttsGeneration = MutableStateFlow(0)
    val ttsGeneration: StateFlow<Int> = _ttsGeneration.asStateFlow()

    /**
     * Starts (or restarts) the chosen speech engine so its voices can be
     * listed. Safe to call repeatedly: unchanged settings are a no-op inside.
     */
    fun prepareSystemTts() {
        viewModelScope.launch(Dispatchers.IO) {
            systemTts.prepare(settingsStore.settings.value.systemVoiceEngine)
            // Bumped even on failure, so the screen stops waiting on an engine
            // that is never going to answer.
            _ttsGeneration.update { it + 1 }
        }
    }

    /** Installed system voices, for the picker in Settings. */
    fun systemVoices(): List<com.example.ondevicellm.audio.VoiceOption> =
        systemTts.availableVoices()

    /** Installed speech engines, for the picker in Settings. */
    fun systemVoiceEngines(): List<com.example.ondevicellm.audio.TtsEngineOption> =
        systemTts.availableEngines()

    // ------------------------------------------------------------- terminal

    /** Runs a command the user typed. Serialised: one shell, one at a time. */
    fun runTerminalCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) { terminal.submit(command) }
    }

    fun clearTerminal() = terminal.clear()

    /** Where commands run, shown so the sandbox is not a mystery. */
    val terminalWorkspace: String get() = terminal.shell.root.path

    fun studioToggleView() = studio.toggleView()
    fun studioRun() = studio.run()
    fun studioEditCode(code: String) = studio.editCode(code)
    fun studioSave() = studio.save()
    fun studioNew() = studio.newProject()
    fun studioOpen(project: StudioProject) = studio.open(project)
    fun studioDelete(id: String) = studio.delete(id)
    fun studioDismissNotice() = studio.dismissNotice()

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
                voiceName = current.systemVoiceName,
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

                is SynthesisResult.Failed -> {
                    ErrorLog.report("Speech", result.message, severity = Severity.WARNING)
                    _uiState.update { it.copy(notice = result.message) }
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
                voiceName = current.systemVoiceName,
            )

            val saved = when (current.ttsEngine) {
                // The system engine writes its own WAV; everything else hands
                // back samples, which is the same file one step later.
                TtsEngine.SYSTEM ->
                    systemTts.prepare(current.systemVoiceEngine) &&
                        systemTts.synthesizeToFile(text, options, target)

                TtsEngine.MODEL, TtsEngine.CLOUD -> {
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
                    notice = if (saved) {
                        Localization.strings.savedAudioTo(target.absolutePath)
                    } else {
                        Localization.strings.couldNotSaveAudio
                    }
                )
            }
        }
    }

    /**
     * Returns the synthesizer for the current settings, loading the user's TTS
     * model on demand. Null when MODEL is selected but no TTS model is set.
     */
    private suspend fun resolveSynthesizer(): SpeechSynthesizer? {
        val current = settingsStore.settings.value
        return when (current.ttsEngine) {
            TtsEngine.SYSTEM -> systemTts.takeIf {
                it.prepare(current.systemVoiceEngine)
            }

            // Stateless — the service is the runtime — so it is rebuilt from
            // settings each time rather than cached and invalidated.
            TtsEngine.CLOUD -> CloudTtsSynthesizer(
                provider = current.cloudProvider,
                apiKey = current.cloudApiKey,
                region = current.cloudRegion,
                voice = current.cloudVoice,
            ).takeIf { it.prepare() }

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


    /** True when custom TTS models can run in this build. See ModelTtsSynthesizer. */
    val ttsRuntimeAvailable: Boolean get() = ModelTtsSynthesizer.isRuntimeAvailable()

    /** Explains, in the user's terms, why speech output is unavailable. */
    fun speechUnavailableReason(): String = when {
        settingsStore.settings.value.ttsEngine == TtsEngine.SYSTEM ->
            Localization.strings.ttsSystemUnavailable

        // The cloud voice only fails this early when it has not been set up,
        // so say which field is missing rather than "unavailable".
        settingsStore.settings.value.ttsEngine == TtsEngine.CLOUD ->
            settingsStore.settings.value.let { current ->
                CloudTts.missingSetting(
                    provider = current.cloudProvider,
                    apiKey = current.cloudApiKey,
                    region = current.cloudRegion,
                    voice = current.cloudVoice,
                    s = Localization.strings,
                ) ?: Localization.strings.cloudRequestFailed
            }

        !ModelTtsSynthesizer.isRuntimeAvailable() ->
            ModelTtsSynthesizer.RUNTIME_MISSING_MESSAGE

        registry.selectedTtsModel == null ->
            Localization.strings.ttsNoModelSelected

        else -> Localization.strings.ttsModelLoadFailed
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


    override fun onCleared() {
        super.onCleared()
        thermalGuard.stop()
        speech.release()
        audioPlayer.release()
        systemTts.release()
        modelTts?.release()
        modelTts = null
        engine?.close()
        engine = null
    }
}
