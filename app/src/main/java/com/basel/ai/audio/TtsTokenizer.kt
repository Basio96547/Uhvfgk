package com.basel.ai.audio

import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Severity
import org.json.JSONObject
import java.io.File

/**
 * Turns text into the integer ids a TTS model expects.
 *
 * There is no universal vocabulary for TTS models, so the mapping has to come
 * from the model author. The app looks for a sidecar JSON file next to the
 * model — `voice.tflite` → `voice.tokens.json` — shaped like:
 *
 * ```json
 * { "pad": 0, "bos": 1, "eos": 2, "vocab": { "a": 3, "b": 4, " ": 5 } }
 * ```
 *
 * `pad`, `bos` and `eos` are optional. When no sidecar exists the app falls
 * back to [CharacterTokenizer] with a generated vocabulary, which will only
 * produce intelligible speech if it happens to match the model — the Models
 * screen says so explicitly.
 */
interface TtsTokenizer {
    val vocabularySize: Int
    fun encode(text: String): IntArray
}

/**
 * Maps characters to ids via an explicit table. Unknown characters are dropped
 * rather than mapped to a wrong id, which would make the model mispronounce
 * instead of simply skipping.
 */
class CharacterTokenizer(
    private val vocab: Map<Char, Int>,
    private val bosId: Int? = null,
    private val eosId: Int? = null,
    /** Insert this id between every character — some VITS exports need it. */
    private val interleaveId: Int? = null,
) : TtsTokenizer {

    override val vocabularySize: Int = vocab.size

    override fun encode(text: String): IntArray {
        val ids = ArrayList<Int>(text.length * 2 + 2)
        bosId?.let(ids::add)

        for (char in text) {
            val id = vocab[char]
                ?: vocab[char.lowercaseChar()]
                ?: continue
            if (interleaveId != null && ids.isNotEmpty()) ids.add(interleaveId)
            ids.add(id)
        }

        eosId?.let(ids::add)
        return ids.toIntArray()
    }

    companion object {

        /** Sidecar path for a model file: `voice.tflite` → `voice.tokens.json`. */
        fun sidecarFor(modelPath: String): File {
            val file = File(modelPath)
            val base = file.name.substringBeforeLast('.', file.name)
            return File(file.parentFile, "$base.tokens.json")
        }

        /** Parses a sidecar vocabulary file, or returns null if unusable. */
        fun fromSidecar(modelPath: String): CharacterTokenizer? {
            val sidecar = sidecarFor(modelPath)
            if (!sidecar.isFile) return null

            return try {
                val root = JSONObject(sidecar.readText())
                val vocabJson = root.optJSONObject("vocab") ?: return null
                val vocab = buildMap {
                    for (key in vocabJson.keys()) {
                        // Only single-character entries make sense here.
                        if (key.length == 1) put(key[0], vocabJson.getInt(key))
                    }
                }
                if (vocab.isEmpty()) return null

                CharacterTokenizer(
                    vocab = vocab,
                    bosId = root.optIntOrNull("bos"),
                    eosId = root.optIntOrNull("eos"),
                    interleaveId = root.optIntOrNull("pad"),
                )
            } catch (e: Exception) {
                ErrorLog.report(
                    "Voice model",
                    "Vocabulary file ${sidecar.name} is malformed — falling back " +
                        "to the built-in table, so pronunciation may be wrong.",
                    e,
                    Severity.WARNING,
                )
                null
            }
        }

        /**
         * Last-resort vocabulary: lowercase Latin, Arabic letters, digits and
         * common punctuation, numbered from [startId].
         */
        fun fallback(startId: Int = 1): CharacterTokenizer {
            val characters = buildList {
                add(' ')
                addAll('a'..'z')
                addAll('0'..'9')
                addAll('ء'..'ي') // Arabic letters
                addAll(listOf('.', ',', '?', '!', '\'', '-', '،', '؟'))
            }
            val vocab = characters.withIndex().associate { (index, char) ->
                char to (startId + index)
            }
            return CharacterTokenizer(vocab)
        }

        private fun JSONObject.optIntOrNull(key: String): Int? =
            if (has(key) && !isNull(key)) getInt(key) else null
    }
}
