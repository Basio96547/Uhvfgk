package com.example.ondevicellm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.ondevicellm.audio.CloudTts
import com.example.ondevicellm.audio.CloudTtsProvider
import com.example.ondevicellm.llm.RoutingMode
import com.example.ondevicellm.web.SearchDepth
import java.util.Locale

/** Which engine turns replies into speech. */
enum class TtsEngine {
    /** Android's built-in engine — always available, offline with a voice pack. */
    SYSTEM,

    /** A TTS model the user added on the Models screen. */
    MODEL,

    /**
     * A hosted service. The only option that sends text off the device, and
     * the only one that currently sounds genuinely human in Arabic.
     */
    CLOUD,
    ;

    fun label(s: AppStrings): String = when (this) {
        SYSTEM -> s.ttsEngineSystem
        MODEL -> s.ttsEngineModel
        CLOUD -> s.ttsEngineCloud
    }
}

data class AppSettings(
    /** Interface language, layout direction, and the language replies come back in. */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /**
     * Prepend the built-in house rules. On by default: a small model without
     * them pads, guesses and drifts, and that is exactly what reads as the
     * assistant not understanding you.
     */
    val guidanceEnabled: Boolean = true,
    val systemPrompt: String = "",
    /**
     * When reasoning runs. AUTO lets the router decide per message — a greeting
     * shouldn't cost a chain of thought.
     */
    val thinkingMode: RoutingMode = RoutingMode.AUTO,
    /** Show the reasoning trace in the chat UI. */
    val showThinking: Boolean = true,
    val voiceLanguageTag: String = Locale.getDefault().toLanguageTag(),
    /**
     * A specific system voice, by the engine's own name. Null means "let the
     * app pick the best installed one", which is the right default because the
     * engine's own default is usually the oldest voice on the device.
     */
    val systemVoiceName: String? = null,
    /**
     * A specific speech engine, by package name. Null uses the system default —
     * which on many phones is a vendor engine with a weaker Arabic voice than
     * one already installed alongside it.
     */
    val systemVoiceEngine: String? = null,
    // ---- Hosted speech ----
    val cloudProvider: CloudTtsProvider = CloudTtsProvider.AZURE,
    /** Stored in app-private storage and sent only to the chosen provider. */
    val cloudApiKey: String = "",
    val cloudRegion: String = "",
    val cloudVoice: String = CloudTts.DEFAULT_AZURE_VOICE,
    // ---- Speech output ----
    val ttsEngine: TtsEngine = TtsEngine.SYSTEM,
    /** Speak each reply as soon as it finishes generating. */
    val autoSpeakReplies: Boolean = false,
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    // ---- Web grounding ----
    /**
     * Off by default: the app is offline-first, and enabling this sends the
     * question to third-party servers. The user opts in explicitly.
     */
    val webSearchEnabled: Boolean = false,
    /** When search runs, once enabled. AUTO searches only when the question needs it. */
    val searchMode: RoutingMode = RoutingMode.AUTO,
    /** Snippets only, or open the top pages and read them. */
    val searchDepth: SearchDepth = SearchDepth.QUICK,
    // ---- Tools ----
    /**
     * Whether the model may act rather than only answer.
     *
     * Off by default, and not out of caution: describing the tools costs
     * context on every single message, and a model that does not need them
     * pays that anyway. It is worth paying when you want it to do things.
     */
    val toolsEnabled: Boolean = false,
    /** The shell tool specifically — the broadest one, so it has its own switch. */
    val toolShellEnabled: Boolean = true,
    /** Let commands change files. Off leaves the model able to look, not touch. */
    val toolAllowWrites: Boolean = false,
    /** `pm`, `settings`, recursive delete. Almost all of it needs root and fails. */
    val toolAllowDangerous: Boolean = false,
    /** Tool calls allowed before the model has to answer. Each one is a full generation. */
    val toolMaxSteps: Int = 3,
    // ---- Reading images ----
    /**
     * Read text out of pages that have none of their own.
     *
     * Off by default because it is the one part of reading a PDF that leaves
     * the device: a picture of each unreadable page is sent to be read.
     */
    val ocrEnabled: Boolean = false,
    /** Google Cloud Vision key, stored in app-private storage. */
    val ocrApiKey: String = "",
)

