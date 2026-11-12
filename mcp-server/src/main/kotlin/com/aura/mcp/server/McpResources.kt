package com.aura.mcp.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** MCP resources exposed by the server (policy/docs/device status). */
object McpResources {
    fun list(): JsonArray = McpRpc.resourcesList()
    fun read(uri: String): JsonObject? = McpRpc.readResource(uri)
}
