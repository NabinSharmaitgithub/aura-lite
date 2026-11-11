package com.aura.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class McpServerHealth(
    val running: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 0,
    val toolCount: Int = 0,
    val clientCount: Int = 0,
    val startedAt: Long = 0L,
    val lastError: String? = null,
) {
    val uptimeMs: Long get() = if (startedAt == 0L) 0 else System.currentTimeMillis() - startedAt

    fun toJson(): JsonObject = buildJsonObject {
        put("status", if (running) "ok" else "stopped")
        put("version", com.aura.mcp.BuildConfig.VERSION_NAME)
        put("tools", toolCount)
        put("clients", clientCount)
        put("uptime", uptimeMs / 1000)
        if (lastError != null) put("error", lastError)
    }
}
