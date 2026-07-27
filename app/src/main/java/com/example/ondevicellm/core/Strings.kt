package com.example.ondevicellm.core

import java.util.Locale

/** Which language the interface is drawn in. */
enum class AppLanguage {
    /** Follow the phone's locale. */
    SYSTEM,
    ARABIC,
    ENGLISH,
    ;

    /** Shown in its own language — never translated. */
    val label: String
        get() = when (this) {
            SYSTEM -> "Auto"
            ARABIC -> "العربية"
            ENGLISH -> "English"
        }

    fun resolve(systemLocale: Locale = Locale.getDefault()): AppStrings = when (this) {
        ARABIC -> ArabicStrings
        ENGLISH -> EnglishStrings
        SYSTEM -> if (systemLocale.language == "ar") ArabicStrings else EnglishStrings
    }
}

/**
 * Every user-facing string in the app, in one place, per language.
 *
 * An interface rather than `strings.xml` on purpose: a missing translation is a
 * compile error here, whereas a missing XML key is a crash on the device in the
 * one language nobody on the team reads. Arabic is a first-class target for
 * this app, not an afterthought, so it has to be impossible to half-translate.
 *
 * Plain Kotlin with no Android imports, so it is unit-testable.
 */
interface AppStrings {

    /** True when text flows right to left. Drives layout direction. */
    val isRtl: Boolean

    /** BCP-47 tag used for speech in and out when the user hasn't chosen one. */
    val defaultVoiceTag: String

    // ---------------------------------------------------------------- nav
    val navChat: String
    val navModels: String
    val navDevice: String
    val navSettings: String
    val chatSubtitle: String
    val modelsSubtitle: String
    val deviceSubtitle: String
    val settingsSubtitle: String

    // ------------------------------------------------------------- header
    val stopGenerating: String
    val stopSpeaking: String
    val newChat: String

    // --------------------------------------------------------------- chat
    val askAnything: String
    val readyWhenYouAre: String
    val runningOffline: String
    val addFirstModel: String
    val browseModels: String
    val privacyNote: String
    val loadingModel: String
    fun loadingNamed(name: String): String
    val warmingUp: String
    val couldNotLoadModel: String
    val tryAgain: String
    val details: String
    val gotIt: String
    val reasoning: String
    val thinking: String
    val expand: String
    val collapse: String
    val listen: String
    val stop: String
    val save: String
    val send: String
    val voiceInput: String
    val stopListening: String
    val listening: String
    val webSearchOn: String
    val webSearchOff: String
    val searchingWeb: String
    val searchingAndReading: String
    fun copyingPercent(percent: Int): String

    // ------------------------------------------------------------ routing
    val routeGreeting: String
    val routeSimple: String
    val routeLookup: String
    val routeReasoning: String
    val routeSearching: String
    val routeThinking: String
    val routeOverridden: String
    /** Joins the base reason with what it decided to do, e.g. "… · searching". */
    fun routeLine(base: String, extras: List<String>): String

    // ------------------------------------------------------------- models
    val addModel: String
    val linkByPath: String
    val scan: String
    val nothingInstalledYet: String
    val pickAModelFile: String
    val pushHint: String
    val absolutePath: String
    val link: String
    val linkedInPlace: String
    val cancel: String
    val remove: String
    val removeModelTitle: String
    fun removeManagedModelBody(name: String, freed: String): String
    fun removeLinkedModelBody(name: String): String
    val fileLeftUntouched: String
    val active: String
    val load: String
    val tune: String
    val type: String
    val backend: String
    val generation: String
    val maxTokens: String
    val temperature: String
    val topK: String
    val reasoningSection: String
    val emitsReasoning: String
    val wrapsThinkTags: String
    val voiceOutput: String
    val sampleRate: String
    val speakerId: String
    val sampleRateNote: String
    val path: String
    val kindText: String
    val kindAsr: String
    val kindTts: String
    val kindMultimodal: String
    val thermalNormal: String
    val thermalWarm: String
    val thermalHot: String
    val thermalCritical: String

    // ------------------------------------------------------------- device
    val hardware: String
    val model: String
    val manufacturer: String
    val socVendor: String
    val cpuCores: String
    val abis: String
    val unknownSoc: String
    val memory: String
    val physicalAndExtended: String
    val total: String
    val free: String
    val ramPlus: String
    val effectiveBudget: String
    val effectiveBudgetNote: String
    val ramPlusOn: String
    val ramPlusOff: String
    val mmapNote: String
    val accelerators: String
    val gpuAndNpu: String
    val vulkan: String
    val openCl: String
    val nnapi: String
    val vendorNpuRuntime: String
    val notFound: String
    val availableWord: String
    val deprecatedOn15: String
    val snapdragonNote: String
    val howNpuBehaves: String
    val npuExplanation: String
    val storage: String
    val whereModelsLive: String
    val freeSpace: String
    val refresh: String
    // ---- what this device can run ----
    val whatFits: String
    val whatFitsSubtitle: String
    fun advisorHeadline(best: String): String
    val advisorNothingFits: String
    val advisorQuantAdvice: String
    val advisorArabicNote: String
    val fitComfortable: String
    val fitTight: String
    val fitTooBig: String
    val off: String
    val unknown: String
    fun freeOf(total: String): String
    fun percentInUse(percent: Int): String

    // -------------------------------------------------------- diagnostics
    val diagnostics: String
    val errorsAndCrashes: String
    val recordedEvents: String
    val whatWentWrong: String
    val nothingWentWrong: String
    val noProblemsRecorded: String
    val tapForDetails: String
    val openLog: String
    fun reviewCount(count: Int): String
    fun recordedCount(count: Int): String
    val copyAll: String
    val clear: String
    val close: String
    val clearDiagnosticsTitle: String
    val clearDiagnosticsBody: String
    val warnings: String
    val diagnosticsIntro: String

    // ----------------------------------------------------------- settings
    val language: String
    val languageSubtitle: String
    val interfaceLanguage: String
    val languageNote: String
    val reasoningTitle: String
    val reasoningSubtitle: String
    val whenToThink: String
    val thinkAuto: String
    val thinkAlways: String
    val thinkNever: String
    val showReasoning: String
    val showReasoningDesc: String
    val reasoningDetectedNote: String
    val webSearch: String
    val webSearchSubtitle: String
    val searchTheWeb: String
    val searchTheWebDesc: String
    val searchOnNote: String
    val searchOffNote: String
    val whenToSearch: String
    val searchAuto: String
    val searchAlways: String
    val searchNever: String
    val depth: String
    val depthQuickLabel: String
    val depthDeepLabel: String
    val depthQuick: String
    val depthDeep: String

    // --------------------------------------------- messages from below the UI
    val searchNothingToDo: String
    val searchUnreachable: String
    val searchNoResults: String
    fun modelFileNotFound(path: String): String
    fun modelFileNotReadable(path: String): String
    val notEnoughMemoryTitle: String
    fun memoryLine(label: String, value: String): String
    val availableRamLabel: String
    val freeExtendedLabel: String
    val ramPlusNotEnabled: String
    val notEnoughMemoryAdvice: String
    val ggufRuntimeMissing: String
    val replyTruncated: String
    fun ggufBackendIgnored(pref: String): String
    fun threadsOfCores(threads: Int, cores: Int): String
    fun cpuAsRequested(soc: String): String
    val noGpuDriver: String
    fun autoNoGpu(soc: String): String
    val autoGpuFits: String
    fun autoCpu(reason: String): String
    val gpuTooLargeModel: String
    val gpuNotEnoughMemory: String
    val gpuSwitchIfItFails: String
    val npuLinked: String
    val npuDetectedUnusable: String
    val npuNoRuntime: String
    val thisDevice: String
    fun unsupportedFormat(fileName: String): String
    val unrecognisedFormat: String
    fun notEnoughStorage(megabytes: Long): String
    val cannotOpenFile: String
    fun noFileAt(path: String): String
    fun fileNotReadable(path: String): String
    fun cannotPlayAudio(sampleRateHz: Int): String
    val speechUnavailable: String
    val speechErrorAudio: String
    val speechErrorClient: String
    val speechErrorPermission: String
    val speechErrorNetwork: String
    val speechErrorTimeout: String
    val speechErrorNoMatch: String
    val speechErrorBusy: String
    val speechErrorServer: String
    val speechErrorNoSpeech: String
    fun speechErrorOther(code: Int): String
    val thermalPaused: String
    val thermalTooHot: String
    val modelLoadFailedFallback: String
    val copyingModel: String
    val importFailed: String
    val importCancelled: String
    fun addedModel(name: String): String
    val couldNotRegisterPath: String
    val noNewModels: String
    fun foundModels(count: Int): String
    fun savedAudioTo(path: String): String
    val couldNotSaveAudio: String
    val ttsSystemUnavailable: String
    val ttsNoModelSelected: String
    val ttsModelLoadFailed: String
    val ttsRuntimeMissing: String
    val ttsEngineSystem: String
    val ttsEngineCloud: String
    val cloudAzure: String
    val cloudElevenLabs: String
    val cloudTitle: String
    val cloudSubtitle: String
    val cloudPrivacy: String
    val cloudProvider: String
    val cloudApiKey: String
    val cloudRegion: String
    val cloudVoice: String
    val cloudVoiceIdHint: String
    val cloudNeedsKey: String
    val cloudNeedsRegion: String
    val cloudNeedsVoiceId: String
    val cloudEmptyAudio: String
    val cloudRequestFailed: String
    fun cloudHttpError(code: Int, detail: String): String
    val cloudKeyStorageNote: String
    val cloudShowKey: String
    val cloudHideKey: String
    val ttsEngineModel: String
    fun ggufLoadFailed(detail: String): String
    fun taskLoadFailed(detail: String, backend: String): String
    val performance: String
    val performanceSubtitle: String
    val thermalSupportedNote: String
    val thermalUnsupportedNote: String
    val speechOutput: String
    val speechOutputSubtitle: String
    val engine: String
    val speechEngineLabel: String
    val speechEngineDefault: String
    val speechEngineNote: String
    val voiceLabel: String
    val voiceAutoBest: String
    val voiceNoneInstalled: String
    val voiceNote: String
    fun voiceQuality(quality: Int): String
    val voiceNeedsNetwork: String
    val systemEngineNote: String
    val litertMissingNote: String
    fun usingVoiceModel(name: String, rate: Int): String
    val noVoiceModelNote: String
    val speakAutomatically: String
    val speakAutomaticallyDesc: String
    val speed: String
    val pitch: String
    val voiceInputTitle: String
    val voiceInputSubtitle: String
    val noRecognizer: String
    val onDeviceRecognition: String
    val cloudRecognition: String
    val voiceLanguage: String
    val voiceLanguageSubtitle: String
    val languageTag: String
    val systemPrompt: String
    val systemPromptSubtitle: String
    val systemPromptPlaceholder: String
    val systemPromptNote: String
    /** Prepended to every conversation so the model answers in the user's language. */
    val replyLanguageInstruction: String

