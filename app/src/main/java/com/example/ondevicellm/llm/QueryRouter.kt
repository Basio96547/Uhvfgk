package com.example.ondevicellm.llm

/** What kind of work a message actually needs. */
enum class QueryKind {
    /** Greetings, thanks, goodbyes. Answer immediately, briefly. */
    SOCIAL,

    /** A direct question the model can answer from its weights. */
    SIMPLE,

    /** Needs current or external facts. */
    LOOKUP,

    /** Needs working through: maths, multi-step, code, comparison. */
    REASONING,
}

/**
 * The plan for one message.
 *
 * Carries the decision, not a sentence about it: the UI renders the
 * explanation in the user's own language. Routing is never a black box — when
 * it gets something wrong the user can see why and override it.
 */
data class RoutingDecision(
    val kind: QueryKind,
    val search: Boolean,
    val think: Boolean,
    val maxTokens: Int,
    /** True when a manual Always/Never overrode what the router would have done. */
    val overridden: Boolean,
)

/** Per-feature override. */
enum class RoutingMode {
    /** Decide per message. */
    AUTO,
    ALWAYS,
    NEVER,
}

/**
 * Decides whether a message needs a web lookup, extended reasoning, both or
 * neither.
 *
 * Written after a real failure: "مرحبا" triggered a web search *and* a long
 * chain of thought, so a greeting took half an hour to answer. Running both on
 * every message is the wrong default — most messages need neither.
 *
 * Deliberately a rule set rather than a model call: routing has to be instant
 * and work offline, and a second inference pass to decide whether to do the
 * first would cost more than it saves. It handles Arabic and English, since
 * both are first-class here.
 */
object QueryRouter {

    // --- Arabic normalisation ------------------------------------------------
    // Diacritics, alef and ya variants and tatweel all vary by keyboard and by
    // writer. Matching raw text against a word list misses most real input.
    private val DIACRITICS = Regex("[ً-ْـ]")
    private val PUNCTUATION = Regex("[\\p{Punct}،؛؟٪-٭۔«»]")

    /**
     * Arabic-Indic digits, both the Arabic (٠-٩) and Persian/Urdu (۰-۹) sets.
     *
     * Samsung's Arabic keyboard produces these, so "احسب ٢٥ × ١٧" arrived with
     * not a single ASCII digit in it and was routed as a simple question with
     * no working shown. Folded to ASCII before any matching.
     */
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    fun foldDigits(text: String): String {
        if (text.none { it in ARABIC_DIGITS || it in PERSIAN_DIGITS }) return text
        return buildString(text.length) {
            for (ch in text) {
                val arabic = ARABIC_DIGITS.indexOf(ch)
                val persian = PERSIAN_DIGITS.indexOf(ch)
                append(
                    when {
                        arabic >= 0 -> '0' + arabic
                        persian >= 0 -> '0' + persian
                        else -> ch
                    }
                )
            }
        }
    }

    /**
     * Whole-phrase containment.
     *
     * Plain `contains` is wrong for Arabic: "الان" (now) sits inside
     * "الاندرويد" (Android), so "compare Android and iPhone" was being routed
     * to a web search. Padding both sides forces a word boundary and works for
     * multi-word phrases too.
     */
    fun hasPhrase(normalizedText: String, phrase: String): Boolean =
        " $normalizedText ".contains(" $phrase ")

    private fun anyPhrase(normalizedText: String, phrases: List<String>): Boolean =
        phrases.any { hasPhrase(normalizedText, it) }

