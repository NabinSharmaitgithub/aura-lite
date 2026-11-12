package com.aura.aura_ui.agent.strategy

/**
 * Context compaction heuristics: keeps recent turns and critical state, trims old bulky
 * observations/tool results to control prompt size.
 */
object ContextCompactor {
    data class Options(
        val maxMessages: Int = 40,
        val keepRecent: Int = 16,
        val maxCharsPerToolResult: Int = 1800,
    )

    fun compact(messages: MutableList<String>, options: Options = Options()): List<String> {
        if (messages.size <= options.maxMessages) {
            return messages.map { trim(it, options.maxCharsPerToolResult) }
        }
        val head = messages.take(2) // system + goal
        val tail = messages.takeLast(options.keepRecent)
        val middle = messages.drop(2).dropLast(options.keepRecent).takeLast(options.maxMessages - head.size - tail.size)
        return (head + middle + tail).map { trim(it, options.maxCharsPerToolResult) }
    }

    private fun trim(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max) + "…[truncated]"
}