    /**
     * House rules given to the model at the start of every conversation.
     *
     * A frontier model works these out on its own; a 4B one does not, and the
     * difference between a small model that pads, confabulates and drifts and
     * one that answers the question is largely this text. Kept short and
     * imperative on purpose — a long system prompt confuses a small model and
     * eats the context it needs for the actual conversation.
     */
    val assistantGuidance: String
    val guidanceTitle: String
    val guidanceDescription: String
    val guidanceNote: String

    // ------------------------------------------------------------- studio
    val navStudio: String
    val studioSubtitle: String
    val studioEmptyTitle: String
    val studioEmptyBody: String
    val studioPromptHint: String
    val studioBuild: String
    val studioPreview: String
    val studioCode: String
    val studioRun: String
    val studioNewProject: String
    val studioProjects: String
    val studioSave: String
    val studioSaved: String
    val studioUntitled: String
    val studioBuilding: String
    val studioNoCodeBack: String
    val studioTooLarge: String
    val studioOfflineNote: String
    fun studioExample(index: Int): String
    /** System prompt for code generation — the whole feature rests on this. */
    val studioSystemPrompt: String
    fun studioCreateTurn(request: String): String
    fun studioEditTurn(code: String, request: String): String


    // ------------------------------------------------------ terminal & tools
    val navTerminal: String
    val terminalSubtitle: String
    val terminalHint: String
    val terminalClear: String
    val terminalRun: String
    val terminalEmpty: String
    val terminalWorkspaceNote: String
    val terminalLimitsNote: String
    val terminalEmptyCommand: String
    val terminalRefused: String
    val terminalTimedOut: String
    val terminalTruncated: String
    val terminalCouldNotStart: String
    fun terminalExitCode(code: Int): String

    /** How the model is told to call a tool. Generated from the live registry. */
    val toolsHeader: String
    val toolsRules: String
    val toolsAvailable: String
    val toolsOptional: String
    val toolResultLabel: String
    val toolFailedLabel: String
    val toolNoOutput: String
    val toolContinueInstruction: String
    fun toolUnknown(name: String): String
    fun toolMissingArg(name: String): String
    fun toolNotPermitted(risk: String): String
    fun toolRunning(name: String): String
    val toolStepLimitReached: String

    val toolShellSummary: String
    val toolShellCommandParam: String
    val toolReadFileSummary: String
    val toolPathParam: String
    val toolLinesParam: String
    val toolMoreLines: String
    fun toolNoSuchFile(path: String): String
    fun toolIsDirectory(path: String): String
    fun toolReadFailed(path: String): String
    val toolWriteFileSummary: String
    val toolWritePathParam: String
    val toolContentParam: String
    val toolAppendParam: String
    fun toolOutsideWorkspace(path: String): String
    fun toolWroteBytes(path: String, bytes: Int): String
    fun toolWriteFailed(path: String): String
    val toolListFilesSummary: String
    val toolListPathParam: String
    val toolEmptyDirectory: String
    fun toolNotADirectory(path: String): String
    fun toolCannotList(path: String): String
    val toolWebSearchSummary: String
    val toolQueryParam: String
    val toolSearchFailed: String
    val toolNowSummary: String
    val toolCalcSummary: String
    val toolExpressionParam: String
    fun toolBadExpression(expression: String): String
    val toolDeviceSummary: String

    // Settings for the above.
    val toolsTitle: String
    val toolsSettingsSubtitle: String
    val toolsEnable: String
    val toolsEnableDesc: String
    val toolsAllowShell: String
    val toolsAllowShellDesc: String
    val toolsAllowWrites: String
    val toolsAllowWritesDesc: String
    val toolsAllowDangerous: String
    val toolsAllowDangerousDesc: String
    val toolsSteps: String
    val toolsStepsNote: String
    val toolsCostNote: String

    // -------------------------------------------------------- routing modes
    val modeAuto: String
    val modeAlways: String
    val modeNever: String
}

// ---------------------------------------------------------------------------

object EnglishStrings : AppStrings {

    override val isRtl = false
    override val defaultVoiceTag = "en-US"

    override val navChat = "Chat"
    override val navModels = "Models"
    override val navDevice = "Device"
    override val navSettings = "Settings"
    override val chatSubtitle = "Private, on-device"
    override val modelsSubtitle = "Add and configure"
    override val deviceSubtitle = "Hardware and memory"
    override val settingsSubtitle = "Voice and behaviour"

    override val stopGenerating = "Stop generating"
    override val stopSpeaking = "Stop speaking"
    override val newChat = "New chat"

    override val askAnything = "Ask anything"
    override val readyWhenYouAre = "Ready when you are"
    override val runningOffline = "Running fully offline on this device."
    override val addFirstModel = "Add your first model"
    override val browseModels = "Browse models"
    override val privacyNote =
        "Everything runs on your device. Nothing is uploaded, and no account is needed."
    override val loadingModel = "Loading model"
    override fun loadingNamed(name: String) = "Loading $name"
    override val warmingUp = "Warming up"
    override val couldNotLoadModel = "Couldn't load the model"
    override val tryAgain = "Try again"
    override val details = "Details"
    override val gotIt = "Got it"
    override val reasoning = "Reasoning"
    override val thinking = "Thinking"
    override val expand = "Expand"
    override val collapse = "Collapse"
    override val listen = "Listen"
    override val stop = "Stop"
    override val save = "Save"
    override val send = "Send"
    override val voiceInput = "Voice input"
    override val stopListening = "Stop listening"
    override val listening = "Listening…"
    override val webSearchOn = "Web search on"
    override val webSearchOff = "Web search off"
    override val searchingWeb = "Searching the web…"
    override val searchingAndReading = "Searching and reading pages…"
    override fun copyingPercent(percent: Int) = "$percent%"

    override val routeGreeting = "Greeting — answering directly"
    override val routeSimple = "Straightforward question"
    override val routeLookup = "Needs current information"
    override val routeReasoning = "Needs working through"
    override val routeSearching = "searching"
    override val routeThinking = "thinking"
    override val routeOverridden = "overrides applied"
    override fun routeLine(base: String, extras: List<String>) =
        if (extras.isEmpty()) base else "$base · ${extras.joinToString(" and ")}"

    override val addModel = "Add model"
    override val linkByPath = "Link by path"
    override val scan = "Scan"
    override val nothingInstalledYet = "Nothing installed yet"
    override val pickAModelFile = "Pick a model file with Add, or push one from your computer:"
    override val pushHint = "For models pushed with adb. The file is referenced in place — " +
        "nothing is copied, so no extra storage is used."
    override val absolutePath = "Absolute path"
    override val link = "Link"
    override val linkedInPlace = "Linked"
    override val cancel = "Cancel"
    override val remove = "Remove"
    override val removeModelTitle = "Remove model?"
    override fun removeManagedModelBody(name: String, freed: String) =
        "\"$name\" will be deleted from app storage, freeing $freed."
    override fun removeLinkedModelBody(name: String) =
        "\"$name\" will be removed from this list."
    override val fileLeftUntouched = "The file on disk is left untouched."
    override val active = "Active"
    override val load = "Load"
    override val tune = "Tune"
    override val type = "Type"
    override val backend = "Backend"
    override val generation = "Generation"
    override val maxTokens = "Max tokens"
    override val temperature = "Temperature"
    override val topK = "Top-K"
    override val reasoningSection = "Reasoning"
    override val emitsReasoning = "Emits reasoning"
    override val wrapsThinkTags = "Wraps its thinking in <think> tags"
    override val voiceOutput = "Voice output"
    override val sampleRate = "Sample rate (Hz)"
    override val speakerId = "Speaker id"
    override val sampleRateNote = "The sample rate must match the model's training output, or " +
        "speech plays too fast or too slow. Put a \"<name>.tokens.json\" vocabulary next " +
        "to the model file for correct pronunciation."
    override val path = "Path"
    override val kindText = "Text"
    override val kindAsr = "Speech → Text"
    override val kindTts = "Text → Speech"
    override val kindMultimodal = "Multimodal"
    override val thermalNormal = "Normal"
    override val thermalWarm = "Warm"
    override val thermalHot = "Hot"
    override val thermalCritical = "Critical"

    override val hardware = "Hardware"
    override val model = "Model"
    override val manufacturer = "Manufacturer"
    override val socVendor = "SoC vendor"
    override val cpuCores = "CPU cores"
    override val abis = "ABIs"
    override val unknownSoc = "Unknown SoC"
    override val memory = "Memory"
    override val physicalAndExtended = "Physical and extended"
    override val total = "Total"
    override val free = "Free"
    override val ramPlus = "RAM Plus"
    override val effectiveBudget = "Effective budget"
    override val effectiveBudgetNote =
        "Available RAM + free extended memory. Decides what can load."
    override val ramPlusOn = "Active"
    override val ramPlusOff = "Not enabled. Turn it on in Settings › Device care › Memory › " +
        "RAM Plus to give large models more headroom."
    override val mmapNote = "Model weights are memory-mapped, so pages can spill into " +
        "extended memory instead of failing to allocate."
    override val accelerators = "Accelerators"
    override val gpuAndNpu = "GPU and NPU support"
    override val vulkan = "Vulkan"
    override val openCl = "OpenCL"
    override val nnapi = "NNAPI"
    override val vendorNpuRuntime = "Vendor NPU runtime"
    override val notFound = "not found"
    override val availableWord = "available"
    override val deprecatedOn15 = "deprecated on Android 15+"
    override val snapdragonNote = "High-end Qualcomm silicon detected. GPU backend recommended."
    override val howNpuBehaves = "How NPU selection behaves"
    override val npuExplanation = "The bundled LLM runtime exposes CPU and GPU. Choosing NPU " +
        "runs the model on the best backend that really exists and says so on the chat " +
        "screen rather than pretending. Wiring in Qualcomm's QNN/Genie runtime is done " +
        "in BackendResolver.kt."
    override val storage = "Storage"
    override val whereModelsLive = "Where models live"
    override val freeSpace = "Free space"
    override val refresh = "Refresh"
    override val whatFits = "What this phone can run"
    override val whatFitsSubtitle = "Weights are the ceiling, not the app"
    override fun advisorHeadline(best: String) =
        "With the memory free right now, the largest model that fits comfortably " +
            "is around $best."
    override val advisorNothingFits = "Not enough free memory for even a small model " +
        "right now. Close background apps, or enable RAM Plus."
    override val advisorQuantAdvice = "A bigger model at Q4_K_M understands more than a " +
        "smaller one at Q8_0 for the same memory. Dropping from 8-bit to 4-bit costs a " +
        "few percent; doubling the parameters is worth far more than that. If you are " +
        "running a 4B at Q8_0, an 8B at Q4_K_M is the same size and a real step up."
    override val advisorArabicNote = "For Arabic specifically, model choice matters more " +
        "than size alone — some families are trained on very little Arabic and stay weak " +
        "at it however large they are. Qwen3 and Gemma 3 both handle Arabic well; there " +
        "are also models built for Arabic first, such as ALLaM. Check that a GGUF build " +
        "exists before downloading."
    override val fitComfortable = "fits"
    override val fitTight = "tight"
    override val fitTooBig = "too big"
    override val off = "Off"
    override val unknown = "unknown"
    override fun freeOf(total: String) = "free of $total"
    override fun percentInUse(percent: Int) = "$percent% in use"

