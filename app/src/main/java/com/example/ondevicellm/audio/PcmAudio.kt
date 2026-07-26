package com.example.ondevicellm.audio

import java.io.File
import java.io.OutputStream

/**
 * Pure-Kotlin PCM helpers shared by every synthesizer.
 *
 * Neural TTS models emit float samples in roughly [-1, 1]; Android's AudioTrack
 * and the WAV container both want signed 16-bit integers. Keeping the
 * conversion here (rather than inline at each call site) means it is unit
 * tested once — see `PcmAudioTest`.
 */
object PcmAudio {

    const val DEFAULT_SAMPLE_RATE = 22_050

    /**
     * Converts float samples to signed 16-bit PCM, clipping anything outside
     * [-1, 1] instead of letting it wrap around into loud noise.
     */
    fun floatToPcm16(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1f, 1f)
            // 32767 rather than 32768 so +1.0 maps to Short.MAX_VALUE exactly.
            out[i] = (clamped * 32767f).toInt().toShort()
        }
        return out
    }

    /** Peak of the absolute value, or 0 for an empty/silent buffer. */
    fun peak(samples: FloatArray): Float {
        var peak = 0f
        for (sample in samples) {
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }

    /**
     * Scales [samples] so the loudest sample sits at [target].
     *
     * Models vary a lot in output level; without this, one model whispers and
     * the next clips. Silent buffers are returned unchanged rather than being
     * amplified into noise.
     */
    fun normalize(samples: FloatArray, target: Float = 0.95f): FloatArray {
        val peak = peak(samples)
        if (peak <= 1e-6f) return samples
        val gain = target / peak
        return FloatArray(samples.size) { samples[it] * gain }
    }
}

/**
 * Minimal mono 16-bit RIFF/WAVE writer, so generated speech can be saved and
 * shared without pulling in a media library.
 */
object WavWriter {

    private const val PCM_FORMAT = 1
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    fun write(target: File, samples: FloatArray, sampleRateHz: Int) {
        target.parentFile?.mkdirs()
        target.outputStream().buffered().use { out ->
            write(out, PcmAudio.floatToPcm16(samples), sampleRateHz)
        }
    }

    fun write(out: OutputStream, pcm: ShortArray, sampleRateHz: Int) {
        val dataBytes = pcm.size * 2
        val byteRate = sampleRateHz * CHANNELS * BITS_PER_SAMPLE / 8

        // RIFF chunk descriptor
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(36 + dataBytes)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " sub-chunk
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(16)
        out.writeShortLe(PCM_FORMAT)
        out.writeShortLe(CHANNELS)
        out.writeIntLe(sampleRateHz)
        out.writeIntLe(byteRate)
        out.writeShortLe(CHANNELS * BITS_PER_SAMPLE / 8) // block align
        out.writeShortLe(BITS_PER_SAMPLE)

        // "data" sub-chunk
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(dataBytes)
        for (sample in pcm) {
            out.writeShortLe(sample.toInt())
        }
    }

    private fun OutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun OutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
