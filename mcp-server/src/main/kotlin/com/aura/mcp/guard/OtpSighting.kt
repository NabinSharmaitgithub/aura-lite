package com.aura.mcp.guard

/**
 * OTP/verification-code sighting guard.
 *
 * Detects likely one-time codes in captured text and prevents unsafe propagation/copying/typing.
 * Raw OTP values are never logged or stored.
 */
object OtpSighting {
    data class Sight(val detected: Boolean, val reason: String? = null)

    private val otpPatterns = listOf(
        Regex("""\b\d{4,8}\b"""),
        Regex("""(?i)\b(otp|one[- ]?time|verification code|code)\b[^\d]{0,20}(\d{4,8})\b"""),
    )

    fun inspect(text: String?): Sight {
        if (text.isNullOrBlank()) return Sight(false)
        // Inspect the whole payload: an OTP can appear anywhere in a tool argument string.
        // Keep a generous cap so huge blobs do not cause regex work, but do not skip normal
        // JSON payloads (which are commonly 40–200 characters).
        val trimmed = text.trim().take(4_000)
        if (trimmed.isEmpty()) return Sight(false)
        val hasKeyword = Regex("(?i)\\b(otp|one[- ]?time|verification|2fa|auth code|security code)\\b").containsMatchIn(trimmed)
        if (!hasKeyword) return Sight(false)
        val hasCode = otpPatterns.any { it.containsMatchIn(trimmed) }
        return if (hasCode) Sight(true, "Possible OTP/verification code detected; sensitive propagation blocked") else Sight(false)
    }

    fun redact(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var out: String = text
        otpPatterns.forEach { pattern ->
            out = pattern.replace(out) { match ->
                val value = match.value
                if (value.all { it.isDigit() } && value.length in 4..8) "******" else value
            }
        }
        return out
    }

    fun blocksTool(tool: String): Boolean = tool in setOf(
        "type_text", "set_value", "clipboard", "compose_sms", "upload_file",
    )
}
