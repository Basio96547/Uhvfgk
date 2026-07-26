package com.example.ondevicellm.model

import com.example.ondevicellm.core.ArabicStrings
import com.example.ondevicellm.core.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAdvisorTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `a phone with plenty of memory is offered a large model`() {
        // A Galaxy S25 Ultra with RAM Plus on and little else running.
        val best = ModelAdvisor.best(12 * gb)
        assertNotNull(best)
        assertEquals("12–14B", best!!.parameters)
    }

    @Test
    fun `the 4B Q8 case is beaten by an 8B at Q4 for the same memory`() {
        // The exact situation reported: a 4B at Q8_0 loaded and understood
        // poorly. An 8B at Q4_K_M is within a few hundred MB of the same size.
        val options = ModelAdvisor.optionsFor(8 * gb)
        val fourBQ8 = options.first { it.parameters == "4B" && it.quantisation == "Q8_0" }
        val eightBQ4 = options.first { it.parameters == "7–9B" && it.quantisation == "Q4_K_M" }

        assertTrue("the 8B should not cost much more", eightBQ4.approxBytes < fourBQ8.approxBytes * 1.2)
        assertEquals(Fit.COMFORTABLE, eightBQ4.fit)
    }

    @Test
    fun `a tight budget still offers something`() {
        val best = ModelAdvisor.best(4 * gb)
        assertNotNull(best)
        assertTrue(best!!.approxBytes < 3 * gb)
    }

    @Test
    fun `nothing is recommended when memory is exhausted`() {
        assertNull(ModelAdvisor.best(512L * 1024 * 1024))
        assertTrue(
            ModelAdvisor.optionsFor(512L * 1024 * 1024).all { it.fit == Fit.TOO_BIG }
        )
    }

    @Test
    fun `options are ordered most capable first`() {
        val sizes = ModelAdvisor.optionsFor(8 * gb).map { it.approxBytes }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    @Test
    fun `a bigger budget never fits fewer models`() {
        val small = ModelAdvisor.optionsFor(4 * gb).count { it.fit != Fit.TOO_BIG }
        val large = ModelAdvisor.optionsFor(16 * gb).count { it.fit != Fit.TOO_BIG }
        assertTrue(large >= small)
    }

    @Test
    fun `an unknown budget never claims something fits`() {
        assertTrue(ModelAdvisor.optionsFor(0).all { it.fit == Fit.TIGHT })
        assertNull(ModelAdvisor.best(0))
    }

    @Test
    fun `the headline is written in the reader's language`() {
        val en = ModelAdvisor.headline(12 * gb, EnglishStrings)
        val ar = ModelAdvisor.headline(12 * gb, ArabicStrings)
        assertTrue(en != ar)
        assertTrue(ar.any { it in '؀'..'ۿ' })
        // Both name the same class, since the arithmetic is language-neutral.
        assertTrue(en.contains("12–14B"))
        assertTrue(ar.contains("12–14B"))
    }

    @Test
    fun `the out-of-memory headline is not a recommendation`() {
        val line = ModelAdvisor.headline(256L * 1024 * 1024, EnglishStrings)
        assertEquals(EnglishStrings.advisorNothingFits, line)
    }
}
