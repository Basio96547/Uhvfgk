package com.basel.ai.agent

import com.basel.ai.llm.QueryRouter
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Arithmetic, because a 4B model cannot do it.
 *
 * This is not a nice-to-have. A small model answers `25 * 17` with a number
 * that looks right and is wrong, confidently, and the user has no way to tell
 * — which is exactly the failure that makes a local assistant feel unreliable
 * at everything else too. Handing the sum to a real evaluator costs a few
 * hundred bytes and removes a whole category of quiet wrongness.
 *
 * Arabic-Indic digits are folded first: a question typed on an Arabic keyboard
 * arrives as `٢٥ × ١٧`, and refusing it would make the tool useless to the
 * people this app is for.
 */
object Calc {

    /** Symbols people actually type, mapped to the ones the parser reads. */
    private val ALIASES = listOf(
        "×" to "*", "✕" to "*", "·" to "*", "х" to "*",
        "÷" to "/", "٪" to "%", "−" to "-", "–" to "-", "—" to "-",
        "٫" to ".", "٬" to "", "," to "",
    )

    /** The evaluated value, or null when the expression is not arithmetic. */
    fun eval(expression: String): Double? {
        var text = QueryRouter.foldDigits(expression).trim()
        for ((from, to) in ALIASES) text = text.replace(from, to)
        if (text.isEmpty()) return null
        return try {
            val parser = Parser(text)
            val value = parser.expression()
            parser.skipSpace()
            if (!parser.atEnd) null else if (value.isFinite()) value else null
        } catch (_: Bad) {
            null
        }
    }

    /**
     * The answer as a person would write it.
     *
     * Integers stay integers — `4` rather than `4.0`, which a model will
     * otherwise repeat back and then start reasoning about as a decimal — and
     * anything else is trimmed to a sane number of places rather than showing
     * a float's full binary tail.
     */
    fun format(value: Double): String {
        if (abs(value) < 1e15 && abs(value - value.roundToLong()) < 1e-9) {
            return value.roundToLong().toString()
        }
        return value.toString()
            .let { if (it.contains('E') || it.contains('e')) it else trimZeros(it) }
    }

    private fun trimZeros(text: String): String {
        if (!text.contains('.')) return text
        return text.trimEnd('0').trimEnd('.')
    }

    private class Bad : Exception(null, null, false, false)

    private class Parser(private val text: String) {
        private var index = 0

        val atEnd: Boolean get() = index >= text.length

        fun skipSpace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        /** Addition and subtraction — lowest precedence. */
        fun expression(): Double {
            var value = term()
            while (true) {
                skipSpace()
                if (atEnd) return value
                when (text[index]) {
                    '+' -> { index++; value += term() }
                    '-' -> { index++; value -= term() }
                    else -> return value
                }
            }
        }

        /** Multiplication, division, remainder. */
        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpace()
                if (atEnd) return value
                when (text[index]) {
                    '*' -> { index++; value *= power() }
                    '/' -> {
                        index++
                        val divisor = power()
                        // Not an exception: "what is 5/0" deserves "that has no
                        // answer", not a crash the model has to interpret.
                        if (divisor == 0.0) throw Bad()
                        value /= divisor
                    }
                    '%' -> {
                        index++
                        val divisor = power()
                        if (divisor == 0.0) throw Bad()
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        /** Right-associative, as everyone writing `2^3^2` expects. */
        private fun power(): Double {
            val base = unary()
            skipSpace()
            if (!atEnd && text[index] == '^') {
                index++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            skipSpace()
            if (atEnd) throw Bad()
            return when (text[index]) {
                '-' -> { index++; -unary() }
                '+' -> { index++; unary() }
                else -> atom()
            }
        }

        private fun atom(): Double {
            skipSpace()
            if (atEnd) throw Bad()
            if (text[index] == '(') {
                index++
                val value = expression()
                skipSpace()
                if (atEnd || text[index] != ')') throw Bad()
                index++
                return value
            }
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
            if (index == start) throw Bad()
            return text.substring(start, index).toDoubleOrNull() ?: throw Bad()
        }
    }
}
