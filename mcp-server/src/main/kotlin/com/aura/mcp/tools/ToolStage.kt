package com.aura.mcp.tools

/**
 * Position in the tool-enforcement pipeline. Every early return now records the exact gate that
 * decided the outcome, instead of leaving "why" implicit in the function body.
 */
enum class ToolStage(val wire: String) {
    RECEIVED("received"),
    SCOPE_CHECKED("scope_checked"),
    PAUSED("paused"),
    POLICY_BLOCKED("policy_blocked"),
    FOREGROUND_BLOCKED("foreground_blocked"),
    APPROVAL_PENDING("approval_pending"),
    APPROVAL_DENIED("approval_denied"),
    OTP_BLOCKED("otp_blocked"),
    GUARD_BLOCKED("guard_blocked"),
    EXECUTING("executing"),
    SETTLING("settling"),
    DONE("done"),
    FAILED("failed"),
    ;

    val blocked: Boolean
        get() = this == PAUSED || this == POLICY_BLOCKED || this == FOREGROUND_BLOCKED ||
            this == APPROVAL_DENIED || this == OTP_BLOCKED || this == GUARD_BLOCKED
}