    override val diagnostics = "Diagnostics"
    override val errorsAndCrashes = "Errors and crashes"
    override val recordedEvents = "Recorded events"
    override val whatWentWrong = "What went wrong, if anything"
    override val nothingWentWrong = "Nothing has gone wrong."
    override val noProblemsRecorded = "No problems recorded. Anything that fails — a model that " +
        "won't load, a search that times out, a crash — will show up here."
    override val tapForDetails = "Tap for details"
    override val openLog = "Open log"
    override fun reviewCount(count: Int) = "Review $count"
    override fun recordedCount(count: Int) =
        "$count recorded ${if (count == 1) "event" else "events"}"
    override val copyAll = "Copy all"
    override val clear = "Clear"
    override val close = "Close"
    override val clearDiagnosticsTitle = "Clear diagnostics?"
    override val clearDiagnosticsBody = "The recorded events will be deleted from this device."
    override val warnings = "Warnings"
    override val diagnosticsIntro = "Failures are recorded here instead of being silently " +
        "ignored — including crashes, which survive a restart."

    override val language = "Language"
    override val languageSubtitle = "Interface and replies"
    override val interfaceLanguage = "Interface"
    override val languageNote = "Changes the whole app, including layout direction. " +
        "Arabic also asks the model to reply in Arabic."
    override val reasoningTitle = "Reasoning"
    override val reasoningSubtitle = "How the model thinks"
    override val whenToThink = "When to think"
    override val thinkAuto = "Reasoning runs only when the question needs it — maths, code, " +
        "comparisons, \"why\" and \"explain\". A greeting is answered straight away instead " +
        "of being deliberated over."
    override val thinkAlways = "Every message gets a full chain of thought. Thorough, but " +
        "slow: even \"hello\" is reasoned about."
    override val thinkNever = "Reasoning is off. Replies come back fastest, and hard questions " +
        "are answered in one pass."
    override val showReasoning = "Show reasoning"
    override val showReasoningDesc = "Display the collapsible thinking trace in chat."
    override val reasoningDetectedNote = "Detected from <think>…</think> in the model's output. " +
        "Mark a model as reasoning-capable on the Models screen."
    override val webSearch = "Web search"
    override val webSearchSubtitle = "Ground answers in live sources"
    override val searchTheWeb = "Search the web"
    override val searchTheWebDesc = "Look up the question before answering, and cite sources."
    override val searchOnNote = "Your questions are sent to DuckDuckGo and Wikipedia. This is " +
        "the only feature that leaves your device — everything else stays offline. Results " +
        "are used as context and cited under each reply."
    override val searchOffNote = "Off. The model answers from its own weights and nothing " +
        "leaves your device. Turning this on sends your questions to DuckDuckGo and Wikipedia."
    override val whenToSearch = "When to search"
    override val searchAuto = "Searches only when the answer depends on something current — " +
        "news, prices, weather, \"latest\", or when you ask it to look something up."
    override val searchAlways = "Every question is looked up first. Slower, and sends more of " +
        "what you type to third-party servers."
    override val searchNever = "Search stays off even though it's enabled above — useful for " +
        "pausing it without losing your settings."
    override val depth = "Depth"
    override val depthQuickLabel = "Quick"
    override val depthDeepLabel = "Read pages"
    override val depthQuick = "Uses result snippets. One round of requests, fastest."
    override val depthDeep = "Opens the top three results and reads them, so answers come from " +
        "page content rather than a two-line snippet. Slower and uses more data."

    override val searchNothingToDo = "Nothing to search for."
    override val searchUnreachable = "Couldn't reach the web. Check your connection, or turn " +
        "search off to answer from the model alone."
    override val searchNoResults = "No useful web results for that question."
    override fun modelFileNotFound(path: String) = "Model file not found:\n$path"
    override fun modelFileNotReadable(path: String) =
        "Model file is not readable by this app:\n$path\n\n" +
            "Re-import it through \"Add model\" so it lives in app storage."
    override val notEnoughMemoryTitle = "Not enough memory to load this model."
    override fun memoryLine(label: String, value: String) = "$label: $value"
    override val availableRamLabel = "Available RAM"
    override val freeExtendedLabel = "Free extended memory (RAM Plus)"
    override val ramPlusNotEnabled = "Extended memory (RAM Plus): not enabled"
    override val notEnoughMemoryAdvice = "Close background apps, enable RAM Plus in " +
        "Settings › Device care › Memory, or use a smaller quantized model."
    override fun ggufBackendIgnored(pref: String) =
        "GGUF models run on the CPU in this build; the $pref preference doesn't apply. "
    override fun threadsOfCores(threads: Int, cores: Int) = "$threads of $cores cores"
    override fun cpuAsRequested(soc: String) = "Running on the $soc CPU, as requested."
    override val noGpuDriver = "No Vulkan or OpenCL driver here — running on CPU."
    override fun autoNoGpu(soc: String) = "Auto: no GPU compute driver on $soc, so the CPU it is."
    override val autoGpuFits = "Auto: GPU — this model is small enough to fit."
    override fun autoCpu(reason: String) = "Auto: CPU. $reason"
    override val gpuTooLargeModel = "This model is too large for a phone GPU allocation; " +
        "the CPU path memory-maps it instead."
    override val gpuNotEnoughMemory =
        "Not enough free memory to keep the weights resident on the GPU."
    override val gpuSwitchIfItFails = "Switch to CPU if loading fails."
    override val npuLinked = "NPU runtime is linked — delegating to it."
    override val npuDetectedUnusable = "This phone has a vendor NPU, but no public API " +
        "exposes it to a general GGUF/.task model, so it can't be used here."
    override val npuNoRuntime = "No usable vendor NPU runtime on this device."
    override val thisDevice = "this device"
    override fun unsupportedFormat(fileName: String) =
        "\"$fileName\" isn't a model format this app can read.\n\n" +
            "Supported: GGUF (.gguf) and MediaPipe bundles (.task).\n\n" +
            "Files ending in .safetensors or .pth come from PyTorch repos and " +
            "need converting to GGUF on a computer first."
    override val unrecognisedFormat = "Unrecognised model format."
    override fun notEnoughStorage(megabytes: Long) =
        "Not enough free storage to import this model (needs $megabytes MB)."
    override val cannotOpenFile = "Cannot open the selected file."
    override fun noFileAt(path: String) = "No file at $path"
    override fun fileNotReadable(path: String) = "File at $path is not readable by this app."
    override fun cannotPlayAudio(sampleRateHz: Int) =
        "This device cannot play audio at $sampleRateHz Hz."
    override val speechUnavailable = "Speech recognition is not available on this device."
    override val speechErrorAudio = "Audio recording error."
    override val speechErrorClient = "Speech client error."
    override val speechErrorPermission = "Microphone permission denied."
    override val speechErrorNetwork = "Network error (install an offline language pack)."
    override val speechErrorTimeout = "Network timeout."
    override val speechErrorNoMatch = "Didn't catch that — try again."
    override val speechErrorBusy = "Recognizer is busy."
    override val speechErrorServer = "Recognition server error."
    override val speechErrorNoSpeech = "No speech detected."
    override fun speechErrorOther(code: Int) = "Speech recognition failed (code $code)."
    override val ggufRuntimeMissing = "The GGUF runtime isn't available in this build.\n\n" +
        "Use a MediaPipe .task model instead, or rebuild the app with the native " +
        "component enabled."
    override val replyTruncated =
        "cut off at the token limit — raise Max tokens for this model to get more"
    override val thermalPaused = "Paused: the phone is getting hot. " +
        "Generation will be slower until it cools down."
    override val thermalTooHot = "The phone is too hot to run the model right now. " +
        "Give it a moment to cool down."
    override val modelLoadFailedFallback = "Failed to load the model."
    override val copyingModel = "Copying model…"
    override val importFailed = "Import failed."
    override val importCancelled = "Import cancelled."
    override fun addedModel(name: String) = "Added \"$name\"."
    override val couldNotRegisterPath = "Could not register that path."
    override val noNewModels = "No new models in /data/local/tmp/llm. Files elsewhere — " +
        "including Downloads — have to be added with \"Add\", which grants access to " +
        "the file you pick."
    override fun foundModels(count: Int) = "Found $count model(s)."
    override fun savedAudioTo(path: String) = "Saved to $path"
    override val couldNotSaveAudio = "Could not save audio."
    override val ttsSystemUnavailable = "The system text-to-speech engine is unavailable. " +
        "Install or enable a TTS engine in system settings."
    override val ttsNoModelSelected = "No text-to-speech model selected. Add one on the " +
        "Models screen and set its type to \"Text → Speech\"."
    override val ttsModelLoadFailed = "Could not load the selected text-to-speech model."
    override val ttsRuntimeMissing = "The speech-model runtime is missing from this build. " +
        "Use the system engine instead."
    override val ttsEngineSystem = "System engine"
    override val ttsEngineCloud = "Cloud voice"
    override val cloudAzure = "Azure Neural"
    override val cloudElevenLabs = "ElevenLabs"
    override val cloudTitle = "Cloud voice"
    override val cloudSubtitle = "The most natural Arabic, at a cost"
    override val cloudPrivacy = "This sends the text of every spoken reply to a third-party " +
        "server. It is the only feature here that does — the model, the search grounding " +
        "aside, and everything else stay on your device. Use it deliberately."
    override val cloudProvider = "Provider"
    override val cloudApiKey = "API key"
    override val cloudRegion = "Region"
    override val cloudVoice = "Voice"
    override val cloudVoiceIdHint = "Voice ID from your account"
    override val cloudNeedsKey = "Enter your API key first."
    override val cloudNeedsRegion = "Enter the region your key belongs to, e.g. westeurope."
    override val cloudNeedsVoiceId = "Paste a voice ID from your ElevenLabs account."
    override val cloudEmptyAudio = "The service returned no audio."
    override val cloudRequestFailed = "The speech request failed."
    override fun cloudHttpError(code: Int, detail: String) =
        if (detail.isBlank()) "Speech service returned HTTP $code."
        else "Speech service returned HTTP $code: $detail"
    override val cloudKeyStorageNote = "The key is stored in this app's private storage on " +
        "this device. It is sent only to the provider you chose."
    override val cloudShowKey = "Show"
    override val cloudHideKey = "Hide"
    override val ttsEngineModel = "My TTS model"
    override fun ggufLoadFailed(detail: String) = "Could not load this GGUF model.\n\n$detail"
    override fun taskLoadFailed(detail: String, backend: String) =
        "The runtime could not load this model.\n\n$detail\n\n" +
            "Check that it is a MediaPipe-compatible .task bundle and that the selected " +
            "backend ($backend) is supported."
    override val performance = "Performance"
    override val performanceSubtitle = "Heat and battery"
    override val thermalSupportedNote = "The app watches the phone's thermal state and lowers " +
        "the number of compute threads as it warms up, pausing if it gets too hot. Sustained " +
        "performance mode is requested so clocks stay steady instead of spiking then throttling."
    override val thermalUnsupportedNote = "This Android version doesn't report thermal state, " +
        "so a conservative thread count is used at all times. Sustained performance mode is " +
        "still requested."
    override val speechOutput = "Speech output"
    override val speechOutputSubtitle = "Read replies aloud"
    override val engine = "Engine"
    override val speechEngineLabel = "Speech engine"
    override val speechEngineDefault = "System default"
    override val speechEngineNote = "Which engine matters more for Arabic than which voice. " +
        "Phones often ship a vendor engine as the default while a better one sits installed " +
        "beside it. If only one is listed, installing \"Speech Services by Google\" from the " +
        "Play Store adds neural Arabic voices, free and offline once downloaded."
    override val voiceLabel = "Voice"
    override val voiceAutoBest = "Best available"
    override val voiceNoneInstalled = "No voice for this language is installed. Add one in " +
        "system settings under Text-to-speech, then reopen this screen."
    override val voiceNote = "Android usually defaults to the oldest voice installed for a " +
        "language. Picking deliberately is often the difference between a robotic voice and " +
        "a natural one, with nothing to download."
    override fun voiceQuality(quality: Int) = when {
        quality >= 500 -> "very high"
        quality >= 400 -> "high"
        quality >= 300 -> "normal"
        else -> "low"
    }
    override val voiceNeedsNetwork = "needs internet"
    override val systemEngineNote = "Android's built-in engine. Works out of the box and stays " +
        "offline once a voice pack is installed."
    override val litertMissingNote = "The speech-model runtime is missing from this build, " +
        "so custom voice models can't run. Use the system engine instead."
    override fun usingVoiceModel(name: String, rate: Int) = "Using \"$name\" at $rate Hz."
    override val noVoiceModelNote = "No voice model selected. Add one on the Models screen and " +
        "set its type to \"Text → Speech\"."
    override val speakAutomatically = "Speak replies automatically"
    override val speakAutomaticallyDesc = "Read each reply aloud as soon as it finishes."
    override val speed = "Speed"
    override val pitch = "Pitch"
    override val voiceInputTitle = "Voice input"
    override val voiceInputSubtitle = "Dictate instead of typing"
    override val noRecognizer = "No speech recognizer is available on this device."
    override val onDeviceRecognition =
        "On-device recognition available — your speech stays on the phone."
    override val cloudRecognition = "Recognition available, but no on-device pack was found. " +
        "Install an offline language pack in system settings to keep transcription local."
    override val voiceLanguage = "Voice language"
    override val voiceLanguageSubtitle = "For speech in and out"
    override val languageTag = "Language tag"
    override val systemPrompt = "System prompt"
    override val systemPromptSubtitle = "Set the assistant's behaviour"
    override val systemPromptPlaceholder = "e.g. You are a concise assistant."
    override val systemPromptNote = "Prepended to each message. Takes effect on the next message."
    override val replyLanguageInstruction = ""
    override val assistantGuidance = """
        You are a careful assistant running on the user's phone.

        - Answer the question that was asked. Do not restate it first.
        - If you do not know, say so. Never invent a fact, number, date, name or link.
        - Keep the answer as short as the question allows. Add detail only when asked.
        - If the request is ambiguous, ask one short question instead of guessing.
        - Use what was said earlier in this conversation.
        - Asked for steps, give numbered steps. Asked for a fact, give the fact.
        - No opening pleasantries, and no closing offer of further help.
    """.trimIndent()
    override val guidanceTitle = "House rules"
    override val guidanceDescription = "Give the model a short set of rules to work by."
    override val guidanceNote = "A large model works these out on its own; a small one " +
        "doesn't. This is most of the difference between an assistant that pads and " +
        "guesses and one that answers. Your own prompt below is added after it."

