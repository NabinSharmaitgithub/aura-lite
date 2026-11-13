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
import com.aura.mcp.guard.ForegroundGuard
import com.aura.mcp.guard.OtpSighting
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
    // Cross-cutting sinks. All emissions are best-effort: a failing sink must never break a tool call.
    private val onSession: ((String, Map<String, Any?>) -> Unit)? = null,
    private val onToolPhase: ((String, String, Map<String, Any?>) -> Unit)? = null,
    private val onTrail: ((String, Map<String, Any?>) -> Unit)? = null,
    private val onPostAction: ((String, JsonObject, ToolResult) -> Unit)? = null,
    private val onLearnings: ((String, JsonObject, ToolResult) -> Unit)? = null,
) {

    val tools = ToolCatalog.all

    private inline fun emit(block: () -> Unit) {
        runCatching(block)
    }

    fun listTools(): List<JsonObject> = ToolCatalog.all
        .filter { def ->
            val byTool = principal.allowedTools?.let { def.name in it } ?: true
            val byToolset = if (principal.allowedTools != null) {
                true
            } else {
                principal.allowedToolsets?.let { ToolCatalog.toolsetOf(def.name).name in it } ?: true
            }
            byTool && byToolset
        }
        .map { it.toJson() }

    suspend fun execute(name: String, args: JsonObject = JsonObject(emptyMap())): ToolResult {
        val started = System.currentTimeMillis()
        val definition = ToolCatalog.byName[name]
            ?: return ToolResult.fail(ToolError.notFound("Unknown tool: $name"))

        // Scope/principal checks
        principal.allowedTools?.let { if (name !in it) return fail(name, args, started, ToolError.scopeDenied("Tool $name is not allowed for this client"), stage = ToolStage.SCOPE_CHECKED) }
        // When an explicit tool allowlist is present it is authoritative (it may span toolsets).
        // Otherwise enforce the toolsets the client was granted.
        if (principal.allowedTools == null) {
            principal.allowedToolsets?.let { allowed ->
                val toolset = ToolCatalog.toolsetOf(name).name
                if (toolset !in allowed) {
                    return fail(
                        name,
                        args,
                        started,
                        ToolError.scopeDenied("Toolset $toolset is not allowed for this client"),
                        stage = ToolStage.SCOPE_CHECKED,
                    )
                }
            }
        }

        emit { onToolPhase?.invoke(name, "start", mapOf("client" to principal.clientId, "stage" to ToolStage.RECEIVED.wire)) }
        emit { onSession?.invoke("tool_call", mapOf("tool" to name, "mutating" to definition.mutating)) }

        // Human pause precedence
        if (hooks?.isPaused() == true && definition.mutating) {
            return fail(name, args, started, ToolError.paused("Paused by user; retry after resume"), stage = ToolStage.PAUSED)
        }

        // Batch execution is itself scope/pause checked; each nested tool re-enters this pipeline.
        if (name == "run_steps") {
            return executeBatch(args, started)
        }

        val foreground = runCatching { bridge.foregroundPackage() }.getOrNull()

        // Sensitive policy (fail-closed)
        val policy = SensitivePolicy.evaluate(name, args, foreground)
        if (!policy.allowed) {
            return fail(name, args, started, ToolError.policyBlocked(policy.reason ?: "Blocked by policy"), blocked = true, stage = ToolStage.POLICY_BLOCKED)
        }

        // Restricted app / foreground protection for mutating/perception-sensitive tools
        val fgGuard = ForegroundGuard.evaluate(foreground, name, definition)
        if (!fgGuard.allowed) {
            return fail(name, args, started, ToolError.policyBlocked(fgGuard.reason ?: "Blocked by foreground guard"), blocked = true, stage = ToolStage.FOREGROUND_BLOCKED)
        }
        if (definition.mutating) {
            if (foreground != null && (restrictedApps.isBlocked(foreground) || SensitivePolicy.isSensitivePackage(foreground))) {
                return fail(name, args, started, ToolError.blocked(foreground), blocked = true, stage = ToolStage.FOREGROUND_BLOCKED)
            }
            if (definition.risky && approvalGate != null) {
                emit { onToolPhase?.invoke(name, "approval_requested", mapOf("stage" to ToolStage.APPROVAL_PENDING.wire)) }
                val approved = approvalGate.approve(
                    ApprovalRequest(
                        tool = name,
                        description = definition.description,
                        args = args,
                        packageName = foreground,
                    ),
                )
                if (!approved) {
                    return fail(
                        name,
                        args,
                        started,
                        ToolError(ToolError.APPROVAL_DENIED, "The user declined this action."),
                        blocked = true,
                        stage = ToolStage.APPROVAL_DENIED,
                    )
                }
            }
        }

        // OTP sighting guard: prevent copying/sending/typing likely one-time codes onward.
        if (OtpSighting.blocksTool(name)) {
            val text = args.toString()
            val sight = OtpSighting.inspect(text)
            if (sight.detected) {
                return fail(
                    name,
                    args,
                    started,
                    ToolError.policyBlocked(sight.reason ?: "Possible OTP/verification code blocked"),
                    blocked = true,
                    stage = ToolStage.OTP_BLOCKED,
                )
            }
        }

        // Action guard (grounding/staleness/basic misuse)
        val guard = actionGuard.evaluate(name, args)
        if (!guard.allowed) {
            return fail(name, args, started, ToolError.actionGuard(guard.reason ?: "Action blocked by guard"), blocked = true, stage = ToolStage.GUARD_BLOCKED)
        }

        emit { onToolPhase?.invoke(name, "executing", mapOf("stage" to ToolStage.EXECUTING.wire)) }

        var result = runCatching { bridge.execute(name, args) }
            .getOrElse { throwable ->
                ToolResult.fail(ToolError.internal(throwable.message ?: "Tool crashed"))
            }
        result = result.copy(stage = if (result.ok) ToolStage.DONE else ToolStage.FAILED)

        if (definition.mutating && result.ok) {
            hooks?.invalidatePerceptionOnMutation(name)
            runCatching { hooks?.settleAfterMutation(name, args, result) }
        }

        val durationMs = System.currentTimeMillis() - started
        audit?.record(
            clientId = principal.clientId,
            tool = name,
            args = args,
            ok = result.ok,
            durationMs = durationMs,
            approvedBy = if (definition.risky) "user" else null,
        )
        emit {
            onToolPhase?.invoke(
                name,
                "end",
                mapOf("ok" to result.ok, "changed" to result.changed, "duration_ms" to durationMs),
            )
        }
        emit {
            onTrail?.invoke(
                "tool_result",
                mapOf(
                    "tool" to name,
                    "ok" to result.ok,
                    "changed" to result.changed,
                    "duration_ms" to durationMs,
                    "code" to result.error?.code,
                ),
            )
        }
        emit { onSession?.invoke("tool_result", mapOf("tool" to name, "ok" to result.ok)) }
        emit { onPostAction?.invoke(name, args, result) }
        if (result.ok && result.changed) {
            emit { onLearnings?.invoke(name, args, result) }
        }
        return result
    }

    private fun fail(
        name: String,
        args: JsonObject,
        started: Long,
        error: ToolError,
        blocked: Boolean = false,
        stage: ToolStage = ToolStage.FAILED,
    ): ToolResult {
        audit?.record(principal.clientId, name, args, false, System.currentTimeMillis() - started, blocked = blocked)
        emit {
            onToolPhase?.invoke(
                name,
                if (blocked) "blocked" else "failed",
                mapOf("code" to error.code, "message" to error.message, "stage" to stage.wire),
            )
        }
        emit { onTrail?.invoke("tool_error", mapOf("tool" to name, "code" to error.code, "blocked" to blocked, "stage" to stage.wire)) }
        return ToolResult.fail(error, stage = stage)
    }

    private suspend fun executeBatch(args: JsonObject, started: Long): ToolResult {
        val steps = args["steps"] as? JsonArray
            ?: return ToolResult.fail(ToolError("invalid_args", "steps must be an array"), stage = ToolStage.FAILED)
        if (steps.size > maxBatchSteps) {
            return ToolResult.fail(ToolError("invalid_args", "Batch exceeds $maxBatchSteps steps"), stage = ToolStage.FAILED)
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
        ).let { if (allOk) it else it.copy(ok = false, stage = ToolStage.FAILED) }
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
        onSession = onSession,
        onToolPhase = onToolPhase,
        onTrail = onTrail,
        onPostAction = onPostAction,
        onLearnings = onLearnings,
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
        onSession = onSession,
        onToolPhase = onToolPhase,
        onTrail = onTrail,
        onPostAction = onPostAction,
        onLearnings = onLearnings,
    )
}
