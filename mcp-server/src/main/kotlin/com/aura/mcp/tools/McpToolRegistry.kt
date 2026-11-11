package com.aura.mcp.tools

import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.bridge.AllowAllRestrictedApps
import com.aura.mcp.bridge.ApprovalGate
import com.aura.mcp.bridge.ApprovalRequest
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.RestrictedAppsStore
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Dispatches tool calls to the device bridge while enforcing the runtime contract:
 *  - restricted apps are refused before dispatch,
 *  - risky tools require human approval,
 *  - every call is audited,
 *  - batches are bounded.
 */
class McpToolRegistry(
    private val bridge: DeviceBridge,
    private val restrictedApps: RestrictedAppsStore = AllowAllRestrictedApps,
    private val approvalGate: ApprovalGate? = null,
    private val audit: McpAuditLogger? = null,
    private val maxBatchSteps: Int = 12,
    private val clientId: String = "local",
) {

    val tools = ToolCatalog.all

    fun listTools(): List<JsonObject> = ToolCatalog.toolListJson()

    suspend fun execute(name: String, args: JsonObject = JsonObject(emptyMap())): ToolResult {
        val started = System.currentTimeMillis()
        val definition = ToolCatalog.byName[name]
            ?: return ToolResult.fail(ToolError.notFound("Unknown tool: $name"))

        if (name == "run_steps") {
            return executeBatch(args, started)
        }

        if (definition.mutating) {
            val foreground = runCatching { bridge.foregroundPackage() }.getOrNull()
            if (foreground != null && restrictedApps.isBlocked(foreground)) {
                audit?.record(clientId, name, args, false, System.currentTimeMillis() - started, blocked = true)
                return ToolResult.fail(ToolError.blocked(foreground))
            }
            if (definition.risky && approvalGate != null) {
                val approved = approvalGate.approve(
                    ApprovalRequest(
                        tool = name,
                        description = definition.description,
                        args = args,
                        packageName = foreground,
                    ),
                )
                if (!approved) {
                    audit?.record(clientId, name, args, false, System.currentTimeMillis() - started, blocked = true)
                    return ToolResult.fail(ToolError(ToolError.APPROVAL_DENIED, "The user declined this action."))
                }
            }
        }

        val result = runCatching { bridge.execute(name, args) }
            .getOrElse { throwable ->
                ToolResult.fail(ToolError.internal(throwable.message ?: "Tool crashed"))
            }
        audit?.record(
            clientId = clientId,
            tool = name,
            args = args,
            ok = result.ok,
            durationMs = System.currentTimeMillis() - started,
            approvedBy = if (definition.risky) "user" else null,
        )
        return result
    }

    private suspend fun executeBatch(args: JsonObject, started: Long): ToolResult {
        val steps = args["steps"] as? JsonArray
            ?: return ToolResult.fail(ToolError("invalid_args", "steps must be an array"))
        if (steps.size > maxBatchSteps) {
            return ToolResult.fail(ToolError("invalid_args", "Batch exceeds $maxBatchSteps steps"))
        }
        val results = buildJsonArray {
            steps.forEachIndexed { i, element ->
                val step = runCatching { element.jsonObject }.getOrNull()
                val tool = step?.get("tool")?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (step == null || tool.isNullOrBlank()) {
                    add(
                        buildJsonObject {
                            put("index", i)
                            put("ok", false)
                            put("error", "invalid step")
                        },
                    )
                    return@forEachIndexed
                }
                val stepArgs = step["args"] as? JsonObject ?: JsonObject(emptyMap())
                if (tool == "run_steps") {
                    add(
                        buildJsonObject {
                            put("index", i)
                            put("ok", false)
                            put("error", "run_steps cannot be nested")
                        },
                    )
                    return@forEachIndexed
                }
                val outcome = execute(tool, stepArgs)
                add(
                    buildJsonObject {
                        put("index", i)
                        put("tool", tool)
                        put("ok", outcome.ok)
                        put("changed", outcome.changed)
                        if (outcome.observed != null) put("observed", outcome.observed)
                        if (outcome.error != null) putJsonObject("error") {
                            put("code", outcome.error.code)
                            put("message", outcome.error.message)
                        }
                    },
                )
                if (!outcome.ok) return@forEachIndexed
            }
        }
        val allOk = results.all { (it as? JsonObject)?.get("ok") == JsonPrimitive(true) }
        return ToolResult.ok(
            changed = true,
            observed = buildJsonObject {
                put("steps", results)
                put("count", steps.size)
            },
            meta = buildJsonObject {
                put("duration_ms", System.currentTimeMillis() - started)
            },
        ).let { if (allOk) it else it.copy(ok = false) }
    }

    /** Direct registry used by tests without approval latency. */
    fun withBridge(newBridge: DeviceBridge) = McpToolRegistry(
        bridge = newBridge,
        restrictedApps = restrictedApps,
        approvalGate = approvalGate,
        audit = audit,
        maxBatchSteps = maxBatchSteps,
        clientId = clientId,
    )
}
