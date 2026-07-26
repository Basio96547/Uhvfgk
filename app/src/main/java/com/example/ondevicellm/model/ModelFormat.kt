package com.example.ondevicellm.model

import java.io.File

/**
 * The on-disk format of a model file, identified from its magic bytes.
 *
 * This exists because the formats look interchangeable from a filename but are
 * not: GGUF (llama.cpp), MediaPipe `.task` bundles and raw TensorFlow Lite
 * flatbuffers are mutually incompatible. Without this check the runtime fails
 * deep inside native code with "Error building tflite model", which tells the
 * user nothing about what to do next.
 */
enum class ModelFormat(val label: String, val supported: Boolean) {
    /** MediaPipe task bundle — a zip container. What this app runs. */
    TASK("MediaPipe .task bundle", true),

    /** Newer LiteRT-LM container. Support depends on the runtime version. */
    LITERTLM("LiteRT-LM", true),

    /** Bare TFLite flatbuffer — fine for TTS models, not for the LLM engine. */
    TFLITE("TensorFlow Lite", true),

    /** llama.cpp format. Run through the bundled llama.cpp engine. */
    GGUF("GGUF (llama.cpp)", true),

    /** PyTorch / safetensors / anything else we can't identify. */
    UNKNOWN("Unrecognised", false),
    ;

    companion object {

        private const val HEADER_BYTES = 16

        /**
         * Identifies [header] (the first bytes of the file). [fileName] is only
         * consulted for containers with no distinctive magic number.
         */
        fun detect(header: ByteArray, fileName: String = ""): ModelFormat {
            // llama.cpp writes "GGUF" as the first four bytes.
            if (header.startsWith("GGUF")) return GGUF

            // .task bundles are zip archives: "PK".
            if (header.size >= 4 &&
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            ) {
                return TASK
            }

            // TFLite flatbuffers carry the "TFL3" identifier at offset 4.
            if (header.size >= 8 && header.startsWith("TFL3", offset = 4)) return TFLITE

            // No reliable magic number is documented for LiteRT-LM, so fall
            // back to the extension for that one only.
            if (fileName.endsWith(".litertlm", ignoreCase = true)) return LITERTLM

            return UNKNOWN
        }

        fun detect(file: File): ModelFormat {
            val header = ByteArray(HEADER_BYTES)
            val read = try {
                file.inputStream().use { it.read(header) }
            } catch (_: Exception) {
                return UNKNOWN
            }
            if (read <= 0) return UNKNOWN
            return detect(header.copyOf(read), file.name)
        }

        private fun ByteArray.startsWith(text: String, offset: Int = 0): Boolean {
            if (size < offset + text.length) return false
            for (i in text.indices) {
                if (this[offset + i] != text[i].code.toByte()) return false
            }
            return true
        }
    }
}

/**
 * Explains why a file can't be used, and what to download instead.
 * Returns null when the format is fine.
 */
fun ModelFormat.rejectionMessage(fileName: String): String? = when (this) {
    ModelFormat.TASK, ModelFormat.LITERTLM, ModelFormat.TFLITE, ModelFormat.GGUF -> null

    ModelFormat.UNKNOWN -> buildString {
        append("\"$fileName\" isn't a model format this app can read.\n\n")
        append("Supported: GGUF (.gguf) and MediaPipe bundles (.task).\n\n")
        append("Files ending in .safetensors or .pth come from PyTorch repos and ")
        append("need converting to GGUF on a computer first.")
    }
}
