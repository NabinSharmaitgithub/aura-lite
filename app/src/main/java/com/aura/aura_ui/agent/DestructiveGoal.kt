package com.aura.aura_ui.agent

object DestructiveGoal {
    const val REFUSAL = "I cannot do that: deleting or wiping data in bulk cannot be undone. Please do this yourself in the app or settings."

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

    fun isBulkDestruction(goal: String?): Boolean {
        if (goal.isNullOrBlank()) return false
        val g = goal.lowercase()

        if (listOf(
            "network settings", "wifi", "restart", "password", "phone screen", "pin",
            "this document", "country code", "from this contact", "app preferences",
            "this photo", "last message", "latest download", "that contact",
            "notification", "browsing history", "cache", "clear the table"
        ).any { it in g }) {
            return false
        }

        if (Regex("\\b(factory reset|factory data reset|wipe my device|erase the phone|reset my phone|reset this device|reset my phone settings|reset all settings|reset my settings to default|erase all data|format the sd card|format internal storage)\\b").containsMatchIn(g)) {
            return true
        }

        val hasBulkVerb = Regex("\\b(delete|remove|erase|clear|wipe|purge)\\b").containsMatchIn(g)
        val hasBulkTarget = Regex("\\b(all|every|my photos|my contacts)\\b").containsMatchIn(g)
        val hasData = Regex("\\b(photo|photos|contact|contacts|message|messages|file|files|conversation|conversations|video|videos|email|emails)\\b").containsMatchIn(g)

        if (hasBulkVerb && hasData && (hasBulkTarget || "my photos" in g || "my contacts" in g)) {
            return true
        }

        return false
    }
}
