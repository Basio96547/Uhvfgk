package com.basel.ai.chat

import com.basel.ai.agent.ToolRun
import com.basel.ai.llm.RoutingDecision
import com.basel.ai.web.SearchSource

enum class Author { USER, MODEL }

data class ChatMessage(
    val id: Long,
    val author: Author,
    val text: String,
    val thinking: String = "",
    val isGenerating: Boolean = false,
    val thinkingExpanded: Boolean = false,
    /** Pages the answer was grounded in, when web search ran. */
    val sources: List<SearchSource> = emptyList(),
    /**
     * How this reply was routed. Kept as the decision rather than a sentence
     * so the UI can word it in the reader's language.
     */
    val routing: RoutingDecision? = null,
    /** Tools the model ran to produce this reply, in order. */
    val toolRuns: List<ToolRun> = emptyList(),
)