    override val navStudio = "Studio"
    override val studioSubtitle = "Build a page by describing it"
    override val studioEmptyTitle = "Describe what to build"
    override val studioEmptyBody = "The model writes one self-contained web page — HTML, " +
        "CSS and JavaScript in a single file — and it runs here immediately."
    override val studioPromptHint = "A calculator with big buttons…"
    override val studioBuild = "Build"
    override val studioPreview = "Preview"
    override val studioCode = "Code"
    override val studioRun = "Run"
    override val studioNewProject = "New"
    override val studioProjects = "Projects"
    override val studioSave = "Save"
    override val studioSaved = "Saved"
    override val studioUntitled = "Untitled page"
    override val studioBuilding = "Writing the page…"
    override val studioNoCodeBack = "The model replied without any code. Try describing " +
        "the page more concretely — what it shows, and what it does."
    override val studioTooLarge = "This page is now too long to send back for editing. " +
        "Edit the code directly, or start a new project."
    override val studioOfflineNote = "The preview runs offline: it cannot reach the network, " +
        "so pages that fetch data or load a CDN will not work here."
    override fun studioExample(index: Int) = when (index) {
        0 -> "A tip calculator"
        1 -> "A pomodoro timer with start and reset"
        2 -> "A colour picker that shows the hex code"
        else -> "A to-do list saved in the browser"
    }
    override val studioSystemPrompt = """
        You write single-file web pages that actually work.

        Output
        - One complete HTML document in a ```html block. Nothing before or after it.
        - Everything in that one file: CSS in <style>, JavaScript in <script>.
        - Given an existing page and a change, return the whole file with the change
          applied — never a snippet, never a description of the edit.

        Make it real, not a mock
        - Every control must do what it looks like it does. A button that does not
          run its function is worse than no button.
        - No placeholder handlers, no TODO, no "implement this", no dummy data
          standing in for a calculation you could just perform.
        - Handle the empty case and the wrong input, not only the happy path. What
          does it show before the user types anything, or when they type letters
          into a number field?
        - Nothing here can reach the network. No CDN, no font service, no image URL,
          no fetch. Use system fonts, CSS and inline SVG.

        Build it for a phone held in one hand
        - Viewport meta tag. Touch targets at least 44px. Text at least 16px.
        - Layout in flexbox or grid so it survives any screen width. Never a fixed
          pixel width for the page itself.
        - Readable contrast, and a dark background unless asked otherwise.

        Restraint
        - Build exactly what was asked, completely. Do not add features nobody asked
          for. A tip calculator is a tip calculator, not a budgeting suite.
        - Name things for what they are: total, not x1.
        - Comment only where the reason is not obvious from the code. Say *why*,
          never restate *what*.
    """.trimIndent()
    override fun studioCreateTurn(request: String) =
        "Build this as a single HTML file:\n\n$request"
    override fun studioEditTurn(code: String, request: String) =
        "Here is the current page:\n\n```html\n$code\n```\n\n" +
            "Apply this change and return the whole updated file:\n\n$request"


    override val navTerminal = "Terminal"
    override val terminalSubtitle = "A real shell, and what the model runs in it"
    override val terminalHint = "Type a command"
    override val terminalClear = "Clear"
    override val terminalRun = "Run"
    override val terminalEmpty = "Nothing has run yet. Try `ls`, `getprop ro.product.model`, " +
        "or `cat /proc/cpuinfo`."
    override val terminalWorkspaceNote = "Commands run in this app's private workspace, as this " +
        "app's user. `cd` is remembered between commands; nothing else is."
    override val terminalLimitsNote = "No root, and Android's toybox rather than GNU tools — so " +
        "no bash, python, curl or git unless you installed one, and some flags differ. " +
        "`pm`, `settings` and most of `dumpsys` will answer permission denied. That is Android, " +
        "not a missing feature here."
    override val terminalEmptyCommand = "Nothing to run."
    override val terminalRefused = "Refused: this either hangs the device or destroys something " +
        "with no undo. It is the one short list the terminal will not run."
    override val terminalTimedOut = "Stopped: took too long."
    override val terminalTruncated = "Output was cut — too long to keep."
    override val terminalCouldNotStart = "Could not start that command."
    override fun terminalExitCode(code: Int) = "Finished with exit code $code and no output."

    override val toolsHeader = "You can use tools. When a tool answers better than guessing, " +
        "emit exactly one call, alone, with no other text:"
    override val toolsRules = """
        Rules:
        - One call per reply. Stop after it; the result comes back and you continue.
        - Never guess a value a tool can give you: the date, a sum, a file's contents,
          anything about this device, anything after your training cut-off.
        - Never invent a tool result. If a call fails, say so and try something else.
        - When you already know the answer, just answer. A tool call is a cost, not a ritual.
        - After the result arrives, answer the person in their language. Do not
          repeat the raw output at them unless they asked to see it.
    """.trimIndent()
    override val toolsAvailable = "Available tools:"
    override val toolsOptional = "optional"
    override val toolResultLabel = "TOOL RESULT"
    override val toolFailedLabel = "(the call failed)"
    override val toolNoOutput = "(no output)"
    override val toolContinueInstruction = "Now answer the person, using this result. " +
        "Call another tool only if you genuinely still need one."
    override fun toolUnknown(name: String) = "There is no tool called \"$name\"."
    override fun toolMissingArg(name: String) = "Missing required argument: $name."
    override fun toolNotPermitted(risk: String) =
        "Not allowed: commands of this kind ($risk) are switched off in Settings."
    override fun toolRunning(name: String) = "Running $name…"
    override val toolStepLimitReached = "Stopped after the tool-step limit. Answer with what " +
        "you have."

    override val toolShellSummary = "Run a shell command on this device and read its output."
    override val toolShellCommandParam = "the command, exactly as you would type it"
    override val toolReadFileSummary = "Read a text file."
    override val toolPathParam = "path to the file"
    override val toolLinesParam = "how many lines to read (default 200)"
    override val toolMoreLines = "… more lines follow; read again with a larger \"lines\"."
    override fun toolNoSuchFile(path: String) = "No such file: $path"
    override fun toolIsDirectory(path: String) = "$path is a directory — use list_files."
    override fun toolReadFailed(path: String) = "Could not read $path."
    override val toolWriteFileSummary = "Write a text file in the workspace."
    override val toolWritePathParam = "path inside the workspace"
    override val toolContentParam = "the full text to write"
    override val toolAppendParam = "\"true\" to add to the end instead of replacing"
    override fun toolOutsideWorkspace(path: String) =
        "$path is outside the workspace. Writing is confined to it."
    override fun toolWroteBytes(path: String, bytes: Int) = "Wrote $bytes bytes to $path."
    override fun toolWriteFailed(path: String) = "Could not write $path."
    override val toolListFilesSummary = "List what is in a directory."
    override val toolListPathParam = "directory (default: current)"
    override val toolEmptyDirectory = "(empty)"
    override fun toolNotADirectory(path: String) = "$path is not a directory."
    override fun toolCannotList(path: String) = "Could not list $path."
    override val toolWebSearchSummary = "Search the web for something you do not know."
    override val toolQueryParam = "what to search for"
    override val toolSearchFailed = "The search did not go through."
    override val toolNowSummary = "The current date and time. Use it instead of guessing."
    override val toolCalcSummary = "Evaluate an arithmetic expression exactly."
    override val toolExpressionParam = "e.g. 25 * 17, (3+4)^2"
    override fun toolBadExpression(expression: String) =
        "Not an arithmetic expression: $expression"
    override val toolDeviceSummary = "Facts about the phone this is running on."