/** Small SharedPreferences-backed settings store exposed as a StateFlow. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read().also { Localization.apply(it.language) })
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        language = AppLanguage.entries
            .firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, null) }
            ?: AppLanguage.SYSTEM,
        guidanceEnabled = prefs.getBoolean(KEY_GUIDANCE, true),
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
        thinkingMode = RoutingMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_THINKING_MODE, null) }
            ?: RoutingMode.AUTO,
        showThinking = prefs.getBoolean(KEY_SHOW_THINKING, true),
        voiceLanguageTag = prefs.getString(KEY_VOICE_LANG, null)
            ?: Locale.getDefault().toLanguageTag(),
        systemVoiceName = prefs.getString(KEY_SYSTEM_VOICE, null),
        systemVoiceEngine = prefs.getString(KEY_SYSTEM_ENGINE, null),
        cloudProvider = CloudTtsProvider.entries
            .firstOrNull { it.name == prefs.getString(KEY_CLOUD_PROVIDER, null) }
            ?: CloudTtsProvider.AZURE,
        cloudApiKey = prefs.getString(KEY_CLOUD_KEY, "").orEmpty(),
        cloudRegion = prefs.getString(KEY_CLOUD_REGION, "").orEmpty(),
        cloudVoice = prefs.getString(KEY_CLOUD_VOICE, null) ?: CloudTts.DEFAULT_AZURE_VOICE,
        ttsEngine = TtsEngine.entries
            .firstOrNull { it.name == prefs.getString(KEY_TTS_ENGINE, null) }
            ?: TtsEngine.SYSTEM,
        autoSpeakReplies = prefs.getBoolean(KEY_AUTO_SPEAK, false),
        speakingRate = prefs.getFloat(KEY_SPEAKING_RATE, 1.0f),
        pitch = prefs.getFloat(KEY_PITCH, 1.0f),
        webSearchEnabled = prefs.getBoolean(KEY_WEB_SEARCH, false),
        searchMode = RoutingMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_SEARCH_MODE, null) }
            ?: RoutingMode.AUTO,
        toolsEnabled = prefs.getBoolean(KEY_TOOLS_ENABLED, false),
        toolShellEnabled = prefs.getBoolean(KEY_TOOL_SHELL, true),
        toolAllowWrites = prefs.getBoolean(KEY_TOOL_WRITES, false),
        toolAllowDangerous = prefs.getBoolean(KEY_TOOL_DANGEROUS, false),
        toolMaxSteps = prefs.getInt(KEY_TOOL_STEPS, 3),
        ocrEnabled = prefs.getBoolean(KEY_OCR_ENABLED, false),
        ocrApiKey = prefs.getString(KEY_OCR_KEY, "").orEmpty(),
        searchDepth = SearchDepth.entries
            .firstOrNull { it.name == prefs.getString(KEY_SEARCH_DEPTH, null) }
            ?: SearchDepth.QUICK,
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        // Kept in step so code below the UI layer raises messages in the right
        // language too — model-load failures are the ones users actually hit.
        Localization.apply(updated.language)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_LANGUAGE, updated.language.name)
            .putBoolean(KEY_GUIDANCE, updated.guidanceEnabled)
            .putString(KEY_SYSTEM_PROMPT, updated.systemPrompt)
            .putString(KEY_THINKING_MODE, updated.thinkingMode.name)
            .putBoolean(KEY_SHOW_THINKING, updated.showThinking)
            .putString(KEY_VOICE_LANG, updated.voiceLanguageTag)
            .putString(KEY_SYSTEM_VOICE, updated.systemVoiceName)
            .putString(KEY_SYSTEM_ENGINE, updated.systemVoiceEngine)
            .putString(KEY_CLOUD_PROVIDER, updated.cloudProvider.name)
            .putString(KEY_CLOUD_KEY, updated.cloudApiKey)
            .putString(KEY_CLOUD_REGION, updated.cloudRegion)
            .putString(KEY_CLOUD_VOICE, updated.cloudVoice)
            .putString(KEY_TTS_ENGINE, updated.ttsEngine.name)
            .putBoolean(KEY_TOOLS_ENABLED, updated.toolsEnabled)
            .putBoolean(KEY_TOOL_SHELL, updated.toolShellEnabled)
            .putBoolean(KEY_TOOL_WRITES, updated.toolAllowWrites)
            .putBoolean(KEY_TOOL_DANGEROUS, updated.toolAllowDangerous)
            .putInt(KEY_TOOL_STEPS, updated.toolMaxSteps)
            .putBoolean(KEY_OCR_ENABLED, updated.ocrEnabled)
            .putString(KEY_OCR_KEY, updated.ocrApiKey)
            .putBoolean(KEY_AUTO_SPEAK, updated.autoSpeakReplies)
            .putFloat(KEY_SPEAKING_RATE, updated.speakingRate)
            .putFloat(KEY_PITCH, updated.pitch)
            .putBoolean(KEY_WEB_SEARCH, updated.webSearchEnabled)
            .putString(KEY_SEARCH_MODE, updated.searchMode.name)
            .putString(KEY_SEARCH_DEPTH, updated.searchDepth.name)
            .apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_GUIDANCE = "guidanceEnabled"
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_THINKING_MODE = "thinkingMode"
        const val KEY_SEARCH_MODE = "searchMode"
        const val KEY_SHOW_THINKING = "showThinking"
        const val KEY_VOICE_LANG = "voiceLanguageTag"
        const val KEY_SYSTEM_VOICE = "systemVoiceName"
        const val KEY_SYSTEM_ENGINE = "systemVoiceEngine"
        const val KEY_CLOUD_PROVIDER = "cloudProvider"
        const val KEY_CLOUD_KEY = "cloudApiKey"
        const val KEY_CLOUD_REGION = "cloudRegion"
        const val KEY_CLOUD_VOICE = "cloudVoice"
        const val KEY_TTS_ENGINE = "ttsEngine"
        const val KEY_TOOLS_ENABLED = "toolsEnabled"
        const val KEY_TOOL_SHELL = "toolShellEnabled"
        const val KEY_TOOL_WRITES = "toolAllowWrites"
        const val KEY_TOOL_DANGEROUS = "toolAllowDangerous"
        const val KEY_TOOL_STEPS = "toolMaxSteps"
        const val KEY_OCR_ENABLED = "ocrEnabled"
        const val KEY_OCR_KEY = "ocrApiKey"
        const val KEY_AUTO_SPEAK = "autoSpeakReplies"
        const val KEY_SPEAKING_RATE = "speakingRate"
        const val KEY_PITCH = "pitch"
        const val KEY_WEB_SEARCH = "webSearchEnabled"
        const val KEY_SEARCH_DEPTH = "searchDepth"
    }
}
