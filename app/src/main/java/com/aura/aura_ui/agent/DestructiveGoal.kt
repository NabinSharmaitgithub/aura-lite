package com.aura.aura_ui.agent

/**
 * Conservative classifier for goals whose execution is expensive or irreversible.
 * A match never blocks the run; it raises one confirmation card before the first mutation.
 */
object DestructiveGoal {
    private val patterns = listOf(
        Regex("\\b(send|post|publish|share|email|sms|text|message)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(call|dial|ring)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(delete|remove|erase|clear|wipe|uninstall|format)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(pay|purchase|buy|order|checkout|transfer|book|subscribe)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(unfriend|block|report|unsubscribe)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(install|download)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(record|screenshot)\\b", RegexOption.IGNORE_CASE),
    )

    fun isDestructive(goal: String): Boolean = patterns.any { it.containsMatchIn(goal) }

    fun reason(goal: String): String = when {
        Regex("\\b(pay|purchase|buy|order|checkout|transfer|book|subscribe)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal) ->
            "This can spend money or create a commitment."
        Regex("\\b(delete|remove|erase|clear|wipe|uninstall|format)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal) ->
            "This deletes data and cannot be undone."
        Regex("\\b(send|post|publish|share|email|sms|text|message)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal) ->
            "This sends something on your behalf."
        Regex("\\b(call|dial|ring)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal) ->
            "This places a call."
        else -> "This changes state outside AURA."
    }
}
