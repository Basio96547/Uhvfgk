package com.basel.ai.agent

/**
 * A small, forgiving JSON reader.
 *
 * `org.json` is on Android but not on the unit-test classpath — the build asks
 * for default return values rather than Robolectric, so every call there
 * silently answers null. Tool-call parsing is the part most likely to break
 * against a real model's output, so it has to be testable on the host, which
 * means it cannot use `org.json`.
 *
 * Forgiving on purpose. A 4B model emits JSON that is *nearly* right: a
 * trailing comma, a single quote, an unquoted key. Rejecting those would turn
 * a working tool call into a failed turn, so all three are accepted. What is
 * not accepted is anything ambiguous — a malformed value returns null and the
 * caller reports it, rather than guessing.
 */
object MiniJson {

    /** Parses one JSON value. Returns null when the text is not valid enough. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        return try {
            reader.skipSpace()
            val value = reader.readValue()
            value
        } catch (_: Malformed) {
            null
        }
    }

    /** Parses and returns an object, or null when the text is anything else. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?>? = parse(text) as? Map<String, Any?>

    /**
     * Renders a value the way a model reads it back most reliably: as a plain
     * string. Numbers lose a trailing `.0`, because `{"count": 3.0}` read aloud
     * as "three point zero" is how a model starts hedging about integers.
     */
    fun asText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is List<*> -> value.joinToString(", ") { asText(it) }
        is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}=${asText(it.value)}" }
        else -> value.toString()
    }

    private class Malformed : Exception(null, null, false, false)

    private class Reader(private val text: String) {
        private var index = 0

        fun skipSpace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun peek(): Char {
            if (index >= text.length) throw Malformed()
            return text[index]
        }

        private fun expect(ch: Char) {
            if (index >= text.length || text[index] != ch) throw Malformed()
            index++
        }

        fun readValue(): Any? {
            skipSpace()
            return when (peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"', '\'' -> readString()
                't', 'T' -> readLiteral("true", true)
                'f', 'F' -> readLiteral("false", false)
                'n', 'N' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipSpace()
            if (index < text.length && text[index] == '}') {
                index++
                return out
            }
            while (true) {
                skipSpace()
                // A trailing comma leaves us looking at the closing brace.
                if (index < text.length && text[index] == '}') {
                    index++
                    return out
                }
                val key = readKey()
                skipSpace()
                expect(':')
                val value = readValue()
                out[key] = value
                skipSpace()
                when {
                    index >= text.length -> throw Malformed()
                    text[index] == ',' -> index++
                    text[index] == '}' -> {
                        index++
                        return out
                    }
                    else -> throw Malformed()
                }
            }
        }

        /** Keys may be quoted or, when the model forgets, bare. */
        private fun readKey(): String {
            skipSpace()
            val ch = peek()
            if (ch == '"' || ch == '\'') return readString()
            val start = index
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) {
                index++
            }
            if (index == start) throw Malformed()
            return text.substring(start, index)
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = mutableListOf<Any?>()
            skipSpace()
            if (index < text.length && text[index] == ']') {
                index++
                return out
            }
            while (true) {
                skipSpace()
                if (index < text.length && text[index] == ']') {
                    index++
                    return out
                }
                out += readValue()
                skipSpace()
                when {
                    index >= text.length -> throw Malformed()
                    text[index] == ',' -> index++
                    text[index] == ']' -> {
                        index++
                        return out
                    }
                    else -> throw Malformed()
                }
            }
        }

        private fun readString(): String {
            val quote = peek()
            index++
            val out = StringBuilder()
            while (true) {
                if (index >= text.length) throw Malformed()
                when (val ch = text[index]) {
                    quote -> {
                        index++
                        return out.toString()
                    }
                    '\\' -> {
                        index++
                        if (index >= text.length) throw Malformed()
                        when (val escape = text[index]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'r' -> out.append('\r')
                            'b' -> out.append('\b')
                            'f' -> out.append('')
                            'u' -> {
                                if (index + 4 >= text.length) throw Malformed()
                                val hex = text.substring(index + 1, index + 5)
                                val code = hex.toIntOrNull(16) ?: throw Malformed()
                                out.append(code.toChar())
                                index += 4
                            }
                            // Anything else escaped is itself: \/ and \' are
                            // both things models emit, and both mean the char.
                            else -> out.append(escape)
                        }
                        index++
                    }
                    else -> {
                        out.append(ch)
                        index++
                    }
                }
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (index + word.length > text.length) throw Malformed()
            if (!text.regionMatches(index, word, 0, word.length, ignoreCase = true)) throw Malformed()
            index += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = index
            if (index < text.length && (text[index] == '-' || text[index] == '+')) index++
            while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) {
                // Stop at a sign that starts the next token rather than an
                // exponent, so `1,-2` does not swallow the minus.
                if (text[index] in "+-" && index > start && text[index - 1] !in "eE") break
                index++
            }
            if (index == start) throw Malformed()
            return text.substring(start, index).toDoubleOrNull() ?: throw Malformed()
        }
    }
}
