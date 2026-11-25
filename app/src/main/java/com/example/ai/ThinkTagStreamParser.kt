package com.example.ai

/**
 * Incrementally separates `<think>...</think>` reasoning blocks from answer text
 * across arbitrary streaming chunk boundaries.
 */
class ThinkTagStreamParser {
    private val buffer = StringBuilder()
    private var inThinkBlock = false

    data class ParsedDelta(
        val thoughtDelta: String,
        val answerDelta: String,
    )

    fun feed(chunk: String): ParsedDelta {
        if (chunk.isEmpty()) return ParsedDelta("", "")
        buffer.append(chunk)

        val thoughtOut = StringBuilder()
        val answerOut = StringBuilder()

        while (buffer.isNotEmpty()) {
            if (!inThinkBlock) {
                val startIdx = buffer.indexOf(OPEN_TAG)
                if (startIdx >= 0) {
                    if (startIdx > 0) {
                        answerOut.append(buffer.substring(0, startIdx))
                    }
                    buffer.delete(0, startIdx + OPEN_TAG.length)
                    inThinkBlock = true
                } else {
                    val holdBack = partialSuffixLength(buffer, OPEN_TAG)
                    val safeLen = buffer.length - holdBack
                    if (safeLen > 0) {
                        answerOut.append(buffer.substring(0, safeLen))
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            } else {
                val endIdx = buffer.indexOf(CLOSE_TAG)
                if (endIdx >= 0) {
                    if (endIdx > 0) {
                        thoughtOut.append(buffer.substring(0, endIdx))
                    }
                    buffer.delete(0, endIdx + CLOSE_TAG.length)
                    inThinkBlock = false
                } else {
                    val holdBack = partialSuffixLength(buffer, CLOSE_TAG)
                    val safeLen = buffer.length - holdBack
                    if (safeLen > 0) {
                        thoughtOut.append(buffer.substring(0, safeLen))
                        buffer.delete(0, safeLen)
                    }
                    break
                }
            }
        }

        return ParsedDelta(
            thoughtDelta = thoughtOut.toString(),
            answerDelta = answerOut.toString(),
        )
    }

    fun flush(): ParsedDelta {
        if (buffer.isEmpty()) return ParsedDelta("", "")
        val remaining = buffer.toString()
        buffer.clear()
        return if (inThinkBlock) {
            ParsedDelta(thoughtDelta = remaining, answerDelta = "")
        } else {
            ParsedDelta(thoughtDelta = "", answerDelta = remaining)
        }
    }

    private fun partialSuffixLength(text: CharSequence, tag: String): Int {
        val maxCheck = minOf(text.length, tag.length - 1)
        for (len in maxCheck downTo 1) {
            var matches = true
            val start = text.length - len
            for (i in 0 until len) {
                if (text[start + i] != tag[i]) {
                    matches = false
                    break
                }
            }
            if (matches) return len
        }
        return 0
    }

    private companion object {
        const val OPEN_TAG = "<think>"
        const val CLOSE_TAG = "</think>"
    }
}