    override val toolsTitle = "Tools"
    override val toolsSettingsSubtitle = "What the model may do besides talk"
    override val toolsEnable = "Let the model use tools"
    override val toolsEnableDesc = "It can look things up, do arithmetic, read files and run " +
        "commands, instead of guessing."
    override val toolsAllowShell = "Terminal"
    override val toolsAllowShellDesc = "Let it run shell commands. Everything it runs is shown " +
        "on the Terminal page."
    override val toolsAllowWrites = "Allow writes"
    override val toolsAllowWritesDesc = "Let commands change files. Off means read-only."
    override val toolsAllowDangerous = "Allow system commands"
    override val toolsAllowDangerousDesc = "pm, settings, recursive delete. Most need root and " +
        "will fail anyway — but leave this off unless you have a reason."
    override val toolsSteps = "Tool steps per answer"
    override val toolsStepsNote = "How many times it may call a tool before it has to answer. " +
        "Each step is a full generation, so this is the main cost."
    override val toolsCostNote = "Describing the tools costs context on every message, so this " +
        "is off by default. Turn it on when you want the model to act, not only answer."

    override val modeAuto = "Auto"
    override val modeAlways = "Always"
    override val modeNever = "Never"
}

// ---------------------------------------------------------------------------

object ArabicStrings : AppStrings {

    override val isRtl = true
    override val defaultVoiceTag = "ar-SA"

    override val navChat = "المحادثة"
    override val navModels = "النماذج"
    override val navDevice = "الجهاز"
    override val navSettings = "الإعدادات"
    override val chatSubtitle = "خاصة، على جهازك"
    override val modelsSubtitle = "إضافة وضبط"
    override val deviceSubtitle = "العتاد والذاكرة"
    override val settingsSubtitle = "الصوت والسلوك"

    override val stopGenerating = "إيقاف التوليد"
    override val stopSpeaking = "إيقاف النطق"
    override val newChat = "محادثة جديدة"

    override val askAnything = "اسأل ما تشاء"
    override val readyWhenYouAre = "جاهز متى ما أردت"
    override val runningOffline = "يعمل بالكامل دون اتصال، على جهازك."
    override val addFirstModel = "أضف نموذجك الأول"
    override val browseModels = "تصفّح النماذج"
    override val privacyNote =
        "كل شيء يعمل على جهازك. لا يُرفع شيء، ولا حاجة إلى حساب."
    override val loadingModel = "جارٍ تحميل النموذج"
    override fun loadingNamed(name: String) = "جارٍ تحميل $name"
    override val warmingUp = "جارٍ التهيئة"
    override val couldNotLoadModel = "تعذّر تحميل النموذج"
    override val tryAgain = "أعد المحاولة"
    override val details = "التفاصيل"
    override val gotIt = "حسنًا"
    override val reasoning = "التفكير"
    override val thinking = "يفكّر"
    override val expand = "توسيع"
    override val collapse = "طيّ"
    override val listen = "استماع"
    override val stop = "إيقاف"
    override val save = "حفظ"
    override val send = "إرسال"
    override val voiceInput = "إدخال صوتي"
    override val stopListening = "إيقاف الاستماع"
    override val listening = "يستمع…"
    override val webSearchOn = "البحث في الويب مُفعّل"
    override val webSearchOff = "البحث في الويب مُعطّل"
    override val searchingWeb = "يبحث في الويب…"
    override val searchingAndReading = "يبحث ويقرأ الصفحات…"
    override fun copyingPercent(percent: Int) = "٪$percent"

    override val routeGreeting = "تحية — رد مباشر"
    override val routeSimple = "سؤال مباشر"
    override val routeLookup = "يحتاج معلومات محدَّثة"
    override val routeReasoning = "يحتاج تحليلًا"
    override val routeSearching = "بحث"
    override val routeThinking = "تفكير"
    override val routeOverridden = "بإعداد يدوي"
    override fun routeLine(base: String, extras: List<String>) =
        if (extras.isEmpty()) base else "$base · ${extras.joinToString(" و")}"

    override val addModel = "إضافة نموذج"
    override val linkByPath = "ربط بمسار"
    override val scan = "فحص"
    override val nothingInstalledYet = "لا توجد نماذج بعد"
    override val pickAModelFile = "اختر ملف نموذج عبر «إضافة»، أو ادفع واحدًا من حاسوبك:"
    override val pushHint = "للنماذج المدفوعة عبر adb. يُشار إلى الملف في مكانه — " +
        "لا يُنسخ شيء، فلا تُستهلك مساحة إضافية."
    override val absolutePath = "المسار الكامل"
    override val link = "ربط"
    override val linkedInPlace = "مربوط"
    override val cancel = "إلغاء"
    override val remove = "حذف"
    override val removeModelTitle = "حذف النموذج؟"
    override fun removeManagedModelBody(name: String, freed: String) =
        "سيُحذف «$name» من مساحة التطبيق، مما يحرّر $freed."
    override fun removeLinkedModelBody(name: String) =
        "سيُزال «$name» من هذه القائمة."
    override val fileLeftUntouched = "الملف على القرص يبقى كما هو."
    override val active = "نشط"
    override val load = "تحميل"
    override val tune = "ضبط"
    override val type = "النوع"
    override val backend = "المعالج"
    override val generation = "التوليد"
    override val maxTokens = "أقصى عدد توكنات"
    override val temperature = "الحرارة"
    override val topK = "Top-K"
    override val reasoningSection = "التفكير"
    override val emitsReasoning = "يُخرج تفكيرًا"
    override val wrapsThinkTags = "يضع تفكيره داخل وسوم <think>"
    override val voiceOutput = "إخراج صوتي"
    override val sampleRate = "معدل العيّنة (هرتز)"
    override val speakerId = "معرّف المتحدث"
    override val sampleRateNote = "يجب أن يطابق معدل العيّنة ما دُرّب عليه النموذج، وإلا " +
        "خرج الصوت أسرع أو أبطأ من اللازم. ضع ملف المفردات ‏«‎<name>.tokens.json»‏ " +
        "بجوار ملف النموذج لضبط النطق."
    override val path = "المسار"
    override val kindText = "نص"
    override val kindAsr = "صوت ← نص"
    override val kindTts = "نص ← صوت"
    override val kindMultimodal = "متعدد الوسائط"
    override val thermalNormal = "طبيعية"
    override val thermalWarm = "دافئ"
    override val thermalHot = "ساخن"
    override val thermalCritical = "حرج"

    override val hardware = "العتاد"
    override val model = "الطراز"
    override val manufacturer = "الشركة المصنّعة"
    override val socVendor = "مورّد المعالج"
    override val cpuCores = "أنوية المعالج"
    override val abis = "معماريات ABI"
    override val unknownSoc = "معالج غير معروف"
    override val memory = "الذاكرة"
    override val physicalAndExtended = "الفعلية والممتدة"
    override val total = "الإجمالي"
    override val free = "المتاح"
    override val ramPlus = "RAM Plus"
    override val effectiveBudget = "الحد الفعلي"
    override val effectiveBudgetNote =
        "الذاكرة المتاحة + الذاكرة الممتدة الحرة. هي التي تحدد ما يمكن تحميله."
    override val ramPlusOn = "مُفعّلة"
    override val ramPlusOff = "غير مُفعّلة. فعّلها من الإعدادات › العناية بالجهاز › الذاكرة › " +
        "RAM Plus لمنح النماذج الكبيرة مساحة أوسع."
    override val mmapNote = "أوزان النموذج تُربط بالذاكرة (mmap)، فتستطيع الصفحات الانسياب " +
        "إلى الذاكرة الممتدة بدل أن يفشل الحجز."
    override val accelerators = "المسرّعات"
    override val gpuAndNpu = "دعم معالج الرسوميات والذكاء"
    override val vulkan = "Vulkan"
    override val openCl = "OpenCL"
    override val nnapi = "NNAPI"
    override val vendorNpuRuntime = "بيئة NPU من المورّد"
    override val notFound = "غير موجودة"
    override val availableWord = "متاحة"
    override val deprecatedOn15 = "مهملة في أندرويد 15 وما بعده"
    override val snapdragonNote = "رُصد معالج كوالكوم من الفئة العليا. يُنصح بمعالج الرسوميات."
    override val howNpuBehaves = "كيف يُختار معالج الذكاء"
    override val npuExplanation = "بيئة تشغيل النماذج المضمّنة توفّر المعالج المركزي " +
        "ومعالج الرسوميات فقط. عند اختيار NPU يُشغَّل النموذج على أفضل خيار متاح فعلًا " +
        "ويُعلَن ذلك في شاشة المحادثة بدل التظاهر. ربط بيئة QNN/Genie من كوالكوم " +
        "يتم في BackendResolver.kt."
    override val storage = "التخزين"
    override val whereModelsLive = "أين تُحفظ النماذج"
    override val freeSpace = "المساحة الحرة"
    override val refresh = "تحديث"
    override val whatFits = "ما الذي يستطيع هذا الجهاز تشغيله"
    override val whatFitsSubtitle = "الأوزان هي السقف، لا التطبيق"
    override fun advisorHeadline(best: String) =
        "بالذاكرة المتاحة الآن، أكبر نموذج يعمل بأريحية هو نحو $best."
    override val advisorNothingFits = "الذاكرة الحرة لا تكفي حتى لنموذج صغير الآن. " +
        "أغلق التطبيقات في الخلفية، أو فعّل RAM Plus."
    override val advisorQuantAdvice = "النموذج الأكبر بتكميم Q4_K_M يفهم أكثر من الأصغر " +
        "بتكميم Q8_0 عند نفس الحجم. النزول من ٨ بت إلى ٤ بت يكلّف نسبة ضئيلة، أما مضاعفة " +
        "عدد المعاملات فتساوي أضعاف ذلك. إن كنت تشغّل 4B بتكميم Q8_0 فإن 8B بتكميم " +
        "Q4_K_M بنفس الحجم تقريبًا وهي نقلة حقيقية."
    override val advisorArabicNote = "في العربية تحديدًا، اختيار النموذج أهم من الحجم وحده — " +
        "بعض العائلات دُرّبت على عربية قليلة فتبقى ضعيفة فيها مهما كبرت. Qwen3 وGemma 3 " +
        "تتعاملان مع العربية جيدًا، وهناك نماذج بُنيت للعربية أولًا مثل ALLaM. تأكد من " +
        "وجود نسخة GGUF قبل التنزيل."
    override val fitComfortable = "مناسب"
    override val fitTight = "على الحافة"
    override val fitTooBig = "أكبر من اللازم"
    override val off = "معطّلة"
    override val unknown = "غير معروف"
    override fun freeOf(total: String) = "متاحة من أصل $total"
    override fun percentInUse(percent: Int) = "٪$percent قيد الاستخدام"

