package com.basel.ai.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PcmAudioTest {

    @Test
    fun `converts float samples to 16-bit PCM`() {
        val pcm = PcmAudio.floatToPcm16(floatArrayOf(0f, 1f, -1f, 0.5f))
        assertEquals(0.toShort(), pcm[0])
        assertEquals(Short.MAX_VALUE, pcm[1])
        assertEquals((-32767).toShort(), pcm[2])
        assertEquals(16383.toShort(), pcm[3])
    }

    @Test
    fun `clips out-of-range samples instead of wrapping`() {
        // Wrapping would flip the sign and produce a loud click.
        val pcm = PcmAudio.floatToPcm16(floatArrayOf(5f, -5f))
        assertEquals(Short.MAX_VALUE, pcm[0])
        assertEquals((-32767).toShort(), pcm[1])
    }

    @Test
    fun `normalize scales the peak to the target and preserves ratios`() {
        val out = PcmAudio.normalize(floatArrayOf(0.1f, -0.05f), target = 1.0f)
        assertEquals(1.0f, out[0], 1e-5f)
        assertEquals(-0.5f, out[1], 1e-5f)
    }

    @Test
    fun `normalize leaves silence untouched`() {
        val silent = floatArrayOf(0f, 0f)
        assertSame(silent, PcmAudio.normalize(silent))
    }

    @Test
    fun `decodes little-endian 16-bit pcm`() {
        // 0x0000, 0x7FFF, 0x8000 — zero, full positive, full negative.
        val bytes = byteArrayOf(0, 0, 0xFF.toByte(), 0x7F, 0, 0x80.toByte())
        val out = PcmAudio.pcm16ToFloat(bytes)
        assertEquals(3, out.size)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(0.99997f, out[1], 1e-4f)
        assertEquals(-1f, out[2], 1e-6f)
    }

    @Test
    fun `a truncated pcm stream is shortened, not a crash`() {
        // A dropped connection leaves an odd byte count; reading past it would
        // be an out-of-bounds on a response the user has no control over.
        val out = PcmAudio.pcm16ToFloat(byteArrayOf(0, 0, 1))
        assertEquals(1, out.size)
    }

    @Test
    fun `decoding honours the length argument`() {
        val bytes = ByteArray(8)
        assertEquals(2, PcmAudio.pcm16ToFloat(bytes, length = 4).size)
        // A length past the end is clamped rather than trusted.
        assertEquals(4, PcmAudio.pcm16ToFloat(bytes, length = 99).size)
    }

    @Test
    fun `float and pcm16 round-trip within one step of the scale`() {
        val original = floatArrayOf(0f, 0.25f, -0.5f, 0.75f)
        val pcm = PcmAudio.floatToPcm16(original)
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
        }
        val back = PcmAudio.pcm16ToFloat(bytes)
        for (i in original.indices) {
            assertEquals(original[i], back[i], 1e-4f)
        }
    }
}

class WavWriterTest {

    private fun le16(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) =
        String(bytes, offset, length, Charsets.US_ASCII)

    @Test
    fun `writes a well-formed mono 16-bit RIFF header`() {
        val out = ByteArrayOutputStream()
        WavWriter.write(out, shortArrayOf(0, 100, -100, 32767), 22050)
        val bytes = out.toByteArray()

        assertEquals(44 + 8, bytes.size)
        assertEquals("RIFF", ascii(bytes, 0, 4))
        assertEquals(36 + 8, le32(bytes, 4))
        assertEquals("WAVE", ascii(bytes, 8, 4))
        assertEquals("fmt ", ascii(bytes, 12, 4))
        assertEquals(16, le32(bytes, 16))
        assertEquals(1, le16(bytes, 20))          // PCM
        assertEquals(1, le16(bytes, 22))          // mono
        assertEquals(22050, le32(bytes, 24))
        assertEquals(22050 * 2, le32(bytes, 28))  // byte rate
        assertEquals(2, le16(bytes, 32))          // block align
        assertEquals(16, le16(bytes, 34))         // bits per sample
        assertEquals("data", ascii(bytes, 36, 4))
        assertEquals(8, le32(bytes, 40))
    }

    @Test
    fun `writes samples little-endian`() {
        val out = ByteArrayOutputStream()
        WavWriter.write(out, shortArrayOf(0, 100, -100, 32767), 22050)
        val bytes = out.toByteArray()

        assertEquals(100, le16(bytes, 46))
        assertEquals(32767, le16(bytes, 50))
    }
}

class CharacterTokenizerTest {

    @Test
    fun `maps characters to ids`() {
        val tokenizer = CharacterTokenizer(mapOf('a' to 5, 'b' to 6, ' ' to 7))
        assertEquals(listOf(5, 7, 6), tokenizer.encode("a b").toList())
    }

    @Test
    fun `drops characters outside the vocabulary`() {
        // Mapping to a wrong id would make the model mispronounce; skipping is safer.
        val tokenizer = CharacterTokenizer(mapOf('a' to 5, 'b' to 6))
        assertEquals(listOf(5, 6), tokenizer.encode("a@#b").toList())
    }

    @Test
    fun `falls back to the lowercase form`() {
        val tokenizer = CharacterTokenizer(mapOf('a' to 5))
        assertEquals(listOf(5), tokenizer.encode("A").toList())
    }

    @Test
    fun `wraps output in bos and eos when configured`() {
        val tokenizer = CharacterTokenizer(mapOf('a' to 5, 'b' to 6), bosId = 1, eosId = 2)
        assertEquals(listOf(1, 5, 6, 2), tokenizer.encode("ab").toList())
    }

    @Test
    fun `interleaves the pad id between characters`() {
        val tokenizer = CharacterTokenizer(mapOf('a' to 5, 'b' to 6), interleaveId = 0)
        assertEquals(listOf(5, 0, 6), tokenizer.encode("ab").toList())
    }

    @Test
    fun `fallback vocabulary covers latin and arabic`() {
        val tokenizer = CharacterTokenizer.fallback()
        assertEquals(5, tokenizer.encode("hello").size)
        assertTrue(tokenizer.encode("مرحبا").isNotEmpty())
    }

    @Test
    fun `derives the sidecar path next to the model`() {
        val sidecar = CharacterTokenizer.sidecarFor("/models/voice.tflite")
        assertEquals("voice.tokens.json", sidecar.name)
        assertEquals("/models", sidecar.parent)
    }

    @Test
    fun `sidecar strips only the final extension`() {
        assertEquals(
            "my.voice.v2.tokens.json",
            CharacterTokenizer.sidecarFor("/models/my.voice.v2.tflite").name,
        )
    }
}
