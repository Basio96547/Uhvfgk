package com.example.ondevicellm.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRouterTest {

    private fun route(message: String) = QueryRouter.route(
        message = message,
        searchMode = RoutingMode.AUTO,
        thinkMode = RoutingMode.AUTO,
        modelSupportsThinking = true,
        defaultMaxTokens = 2048,
    )

    // ---- the bug this class exists for ------------------------------------
    // "مرحبا" searched the web and ran a chain of thought. Half an hour for a
    // greeting.

    @Test
    fun `arabic greeting neither searches nor thinks`() {
        val d = route("مرحبا")
        assertEquals(QueryKind.SOCIAL, d.kind)
        assertFalse(d.search)
        assertFalse(d.think)
    }

    @Test
    fun `greeting answer is capped short`() {
        assertEquals(200, route("مرحبا").maxTokens)
        assertEquals(200, route("hello").maxTokens)
    }

    @Test
    fun `greetings in both languages are social`() {
        val greetings = listOf(
            "مرحبا", "أهلاً", "السلام عليكم", "صباح الخير", "كيف حالك؟",
            "شكرا", "شكراً جزيلاً", "مع السلامة", "تمام",
            "hi", "hello there", "hey", "good morning", "thanks a lot",
            "thank you", "ok", "bye",
        )
        greetings.forEach {
            assertEquals("expected SOCIAL for \"$it\"", QueryKind.SOCIAL, route(it).kind)
        }
    }

    // ---- normalisation ----------------------------------------------------

    @Test
    fun `normalize folds arabic spelling variants`() {
        assertEquals("اهلا", QueryRouter.normalize("أهلاً"))
        assertEquals("مرحبا", QueryRouter.normalize("مَرْحَبا!"))
        assertEquals("كيف حالك", QueryRouter.normalize("كيف حالك؟"))
    }

    @Test
    fun `hasPhrase requires a word boundary`() {
        // "الان" (now) sits inside "الاندرويد" (Android). Plain contains sent
        // "قارن بين الاندرويد والايفون" to a web search.
        assertFalse(QueryRouter.hasPhrase("قارن بين الاندرويد والايفون", "الان"))
        assertTrue(QueryRouter.hasPhrase("ما هو الطقس الان", "الان"))
    }

    // ---- a greeting attached to a real question is not social -------------

    @Test
    fun `greeting plus a real question keeps the question`() {
        val d = route("مرحبا، لماذا السماء زرقاء؟")
        assertEquals(QueryKind.REASONING, d.kind)
        assertTrue(d.think)
    }

    @Test
    fun `hi plus lookup still searches`() {
        val d = route("hi, what is the weather today?")
        assertEquals(QueryKind.LOOKUP, d.kind)
        assertTrue(d.search)
    }

    // ---- lookups ----------------------------------------------------------

    @Test
    fun `time-sensitive questions search`() {
        listOf(
            "ما هي أخبار اليوم؟",
            "كم سعر الذهب الآن؟",
            "what is the latest version of Android?",
            "bitcoin price right now",
        ).forEach {
            val d = route(it)
            assertTrue("expected search for \"$it\"", d.search)
            assertEquals(QueryKind.LOOKUP, d.kind)
        }
    }

    @Test
    fun `an explicit instruction to look it up wins`() {
        assertTrue(route("ابحث عن أفضل مطعم في الرياض").search)
        assertTrue(route("google the capital of Peru").search)
    }

    // ---- reasoning --------------------------------------------------------

    @Test
    fun `arithmetic needs working through`() {
        val d = route("احسب 25 * 17")
        assertEquals(QueryKind.REASONING, d.kind)
        assertTrue(d.think)
        assertFalse(d.search)
    }

    @Test
    fun `comparisons reason without searching`() {
        val d = route("قارن بين الاندرويد والايفون")
        assertEquals(QueryKind.REASONING, d.kind)
        assertTrue(d.think)
        assertFalse("comparison is not a lookup", d.search)
    }

    @Test
    fun `code questions reason`() {
        listOf(
            "why does this throw a null pointer exception?",
            "اشرح لي هذا الكود",
            "refactor this function for me",
        ).forEach {
            assertTrue("expected think for \"$it\"", route(it).think)
        }
    }

    @Test
    fun `a very long message reasons`() {
        val long = (1..40).joinToString(" ") { "كلمة" }
        assertEquals(QueryKind.REASONING, route(long).kind)
    }

    // ---- plain questions --------------------------------------------------

    @Test
    fun `a simple question does neither and is capped`() {
        val d = route("ما عاصمة فرنسا")
        assertEquals(QueryKind.SIMPLE, d.kind)
        assertFalse(d.search)
        assertFalse(d.think)
        assertEquals(700, d.maxTokens)
    }

    @Test
    fun `reasoning gets the full token budget`() {
        assertEquals(2048, route("explain step by step how a CPU cache works").maxTokens)
    }

    // ---- overrides --------------------------------------------------------

    @Test
    fun `ALWAYS forces both except on a greeting`() {
        val d = QueryRouter.route(
            message = "ما عاصمة فرنسا",
            searchMode = RoutingMode.ALWAYS,
            thinkMode = RoutingMode.ALWAYS,
            modelSupportsThinking = true,
        )
        assertTrue(d.search)
        assertTrue(d.think)

        // A greeting is exempt: forcing a chain of thought on "hello" is the
        // exact behaviour this router was written to stop.
        val greeting = QueryRouter.route(
            message = "مرحبا",
            searchMode = RoutingMode.ALWAYS,
            thinkMode = RoutingMode.ALWAYS,
            modelSupportsThinking = true,
        )
        assertFalse(greeting.search)
        assertFalse(greeting.think)
    }

    @Test
    fun `NEVER suppresses both`() {
        val d = QueryRouter.route(
            message = "ما أخبار اليوم؟ ولماذا؟",
            searchMode = RoutingMode.NEVER,
            thinkMode = RoutingMode.NEVER,
            modelSupportsThinking = true,
        )
        assertFalse(d.search)
        assertFalse(d.think)
    }

    @Test
    fun `a model without a reasoning mode never thinks`() {
        val d = QueryRouter.route(
            message = "احسب 25 * 17",
            thinkMode = RoutingMode.ALWAYS,
            modelSupportsThinking = false,
        )
        assertFalse(d.think)
    }

    // ---- misc -------------------------------------------------------------

    @Test
    fun `empty input is social, not a search`() {
        val d = route("   ")
        assertEquals(QueryKind.SOCIAL, d.kind)
        assertFalse(d.search)
    }

    @Test
    fun `the reason is never blank`() {
        listOf("مرحبا", "ما عاصمة فرنسا", "أخبار اليوم", "احسب 2 + 2").forEach {
            assertTrue(route(it).reason.isNotBlank())
        }
    }

    @Test
    fun `the directive matches the decision`() {
        assertEquals(" /think", QueryRouter.thinkingDirective(true))
        assertEquals(" /no_think", QueryRouter.thinkingDirective(false))
    }
}