    override val diagnostics = "التشخيص"
    override val errorsAndCrashes = "الأخطاء والانهيارات"
    override val recordedEvents = "الأحداث المسجّلة"
    override val whatWentWrong = "ما الذي أخفق، إن أخفق شيء"
    override val nothingWentWrong = "لم يحدث أي خطأ."
    override val noProblemsRecorded = "لا مشاكل مسجّلة. أي إخفاق — نموذج لا يُحمَّل، بحث " +
        "ينتهي بمهلة، انهيار — سيظهر هنا."
    override val tapForDetails = "اضغط للتفاصيل"
    override val openLog = "فتح السجل"
    override fun reviewCount(count: Int) = "مراجعة $count"
    override fun recordedCount(count: Int) = when (count) {
        1 -> "حدث واحد مسجّل"
        2 -> "حدثان مسجّلان"
        in 3..10 -> "$count أحداث مسجّلة"
        else -> "$count حدثًا مسجّلًا"
    }
    override val copyAll = "نسخ الكل"
    override val clear = "مسح"
    override val close = "إغلاق"
    override val clearDiagnosticsTitle = "مسح سجل التشخيص؟"
    override val clearDiagnosticsBody = "ستُحذف الأحداث المسجّلة من هذا الجهاز."
    override val warnings = "التحذيرات"
    override val diagnosticsIntro = "تُسجَّل الإخفاقات هنا بدل تجاهلها بصمت — " +
        "بما فيها الانهيارات، وهي تبقى بعد إعادة التشغيل."

    override val language = "اللغة"
    override val languageSubtitle = "الواجهة والردود"
    override val interfaceLanguage = "الواجهة"
    override val languageNote = "يغيّر التطبيق بأكمله، بما في ذلك اتجاه التخطيط. " +
        "واختيار العربية يطلب من النموذج أن يجيب بالعربية."
    override val reasoningTitle = "التفكير"
    override val reasoningSubtitle = "كيف يفكّر النموذج"
    override val whenToThink = "متى يفكّر"
    override val thinkAuto = "لا يعمل التفكير إلا حين يحتاجه السؤال — حساب، برمجة، " +
        "مقارنة، «لماذا» و«اشرح». أما التحية فيُرد عليها فورًا بلا مداولة."
    override val thinkAlways = "كل رسالة تحصل على سلسلة تفكير كاملة. دقيق، لكنه بطيء: " +
        "حتى «مرحبا» يُفكَّر فيها."
    override val thinkNever = "التفكير مُعطّل. الردود أسرع ما تكون، والأسئلة الصعبة " +
        "يُجاب عنها دفعة واحدة."
    override val showReasoning = "إظهار التفكير"
    override val showReasoningDesc = "عرض أثر التفكير القابل للطيّ داخل المحادثة."
    override val reasoningDetectedNote = "يُستخرج من <think>…</think> في مخرجات النموذج. " +
        "علّم النموذج بأنه قادر على التفكير من شاشة النماذج."
    override val webSearch = "البحث في الويب"
    override val webSearchSubtitle = "استناد الإجابات إلى مصادر حيّة"
    override val searchTheWeb = "ابحث في الويب"
    override val searchTheWebDesc = "يبحث عن السؤال قبل الإجابة، ويذكر المصادر."
    override val searchOnNote = "تُرسَل أسئلتك إلى DuckDuckGo وويكيبيديا. هذه هي الميزة " +
        "الوحيدة التي تغادر جهازك — وكل ما عداها يبقى دون اتصال. تُستخدم النتائج " +
        "كسياق وتُذكر تحت كل رد."
    override val searchOffNote = "مُعطّل. يجيب النموذج من أوزانه ولا يغادر جهازك شيء. " +
        "تفعيله يرسل أسئلتك إلى DuckDuckGo وويكيبيديا."
    override val whenToSearch = "متى يبحث"
    override val searchAuto = "لا يبحث إلا حين تعتمد الإجابة على شيء متغيّر — أخبار، " +
        "أسعار، طقس، «آخر»، أو حين تطلب منه البحث صراحةً."
    override val searchAlways = "يُبحث عن كل سؤال أولًا. أبطأ، ويرسل قدرًا أكبر مما تكتبه " +
        "إلى خوادم خارجية."
    override val searchNever = "يبقى البحث متوقفًا رغم تفعيله أعلاه — مفيد لإيقافه مؤقتًا " +
        "دون فقدان إعداداتك."
    override val depth = "العمق"
    override val depthQuickLabel = "سريع"
    override val depthDeepLabel = "قراءة الصفحات"
    override val depthQuick = "يعتمد على مقتطفات النتائج. جولة طلبات واحدة، وهي الأسرع."
    override val depthDeep = "يفتح أفضل ثلاث نتائج ويقرأها، فتأتي الإجابة من محتوى الصفحة " +
        "لا من سطرين. أبطأ ويستهلك بيانات أكثر."

