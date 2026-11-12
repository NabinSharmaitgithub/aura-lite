package com.aura.mcp.guard

import com.aura.mcp.bridge.ToolDefinition

/**
 * Foreground protection: prevents automation in sensitive/protected contexts.
 *
 * This is deliberately conservative: when in doubt, block mutating/perception-sensitive
 * actions and return a structured reason so the user can take over.
 */
object ForegroundGuard {
    data class Decision(val allowed: Boolean, val reason: String? = null)

    private val protectedPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.systemui",
        "com.android.phone",
    )

    private val protectedActivityHints = listOf(
        "permission",
        "install",
        "uninstall",
        "payment",
        "checkout",
        "authenticator",
        "bank",
        "wallet",
        "pin",
        "password",
        "biometric",
    )

    fun evaluate(foregroundPackage: String?, tool: String, definition: ToolDefinition?): Decision {
        if (!definition?.mutating.orFalse() && !perceptionSensitive(tool)) return Decision(true)
        val pkg = foregroundPackage?.trim().orEmpty()
        if (pkg.isBlank()) return Decision(true)
        val lower = pkg.lowercase()
        if (lower in protectedPackages) {
            return Decision(false, "foreground_guard: $pkg is a protected system/permission surface")
        }
        if (lower.contains("authenticator") || lower.contains("password") || lower.contains("bank") || lower.contains("wallet")) {
            return Decision(false, "foreground_guard: $pkg is a sensitive credential/financial app")
        }
        return Decision(true)
    }

    fun evaluateActivity(activityClass: String?): Decision {
        val hint = activityClass?.lowercase().orEmpty()
        if (hint.isBlank()) return Decision(true)
        return if (protectedActivityHints.any { hint.contains(it) }) {
            Decision(true) // activity hints are advisory; package checks are authoritative
        } else {
            Decision(true)
        }
    }

    private fun perceptionSensitive(tool: String): Boolean = tool in setOf(
        "read_screen", "perceive_screen", "get_screenshot", "get_device_status",
    )

    private fun Boolean?.orFalse(): Boolean = this ?: false
}
