package com.aura.mcp.bridge

interface McpAuditLogger {
    fun log(
        toolName: String,
        tokenId: String?,
        success: Boolean,
        scopeDenied: Boolean,
        durationMs: Long,
        errorSummary: String? = null,
    )

    fun logConnection(
        event: ConnectionEventType,
        tokenId: String?,
        label: String?,
        host: String?,
        platform: String?,
    ) = Unit
}

enum class ConnectionEventType {
    AUTO_APPROVED,
    APPROVED,
    DENIED,
    DISCONNECTED,
}

object NoOpAuditLogger : McpAuditLogger {
    override fun log(
        toolName: String,
        tokenId: String?,
        success: Boolean,
        scopeDenied: Boolean,
        durationMs: Long,
        errorSummary: String?,
    ) = Unit
}
