package com.aura.mcp.bridge

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Uniform result envelope for every tool, in-process and over MCP.
 *
 * { ok, changed, observed, error, meta }
 */
data class ToolError(
    val code: String,
    val message: String,
    val fix: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("code", code)
        put("message", message)
        if (fix != null) put("fix", fix)
    }

    companion object {
        const val PERMISSION_DENIED = "permission_denied"
        const val NOT_FOUND = "not_found"
        const val AMBIGUOUS = "ambiguous"
        const val BLOCKED_APP = "blocked_app"
        const val TIMEOUT = "timeout"
        const val STALE_SCREEN = "stale_screen"
        const val UNSUPPORTED = "unsupported"
        const val INTERNAL = "internal"
        const val CANCELLED = "cancelled"
        const val APPROVAL_DENIED = "approval_denied"
        const val POLICY_BLOCKED = "policy_blocked"
        const val SCOPE_DENIED = "scope_denied"
        const val PAUSED_BY_USER = "paused_by_user"
        const val ACTION_GUARD = "action_guard"

        fun permissionDenied(message: String, fix: String? = null) =
            ToolError(PERMISSION_DENIED, message, fix)

        fun notFound(message: String) = ToolError(NOT_FOUND, message)
        fun blocked(pkg: String) = ToolError(BLOCKED_APP, "AURA is not allowed to act in $pkg", "settings/restricted_apps")
        fun timeout(message: String) = ToolError(TIMEOUT, message)
        fun unsupported(message: String) = ToolError(UNSUPPORTED, message)
        fun internal(message: String) = ToolError(INTERNAL, message)
        fun policyBlocked(message: String, fix: String? = null) = ToolError(POLICY_BLOCKED, message, fix)
        fun scopeDenied(message: String) = ToolError(SCOPE_DENIED, message)
        fun paused(message: String = "Paused by user") = ToolError(PAUSED_BY_USER, message)
        fun actionGuard(message: String, fix: String? = null) = ToolError(ACTION_GUARD, message, fix)
    }
}

data class ToolResult(
    val ok: Boolean,
    val changed: Boolean = false,
    val observed: JsonObject? = null,
    val error: ToolError? = null,
    val meta: JsonObject? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("ok", ok)
        put("changed", changed)
        put("observed", observed ?: JsonNull)
        put("error", error?.toJson() ?: JsonNull)
        put("meta", meta ?: JsonNull)
    }

    companion object {
        fun ok(
            changed: Boolean = false,
            observed: JsonObject? = null,
            meta: JsonObject? = null,
        ) = ToolResult(true, changed, observed, null, meta)

        fun fail(error: ToolError, observed: JsonObject? = null) =
            ToolResult(false, false, observed, error, null)

        fun permission(message: String, fix: String? = null) =
            fail(ToolError.permissionDenied(message, fix))
    }
}

/**
 * Every device capability the tool surface needs is reached through this bridge.
 * The app implements it; :mcp-server never links against accessibility or Compose.
 */
interface DeviceBridge {
    suspend fun execute(tool: String, args: JsonObject): ToolResult

    /** Human-readable foreground app package, used by restricted-app checks. */
    suspend fun foregroundPackage(): String?

    /** Optional listener fan-out for watch_device_events. */
    suspend fun startWatching(events: List<String>)
    suspend fun stopWatching()
}

data class ApprovalRequest(
    val tool: String,
    val description: String,
    val args: JsonObject,
    val packageName: String?,
)

/** Returns true when the user approved. Implemented by the app with a notification. */
fun interface ApprovalGate {
    suspend fun approve(request: ApprovalRequest): Boolean
}

/** Per-app restriction list consulted before every mutating dispatch. */
interface RestrictedAppsStore {
    fun isBlocked(packageName: String): Boolean
    fun blockedPackages(): Set<String>
}

/** Default: nothing blocked (used by tests and headless builds). */
object AllowAllRestrictedApps : RestrictedAppsStore {
    override fun isBlocked(packageName: String) = false
    override fun blockedPackages(): Set<String> = emptySet()
}

/** A tool definition is data, so the same object feeds MCP tools/list and the UI. */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val domain: ToolDomain,
    val mutating: Boolean = false,
    val risky: Boolean = false,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
    }
}

enum class ToolDomain(val label: String) {
    UI("Interface"),
    APPS("Apps"),
    DATA("Data"),
    SYSTEM("System"),
    MEDIA("Media"),
    NOTIFICATIONS("Notifications"),
    BROWSER("Browser"),
    PERCEPTION("Perception"),
    WEB("Web"),
    SESSION("Session"),
}

/** Marker for bridge capabilities that are not wired on this build. */
object UnavailableBridge : DeviceBridge {
    override suspend fun execute(tool: String, args: JsonObject): ToolResult =
        ToolResult.fail(ToolError.unsupported("$tool is not available on this build"))

    override suspend fun foregroundPackage(): String? = null
    override suspend fun startWatching(events: List<String>) = Unit
    override suspend fun stopWatching() = Unit
}
