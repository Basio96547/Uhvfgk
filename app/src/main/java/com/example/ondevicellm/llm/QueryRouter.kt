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
    ;

    val label: String
        get() = when (this) {
            SOCIAL -> "Chat"
            SIMPLE -> "Direct"
            LOOKUP -> "Web"
            REASONING -> "Thinking"
        }
}

/**
 * The plan for one message.
 *
 * [reason] is shown in the UI so the routing is never a black box — when it
 * gets something wrong the user can see why and override it.
 */
data class RoutingDecision(
    val kind: QueryKind,
    val search: Boolean,
    val think: Boolean,
    val maxTokens: Int,
    val reason: String,
)

/** Per-feature override. */
enum class RoutingMode {
    /** Decide per message. */
    AUTO,
    ALWAYS,
    NEVER,
    ;

    val label: String
        get() = when (this) {
            AUTO -> "Auto"
            ALWAYS -> "Always"
            NEVER -> "Never"
        }
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
    private val PUNCTUATION = Regex("[\\p{Punct}،؛؟٪-٭۔]")

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

    fun normalize(text: String): String = text
        .lowercase()
        .replace(DIACRITICS, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(PUNCTUATION, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Greetings and pleasantries — never worth a search or a chain of thought. */
    private val SOCIAL_PHRASES = listOf(
        // Arabic
        "مرحبا", "اهلا", "اهلين", "هلا", "السلام عليكم", "وعليكم السلام",
        "صباح الخير", "مساء الخير", "صباح النور", "تصبح علي خير",
        "كيف حالك", "كيفك", "شلونك", "شخبارك", "عساك بخير",
        "شكرا", "مشكور", "يعطيك العافيه", "تسلم", "الله يعافيك",
        "مع السلامه", "وداعا", "الي اللقاء", "تمام", "طيب", "اوك", "ماشي",
        // English
        "hi", "hello", "hey", "yo", "good morning", "good evening", "good night",
        "how are you", "how r u", "whats up", "sup",
        "thanks", "thank you", "thx", "ty", "appreciate it",
        "bye", "goodbye", "see you", "ok", "okay", "cool", "nice", "great",
    )

    /** Asking for something that changes over time, or that the model can't know. */
    private val LOOKUP_MARKERS = listOf(
        // Arabic
        "اليوم", "الان", "حاليا", "الحين", "احدث", "اخر", "جديد", "مؤخرا",
        "اخبار", "خبر", "سعر", "اسعار", "الطقس", "الجو", "درجه الحراره",
        "متي يصدر", "متي صدر", "هذا العام", "هذه السنه", "هذا الشهر",
        "من هو", "من هي", "ما هو موقع", "كم سعر",
        // English
        "today", "right now", "currently", "latest", "newest", "recent",
        "news", "headline", "price", "cost of", "weather", "forecast",
        "stock", "release date", "released", "version", "changelog",
        "this year", "this month", "this week", "who is", "who was",
    )

    /** An explicit instruction to look it up beats any heuristic. */
    private val EXPLICIT_SEARCH = listOf(
        "ابحث", "ابحثلي", "دور", "دورلي", "جيب لي معلومات", "شوف بالنت",
        "search", "google", "look up", "look it up", "find online", "web search",
    )

    /** Wants an explanation or a derivation, not a fact. */
    private val REASONING_MARKERS = listOf(
        // Arabic
        "لماذا", "ليش", "ليه", "كيف يعمل", "كيف تعمل", "اشرح", "وضح",
        "قارن", "الفرق بين", "افضل ام", "حلل", "استنتج", "برهن", "اثبت",
        "احسب", "كم يساوي", "حل المسئله", "خطوه بخطوه", "علل",
        // English
        "why", "how does", "how do", "explain", "elaborate", "walk me through",
        "compare", "difference between", "trade-off", "tradeoff", "pros and cons",
        "analyse", "analyze", "prove", "derive", "calculate", "solve",
        "step by step", "reason about", "debug", "optimize", "optimise",
        "algorithm", "complexity", "refactor",
    )

    /** Arithmetic in the text is a strong signal that working is required. */
    private val ARITHMETIC = Regex("\\d+\\s*[+\\-*/×÷^%]\\s*\\d+")

    /** Code, which usually needs care even when the question is short. */
    private val CODE_MARKERS = listOf(
        "```", "function", "def ", "class ", "import ", "null pointer",
        "stack trace", "exception", "compile", "كود", "داله", "خطا برمجي",
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
        val reasoning = ARITHMETIC.containsMatchIn(message) ||
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
            reason = explain(kind, search, think, searchMode, thinkMode),
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

    private fun explain(
        kind: QueryKind,
        search: Boolean,
        think: Boolean,
        searchMode: RoutingMode,
        thinkMode: RoutingMode,
    ): String {
        val forced = searchMode != RoutingMode.AUTO || thinkMode != RoutingMode.AUTO
        val base = when (kind) {
            QueryKind.SOCIAL -> "Greeting — answering directly"
            QueryKind.SIMPLE -> "Straightforward question"
            QueryKind.LOOKUP -> "Needs current information"
            QueryKind.REASONING -> "Needs working through"
        }
        val extras = buildList {
            if (search) add("searching")
            if (think) add("thinking")
        }
        return when {
            extras.isEmpty() && forced -> "$base · overrides applied"
            extras.isEmpty() -> base
            else -> "$base · ${extras.joinToString(" and ")}"
        }
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