    fun normalize(text: String): String = foldDigits(text)
        .lowercase()
        .replace(DIACRITICS, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ى', 'ي').replace('ئ', 'ي')
        .replace('ة', 'ه')
        .replace('ؤ', 'و')
        // Gulf and Egyptian keyboards; also common in transliterated chat.
        .replace('گ', 'ك').replace('ک', 'ك')
        .replace('ی', 'ي')
        .replace(PUNCTUATION, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Greetings and pleasantries — never worth a search or a chain of thought.
     *
     * Arabic is covered across dialects on purpose: people write to this app in
     * Gulf, Egyptian, Levantine and Maghrebi, not in the textbook form. Every
     * entry is already normalised (no diacritics, ا/ي/ه folded), because that is
     * what it is matched against.
     */
    private val SOCIAL_PHRASES = listOf(
        // --- Arabic: greetings ---
        "مرحبا", "مرحبتين", "اهلا", "اهلين", "اهلا وسهلا", "هلا", "هلا والله",
        "يا هلا", "السلام عليكم", "وعليكم السلام", "سلام", "سلام عليكم",
        "صباح الخير", "مساء الخير", "صباح النور", "مساء النور",
        "تصبح علي خير", "صباحك سعيد", "نهارك سعيد",
        // --- Arabic: how are you, by dialect ---
        "كيف حالك", "كيف الحال", "كيفك", "كيف حالكم", "شلونك", "شلونكم",
        "شخبارك", "شخبارك اليوم", "عساك بخير", "عساكم بخير", "وش اخبارك",
        "ايه الاخبار", "عامل ايه", "ازيك", "ازيك عامل ايه", "لباس", "كي داير",
        "شو الاخبار", "شو اخبارك",
        // --- Arabic: thanks and blessings ---
        "شكرا", "شكرا لك", "شكرا جزيلا", "مشكور", "مشكوره", "متشكر",
        "يعطيك العافيه", "الله يعطيك العافيه", "تسلم", "تسلم ايدك",
        "الله يعافيك", "جزاك الله خير", "بارك الله فيك", "ما قصرت",
        // --- Arabic: farewells and fillers ---
        "مع السلامه", "وداعا", "الي اللقاء", "في امان الله", "بالتوفيق",
        "تمام", "طيب", "اوك", "ماشي", "زين", "حلو", "ممتاز", "عظيم",
        "اكيد", "ان شاء الله", "ولا يهمك", "عفوا", "العفو", "لا شكر علي واجب",
        // --- English ---
        "hi", "hello", "hey", "yo", "good morning", "good evening", "good night",
        "how are you", "how r u", "how are u", "whats up", "sup", "howdy",
        "thanks", "thank you", "thanks a lot", "thx", "ty", "appreciate it",
        "bye", "goodbye", "see you", "ok", "okay", "cool", "nice", "great",
        "perfect", "got it", "sure", "no problem", "youre welcome",
    )

    /** Asking for something that changes over time, or that the model can't know. */
    private val LOOKUP_MARKERS = listOf(
        // --- Arabic: time ---
        "اليوم", "الان", "حاليا", "الحين", "هسه", "دلوقتي", "دلوقت",
        "احدث", "اخر", "اخر شي", "جديد", "الجديد", "مؤخرا", "هالايام",
        "هذا العام", "هذه السنه", "هذا الشهر", "هذا الاسبوع", "امس", "بكره",
        "متي يصدر", "متي صدر", "متي ينزل", "موعد",
        // --- Arabic: things that change ---
        "اخبار", "خبر", "عاجل", "سعر", "اسعار", "بكم", "كم سعر", "تكلفه",
        "الطقس", "الجو", "درجه الحراره", "امطار", "توقعات",
        "الدولار", "الريال", "الذهب", "العمله", "البورصه", "الاسهم",
        "نتيجه", "نتيجه المباراه", "مباراه", "دوري", "ترتيب",
        // --- Arabic: entities the model may not know ---
        "من هو", "من هي", "من هم", "ما هو موقع", "رابط", "موقع",
        "اصدار", "الاصدار", "مواصفات",
        // --- English ---
        "today", "right now", "currently", "latest", "newest", "recent",
        "news", "breaking", "headline", "price", "cost of", "how much is",
        "weather", "forecast", "temperature",
        "stock", "exchange rate", "release date", "released", "version",
        "changelog", "score", "results", "standings",
        "this year", "this month", "this week", "who is", "who was",
    )

    /** An explicit instruction to look it up beats any heuristic. */
    private val EXPLICIT_SEARCH = listOf(
        "ابحث", "ابحث لي", "ابحثلي", "دور", "دور لي", "دورلي", "بحث",
        "جيب لي معلومات", "شوف بالنت", "شوف في النت", "شوف على النت",
        "من الانترنت", "من النت", "من الويب", "ابحث في الانترنت",
        "search", "google", "look up", "look it up", "find online", "web search",
    )

    /** Wants an explanation or a derivation, not a fact. */
    private val REASONING_MARKERS = listOf(
        // --- Arabic: why / how ---
        "لماذا", "لماذا لا", "ليش", "ليه", "علي وش", "كيف يعمل", "كيف تعمل",
        "كيف يشتغل", "ازاي", "كيفيه", "ما السبب", "السبب",
        // --- Arabic: explain ---
        "اشرح", "اشرح لي", "وضح", "فسر", "بسط", "علل", "فصل",
        // --- Arabic: compare and judge ---
        "قارن", "الفرق بين", "الفروق", "ايهما افضل", "افضل ام", "وش الافضل",
        "مميزات وعيوب", "ايجابيات وسلبيات", "رايك",
        // --- Arabic: work it out ---
        "حلل", "استنتج", "برهن", "اثبت", "استنبط",
        "احسب", "احسب لي", "كم يساوي", "كم الناتج", "حل المسئله", "حل المساله",
        "خطوه بخطوه", "بالتفصيل", "اكتب لي", "صمم", "خطط",
        // --- English ---
        "why", "how does", "how do", "how would", "explain", "elaborate",
        "walk me through", "compare", "difference between", "trade-off",
        "tradeoff", "pros and cons", "which is better",
        "analyse", "analyze", "prove", "derive", "calculate", "solve",
        "step by step", "in detail", "reason about", "debug", "optimize",
        "optimise", "algorithm", "complexity", "refactor", "design",
    )

    /**
     * Arithmetic in the text is a strong signal that working is required.
     *
     * Matched against digit-folded text, so "احسب ٢٥ × ١٧" reaches this as
     * "احسب 25 × 17". Not against fully normalised text: that strips `+` as
     * punctuation, which would hide half the arithmetic there is.
     */
    private val ARITHMETIC = Regex("\\d+\\s*[+\\-*/×÷^%]\\s*\\d+")

    /** Code, which usually needs care even when the question is short. */
    private val CODE_MARKERS = listOf(
        "```", "function", "def ", "class ", "import ", "null pointer",
        "stack trace", "exception", "compile", "traceback", "segfault",
        "كود", "برمج", "داله", "دالة", "خطا برمجي", "خطأ برمجي", "سكربت",
    )

    private const val TRIVIAL_TOKENS = 200
    private const val SIMPLE_TOKENS = 700

    /**
     * Routes [message].
     *
     * [searchMode] and [thinkMode] let the user override; [modelSupportsThinking]
     * stops us asking a model to reason when it has no reasoning mode.
     */
    fun route(
        message: String,
        searchMode: RoutingMode = RoutingMode.AUTO,
        thinkMode: RoutingMode = RoutingMode.AUTO,
        modelSupportsThinking: Boolean = false,
        defaultMaxTokens: Int = 1024,
    ): RoutingDecision {
        val text = normalize(message)
        val words = if (text.isEmpty()) emptyList() else text.split(' ')

        val social = isSocial(text, words)
        val explicitSearch = anyPhrase(text, EXPLICIT_SEARCH)
        val lookup = explicitSearch || anyPhrase(text, LOOKUP_MARKERS)
        // Digits folded but punctuation intact: normalize() strips "+" along
        // with the rest of \p{Punct}, which would hide "12 + 8" from the
        // arithmetic check.
        val reasoning = ARITHMETIC.containsMatchIn(foldDigits(message)) ||
            anyPhrase(text, REASONING_MARKERS) ||
            // Code markers are matched raw: they carry their own punctuation.
            CODE_MARKERS.any { message.lowercase().contains(it) } ||
            words.size >= LONG_QUESTION_WORDS

        val kind = when {
            social -> QueryKind.SOCIAL
            lookup -> QueryKind.LOOKUP
            reasoning -> QueryKind.REASONING
            else -> QueryKind.SIMPLE
        }

        // A greeting never justifies either, whatever the markers say.
        val autoSearch = !social && lookup
        val autoThink = !social && reasoning

        val search = when (searchMode) {
            RoutingMode.ALWAYS -> !social
            RoutingMode.NEVER -> false
            RoutingMode.AUTO -> autoSearch
        }
        val think = modelSupportsThinking && when (thinkMode) {
            RoutingMode.ALWAYS -> !social
            RoutingMode.NEVER -> false
            RoutingMode.AUTO -> autoThink
        }

        val maxTokens = when (kind) {
            // A greeting that runs to a thousand tokens is a bug, not an answer.
            QueryKind.SOCIAL -> TRIVIAL_TOKENS
            QueryKind.SIMPLE -> minOf(defaultMaxTokens, SIMPLE_TOKENS)
            else -> defaultMaxTokens
        }

        return RoutingDecision(
            kind = kind,
            search = search,
            think = think,
            maxTokens = maxTokens,
            overridden = searchMode != RoutingMode.AUTO || thinkMode != RoutingMode.AUTO,
        )
    }

    /**
     * A message counts as social when the whole thing is a pleasantry — not
     * merely when it starts with one. "hi, why does X happen" is a real
     * question with a greeting attached.
     */
    private fun isSocial(text: String, words: List<String>): Boolean {
        if (text.isEmpty()) return true
        if (words.size > MAX_SOCIAL_WORDS) return false

        var remaining = text
        var matched = false
        for (phrase in SOCIAL_PHRASES.sortedByDescending { it.length }) {
            if (hasPhrase(remaining, phrase)) {
                matched = true
                remaining = (" $remaining ").replace(" $phrase ", " ")
                    .replace(Regex("\\s+"), " ").trim()
            }
        }
        // A greeting has to actually be present. Without this, any short
        // message fell through as social — "احسب 25 * 17" among them.
        if (!matched) return false

        // Only filler left over (a name, "و", an emoji) means it was just a greeting.
        return remaining.isEmpty() || remaining.length <= LEFTOVER_SLACK
    }

    /**
     * Suffix that turns a reasoning model's chain of thought off or on.
     *
     * Qwen3 and models that copy its convention read `/no_think` and `/think`
     * from the user turn. Sending it is harmless for models that don't — it
     * reads as a stray token — and it's the only way to stop a reasoning model
     * deliberating over "hello".
     */
    fun thinkingDirective(think: Boolean): String = if (think) " /think" else " /no_think"

    private const val MAX_SOCIAL_WORDS = 6
    private const val LEFTOVER_SLACK = 12
    private const val LONG_QUESTION_WORDS = 28
}
