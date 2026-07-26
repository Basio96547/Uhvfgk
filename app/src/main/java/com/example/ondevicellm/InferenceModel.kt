package com.example.ondevicellm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

/**
 * Thin wrapper around the MediaPipe LLM Inference API.
 *
 * The model file (a `.task` bundle, e.g. Gemma) is expected to live on the
 * device at [MODEL_PATH]. See the project README for how to push a model with
 * `adb`. Everything here runs fully on-device — no network calls.
 */
class InferenceModel private constructor(context: Context) {

    private val llmInference: LlmInference
    private var session: LlmInferenceSession

    init {
        val modelFile = File(MODEL_PATH)
        if (!modelFile.exists()) {
            throw ModelNotFoundException(MODEL_PATH)
        }

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(MODEL_PATH)
            .setMaxTokens(MAX_TOKENS)
            .build()

        llmInference = LlmInference.createFromOptions(context, inferenceOptions)
        session = createSession()
    }

    private fun createSession(): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
    }

    /**
     * Streams a response for [prompt]. [onPartial] is invoked with each token
     * chunk as it is generated; [done] is true on the final chunk.
     */
    fun generateResponseAsync(
        prompt: String,
        onPartial: (partialResult: String, done: Boolean) -> Unit,
    ) {
        session.addQueryChunk(prompt)
        session.generateResponseAsync { partialResult, done ->
            onPartial(partialResult, done)
        }
    }

    /** Clears conversation state and starts a fresh session. */
    fun resetSession() {
        session.close()
        session = createSession()
    }

    fun close() {
        session.close()
        llmInference.close()
    }

    class ModelNotFoundException(path: String) :
        IllegalStateException("No model found at \"$path\". Push a .task model there first (see README).")

    companion object {
        /**
         * Where the app looks for the model bundle. This path is writable via
         * `adb push` without root and is a common convention for MediaPipe demos.
         */
        const val MODEL_PATH = "/data/local/tmp/llm/model.task"

        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f

        @Volatile
        private var instance: InferenceModel? = null

        fun getInstance(context: Context): InferenceModel {
            return instance ?: synchronized(this) {
                instance ?: InferenceModel(context.applicationContext).also { instance = it }
            }
        }
    }
}