    override val searchNothingToDo = "لا يوجد ما يُبحث عنه."
    override val searchUnreachable = "تعذّر الوصول إلى الويب. تحقّق من اتصالك، أو أوقف " +
        "البحث لتأتي الإجابة من النموذج وحده."
    override val searchNoResults = "لا توجد نتائج ويب مفيدة لهذا السؤال."
    override fun modelFileNotFound(path: String) = "لم يُعثر على ملف النموذج:\n$path"
    override fun modelFileNotReadable(path: String) =
        "لا يستطيع التطبيق قراءة ملف النموذج:\n$path\n\n" +
            "أعد إضافته عبر «إضافة نموذج» ليصبح داخل مساحة التطبيق."
    override val notEnoughMemoryTitle = "الذاكرة لا تكفي لتحميل هذا النموذج."
    override fun memoryLine(label: String, value: String) = "$label: $value"
    override val availableRamLabel = "الذاكرة المتاحة"
    override val freeExtendedLabel = "الذاكرة الممتدة الحرة (RAM Plus)"
    override val ramPlusNotEnabled = "الذاكرة الممتدة (RAM Plus): غير مُفعّلة"
    override val notEnoughMemoryAdvice = "أغلق التطبيقات في الخلفية، أو فعّل RAM Plus من " +
        "الإعدادات › العناية بالجهاز › الذاكرة، أو استخدم نموذجًا بتكميم أصغر."
    override fun ggufBackendIgnored(pref: String) =
        "نماذج GGUF تعمل على المعالج المركزي في هذه النسخة، فلا ينطبق خيار $pref. "
    override fun threadsOfCores(threads: Int, cores: Int) = "$threads خيوط من $cores نواة"
    override fun cpuAsRequested(soc: String) = "يعمل على معالج $soc المركزي، كما طلبت."
    override val noGpuDriver = "لا يوجد تعريف Vulkan أو OpenCL هنا — التشغيل على المعالج المركزي."
    override fun autoNoGpu(soc: String) =
        "تلقائي: لا تعريف حوسبة لمعالج الرسوميات في $soc، فالمعالج المركزي إذًا."
    override val autoGpuFits = "تلقائي: معالج الرسوميات — حجم هذا النموذج يسمح بذلك."
    override fun autoCpu(reason: String) = "تلقائي: المعالج المركزي. $reason"
    override val gpuTooLargeModel = "هذا النموذج أكبر من أن يُحجز على معالج رسوميات هاتف، " +
        "لذا يُربط بالذاكرة على المعالج المركزي."
    override val gpuNotEnoughMemory =
        "الذاكرة الحرة لا تكفي لإبقاء الأوزان مقيمة على معالج الرسوميات."
    override val gpuSwitchIfItFails = "انتقل إلى المعالج المركزي إن فشل التحميل."
    override val npuLinked = "بيئة NPU مربوطة — سيُفوَّض إليها التشغيل."
    override val npuDetectedUnusable = "في هذا الهاتف معالج ذكاء من المورّد، لكن لا توجد " +
        "واجهة عامة تتيحه لنموذج GGUF أو ‎.task عام، فلا يمكن استخدامه هنا."
    override val npuNoRuntime = "لا توجد بيئة NPU صالحة للاستخدام في هذا الجهاز."
    override val thisDevice = "هذا الجهاز"
    override fun unsupportedFormat(fileName: String) =
        "«$fileName» ليس صيغة نموذج يقرؤها هذا التطبيق.\n\n" +
            "المدعوم: GGUF (‎.gguf) وحزم MediaPipe (‎.task).\n\n" +
            "الملفات المنتهية بـ ‎.safetensors أو ‎.pth تأتي من مستودعات PyTorch " +
            "وتحتاج تحويلًا إلى GGUF على حاسوب أولًا."
    override val unrecognisedFormat = "صيغة نموذج غير معروفة."
    override fun notEnoughStorage(megabytes: Long) =
        "المساحة الحرة لا تكفي لاستيراد هذا النموذج (يحتاج $megabytes ميغابايت)."
    override val cannotOpenFile = "تعذّر فتح الملف المختار."
    override fun noFileAt(path: String) = "لا يوجد ملف في $path"
    override fun fileNotReadable(path: String) = "الملف في $path لا يستطيع التطبيق قراءته."
    override fun cannotPlayAudio(sampleRateHz: Int) =
        "هذا الجهاز لا يستطيع تشغيل صوت بمعدل $sampleRateHz هرتز."
    override val speechUnavailable = "التعرّف على الكلام غير متاح في هذا الجهاز."
    override val speechErrorAudio = "خطأ في تسجيل الصوت."
    override val speechErrorClient = "خطأ في عميل التعرّف على الكلام."
    override val speechErrorPermission = "رُفض إذن الميكروفون."
    override val speechErrorNetwork = "خطأ في الشبكة (ثبّت حزمة لغة تعمل دون اتصال)."
    override val speechErrorTimeout = "انتهت مهلة الشبكة."
    override val speechErrorNoMatch = "لم ألتقط ما قلته — أعد المحاولة."
    override val speechErrorBusy = "محرّك التعرّف مشغول."
    override val speechErrorServer = "خطأ في خادم التعرّف."
    override val speechErrorNoSpeech = "لم يُرصد أي كلام."
    override fun speechErrorOther(code: Int) = "أخفق التعرّف على الكلام (رمز $code)."
    override val ggufRuntimeMissing = "بيئة تشغيل GGUF غير متوفّرة في هذه النسخة.\n\n" +
        "استخدم نموذج ‎.task من MediaPipe، أو أعد بناء التطبيق مع تفعيل المكوّن الأصلي."
    override val replyTruncated =
        "انقطع الرد عند حدّ التوكنات — ارفع «أقصى عدد توكنات» لهذا النموذج"
    override val thermalPaused = "توقّف مؤقت: الجهاز يسخن. " +
        "سيبقى التوليد أبطأ حتى يبرد."
    override val thermalTooHot = "الجهاز أسخن من أن يشغّل النموذج الآن. " +
        "امهله لحظة ليبرد."
    override val modelLoadFailedFallback = "تعذّر تحميل النموذج."
    override val copyingModel = "جارٍ نسخ النموذج…"
    override val importFailed = "فشل الاستيراد."
    override val importCancelled = "أُلغي الاستيراد."
    override fun addedModel(name: String) = "أُضيف «$name»."
    override val couldNotRegisterPath = "تعذّر تسجيل هذا المسار."
    override val noNewModels = "لا نماذج جديدة في ‎/data/local/tmp/llm. الملفات في أماكن " +
        "أخرى — بما فيها مجلد التنزيلات — تُضاف عبر «إضافة»، وهي التي تمنح الإذن " +
        "بالملف الذي تختاره."
    override fun foundModels(count: Int) = when (count) {
        1 -> "عُثر على نموذج واحد."
        2 -> "عُثر على نموذجين."
        in 3..10 -> "عُثر على $count نماذج."
        else -> "عُثر على $count نموذجًا."
    }
    override fun savedAudioTo(path: String) = "حُفظ في $path"
    override val couldNotSaveAudio = "تعذّر حفظ الصوت."
    override val ttsSystemUnavailable = "محرّك النطق في النظام غير متاح. ثبّت محرّك نطق " +
        "أو فعّله من إعدادات النظام."
    override val ttsNoModelSelected = "لم يُختر نموذج نطق. أضف واحدًا من شاشة النماذج " +
        "واضبط نوعه على «نص ← صوت»."
    override val ttsModelLoadFailed = "تعذّر تحميل نموذج النطق المختار."
    override val ttsRuntimeMissing = "بيئة تشغيل نماذج النطق غير موجودة في هذه النسخة. " +
        "استخدم محرّك النظام بدلًا منها."
    override val ttsEngineSystem = "محرّك النظام"
    override val ttsEngineCloud = "صوت سحابي"
    override val cloudAzure = "Azure Neural"
    override val cloudElevenLabs = "ElevenLabs"
    override val cloudTitle = "الصوت السحابي"
    override val cloudSubtitle = "أنضج صوت عربي، بثمن"
    override val cloudPrivacy = "هذا يرسل نصّ كل رد يُنطق إلى خادم طرف ثالث. وهي الميزة " +
        "الوحيدة هنا التي تفعل ذلك — النموذج وكل ما عداه، عدا تأريض البحث، يبقى على " +
        "جهازك. استعملها عن قصد."
    override val cloudProvider = "المزوّد"
    override val cloudApiKey = "مفتاح الواجهة"
    override val cloudRegion = "المنطقة"
    override val cloudVoice = "الصوت"
    override val cloudVoiceIdHint = "معرّف الصوت من حسابك"
    override val cloudNeedsKey = "أدخل مفتاح الواجهة أولًا."
    override val cloudNeedsRegion = "أدخل المنطقة التي يتبعها مفتاحك، مثل westeurope."
    override val cloudNeedsVoiceId = "الصق معرّف صوت من حسابك في ElevenLabs."
    override val cloudEmptyAudio = "لم يُرجع الخادم أي صوت."
    override val cloudRequestFailed = "أخفق طلب النطق."
    override fun cloudHttpError(code: Int, detail: String) =
        if (detail.isBlank()) "أرجع خادم النطق رمز HTTP $code."
        else "أرجع خادم النطق رمز HTTP $code: $detail"
    override val cloudKeyStorageNote = "يُحفظ المفتاح في مساحة هذا التطبيق الخاصة على هذا " +
        "الجهاز، ولا يُرسل إلا إلى المزوّد الذي اخترته."
    override val cloudShowKey = "إظهار"
    override val cloudHideKey = "إخفاء"
    override val ttsEngineModel = "نموذج النطق الخاص بي"
    override fun ggufLoadFailed(detail: String) = "تعذّر تحميل نموذج GGUF هذا.\n\n$detail"
    override fun taskLoadFailed(detail: String, backend: String) =
        "لم تستطع بيئة التشغيل تحميل هذا النموذج.\n\n$detail\n\n" +
            "تأكد أنه حزمة ‎.task متوافقة مع MediaPipe، وأن المعالج المختار " +
            "($backend) مدعوم."
    override val performance = "الأداء"
    override val performanceSubtitle = "الحرارة والبطارية"
    override val thermalSupportedNote = "يراقب التطبيق حرارة الجهاز ويقلّل عدد خيوط المعالجة " +
        "كلما ارتفعت، ويتوقف مؤقتًا إذا اشتدّت. ويُطلب وضع الأداء المستدام لتبقى " +
        "الترددات ثابتة بدل أن تقفز ثم تُخنَق."
    override val thermalUnsupportedNote = "إصدار أندرويد هذا لا يبلّغ عن الحالة الحرارية، " +
        "لذا يُستخدم عدد خيوط متحفّظ دائمًا. ويُطلب وضع الأداء المستدام مع ذلك."
    override val speechOutput = "إخراج الصوت"
    override val speechOutputSubtitle = "قراءة الردود بصوت مسموع"
    override val engine = "المحرّك"
    override val speechEngineLabel = "محرّك النطق"
    override val speechEngineDefault = "افتراضي النظام"
    override val speechEngineNote = "المحرّك أهم للعربية من الصوت. الهواتف تشحن غالبًا " +
        "بمحرّك الشركة كافتراضي بينما محرّك أفضل مثبّت بجواره. وإن لم تجد سوى واحد، " +
        "فتثبيت «Speech Services by Google» من متجر Play يضيف أصواتًا عربية عصبية، " +
        "مجانًا وتعمل دون اتصال بعد تنزيلها."
    override val voiceLabel = "الصوت"
    override val voiceAutoBest = "أفضل المتاح"
    override val voiceNoneInstalled = "لا يوجد صوت مثبّت لهذه اللغة. أضف واحدًا من إعدادات " +
        "النظام تحت «تحويل النص إلى كلام»، ثم أعد فتح هذه الشاشة."
    override val voiceNote = "أندرويد يختار عادةً أقدم صوت مثبّت للغة. والاختيار المتعمّد " +
        "هو غالبًا الفرق بين صوت آلي وصوت طبيعي، دون تنزيل أي شيء."
    override fun voiceQuality(quality: Int) = when {
        quality >= 500 -> "عالية جدًا"
        quality >= 400 -> "عالية"
        quality >= 300 -> "متوسطة"
        else -> "منخفضة"
    }
    override val voiceNeedsNetwork = "يحتاج إنترنت"
    override val systemEngineNote = "محرّك أندرويد المدمج. يعمل مباشرة ويبقى دون اتصال " +
        "بعد تثبيت حزمة صوت."
    override val litertMissingNote = "بيئة تشغيل نماذج النطق غير موجودة في هذه النسخة، " +
        "فلا تعمل نماذج الصوت المخصّصة. استخدم محرّك النظام بدلًا منها."
    override fun usingVoiceModel(name: String, rate: Int) = "يستخدم «$name» بمعدل $rate هرتز."
    override val noVoiceModelNote = "لم يُختر نموذج صوت. أضف واحدًا من شاشة النماذج واضبط " +
        "نوعه على «نص ← صوت»."
    override val speakAutomatically = "نطق الردود تلقائيًا"
    override val speakAutomaticallyDesc = "يقرأ كل رد بصوت مسموع فور اكتماله."
    override val speed = "السرعة"
    override val pitch = "طبقة الصوت"
    override val voiceInputTitle = "الإدخال الصوتي"
    override val voiceInputSubtitle = "أملِ بدل الكتابة"
    override val noRecognizer = "لا يتوفّر محرّك تعرّف على الكلام في هذا الجهاز."
    override val onDeviceRecognition = "التعرّف على الجهاز متاح — صوتك لا يغادر الهاتف."
    override val cloudRecognition = "التعرّف متاح، لكن لم تُوجد حزمة تعمل على الجهاز. " +
        "ثبّت حزمة لغة دون اتصال من إعدادات النظام ليبقى التفريغ محليًا."
    override val voiceLanguage = "لغة الصوت"
    override val voiceLanguageSubtitle = "للإدخال والإخراج الصوتي"
    override val languageTag = "رمز اللغة"
    override val systemPrompt = "توجيه النظام"
    override val systemPromptSubtitle = "اضبط سلوك المساعد"
    override val systemPromptPlaceholder = "مثال: أنت مساعد موجز."
    override val systemPromptNote = "يُضاف قبل كل رسالة. يسري على الرسالة التالية."
    override val replyLanguageInstruction =
        "أجب دائمًا باللغة العربية الفصحى المبسّطة، بوضوح واختصار، " +
            "ما لم يطلب المستخدم لغة أخرى صراحةً."
    override val assistantGuidance = """
        أنت مساعد دقيق يعمل على هاتف المستخدم.

        - أجب عن السؤال المطروح، ولا تُعِد صياغته قبل الإجابة.
        - إن كنت لا تعرف فقل ذلك. لا تخترع رقمًا ولا تاريخًا ولا اسمًا ولا رابطًا.
        - اجعل الرد بقدر السؤال. لا تُطِل إلا إذا طُلب منك.
        - إن كان الطلب غامضًا فاسأل سؤالًا واحدًا قصيرًا بدل التخمين.
        - استعمل ما قيل سابقًا في هذه المحادثة.
        - إن طُلبت خطوات فاكتبها مرقّمة، وإن طُلبت معلومة فاذكرها مباشرة.
        - لا تبدأ بعبارة مجاملة ولا تختم بعرض المساعدة.
        - افهم اللهجات العربية جميعها، وأجب بالفصحى المبسّطة.
    """.trimIndent()
    override val guidanceTitle = "قواعد التعامل"
    override val guidanceDescription = "امنح النموذج مجموعة قواعد قصيرة يسير عليها."
    override val guidanceNote = "النموذج الكبير يستنتج هذه القواعد وحده، والصغير لا يفعل. " +
        "وهي معظم الفرق بين مساعد يُطيل ويخمّن ومساعد يجيب. توجيهك الخاص أدناه يُضاف بعدها."

