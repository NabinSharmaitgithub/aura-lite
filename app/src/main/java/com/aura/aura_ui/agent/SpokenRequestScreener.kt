package com.aura.aura_ui.agent

/**
 * Screens voice-originated requests before automation. Voice is easy to mis-hear and easy to
 * trigger accidentally, so high-impact requests get an explicit confirmation card even when the
 * text-based destructive-goal heuristic would not require one.
 */
object SpokenRequestScreener {
    private val highImpact = listOf(
        "send money", "transfer", "pay ", "purchase", "checkout", "delete", "erase",
        "uninstall", "factory reset", "format", "password", "passcode", "otp",
        "one-time code", "verification code", "credit card", "card number", "cvv",
        "call ", "dial ", "message ", "text ", "email ", "share my location",
    )

    fun requiresConfirmation(goal: String, voice: Boolean): Boolean {
        if (!voice) return false
        val lower = goal.lowercase()
        return highImpact.any { lower.contains(it) }
    }

    fun reason(goal: String): String =
        "This voice request can change data, contact someone, or touch sensitive information: \"${goal.take(120)}\""
}
