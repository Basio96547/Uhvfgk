package com.example.ondevicellm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Which engine turns replies into speech. */
enum class TtsEngine {
    /** Android's built-in engine — always available, offline with a voice pack. */
    SYSTEM,

    /** A TTS model the user added on the Models screen. */
    MODEL,
    ;

    val label: String
        get() = when (this) {
            SYSTEM -> "System engine"
            MODEL -> "My TTS model"
        }
}

data class AppSettings(
    val systemPrompt: String = "",
    /** Ask reasoning-capable models to think before answering. */
    val thinkingEnabled: Boolean = true,
    /** Show the reasoning trace in the chat UI. */
    val showThinking: Boolean = true,
    val voiceLanguageTag: String = Locale.getDefault().toLanguageTag(),
    // ---- Speech output ----
    val ttsEngine: TtsEngine = TtsEngine.SYSTEM,
    /** Speak each reply as soon as it finishes generating. */
    val autoSpeakReplies: Boolean = false,
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.0f,
)

/** Small SharedPreferences-backed settings store exposed as a StateFlow. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
        thinkingEnabled = prefs.getBoolean(KEY_THINKING_ENABLED, true),
        showThinking = prefs.getBoolean(KEY_SHOW_THINKING, true),
        voiceLanguageTag = prefs.getString(KEY_VOICE_LANG, null)
            ?: Locale.getDefault().toLanguageTag(),
        ttsEngine = TtsEngine.entries
            .firstOrNull { it.name == prefs.getString(KEY_TTS_ENGINE, null) }
            ?: TtsEngine.SYSTEM,
        autoSpeakReplies = prefs.getBoolean(KEY_AUTO_SPEAK, false),
        speakingRate = prefs.getFloat(KEY_SPEAKING_RATE, 1.0f),
        pitch = prefs.getFloat(KEY_PITCH, 1.0f),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_SYSTEM_PROMPT, updated.systemPrompt)
            .putBoolean(KEY_THINKING_ENABLED, updated.thinkingEnabled)
            .putBoolean(KEY_SHOW_THINKING, updated.showThinking)
            .putString(KEY_VOICE_LANG, updated.voiceLanguageTag)
            .putString(KEY_TTS_ENGINE, updated.ttsEngine.name)
            .putBoolean(KEY_AUTO_SPEAK, updated.autoSpeakReplies)
            .putFloat(KEY_SPEAKING_RATE, updated.speakingRate)
            .putFloat(KEY_PITCH, updated.pitch)
            .apply()
    }

    private companion object {
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_THINKING_ENABLED = "thinkingEnabled"
        const val KEY_SHOW_THINKING = "showThinking"
        const val KEY_VOICE_LANG = "voiceLanguageTag"
        const val KEY_TTS_ENGINE = "ttsEngine"
        const val KEY_AUTO_SPEAK = "autoSpeakReplies"
        const val KEY_SPEAKING_RATE = "speakingRate"
        const val KEY_PITCH = "pitch"
    }
}
