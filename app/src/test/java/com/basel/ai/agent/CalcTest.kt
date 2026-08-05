package com.basel.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {

    private fun eval(expression: String) = Calc.eval(expression)

    @Test
    fun `the four operations`() {
        assertEquals(425.0, eval("25 * 17")!!, 1e-9)
        assertEquals(7.0, eval("3 + 4")!!, 1e-9)
        assertEquals(-1.0, eval("3 - 4")!!, 1e-9)
        assertEquals(2.5, eval("5 / 2")!!, 1e-9)
        assertEquals(1.0, eval("7 % 3")!!, 1e-9)
    }

    @Test
    fun `precedence and parentheses`() {
        assertEquals(14.0, eval("2 + 3 * 4")!!, 1e-9)
        assertEquals(20.0, eval("(2 + 3) * 4")!!, 1e-9)
        assertEquals(49.0, eval("(3+4)^2")!!, 1e-9)
    }

    @Test
    fun `exponentiation is right-associative`() {
        // 2^(3^2) = 512, not (2^3)^2 = 64.
        assertEquals(512.0, eval("2^3^2")!!, 1e-9)
    }

    @Test
    fun `unary minus`() {
        assertEquals(-5.0, eval("-5")!!, 1e-9)
        assertEquals(2.0, eval("-3 + 5")!!, 1e-9)
        assertEquals(6.0, eval("-(-6)")!!, 1e-9)
    }

    @Test
    fun `arabic-indic digits are understood`() {
        // Typed on an Arabic keyboard. Refusing this would make the tool
        // useless to the people the app is for.
        assertEquals(425.0, eval("٢٥ × ١٧")!!, 1e-9)
        assertEquals(8.0, eval("٥ + ٣")!!, 1e-9)
    }

    @Test
    fun `the symbols people actually type`() {
        assertEquals(12.0, eval("3 × 4")!!, 1e-9)
        assertEquals(4.0, eval("12 ÷ 3")!!, 1e-9)
        assertEquals(1000.0, eval("1,000")!!, 1e-9)
    }

    @Test
    fun `division by zero has no answer rather than an infinity`() {
        assertNull(eval("5 / 0"))
        assertNull(eval("5 % 0"))
    }

    @Test
    fun `prose is not arithmetic`() {
        assertNull(eval("what is the capital of Japan"))
        assertNull(eval(""))
        assertNull(eval("2 +"))
        assertNull(eval("(2 + 3"))
        assertNull(eval("2 3"))
    }

    // -------------------------------------------------------------- format

    @Test
    fun `whole numbers stay whole`() {
        // "4.0" is how a model starts hedging about an integer.
        assertEquals("4", Calc.format(4.0))
        assertEquals("425", Calc.format(425.0))
        assertEquals("-7", Calc.format(-7.0))
    }

    @Test
    fun `fractions keep their digits without a binary tail`() {
        assertEquals("2.5", Calc.format(2.5))
        assertEquals("0.125", Calc.format(0.125))
    }
}
