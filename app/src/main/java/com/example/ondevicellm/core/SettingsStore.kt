package com.example.ondevicellm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.ondevicellm.llm.RoutingMode
import com.example.ondevicellm.web.SearchDepth
import java.util.Locale

/** Which engine turns replies into speech. */
enum class TtsEngine {
    /** Android's built-in engine — always available, offline with a voice pack. */
    SYSTEM,

    /** A TTS model the user added on the Models screen. */
    MODEL,
    ;

    fun label(s: AppStrings): String = when (this) {
        SYSTEM -> s.ttsEngineSystem
        MODEL -> s.ttsEngineModel
    }
}

data class AppSettings(
    /** Interface language, layout direction, and the language replies come back in. */
    val language: AppLanguage = AppLanguage.SYSTEM,
    val systemPrompt: String = "",
    /**
     * When reasoning runs. AUTO lets the router decide per message — a greeting
     * shouldn't cost a chain of thought.
     */
    val thinkingMode: RoutingMode = RoutingMode.AUTO,
    /** Show the reasoning trace in the chat UI. */
    val showThinking: Boolean = true,
    val voiceLanguageTag: String = Locale.getDefault().toLanguageTag(),
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
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
        thinkingMode = RoutingMode.entries
            .firstOrNull { it.name == prefs.getString(KEY_THINKING_MODE, null) }
            ?: RoutingMode.AUTO,
        showThinking = prefs.getBoolean(KEY_SHOW_THINKING, true),
        voiceLanguageTag = prefs.getString(KEY_VOICE_LANG, null)
            ?: Locale.getDefault().toLanguageTag(),
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
            .putString(KEY_SYSTEM_PROMPT, updated.systemPrompt)
            .putString(KEY_THINKING_MODE, updated.thinkingMode.name)
            .putBoolean(KEY_SHOW_THINKING, updated.showThinking)
            .putString(KEY_VOICE_LANG, updated.voiceLanguageTag)
            .putString(KEY_TTS_ENGINE, updated.ttsEngine.name)
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
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_THINKING_MODE = "thinkingMode"
        const val KEY_SEARCH_MODE = "searchMode"
        const val KEY_SHOW_THINKING = "showThinking"
        const val KEY_VOICE_LANG = "voiceLanguageTag"
        const val KEY_TTS_ENGINE = "ttsEngine"
        const val KEY_AUTO_SPEAK = "autoSpeakReplies"
        const val KEY_SPEAKING_RATE = "speakingRate"
        const val KEY_PITCH = "pitch"
        const val KEY_WEB_SEARCH = "webSearchEnabled"
        const val KEY_SEARCH_DEPTH = "searchDepth"
    }
}
