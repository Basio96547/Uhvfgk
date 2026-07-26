package com.example.ondevicellm.audio

import com.example.ondevicellm.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.FloatBuffer

/**
 * Runs a user-supplied TTS model with LiteRT (TensorFlow Lite).
 *
 * ### Expected tensor contract
 *
 * Input 0 — `int32`, shape `[1, T]`: token ids from [TtsTokenizer].
 * Additional inputs are matched by shape and filled when present:
 *  - a scalar/1-element `int32` tensor receives the speaker id;
 *  - a scalar/1-element `float32` tensor receives the speaking rate.
 *
 * Output 0 — `float32`, shape `[1, N]` or `[N]`: mono waveform in ~[-1, 1] at
 * the model's native sample rate (set it per model on the Models screen).
 *
 * This covers the common VITS/Piper-style export. A model with a different
 * signature needs this class adapted — the failure is reported clearly rather
 * than producing noise, and [describeSignature] dumps the actual tensor layout
 * so you can see what a given model wants.
 */
class ModelTtsSynthesizer(val spec: ModelSpec) : SpeechSynthesizer {

    override val displayName: String = spec.displayName

    private var interpreter: Interpreter? = null
    private var tokenizer: TtsTokenizer? = null

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        if (interpreter != null) return@withContext true
        if (!isRuntimeAvailable()) return@withContext false

        val file = File(spec.path)
        if (!file.isFile || !file.canRead()) return@withContext false

        // Leave a couple of cores for the UI and any concurrent LLM decode.
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4)
        val options = Interpreter.Options().setNumThreads(threads)

        // Throwable, not Exception: a missing LiteRT runtime surfaces as
        // NoClassDefFoundError / UnsatisfiedLinkError, which are Errors.
        runCatching { Interpreter(file, options) }
            .onSuccess { loaded ->
                interpreter = loaded
                tokenizer = CharacterTokenizer.fromSidecar(spec.path)
                    ?: CharacterTokenizer.fallback()
            }
            .isSuccess
    }

    override suspend fun speak(text: String, options: SpeechOptions): SynthesisResult =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext SynthesisResult.Failed("Nothing to speak.")
            if (!isRuntimeAvailable()) {
                return@withContext SynthesisResult.Failed(RUNTIME_MISSING_MESSAGE)
            }
            if (!prepare()) {
                return@withContext SynthesisResult.Failed(
                    "Could not load the TTS model at ${spec.path}."
                )
            }

            val engine = interpreter
                ?: return@withContext SynthesisResult.Failed("TTS model not loaded.")
            val tokens = tokenizer?.encode(text)
                ?: return@withContext SynthesisResult.Failed("No tokenizer available.")

            if (tokens.isEmpty()) {
                return@withContext SynthesisResult.Failed(
                    "None of the input characters are in this model's vocabulary."
                )
            }

            runCatching { synthesize(engine, tokens, options) }
                .fold(
                    onSuccess = { samples ->
                        SynthesisResult.Pcm(
                            samples = PcmAudio.normalize(samples),
                            sampleRateHz = spec.ttsSampleRateHz,
                        )
                    },
                    onFailure = { error ->
                        SynthesisResult.Failed(
                            "This model's signature doesn't match the expected " +
                                "layout.\n\n${error.message}\n\n${describeSignature()}"
                        )
                    },
                )
        }

    private fun synthesize(
        engine: Interpreter,
        tokens: IntArray,
        options: SpeechOptions,
    ): FloatArray {
        engine.resizeInput(0, intArrayOf(1, tokens.size))
        engine.allocateTensors()

        val inputs = arrayOfNulls<Any>(engine.inputTensorCount)
        inputs[0] = arrayOf(tokens)

        // Fill any auxiliary scalar inputs the export declares.
        for (index in 1 until engine.inputTensorCount) {
            val tensor = engine.getInputTensor(index)
            inputs[index] = when {
                tensor.numElements() != 1 -> continue
                tensor.dataType() == DataType.INT32 -> intArrayOf(options.speakerId)
                tensor.dataType() == DataType.FLOAT32 -> floatArrayOf(options.speakingRate)
                else -> continue
            }
        }

        val outputTensor = engine.getOutputTensor(0)
        val sampleCount = outputTensor.numElements()
        require(sampleCount > 0) {
            "Output tensor has no elements — the model may need a fixed input length."
        }

        val buffer = FloatBuffer.allocate(sampleCount)
        engine.runForMultipleInputsOutputs(inputs, mapOf(0 to buffer))

        val samples = FloatArray(sampleCount)
        buffer.rewind()
        buffer.get(samples)
        return samples
    }

    /** Human-readable dump of the model's inputs and outputs, for diagnosis. */
    fun describeSignature(): String {
        val engine = interpreter ?: return "Model not loaded."
        return buildString {
            appendLine("Model signature:")
            for (index in 0 until engine.inputTensorCount) {
                val tensor = engine.getInputTensor(index)
                appendLine(
                    "  input $index: ${tensor.dataType()} " +
                        tensor.shape().joinToString(prefix = "[", postfix = "]")
                )
            }
            for (index in 0 until engine.outputTensorCount) {
                val tensor = engine.getOutputTensor(index)
                appendLine(
                    "  output $index: ${tensor.dataType()} " +
                        tensor.shape().joinToString(prefix = "[", postfix = "]")
                )
            }
        }
    }

    override fun stop() {
        // Synthesis is a single blocking call; cancellation happens by
        // cancelling the calling coroutine. Playback is stopped via AudioPlayer.
    }

    override fun release() {
        runCatching { interpreter?.close() }
        interpreter = null
        tokenizer = null
    }

    companion object {

        const val RUNTIME_MISSING_MESSAGE: String =
            "The LiteRT runtime isn't bundled in this build, so custom TTS models " +
                "can't run.\n\nSwitch the speech engine to \"System engine\" in " +
                "Settings, or enable LiteRT: in app/build.gradle.kts change\n" +
                "  compileOnly(\"org.tensorflow:tensorflow-lite:…\")\n" +
                "to\n" +
                "  implementation(\"org.tensorflow:tensorflow-lite:…\")\n" +
                "and rebuild."

        /**
         * Whether the LiteRT classes are actually present.
         *
         * The dependency is `compileOnly` by default so the app can never
         * conflict with the TFLite runtime MediaPipe links internally. That
         * means these classes may legitimately be absent at runtime, and every
         * entry point has to check before touching them.
         */
        fun isRuntimeAvailable(): Boolean = runCatching {
            Class.forName("org.tensorflow.lite.Interpreter")
            true
        }.getOrDefault(false)
    }
}
