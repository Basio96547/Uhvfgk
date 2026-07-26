package com.example.ondevicellm.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays mono PCM produced by a TTS model through [AudioTrack].
 *
 * Streaming rather than static mode: generated speech can be tens of seconds
 * long, and streaming lets [stop] cut playback immediately instead of waiting
 * for a whole buffer to drain.
 */
class AudioPlayer {

    private var track: AudioTrack? = null
    @Volatile
    private var stopped = false

    val isPlaying: Boolean
        get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    /** Suspends until playback finishes, is stopped, or the caller is cancelled. */
    suspend fun play(samples: FloatArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        stop()
        stopped = false

        val pcm = PcmAudio.floatToPcm16(samples)
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // getMinBufferSize returns a negative error code for unsupported rates.
        if (minBuffer <= 0) {
            throw IllegalArgumentException(
                "This device cannot play audio at ${sampleRateHz} Hz."
            )
        }

        val bufferBytes = maxOf(minBuffer, CHUNK_SAMPLES * 2)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        try {
            audioTrack.play()
            var offset = 0
            while (offset < pcm.size && !stopped && coroutineContext.isActive) {
                val count = minOf(CHUNK_SAMPLES, pcm.size - offset)
                val written = audioTrack.write(pcm, offset, count)
                if (written <= 0) break
                offset += written
            }
            if (!stopped && coroutineContext.isActive) {
                // Let the hardware buffer drain so the tail isn't clipped.
                audioTrack.stop()
            }
        } finally {
            releaseTrack(audioTrack)
            if (track === audioTrack) track = null
        }
    }

    fun stop() {
        stopped = true
        track?.let { current ->
            runCatching { if (current.state == AudioTrack.STATE_INITIALIZED) current.pause() }
            runCatching { current.flush() }
        }
    }

    fun release() {
        stop()
        track?.let(::releaseTrack)
        track = null
    }

    private fun releaseTrack(audioTrack: AudioTrack) {
        runCatching {
            if (audioTrack.state == AudioTrack.STATE_INITIALIZED &&
                audioTrack.playState != AudioTrack.PLAYSTATE_STOPPED
            ) {
                audioTrack.stop()
            }
        }
        runCatching { audioTrack.release() }
    }

    private companion object {
        /** ~46 ms at 22.05 kHz — small enough for responsive stopping. */
        const val CHUNK_SAMPLES = 1024
    }
}
