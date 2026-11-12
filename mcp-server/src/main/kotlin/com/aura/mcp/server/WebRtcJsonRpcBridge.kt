package com.aura.mcp.server

import com.aura.mcp.bridge.WebRtcTransport
import com.aura.mcp.tools.McpToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Bridges aura-lite's JSON-RPC/MCP dispatch over a WebRTC DataChannel transport.
 * Handles newline-delimited framing and per-client principal-scoped tool execution.
 */
class WebRtcJsonRpcBridge(
    private val transport: WebRtcTransport,
    private val baseRegistry: McpToolRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val buffer = StringBuilder()
    @Volatile private var clientRegistry: McpToolRegistry = baseRegistry

    init {
        transport.onBytesReceived = { bytes ->
            val text = bytes.toString(Charsets.UTF_8)
            synchronized(buffer) {
                buffer.append(text)
                while (true) {
                    val idx = buffer.indexOf("\n")
                    if (idx < 0) break
                    val line = buffer.substring(0, idx).trim()
                    buffer.delete(0, idx + 1)
                    if (line.isNotEmpty()) dispatchLine(line)
                }
            }
        }
        transport.onNewClient = { client ->
            clientRegistry = baseRegistry.withPrincipal(ToolAccess.principalFor(client.tokenId, client.toolsets.toList()))
        }
        transport.onDisconnected = {
            clientRegistry = baseRegistry
        }
    }

    private fun dispatchLine(line: String) {
        scope.launch {
            val response = runCatching { dispatch(line) }.getOrElse { e ->
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", kotlinx.serialization.json.JsonNull)
                    putJsonObject("error") { put("code", -32603); put("message", e.message ?: "internal error") }
                }
            }
            response?.let { send(it) }
        }
    }

    private suspend fun dispatch(raw: String): JsonObject? {
        val request = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return errorResponse(null, -32700, "parse error")
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.content ?: return errorResponse(id, -32600, "invalid request")
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        // Notifications have no id in some cases; still handle common ones.
        return when (method) {
            "initialize" -> success(id, McpRpc.initializeResult())
            "notifications/initialized" -> null
            "ping" -> success(id, buildJsonObject { })
            "tools/list" -> success(id, buildJsonObject {
                put("tools", buildJsonArray { clientRegistry.listTools().forEach { add(it) } })
            })
            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "missing tool name")
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val result = clientRegistry.execute(name, arguments)
                success(id, buildJsonObject {
                    put("isError", !result.ok)
                    put("structuredContent", result.toJson())
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", result.toJson().toString()) })
                    })
                })
            }
            "prompts/list" -> success(id, buildJsonObject { put("prompts", McpRpc.promptsList()) })
            "prompts/get" -> {
                val name = params["name"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "missing prompt name")
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val prompt = McpRpc.getPrompt(name, arguments) ?: return errorResponse(id, -32602, "unknown prompt: $name")
                success(id, prompt)
            }
            "resources/list" -> success(id, buildJsonObject { put("resources", McpRpc.resourcesList()) })
            "resources/read" -> {
                val uri = params["uri"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "missing resource uri")
                val resource = McpRpc.readResource(uri) ?: return errorResponse(id, -32602, "unknown resource: $uri")
                success(id, resource)
            }
            else -> if (id != null) errorResponse(id, -32601, "method not found: $method") else null
        }
    }

    private fun send(obj: JsonObject) {
        transport.sendFrame((obj.toString() + "\n").toByteArray(Charsets.UTF_8))
    }

    private fun success(id: kotlinx.serialization.json.JsonElement?, result: JsonObject): JsonObject =
        buildJsonObject { put("jsonrpc", "2.0"); put("id", id ?: kotlinx.serialization.json.JsonNull); put("result", result) }

    private fun errorResponse(id: kotlinx.serialization.json.JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id ?: kotlinx.serialization.json.JsonNull)
            putJsonObject("error") { put("code", code); put("message", message) }
        }

    fun close() {
        // scope left to caller lifecycle; transport closed by controller
    }
}
