package com.basel.ai.llm

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
    fun `a thinking turn gets room for the thinking and the answer`() {
        // The chain of thought eats most of the budget. Capping a thinking turn
        // at a plain turn's number is how a model deliberates carefully and is
        // then cut off before it says anything.
        val d = QueryRouter.route(
            message = "explain step by step how a CPU cache works",
            modelSupportsThinking = true,
            defaultMaxTokens = 1024,
        )
        assertTrue(d.think)
        assertTrue("thinking needs more than the plain budget", d.maxTokens > 1024)
    }

    @Test
    fun `a model that cannot think keeps the plain budget`() {
        val d = QueryRouter.route(
            message = "explain step by step how a CPU cache works",
            modelSupportsThinking = false,
            defaultMaxTokens = 1024,
        )
        assertFalse(d.think)
        assertEquals(1024, d.maxTokens)
    }

    @Test
    fun `a generous model budget is never lowered for a thinking turn`() {
        val d = QueryRouter.route(
            message = "احسب 25 * 17",
            modelSupportsThinking = true,
            defaultMaxTokens = 4096,
        )
        assertEquals(4096, d.maxTokens)
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
    fun `auto routing is never marked as overridden`() {
        listOf("مرحبا", "ما عاصمة فرنسا", "أخبار اليوم", "احسب 2 + 2").forEach {
            assertFalse(route(it).overridden)
        }
        assertTrue(
            QueryRouter.route("مرحبا", thinkMode = RoutingMode.NEVER).overridden
        )
    }

    // ---- Arabic beyond the textbook form ----------------------------------

    @Test
    fun `arabic-indic digits are understood`() {
        // Samsung's Arabic keyboard produces ٠-٩, so this arrived with no ASCII
        // digit in it at all and was routed as a plain question.
        val d = route("احسب ٢٥ × ١٧")
        assertEquals(QueryKind.REASONING, d.kind)
        assertTrue(d.think)
    }

    @Test
    fun `persian digits are understood too`() {
        assertEquals(QueryKind.REASONING, route("۱۲ + ۸").kind)
    }

    @Test
    fun `normalize folds both digit sets`() {
        assertEquals("25 17", QueryRouter.normalize("٢٥ ١٧"))
        assertEquals("1980", QueryRouter.normalize("۱۹۸۰"))
    }

    @Test
    fun `dialect greetings are recognised`() {
        listOf(
            "شلونك",        // Gulf
            "ازيك",          // Egyptian
            "شو الاخبار",    // Levantine
            "كي داير",       // Maghrebi
            "هلا والله",
            "يعطيك العافية",
            "الله يعطيك العافية",
            "ما قصرت",
        ).forEach {
            assertEquals("expected SOCIAL for \"$it\"", QueryKind.SOCIAL, route(it).kind)
        }
    }

    @Test
    fun `dialect lookups still search`() {
        listOf(
            "كم سعر الدولار الحين",
            "ايه الاخبار النهاردة عن الذهب",
            "نتيجة المباراة اليوم",
        ).forEach {
            assertTrue("expected search for \"$it\"", route(it).search)
        }
    }

    @Test
    fun `dialect reasoning is recognised`() {
        listOf(
            "ليش السماء زرقاء",
            "ازاي بيشتغل المحرك",
            "وش الافضل اندرويد ولا ايفون",
            "اشرح لي بالتفصيل",
        ).forEach {
            assertTrue("expected think for \"$it\"", route(it).think)
        }
    }

    @Test
    fun `arabic quotation marks do not break matching`() {
        assertEquals(QueryKind.SOCIAL, route("«مرحبا»").kind)
    }

    @Test
    fun `alef variants all fold together`() {
        listOf("أهلا", "إهلا", "آهلا", "اهلا").forEach {
            assertEquals(QueryKind.SOCIAL, route(it).kind)
        }
    }

    @Test
    fun `the directive matches the decision`() {
        assertEquals(" /think", QueryRouter.thinkingDirective(true))
        assertEquals(" /no_think", QueryRouter.thinkingDirective(false))
    }
}
