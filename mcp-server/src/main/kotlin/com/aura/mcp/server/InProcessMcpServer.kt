package com.aura.mcp.server

import com.aura.mcp.bridge.ToolResult
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolCatalog
import com.aura.mcp.bridge.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/**
 * The local agent's client. No sockets, no TLS, no auth: it calls the same registry that
 * external clients reach over LAN HTTPS, so the contract cannot drift.
 */
interface McpClient {
    suspend fun initialize(): JsonObject
    fun listTools(): List<ToolDefinition>
    suspend fun callTool(name: String, args: JsonObject): ToolResult
}

class InProcessMcpServer(private val registry: McpToolRegistry) {

    fun listTools(): List<ToolDefinition> = ToolCatalog.all

    suspend fun call(name: String, args: JsonObject): ToolResult = registry.execute(name, args)

    fun initialize(): JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("protocolVersion", "2025-03-26")
        put("serverInfo", kotlinx.serialization.json.buildJsonObject {
            put("name", "aura")
            put("version", com.aura.mcp.BuildConfig.VERSION_NAME)
        })
        put("capabilities", kotlinx.serialization.json.buildJsonObject {
            put("tools", kotlinx.serialization.json.buildJsonObject {})
        })
    }

    fun asClient(): McpClient = object : McpClient {
        override suspend fun initialize(): JsonObject = this@InProcessMcpServer.initialize()
        override fun listTools(): List<ToolDefinition> = this@InProcessMcpServer.listTools()
        override suspend fun callTool(name: String, args: JsonObject): ToolResult =
            this@InProcessMcpServer.call(name, args)
    }
}
