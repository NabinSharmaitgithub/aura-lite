package com.aura.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WebRtcApprovalRequest(
    val clientName: String = "",
    val version: String = "",
    val verificationCode: String = "",
    val host: String? = null,
    val platform: String? = null,
)

sealed interface McpServerHealth {
    val running: Boolean get() = false
    val host: String get() = "127.0.0.1"
    val port: Int get() = 0
    val toolCount: Int get() = 0
    val clientCount: Int get() = 0
    val startedAt: Long get() = 0L
    val lastError: String? get() = null
    val state: String get() = "stopped"
    val uptimeMs: Long get() = if (startedAt == 0L) 0 else System.currentTimeMillis() - startedAt

    fun toJson(): JsonObject = buildJsonObject {
        put("status", if (running) "ok" else "stopped")
        put("state", state)
        put("version", com.aura.mcp.BuildConfig.VERSION_NAME)
        put("tools", toolCount)
        put("clients", clientCount)
        put("uptime", uptimeMs / 1000)
        if (lastError != null) put("error", lastError)
    }

    fun copy(
        running: Boolean = this.running,
        host: String = this.host,
        port: Int = this.port,
        toolCount: Int = this.toolCount,
        clientCount: Int = this.clientCount,
        startedAt: Long = this.startedAt,
        lastError: String? = this.lastError,
        state: String = this.state,
    ): McpServerHealth = Simple(
        running = running,
        host = host,
        port = port,
        toolCount = toolCount,
        clientCount = clientCount,
        startedAt = startedAt,
        lastError = lastError,
        state = state,
    )

    data class Simple(
        override val running: Boolean = false,
        override val host: String = "127.0.0.1",
        override val port: Int = 0,
        override val toolCount: Int = 0,
        override val clientCount: Int = 0,
        override val startedAt: Long = 0L,
        override val lastError: String? = null,
        override val state: String = "stopped",
    ) : McpServerHealth

    data object Stopped : McpServerHealth {
        override val running: Boolean = false
        override val state: String = "stopped"
    }

    data object Starting : McpServerHealth {
        override val running: Boolean = false
        override val state: String = "starting"
    }

    data class Crashed(val reason: String) : McpServerHealth {
        override val running: Boolean = false
        override val lastError: String = reason
        override val state: String = "crashed"
    }

    data class Listening(
        override val port: Int = 4816,
        override val host: String = "127.0.0.1",
        val reachableAddresses: List<String> = emptyList(),
        override val toolCount: Int = 0,
        override val clientCount: Int = 0,
        override val startedAt: Long = System.currentTimeMillis(),
        override val state: String = "listening",
    ) : McpServerHealth {
        override val running: Boolean get() = true
    }

    companion object {
        operator fun invoke(
            running: Boolean = false,
            host: String = "127.0.0.1",
            port: Int = 0,
            toolCount: Int = 0,
            clientCount: Int = 0,
            startedAt: Long = 0L,
            lastError: String? = null,
            state: String = "stopped",
        ): McpServerHealth = Simple(
            running = running,
            host = host,
            port = port,
            toolCount = toolCount,
            clientCount = clientCount,
            startedAt = startedAt,
            lastError = lastError,
            state = state,
        )
    }
}
