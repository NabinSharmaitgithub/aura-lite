package com.aura.mcp.server

import com.aura.mcp.bridge.ToolDomain
import com.aura.mcp.tools.ToolCatalog
import com.aura.mcp.tools.ToolPrincipal

object ToolAccess {
    private val core = setOf(
        "read_screen", "perceive_screen", "get_screenshot", "request_screen_capture_permission",
        "tap", "swipe", "scroll", "set_value", "type_text", "press_key", "press_enter",
        "wait_for", "verify_action", "run_steps", "launch_app", "lookup_app",
        "open_deeplink", "resolve_deeplink", "list_app_deeplinks", "get_device_status",
        "end_session", "ask_user",
    )
    private val assistant = core + ToolCatalog.all.filter {
        it.domain in setOf(ToolDomain.NOTIFICATIONS, ToolDomain.MEDIA, ToolDomain.DATA, ToolDomain.SYSTEM, ToolDomain.WEB)
    }.map { it.name }.toSet()
    private val browser = ToolCatalog.all.filter { it.domain == ToolDomain.BROWSER }.map { it.name }.toSet()

    fun principalFor(clientId: String, scopes: List<String>): ToolPrincipal {
        if (scopes.isEmpty() || scopes.contains("*") || scopes.contains("ALL")) {
            return ToolPrincipal(clientId = clientId)
        }
        val allowed = linkedSetOf<String>()
        scopes.forEach { scope ->
            when (scope.uppercase()) {
                "CORE" -> allowed += core
                "ASSISTANT" -> allowed += assistant
                "BROWSER" -> allowed += browser
                else -> if (ToolCatalog.byName.containsKey(scope)) allowed += scope
            }
        }
        return ToolPrincipal(
            clientId = clientId,
            allowedToolsets = scopes.toSet(),
            allowedTools = if (allowed.isEmpty()) null else allowed,
        )
    }
}
