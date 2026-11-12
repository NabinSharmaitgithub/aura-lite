package com.aura.mcp.tools

import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.bridge.AllowAllRestrictedApps
import com.aura.mcp.bridge.ApprovalGate
import com.aura.mcp.bridge.ApprovalRequest
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.RestrictedAppsStore
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import com.aura.mcp.cache.PerceptionCache
import com.aura.mcp.guard.ActionGuard
import com.aura.mcp.server.policy.SensitivePolicy
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
 * Tool call principal/context for scope and policy enforcement.
 */
data class ToolPrincipal(
    val clientId: String = "local",
    val allowedToolsets: Set<String>? = null, // e.g. CORE/ASSISTANT/BROWSER; null = all
    val allowedTools: Set<String>? = null,    // explicit allowlist; null = catalog
)

/** Hooks for control/pause, settle/observe, and cache invalidation. */
interface ToolPipelineHooks {
    fun isPaused(): Boolean = false
    suspend fun settleAfterMutation(tool: String, args: JsonObject, result: ToolResult) {}
    fun invalidatePerceptionOnMutation(tool: String) {}
}

/**
 * Dispatches tool calls through a single enforcement pipeline (scopedTool-style):
 * principal/scope -> policy -> pause -> restricted foreground -> approval -> action guard -> execute -> audit/hooks.
 */
class McpToolRegistry(
    private val bridge: DeviceBridge,
    private val restrictedApps: RestrictedAppsStore = AllowAllRestrictedApps,
    private val approvalGate: ApprovalGate? = null,
    private val audit: McpAuditLogger? = null,
    private val maxBatchSteps: Int = 12,
    private val clientId: String = "local",
    private val perceptionCache: PerceptionCache? = null,
    private val actionGuard: ActionGuard = ActionGuard(perceptionCache),
    private val hooks: ToolPipelineHooks? = null,
    private val principal: ToolPrincipal = ToolPrincipal(clientId = clientId),
) {

    val tools = ToolCatalog.all

    fun listTools(): List<JsonObject> = ToolCatalog.toolListJson(principal.allowedTools)

    suspend fun execute(name: String, args: JsonObject = JsonObject(emptyMap())): ToolResult {
        val started = System.currentTimeMillis()
        val definition = ToolCatalog.byName[name]
            ?: return ToolResult.fail(ToolError.notFound("Unknown tool: $name"))

        if (name == "run_steps") {
            return executeBatch(args, started)
        }

        // Scope/principal checks
        principal.allowedTools?.let { if (name !in it) return fail(name, args, started, ToolError.scopeDenied("Tool $name is not allowed for this client")) }

        // Human pause precedence
        if (hooks?.isPaused() == true && definition.mutating) {
            return fail(name, args, started, ToolError.paused("Paused by user; retry after resume"))
        }

        val foreground = runCatching { bridge.foregroundPackage() }.getOrNull()

        // Sensitive policy (fail-closed)
        val policy = SensitivePolicy.evaluate(name, args, foreground)
        if (!policy.allowed) {
            return fail(name, args, started, ToolError.policyBlocked(policy.reason ?: "Blocked by policy"), blocked = true)
        }

        // Restricted app / foreground protection for mutating tools
        if (definition.mutating) {
            if (foreground != null && (restrictedApps.isBlocked(foreground) || SensitivePolicy.isSensitivePackage(foreground))) {
                audit?.record(principal.clientId, name, args, false, System.currentTimeMillis() - started, blocked = true)
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
                    audit?.record(principal.clientId, name, args, false, System.currentTimeMillis() - started, blocked = true)
                    return ToolResult.fail(ToolError(ToolError.APPROVAL_DENIED, "The user declined this action."))
                }
            }
        }

        // Action guard (grounding/staleness/basic misuse)
        val guard = actionGuard.evaluate(name, args)
        if (!guard.allowed) {
            return fail(name, args, started, ToolError.actionGuard(guard.reason ?: "Action blocked by guard"), blocked = true)
        }

        val result = runCatching { bridge.execute(name, args) }
            .getOrElse { throwable ->
                ToolResult.fail(ToolError.internal(throwable.message ?: "Tool crashed"))
            }

        if (definition.mutating && result.ok) {
            hooks?.invalidatePerceptionOnMutation(name)
            runCatching { hooks?.settleAfterMutation(name, args, result) }
        }

        audit?.record(
            clientId = principal.clientId,
            tool = name,
            args = args,
            ok = result.ok,
            durationMs = System.currentTimeMillis() - started,
            approvedBy = if (definition.risky) "user" else null,
        )
        return result
    }

    private fun fail(name: String, args: JsonObject, started: Long, error: ToolError, blocked: Boolean = false): ToolResult {
        audit?.record(principal.clientId, name, args, false, System.currentTimeMillis() - started, blocked = blocked)
        return ToolResult.fail(error)
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
                    add(buildJsonObject { put("index", i); put("ok", false); put("error", "invalid step") })
                    return@forEachIndexed
                }
                val stepArgs = step["args"] as? JsonObject ?: JsonObject(emptyMap())
                if (tool == "run_steps") {
                    add(buildJsonObject { put("index", i); put("ok", false); put("error", "run_steps cannot be nested") })
                    return@forEachIndexed
                }
                val outcome = execute(tool, stepArgs)
                add(buildJsonObject {
                    put("index", i)
                    put("tool", tool)
                    put("ok", outcome.ok)
                    put("changed", outcome.changed)
                    if (outcome.observed != null) put("observed", outcome.observed)
                    if (outcome.error != null) putJsonObject("error") {
                        put("code", outcome.error.code)
                        put("message", outcome.error.message)
                    }
                })
                if (!outcome.ok) return@forEachIndexed
            }
        }
        val allOk = results.all { (it as? JsonObject)?.get("ok") == JsonPrimitive(true) }
        return ToolResult.ok(
            changed = true,
            observed = buildJsonObject { put("steps", results); put("count", steps.size) },
            meta = buildJsonObject { put("duration_ms", System.currentTimeMillis() - started) },
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
        perceptionCache = perceptionCache,
        actionGuard = actionGuard,
        hooks = hooks,
        principal = principal,
    )

    fun withPrincipal(newPrincipal: ToolPrincipal) = McpToolRegistry(
        bridge = bridge,
        restrictedApps = restrictedApps,
        approvalGate = approvalGate,
        audit = audit,
        maxBatchSteps = maxBatchSteps,
        clientId = newPrincipal.clientId,
        perceptionCache = perceptionCache,
        actionGuard = actionGuard,
        hooks = hooks,
        principal = newPrincipal,
    )
}
