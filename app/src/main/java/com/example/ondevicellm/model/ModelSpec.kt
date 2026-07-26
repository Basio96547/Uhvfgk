package com.example.ondevicellm.model

import com.example.ondevicellm.core.AppStrings
import org.json.JSONObject

/** What a registered model is used for. */
enum class ModelKind {
    /** Text generation / chat. */
    TEXT,

    /** Speech-to-text. Transcribes voice input. */
    ASR,

    /** Text-to-speech. Generates spoken audio from replies. */
    TTS,

    /** Text + image (and, for some builds, audio) in one model. */
    MULTIMODAL,
    ;

    fun label(s: AppStrings): String = when (this) {
        TEXT -> s.kindText
        ASR -> s.kindAsr
        TTS -> s.kindTts
        MULTIMODAL -> s.kindMultimodal
    }

    /** Kinds that produce chat replies, as opposed to handling audio. */
    val isConversational: Boolean get() = this == TEXT || this == MULTIMODAL
}

/** Which accelerator the user wants this model to run on. */
enum class BackendPref {
    /** Pick the best backend the device and runtime actually support. */
    AUTO,
    CPU,
    GPU,

    /** Vendor NPU (Qualcomm Hexagon). Falls back when unavailable — see BackendResolver. */
    NPU,
    ;

    /** CPU/GPU/NPU are the hardware's own names; only "Auto" is a word. */
    fun label(s: AppStrings): String = when (this) {
        AUTO -> s.modeAuto
        CPU -> "CPU"
        GPU -> "GPU"
        NPU -> "NPU"
    }
}

/**
 * A model the user has added to the app.
 *
 * [path] points at a `.task` bundle on disk. Models can either be *managed*
 * (copied into the app's private storage by the importer) or referenced in
 * place (e.g. pushed to `/data/local/tmp/llm` with adb).
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val path: String,
    val kind: ModelKind = ModelKind.TEXT,
    val backend: BackendPref = BackendPref.AUTO,
    val maxTokens: Int = 1024,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.8f,
    /** Model emits reasoning traces (e.g. `<think>…</think>`). */
    val supportsThinking: Boolean = false,
    /** Soft display cap on reasoning characters; 0 = unlimited. */
    val thinkingBudgetChars: Int = 0,
    /** True when the file lives in app-private storage and we may delete it. */
    val managed: Boolean = false,
    val sizeBytes: Long = 0L,
    // ---- Text-to-speech settings (ModelKind.TTS only) ----
    /** Native output rate of the TTS model. Common: 16000, 22050, 24000. */
    val ttsSampleRateHz: Int = 22_050,
    /** Voice index for multi-speaker TTS models. */
    val ttsSpeakerId: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("path", path)
        put("kind", kind.name)
        put("backend", backend.name)
        put("maxTokens", maxTokens)
        put("topK", topK)
        put("topP", topP.toDouble())
        put("temperature", temperature.toDouble())
        put("supportsThinking", supportsThinking)
        put("thinkingBudgetChars", thinkingBudgetChars)
        put("managed", managed)
        put("sizeBytes", sizeBytes)
        put("ttsSampleRateHz", ttsSampleRateHz)
        put("ttsSpeakerId", ttsSpeakerId)
    }

    companion object {
        fun fromJson(json: JSONObject): ModelSpec = ModelSpec(
            id = json.getString("id"),
            displayName = json.optString("displayName", "Model"),
            path = json.getString("path"),
            kind = parseKind(json.optString("kind")),
            backend = enumOrDefault(json.optString("backend"), BackendPref.AUTO),
            maxTokens = json.optInt("maxTokens", 1024),
            topK = json.optInt("topK", 40),
            topP = json.optDouble("topP", 0.95).toFloat(),
            temperature = json.optDouble("temperature", 0.8).toFloat(),
            supportsThinking = json.optBoolean("supportsThinking", false),
            thinkingBudgetChars = json.optInt("thinkingBudgetChars", 0),
            managed = json.optBoolean("managed", false),
            sizeBytes = json.optLong("sizeBytes", 0L),
            ttsSampleRateHz = json.optInt("ttsSampleRateHz", 22_050),
            ttsSpeakerId = json.optInt("ttsSpeakerId", 0),
        )

        /**
         * Older builds stored speech-to-text models as `AUDIO`; map that onto
         * [ModelKind.ASR] so existing registries keep working.
         */
        private fun parseKind(name: String?): ModelKind = when (name) {
            "AUDIO" -> ModelKind.ASR
            else -> enumOrDefault(name, ModelKind.TEXT)
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
