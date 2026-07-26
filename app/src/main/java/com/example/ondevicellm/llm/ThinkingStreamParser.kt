package com.example.ondevicellm.llm

/**
 * Splits a streaming model response into *reasoning* and *answer* text.
 *
 * Reasoning models (Qwen3, DeepSeek-R1 distills, …) wrap their chain of thought
 * in `<think>…</think>`. Because tokens arrive in arbitrary chunks, a tag can
 * be split across two callbacks ("`<thi`" + "`nk>`"). This parser buffers a
 * trailing partial-tag suffix so tags are never missed or leaked into output.
 */
class ThinkingStreamParser(
    private val openTag: String = "<think>",
    private val closeTag: String = "</think>",
    /**
     * Some chat templates pre-fill the opening tag, so the stream starts *inside*
     * the reasoning block and only ever emits a closing tag.
     */
    startInsideThinking: Boolean = false,
) {

    data class Delta(val thinking: String, val answer: String) {
        val isEmpty: Boolean get() = thinking.isEmpty() && answer.isEmpty()
    }

    private val pending = StringBuilder()
    private var inThinking = startInsideThinking

    /** True once a closing tag has been seen — reasoning is complete. */
    var thinkingFinished: Boolean = false
        private set

    /** True if any reasoning text was produced at all. */
    var sawThinking: Boolean = false
        private set

    fun consume(chunk: String): Delta {
        if (chunk.isEmpty()) return Delta("", "")
        pending.append(chunk)

        val thinking = StringBuilder()
        val answer = StringBuilder()

        while (true) {
            val tag = if (inThinking) closeTag else openTag
            val index = pending.indexOf(tag)

            if (index >= 0) {
                val before = pending.substring(0, index)
                if (inThinking) thinking.append(before) else answer.append(before)
                pending.delete(0, index + tag.length)

                if (inThinking) {
                    thinkingFinished = true
                } else {
                    sawThinking = true
                }
                inThinking = !inThinking
                continue
            }

            // No complete tag. Emit everything except a suffix that might still
            // grow into one on the next chunk.
            val hold = partialTagSuffixLength(pending, tag)
            val emitUpTo = pending.length - hold
            if (emitUpTo > 0) {
                val text = pending.substring(0, emitUpTo)
                if (inThinking) thinking.append(text) else answer.append(text)
                pending.delete(0, emitUpTo)
            }
            break
        }

        if (thinking.isNotEmpty()) sawThinking = true
        return Delta(thinking.toString(), answer.toString())
    }

    /** Flushes any buffered text once generation is done. */
    fun flush(): Delta {
        if (pending.isEmpty()) return Delta("", "")
        val rest = pending.toString()
        pending.setLength(0)
        return if (inThinking) Delta(rest, "") else Delta("", rest)
    }

    /**
     * Length of the longest suffix of [buffer] that is a proper prefix of [tag].
     * That suffix must be withheld until more text arrives.
     */
    private fun partialTagSuffixLength(buffer: CharSequence, tag: String): Int {
        val max = minOf(tag.length - 1, buffer.length)
        for (length in max downTo 1) {
            var matches = true
            val start = buffer.length - length
            for (i in 0 until length) {
                if (buffer[start + i] != tag[i]) {
                    matches = false
                    break
                }
            }
            if (matches) return length
        }
        return 0
    }
}
