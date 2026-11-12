package com.aura.mcp.server.policy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fail-closed policy checks for sensitive targets and exfiltration-prone tool calls.
 *
 * Contract:
 * - Denials are permanent for the attempted action (`policy_blocked`), not temporary pauses.
 * - This is code enforcement, not prompt guidance.
 * - Checks are conservative and may produce false positives; prefer safe refusal.
 */
object SensitivePolicy {

    data class Decision(
        val allowed: Boolean,
        val code: String = "policy_blocked",
        val reason: String? = null,
        val category: String? = null,
    ) {
        companion object {
            val Allow = Decision(true)
            fun deny(reason: String, category: String = "sensitive"): Decision =
                Decision(false, "policy_blocked", reason, category)
        }
    }

    private val sensitivePackageTokens = listOf(
        "bank", "banking", "wallet", "pay", "payment", "upi", "phonepe", "gpay", "googlepay",
        "paypal", "venmo", "cashapp", "revolut", "wise", "binance", "coinbase", "crypto",
        "authenticator", "otp", "2fa", "password", "keychain", "keepass", "bitwarden",
        "lastpass", "1password", "dashlane", "enpass",
    )

    private val sensitiveUrlTokens = listOf(
        "login", "signin", "sign-in", "password", "passwd", "otp", "2fa", "verify",
        "checkout", "payment", "billing", "card", "bank",
    )

    private val outgoingTools = setOf("type_text", "set_value", "clipboard", "open_deeplink", "system_intent", "browser_act", "browser_upload", "compose_sms", "dial")

    fun evaluate(
        tool: String,
        args: JsonObject,
        foregroundPackage: String? = null,
        userGoal: String? = null,
    ): Decision {
        val fg = foregroundPackage?.lowercase()
        if (fg != null && isSensitivePackage(fg)) {
            return Decision.deny(
                "Actions are blocked in sensitive app context ($foregroundPackage). Hand control to the user.",
                "sensitive_app",
            )
        }

        userGoal?.let {
            if (containsCredentialHarvestIntent(it)) {
                return Decision.deny(
                    "Request appears to involve credentials/OTP/payment secrets and is blocked.",
                    "sensitive_goal",
                )
            }
        }

        val textBlob = collectText(args)
        if (tool in outgoingTools && looksLikeSecretMaterial(textBlob)) {
            return Decision.deny(
                "Refusing to enter or transmit likely secret/OTP/payment data.",
                "sensitive_exfiltration",
            )
        }

        if (tool == "open_deeplink" || tool == "browser_open") {
            val uri = args.string("uri") ?: args.string("url")
            if (uri != null && isSensitiveUri(uri)) {
                return Decision.deny(
                    "Opening sensitive/auth/payment URLs is restricted; ask the user to continue manually.",
                    "sensitive_url",
                )
            }
        }

        if (tool == "system_intent") {
            val action = args.string("action")?.lowercase().orEmpty()
            if (action.contains("password") || action.contains("security") || action.contains("payment")) {
                return Decision.deny("Sensitive system intent is blocked.", "sensitive_intent")
            }
        }

        return Decision.Allow
    }

    fun isSensitivePackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return sensitivePackageTokens.any { p.contains(it) }
    }

    private fun isSensitiveUri(uri: String): Boolean {
        val u = uri.lowercase()
        return sensitiveUrlTokens.any { u.contains(it) }
    }

    private fun collectText(args: JsonObject): String = buildString {
        args.forEach { (k, v) ->
            append(k).append(':')
            append(v.toString())
            append('\n')
        }
    }.lowercase()

    private fun looksLikeSecretMaterial(text: String): Boolean {
        if (text.contains("password") || text.contains("passwd") || text.contains("passcode")) return true
        if (text.contains("otp") || text.contains("one-time") || text.contains("verification code")) return true
        if (text.contains("cvv") || text.contains("cvc") || text.contains("card number")) return true
        if (Regex("\\b\\d{6}\\b").containsMatchIn(text) && (text.contains("otp") || text.contains("code"))) return true
        return false
    }

    private fun containsCredentialHarvestIntent(goal: String): Boolean {
        val g = goal.lowercase()
        val asksSecret = listOf("password", "otp", "2fa", "verification code", "cvv", "card number", "pin")
        val exfilVerb = listOf("tell me", "read", "show", "extract", "copy", "share", "send")
        return asksSecret.any { g.contains(it) } && exfilVerb.any { g.contains(it) }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
