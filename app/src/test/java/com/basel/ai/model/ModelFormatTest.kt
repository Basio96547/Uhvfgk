package com.basel.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelFormatTest {

    private fun bytes(vararg parts: Any): ByteArray {
        val out = ArrayList<Byte>()
        for (part in parts) when (part) {
            is String -> part.forEach { out.add(it.code.toByte()) }
            is Int -> out.add(part.toByte())
        }
        return out.toByteArray()
    }

    @Test
    fun `identifies GGUF by magic bytes`() {
        assertEquals(ModelFormat.GGUF, ModelFormat.detect(bytes("GGUF", 3, 0, 0, 0), "q.gguf"))
    }

    @Test
    fun `identifies a task bundle by its zip header`() {
        assertEquals(
            ModelFormat.TASK,
            ModelFormat.detect(bytes(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0), "m.task"),
        )
    }

    @Test
    fun `identifies TFLite by the identifier at offset four`() {
        assertEquals(
            ModelFormat.TFLITE,
            ModelFormat.detect(bytes(0x18, 0, 0, 0, "TFL3"), "voice.tflite"),
        )
    }

    @Test
    fun `falls back to the extension only for LiteRT-LM`() {
        assertEquals(
            ModelFormat.LITERTLM,
            ModelFormat.detect(bytes(1, 2, 3, 4, 5, 6, 7, 8), "m.litertlm"),
        )
    }

    @Test
    fun `does not trust a misleading extension`() {
        // Routing on the filename alone would hand a non-GGUF file to llama.cpp.
        assertEquals(
            ModelFormat.UNKNOWN,
            ModelFormat.detect(bytes(0, 0, 0, 0, 0, 0, 0, 0), "fake.gguf"),
        )
    }

    @Test
    fun `handles empty and truncated headers`() {
        assertEquals(ModelFormat.UNKNOWN, ModelFormat.detect(ByteArray(0), "x"))
        assertEquals(ModelFormat.UNKNOWN, ModelFormat.detect(bytes("GG"), "x"))
    }

    @Test
    fun `accepts every runnable format`() {
        assertNull(ModelFormat.GGUF.rejectionMessage("a.gguf"))
        assertNull(ModelFormat.TASK.rejectionMessage("a.task"))
        assertNull(ModelFormat.LITERTLM.rejectionMessage("a.litertlm"))
    }

    @Test
    fun `explains what to do about an unusable file`() {
        assertNotNull(ModelFormat.UNKNOWN.rejectionMessage("model.safetensors"))
    }
}
