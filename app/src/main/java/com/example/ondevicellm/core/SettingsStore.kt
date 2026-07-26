package com.example.ondevicellm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppSettings(
    val systemPrompt: String = "",
    /** Ask reasoning-capable models to think before answering. */
    val thinkingEnabled: Boolean = true,
    /** Show the reasoning trace in the chat UI. */
    val showThinking: Boolean = true,
    val voiceLanguageTag: String = Locale.getDefault().toLanguageTag(),
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
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_SYSTEM_PROMPT, updated.systemPrompt)
            .putBoolean(KEY_THINKING_ENABLED, updated.thinkingEnabled)
            .putBoolean(KEY_SHOW_THINKING, updated.showThinking)
            .putString(KEY_VOICE_LANG, updated.voiceLanguageTag)
            .apply()
    }

    private companion object {
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_THINKING_ENABLED = "thinkingEnabled"
        const val KEY_SHOW_THINKING = "showThinking"
        const val KEY_VOICE_LANG = "voiceLanguageTag"
    }
}
