package com.example.ondevicellm.audio

import com.example.ondevicellm.core.Localization
import com.example.ondevicellm.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
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
        inputs[0] = tokenTensor(engine.getInputTensor(0), tokens)

        // Every remaining input must be filled: leaving one null makes the
        // interpreter throw. VITS/Piper exports declare input_lengths and a
        // three-element scales vector alongside the token ids, so matching only
        // single-element tensors — as this used to — left those null and failed
        // on exactly the models it was written for.
        for (index in 1 until engine.inputTensorCount) {
            inputs[index] = auxiliaryInput(engine.getInputTensor(index), tokens.size, options)
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

    /** Token ids in whatever integer width the export declares. */
    private fun tokenTensor(tensor: Tensor, tokens: IntArray): Any =
        if (tensor.dataType() == DataType.INT64) {
            // Piper and most VITS exports use int64 for token ids.
            arrayOf(LongArray(tokens.size) { tokens[it].toLong() })
        } else {
            arrayOf(tokens)
        }

    /**
     * Builds a value for an auxiliary input.
     *
     * Tensor names are the only reliable signal for what an input means, so
     * they're checked first; shape and type decide the fallback. The result is
     * never null — an unrecognised input gets zeros, which the model may
     * ignore, rather than an exception before it even runs.
     */
    private fun auxiliaryInput(tensor: Tensor, tokenCount: Int, options: SpeechOptions): Any {
        val name = tensor.name()?.lowercase().orEmpty()
        val elements = tensor.numElements().coerceAtLeast(1)
        val isLong = tensor.dataType() == DataType.INT64
        val isInt = tensor.dataType() == DataType.INT32

        return when {
            // Number of real tokens, so the model doesn't read padding.
            name.contains("length") && (isLong || isInt) ->
                intVector(elements, tokenCount.toLong(), isLong)

            name.contains("sid") || name.contains("speaker") ->
                intVector(elements, options.speakerId.toLong(), isLong)

            // VITS scales: [noise, length, noise_w]. Length scale is the
            // inverse of speed — a larger value stretches the audio.
            name.contains("scale") && elements >= 3 -> floatArrayOf(
                DEFAULT_NOISE_SCALE,
                1f / options.speakingRate.coerceIn(0.25f, 4f),
                DEFAULT_NOISE_SCALE_W,
            ) + FloatArray(elements - 3)

            isLong || isInt -> intVector(elements, options.speakerId.toLong(), isLong)

            tensor.dataType() == DataType.FLOAT32 ->
                FloatArray(elements) { if (elements == 1) options.speakingRate else 0f }

            // Unknown width: zeros are safer than leaving the slot null.
            else -> FloatArray(elements)
        }
    }

    private fun intVector(elements: Int, value: Long, isLong: Boolean): Any =
        if (isLong) LongArray(elements) { value } else IntArray(elements) { value.toInt() }

    /** Human-readable dump of the model's inputs and outputs, for diagnosis. */
    fun describeSignature(): String {
        val engine = interpreter ?: return "Model not loaded."
        return buildString {
            appendLine("Model signature:")
            for (index in 0 until engine.inputTensorCount) {
                val tensor = engine.getInputTensor(index)
                appendLine(
                    "  input $index \"${tensor.name()}\": ${tensor.dataType()} " +
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

        // VITS defaults; these are the values the reference implementation ships.
        private const val DEFAULT_NOISE_SCALE = 0.667f
        private const val DEFAULT_NOISE_SCALE_W = 0.8f

        /**
         * Shown only if the runtime is somehow absent at runtime.
         *
         * It used to instruct the user to edit `app/build.gradle.kts`, which is
         * not a thing to say to someone holding a phone. The dependency is
         * packaged now, so this is a genuine "should never happen" rather than
         * a known limitation dressed up as guidance.
         */
        val RUNTIME_MISSING_MESSAGE: String
            get() = Localization.strings.ttsRuntimeMissing

        /**
         * Whether the LiteRT classes are actually present.
         *
         * Checked rather than assumed. The dependency is packaged now, but a
         * shrinker or a future exclusion could still take it away, and every
         * entry point looks before touching it.
         */
        fun isRuntimeAvailable(): Boolean = runCatching {
            Class.forName("org.tensorflow.lite.Interpreter")
            true
        }.getOrDefault(false)
    }
}
