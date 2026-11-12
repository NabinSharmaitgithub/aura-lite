package com.aura.mcp.server

import com.aura.mcp.Toolset
import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.bridge.AllowAllRestrictedApps
import com.aura.mcp.bridge.ApprovalGate
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.RestrictedAppsStore
import com.aura.mcp.cache.CvMemo
import com.aura.mcp.cache.LastSeenScreen
import com.aura.mcp.cache.PerceptionCache
import com.aura.mcp.guard.ActionGuard
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolPipelineHooks
import com.aura.mcp.tools.ToolPrincipal

/** Cross-cutting sinks/hooks attached to a server instance. */
data class McpSinks(
    val audit: McpAuditLogger? = null,
    val session: ((String, Map<String, Any?>) -> Unit)? = null,
    val toolPhase: ((String, String, Map<String, Any?>) -> Unit)? = null,
    val trail: ((String, Map<String, Any?>) -> Unit)? = null,
    val postActionObserver: ((String, kotlinx.serialization.json.JsonObject, com.aura.mcp.bridge.ToolResult) -> Unit)? = null,
    val learnings: ((String, kotlinx.serialization.json.JsonObject, com.aura.mcp.bridge.ToolResult) -> Unit)? = null,
)

data class McpCaches(
    val perception: PerceptionCache = PerceptionCache(),
    val cvMemo: CvMemo = CvMemo(),
    val lastSeen: LastSeenScreen = LastSeenScreen(),
)

/**
 * Single composition root for MCP server capabilities (tools/resources/prompts/instructions)
 * and shared enforcement pipeline wiring.
 */
class McpServerBuilder(
    private val bridge: DeviceBridge,
) {
    private val enabledToolsets = linkedSetOf(Toolset.CORE, Toolset.ASSISTANT, Toolset.BROWSER)
    private var restrictedApps: RestrictedAppsStore = AllowAllRestrictedApps
    private var approvalGate: ApprovalGate? = null
    private var hooks: ToolPipelineHooks? = null
    private var sinks: McpSinks = McpSinks()
    private var caches: McpCaches = McpCaches()
    private var maxBatchSteps: Int = 12

    fun toolsets(vararg toolsets: Toolset) = apply {
        if (toolsets.isNotEmpty()) {
            enabledToolsets.clear()
            enabledToolsets.addAll(toolsets.toList())
        }
    }
    fun restrictedApps(store: RestrictedAppsStore) = apply { restrictedApps = store }
    fun approvalGate(gate: ApprovalGate?) = apply { approvalGate = gate }
    fun hooks(hooks: ToolPipelineHooks?) = apply { this.hooks = hooks }
    fun sinks(sinks: McpSinks) = apply { this.sinks = sinks }
    fun caches(caches: McpCaches) = apply { this.caches = caches }
    fun maxBatchSteps(value: Int) = apply { maxBatchSteps = value.coerceIn(1, 50) }

    fun registerSkillPrompt(name: String, description: String, promptText: String) = apply {
        McpPrompts.registerSkillPrompt(name, description, promptText)
    }

    data class BuiltServer(
        val registry: McpToolRegistry,
        val inProcess: InProcessMcpServer,
        val caches: McpCaches,
        val sinks: McpSinks,
        val toolsets: Set<Toolset>,
    ) {
        fun resources() = McpResources
        fun prompts() = McpPrompts
        fun instructions() = McpInstructions.Doctrine.text
    }

    fun build(clientId: String = "local"): BuiltServer {
        val principal = ToolPrincipal(
            clientId = clientId,
            allowedToolsets = enabledToolsets.map { it.name }.toSet(),
            allowedTools = null,
        )
        val registry = McpToolRegistry(
            bridge = bridge,
            restrictedApps = restrictedApps,
            approvalGate = approvalGate,
            audit = sinks.audit,
            maxBatchSteps = maxBatchSteps,
            clientId = clientId,
            perceptionCache = caches.perception,
            actionGuard = ActionGuard(caches.perception),
            hooks = hooks,
            principal = principal,
            onSession = sinks.session,
            onToolPhase = sinks.toolPhase,
            onTrail = sinks.trail,
            onPostAction = sinks.postActionObserver,
            onLearnings = sinks.learnings,
        )
        return BuiltServer(
            registry = registry,
            inProcess = InProcessMcpServer(registry),
            caches = caches,
            sinks = sinks,
            toolsets = enabledToolsets.toSet(),
        )
    }
}