    override val navStudio = "الاستوديو"
    override val studioSubtitle = "اصنع صفحة بوصفها"
    override val studioEmptyTitle = "صِف ما تريد بناءه"
    override val studioEmptyBody = "يكتب النموذج صفحة ويب مكتملة بذاتها — HTML و CSS " +
        "وJavaScript في ملف واحد — وتعمل هنا فورًا."
    override val studioPromptHint = "آلة حاسبة بأزرار كبيرة…"
    override val studioBuild = "ابنِ"
    override val studioPreview = "المعاينة"
    override val studioCode = "الكود"
    override val studioRun = "تشغيل"
    override val studioNewProject = "جديد"
    override val studioProjects = "المشاريع"
    override val studioSave = "حفظ"
    override val studioSaved = "حُفظ"
    override val studioUntitled = "صفحة بلا عنوان"
    override val studioBuilding = "يكتب الصفحة…"
    override val studioNoCodeBack = "رد النموذج بلا كود. جرّب وصف الصفحة بشكل أوضح — " +
        "ماذا تعرض، وماذا تفعل."
    override val studioTooLarge = "هذه الصفحة صارت أطول من أن تُرسل للتعديل. عدّل الكود " +
        "مباشرة، أو ابدأ مشروعًا جديدًا."
    override val studioOfflineNote = "المعاينة تعمل دون اتصال: لا تصل إلى الشبكة، فالصفحات " +
        "التي تجلب بيانات أو تحمّل من CDN لن تعمل هنا."
    override fun studioExample(index: Int) = when (index) {
        0 -> "حاسبة بقشيش"
        1 -> "مؤقّت بومودورو مع بدء وإعادة"
        2 -> "منتقي ألوان يعرض الرمز السداسي"
        else -> "قائمة مهام تُحفظ في المتصفح"
    }
    override val studioSystemPrompt = """
        تكتب صفحات ويب في ملف واحد، وتعمل فعلًا.

        المخرجات
        - مستند HTML واحد كامل داخل كتلة ```html. لا شيء قبلها ولا بعدها.
        - كل شيء في هذا الملف: CSS داخل <style> وJavaScript داخل <script>.
        - إذا أُعطيت صفحة قائمة وطُلب تعديل، أعد الملف كاملًا بعد التعديل —
          لا مقتطفًا ولا وصفًا للتغيير.

        اجعلها حقيقية لا واجهة وهمية
        - كل عنصر تحكم يجب أن يفعل ما يبدو أنه يفعله. زر لا يشغّل وظيفته
          أسوأ من عدم وجوده.
        - لا معالِجات فارغة، ولا TODO، ولا «أكمل هنا»، ولا بيانات وهمية
          مكان حساب تستطيع إجراءه.
        - عالج الحالة الفارغة والمدخل الخاطئ، لا المسار الناجح وحده. ماذا
          تعرض قبل أن يكتب المستخدم شيئًا، أو حين يكتب حروفًا في حقل أرقام؟
        - لا شيء هنا يصل إلى الشبكة. لا CDN ولا خدمة خطوط ولا رابط صورة
          ولا fetch. استعمل خطوط النظام وCSS وSVG مضمّنًا.

        ابنِها لهاتف يُمسك بيد واحدة
        - وسم viewport. مساحة اللمس 44px على الأقل، وحجم النص 16px فأكثر.
        - التخطيط بـ flexbox أو grid ليصمد على أي عرض شاشة. ولا تحدد عرضًا
          ثابتًا بالبكسل للصفحة نفسها.
        - تباين مقروء، وخلفية داكنة ما لم يُطلب غير ذلك.

        الانضباط
        - ابنِ ما طُلب بالضبط، كاملًا. ولا تضف ميزات لم يطلبها أحد. حاسبة
          البقشيش حاسبة بقشيش، لا نظام ميزانية.
        - سمِّ الأشياء بأسمائها: الإجمالي، لا x1.
        - علّق فقط حيث لا يكون السبب واضحًا من الكود. اذكر *لماذا*، ولا تُعِد
          وصف *ماذا*.
    """.trimIndent()
    override fun studioCreateTurn(request: String) =
        "ابنِ هذا كملف HTML واحد:\n\n$request"
    override fun studioEditTurn(code: String, request: String) =
        "هذه الصفحة الحالية:\n\n```html\n$code\n```\n\n" +
            "طبّق هذا التغيير وأعد الملف كاملًا:\n\n$request"


    override val navTerminal = "الطرفية"
    override val terminalSubtitle = "طرفية حقيقية، وما ينفّذه النموذج فيها"
    override val terminalHint = "اكتب أمرًا"
    override val terminalClear = "مسح"
    override val terminalRun = "نفّذ"
    override val terminalEmpty = "لم يُنفَّذ شيء بعد. جرّب `ls` أو `getprop ro.product.model` " +
        "أو `cat /proc/cpuinfo`."
    override val terminalWorkspaceNote = "تُنفَّذ الأوامر في مساحة هذا التطبيق الخاصة، وبصلاحية " +
        "مستخدمه. الأمر `cd` يُحفظ بين الأوامر، وما عداه لا."
    override val terminalLimitsNote = "لا صلاحية جذر، وأدوات أندرويد (toybox) لا أدوات جنو — " +
        "فلا bash ولا python ولا curl ولا git ما لم تُثبّتها، وبعض الخيارات مختلفة. " +
        "و`pm` و`settings` ومعظم `dumpsys` سترد برفض الصلاحية. هذا سلوك أندرويد، لا نقص هنا."
    override val terminalEmptyCommand = "لا شيء لتنفيذه."
    override val terminalRefused = "مرفوض: هذا إمّا يُعلّق الجهاز أو يُتلف شيئًا بلا رجعة. " +
        "وهي القائمة القصيرة الوحيدة التي لا تنفّذها الطرفية."
    override val terminalTimedOut = "أُوقف: استغرق وقتًا طويلًا."
    override val terminalTruncated = "قُطع الخرج — أطول من أن يُحفظ."
    override val terminalCouldNotStart = "تعذّر تشغيل هذا الأمر."
    override fun terminalExitCode(code: Int) = "انتهى برمز خروج $code وبلا خرج."

    override val toolsHeader = "لديك أدوات. حين تكون الأداة أفضل من التخمين، أصدر نداءً " +
        "واحدًا فقط، وحده، بلا أي نص آخر:"
    override val toolsRules = """
        القواعد:
        - نداء واحد في الرد. توقّف بعده؛ ستصلك النتيجة ثم تُكمل.
        - لا تخمّن قيمة تستطيع أداة أن تعطيك إياها: التاريخ، ناتج عملية حسابية،
          محتوى ملف، أي شيء عن هذا الجهاز، أي شيء بعد تاريخ تدريبك.
        - لا تختلق نتيجة أداة أبدًا. إن أخفق النداء فقل ذلك وجرّب طريقًا آخر.
        - إن كنت تعرف الجواب أصلًا فأجب مباشرة. نداء الأداة كلفة لا طقس.
        - بعد وصول النتيجة، أجب الشخص بلغته. ولا تُعِد عليه الخرج الخام
          إلا إن طلب رؤيته.
    """.trimIndent()
    override val toolsAvailable = "الأدوات المتاحة:"
    override val toolsOptional = "اختياري"
    override val toolResultLabel = "نتيجة الأداة"
    override val toolFailedLabel = "(أخفق النداء)"
    override val toolNoOutput = "(بلا خرج)"
    override val toolContinueInstruction = "الآن أجب الشخص مستعينًا بهذه النتيجة. " +
        "ولا تنادِ أداة أخرى إلا إن كنت فعلًا ما زلت تحتاجها."
    override fun toolUnknown(name: String) = "لا توجد أداة باسم «$name»."
    override fun toolMissingArg(name: String) = "ينقص وسيط مطلوب: $name."
    override fun toolNotPermitted(risk: String) =
        "غير مسموح: الأوامر من هذا النوع ($risk) معطّلة في الإعدادات."
    override fun toolRunning(name: String) = "ينفّذ $name…"
    override val toolStepLimitReached = "توقّف عند حد خطوات الأدوات. أجب بما لديك."

    override val toolShellSummary = "نفّذ أمرًا في طرفية هذا الجهاز واقرأ خرجه."
    override val toolShellCommandParam = "الأمر كما تكتبه تمامًا"
    override val toolReadFileSummary = "اقرأ ملفًا نصيًا."
    override val toolPathParam = "مسار الملف"
    override val toolLinesParam = "كم سطرًا تقرأ (الافتراضي ٢٠٠)"
    override val toolMoreLines = "… وهناك أسطر أخرى؛ أعد القراءة بقيمة \"lines\" أكبر."
    override fun toolNoSuchFile(path: String) = "لا يوجد ملف: $path"
    override fun toolIsDirectory(path: String) = "$path مجلد — استخدم list_files."
    override fun toolReadFailed(path: String) = "تعذّرت قراءة $path."
    override val toolWriteFileSummary = "اكتب ملفًا نصيًا داخل مساحة العمل."
    override val toolWritePathParam = "مسار داخل مساحة العمل"
    override val toolContentParam = "النص الكامل المراد كتابته"
    override val toolAppendParam = "\"true\" للإضافة في النهاية بدل الاستبدال"
    override fun toolOutsideWorkspace(path: String) =
        "$path خارج مساحة العمل. الكتابة محصورة داخلها."
    override fun toolWroteBytes(path: String, bytes: Int) = "كُتب $bytes بايت في $path."
    override fun toolWriteFailed(path: String) = "تعذّرت الكتابة في $path."
    override val toolListFilesSummary = "اعرض ما في مجلد."
    override val toolListPathParam = "المجلد (الافتراضي: الحالي)"
    override val toolEmptyDirectory = "(فارغ)"
    override fun toolNotADirectory(path: String) = "$path ليس مجلدًا."
    override fun toolCannotList(path: String) = "تعذّر عرض محتوى $path."
    override val toolWebSearchSummary = "ابحث في الويب عمّا لا تعرفه."
    override val toolQueryParam = "ما الذي تبحث عنه"
    override val toolSearchFailed = "لم يتم البحث."
    override val toolNowSummary = "التاريخ والوقت الحاليان. استخدمها بدل التخمين."
    override val toolCalcSummary = "احسب تعبيرًا حسابيًا بدقة."
    override val toolExpressionParam = "مثل 25 * 17 أو (3+4)^2"
    override fun toolBadExpression(expression: String) = "ليس تعبيرًا حسابيًا: $expression"
    override val toolDeviceSummary = "حقائق عن الهاتف الذي يعمل عليه."

    override val toolsTitle = "الأدوات"
    override val toolsSettingsSubtitle = "ما يستطيع النموذج فعله غير الكلام"
    override val toolsEnable = "اسمح للنموذج باستخدام الأدوات"
    override val toolsEnableDesc = "يستطيع أن يبحث ويحسب ويقرأ الملفات وينفّذ الأوامر، " +
        "بدل أن يخمّن."
    override val toolsAllowShell = "الطرفية"
    override val toolsAllowShellDesc = "اسمح له بتنفيذ أوامر الطرفية. كل ما ينفّذه يظهر في " +
        "صفحة الطرفية."
    override val toolsAllowWrites = "اسمح بالكتابة"
    override val toolsAllowWritesDesc = "اسمح للأوامر بتغيير الملفات. عند الإيقاف تكون القراءة فقط."
    override val toolsAllowDangerous = "اسمح بأوامر النظام"
    override val toolsAllowDangerousDesc = "مثل pm و settings والحذف المتكرر. أكثرها يحتاج " +
        "صلاحية جذر وسيخفق أصلًا — لكن اتركه مطفأً ما لم يكن لديك سبب."
    override val toolsSteps = "خطوات الأدوات لكل إجابة"
    override val toolsStepsNote = "كم مرة يجوز له أن ينادي أداة قبل أن يجيب. كل خطوة توليد " +
        "كامل، وهي الكلفة الأساسية."
    override val toolsCostNote = "وصف الأدوات يكلّف سياقًا في كل رسالة، لذا فهي مطفأة افتراضيًا. " +
        "شغّلها حين تريد من النموذج أن يعمل لا أن يجيب فقط."

    override val modeAuto = "تلقائي"
    override val modeAlways = "دائمًا"
    override val modeNever = "أبدًا"
}

/**
 * The active language, readable from outside Compose.
 *
 * Engines and the search service raise user-facing messages far below the UI
 * layer, and those were the messages actually seen in English on the device.
 * Kept in sync by [com.example.ondevicellm.core.SettingsStore].
 */
object Localization {
    @Volatile
    var strings: AppStrings = EnglishStrings
        private set

    fun apply(language: AppLanguage) {
        strings = language.resolve()
    }
}
