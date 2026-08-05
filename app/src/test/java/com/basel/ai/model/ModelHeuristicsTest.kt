package com.basel.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelHeuristicsTest {

    @Test
    fun `recognises chat models`() {
        assertEquals(ModelKind.TEXT, ModelHeuristics.guessKind("gemma3-1b-it-int4.task"))
    }

    @Test
    fun `recognises speech-to-text models`() {
        assertEquals(ModelKind.ASR, ModelHeuristics.guessKind("whisper-tiny.tflite"))
        assertEquals(ModelKind.ASR, ModelHeuristics.guessKind("moonshine-base.tflite"))
    }

    @Test
    fun `recognises text-to-speech models`() {
        assertEquals(ModelKind.TTS, ModelHeuristics.guessKind("kokoro-v1.tflite"))
        assertEquals(ModelKind.TTS, ModelHeuristics.guessKind("ar_piper_voice.tflite"))
        assertEquals(ModelKind.TTS, ModelHeuristics.guessKind("vits-male.tflite"))
    }

    @Test
    fun `prefers TTS when a name matches both speech families`() {
        // "speecht5_tts" contains both an ASR hint ("speech") and a TTS hint.
        assertEquals(ModelKind.TTS, ModelHeuristics.guessKind("speecht5_tts.tflite"))
    }

    @Test
    fun `recognises multimodal models`() {
        assertEquals(ModelKind.MULTIMODAL, ModelHeuristics.guessKind("gemma-3n-e2b.task"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(ModelKind.TTS, ModelHeuristics.guessKind("KOKORO.TFLITE"))
    }

    @Test
    fun `flags reasoning-capable models`() {
        assertTrue(ModelHeuristics.guessThinking("qwen3-1.7b.task"))
        assertTrue(ModelHeuristics.guessThinking("deepseek-r1-distill.task"))
        assertFalse(ModelHeuristics.guessThinking("gemma3-1b.task"))
    }

    @Test
    fun `guesses the native sample rate per TTS family`() {
        assertEquals(24_000, ModelHeuristics.guessTtsSampleRate("kokoro-v1.tflite"))
        assertEquals(22_050, ModelHeuristics.guessTtsSampleRate("piper-ar.tflite"))
        assertEquals(16_000, ModelHeuristics.guessTtsSampleRate("speecht5_tts.tflite"))
        assertEquals(22_050, ModelHeuristics.guessTtsSampleRate("unknown.tflite"))
    }
}
