package com.aura.mcp.server

import com.aura.mcp.McpServerHealth
import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.lan.PairingCrypto
import com.aura.mcp.lan.ServerIdentity
import com.aura.mcp.lan.TrustedDeviceStore
import com.aura.mcp.tools.McpToolRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * LAN MCP transport, JSON-RPC 2.0 over HTTPS. The in-process path never goes through here.
 */
class HttpMcpServer(
    private val registry: McpToolRegistry,
    private val trustedDevices: TrustedDeviceStore,
    private val pairing: PairingCrypto,
    private val identity: ServerIdentity,
    private val audit: McpAuditLogger,
    private val health: () -> McpServerHealth,
) {
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    fun start(bindHost: String, requestedPort: Int): Int {
        port = requestedPort
        engine = embeddedServer(
            factory = Netty,
            configure = {
                sslConnector(
                    keyStore = identity.keyStore,
                    keyAlias = ServerIdentity.ALIAS,
                    keyStorePassword = { ServerIdentity.PASSWORD.toCharArray() },
                    privateKeyPassword = { ServerIdentity.PASSWORD.toCharArray() },
                ) {
                    host = bindHost
                    port = requestedPort
                }
            },
        ) {
            module()
        }.also { it.start(wait = false) }
        return port
    }

    fun stop() {
        engine?.stop(500, 2000)
        engine = null
    }

    private fun Application.module() {
        routing {
            get("/health") {
                call.respondText(health().toJson().toString(), status = HttpStatusCode.OK)
            }

            post("/pair") {
                val body = runCatching { Json.parseToJsonElement(call.receiveText()).jsonObject }.getOrNull()
                val nonce = body?.get("nonce")?.jsonPrimitive?.content
                val name = body?.get("name")?.jsonPrimitive?.content ?: "MCP client"
                if (nonce == null || !pairing.verifyNonce(nonce)) {
                    call.respondText("""{"error":"invalid_nonce"}""", status = HttpStatusCode.Forbidden)
                    return@post
                }
                val token = pairing.newToken()
                val device = trustedDevices.add(name, token)
                call.respondText(
                    buildJsonObject {
                        put("token", token)
                        put("clientId", device.clientId)
                    }.toString(),
                    status = HttpStatusCode.OK,
                )
            }

            post("/mcp") {
                val token = call.request.headers["Authorization"]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val device = token?.let { trustedDevices.findByToken(it) }
                if (device == null) {
                    call.respondText(
                        """{"jsonrpc":"2.0","id":null,"error":{"code":-32001,"message":"unauthorized"}}""",
                        status = HttpStatusCode.Unauthorized,
                    )
                    return@post
                }
                trustedDevices.touch(device.clientId)
                val raw = call.receiveText()
                val response = runCatching { dispatch(raw, device.clientId) }
                    .getOrElse { error ->
                        errorResponse(null, -32603, error.message ?: "internal error")
                    }
                call.respondText(response.toString(), status = HttpStatusCode.OK)
            }
        }
    }

    private suspend fun dispatch(raw: String, clientId: String): JsonObject {
        val request = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return errorResponse(null, -32700, "parse error")
        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.content ?: return errorResponse(id, -32600, "invalid request")
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        return when (method) {
            "initialize" -> success(
                id,
                buildJsonObject {
                    put("protocolVersion", params["protocolVersion"]?.jsonPrimitive?.content ?: "2025-03-26")
                    putJsonObject("serverInfo") {
                        put("name", "aura")
                        put("version", com.aura.mcp.BuildConfig.VERSION_NAME)
                    }
                    putJsonObject("capabilities") { putJsonObject("tools") {} }
                },
            )

            "ping" -> success(id, buildJsonObject { })

            "tools/list" -> success(
                id,
                buildJsonObject {
                    put("tools", buildJsonArray { registry.listTools().forEach { add(it) } })
                },
            )

            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.content ?: return errorResponse(id, -32602, "missing tool name")
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val result = registry.execute(name, arguments)
                success(
                    id,
                    buildJsonObject {
                        put("isError", !result.ok)
                        put("structuredContent", result.toJson())
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", result.toJson().toString())
                                    },
                                )
                            },
                        )
                    },
                )
            }

            "prompts/list" -> success(id, buildJsonObject { put("prompts", JsonArray(emptyList())) })

            "resources/list" -> success(id, buildJsonObject { put("resources", JsonArray(emptyList())) })

            else -> errorResponse(id, -32601, "method not found: $method")
        }
    }

    private fun success(id: kotlinx.serialization.json.JsonElement?, result: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("result", result)
        }

    private fun errorResponse(id: kotlinx.serialization.json.JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
}
