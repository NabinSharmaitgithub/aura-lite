package com.aura.mcp.server

import com.aura.mcp.bridge.ToolResult
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolCatalog
import com.aura.mcp.bridge.ToolDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The local agent's client. No sockets, no TLS, no auth: it calls the same registry that
 * external clients reach over LAN HTTPS, so the contract cannot drift.
 */
interface McpClient {
    suspend fun initialize(): JsonObject
    fun listTools(): List<ToolDefinition>
    suspend fun callTool(name: String, args: JsonObject): ToolResult
    fun listResources(): JsonArray
    fun readResource(uri: String): JsonObject?
    fun listPrompts(): JsonArray
    fun getPrompt(name: String, arguments: JsonObject): JsonObject?
}

class InProcessMcpServer(private val registry: McpToolRegistry) {

    fun listTools(): List<ToolDefinition> = ToolCatalog.all

    suspend fun call(name: String, args: JsonObject): ToolResult = registry.execute(name, args)

    fun initialize(): JsonObject = McpRpc.initializeResult()

    fun listResources(): JsonArray = McpResources.list()

    fun readResource(uri: String): JsonObject? = McpResources.read(uri)

    fun listPrompts(): JsonArray = McpPrompts.list()

    fun getPrompt(name: String, arguments: JsonObject): JsonObject? = McpPrompts.get(name, arguments)

    fun asClient(): McpClient = object : McpClient {
        override suspend fun initialize(): JsonObject = this@InProcessMcpServer.initialize()
        override fun listTools(): List<ToolDefinition> = this@InProcessMcpServer.listTools()
        override suspend fun callTool(name: String, args: JsonObject): ToolResult =
            this@InProcessMcpServer.call(name, args)
        override fun listResources(): JsonArray = this@InProcessMcpServer.listResources()
        override fun readResource(uri: String): JsonObject? = this@InProcessMcpServer.readResource(uri)
        override fun listPrompts(): JsonArray = this@InProcessMcpServer.listPrompts()
        override fun getPrompt(name: String, arguments: JsonObject): JsonObject? =
            this@InProcessMcpServer.getPrompt(name, arguments)
    }
}
