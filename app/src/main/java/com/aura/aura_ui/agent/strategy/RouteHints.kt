package com.aura.aura_ui.agent.strategy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One structural action in a learned route. Only tool names and structural selectors
 * (resource id / class / role / index) are stored; typed text, credentials and screen content
 * are never captured.
 */
@Serializable
data class RouteAction(
    val tool: String,
    val target: String? = null,
)

/**
 * A privacy-aware learned route: "for this kind of goal in this package, these structural
 * actions worked". It contains no user text, message bodies, phone numbers or secrets.
 */
@Serializable
data class RouteHint(
    val id: String,
    val packageName: String,
    val goalKey: String,
    val actions: List<RouteAction>,
    val successCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    fun summary(): String = buildString {
        append(packageName.ifBlank { "any app" })
        append(" · ")
        append(goalKey.ifBlank { "generic" })
        append(" · ")
        append(actions.joinToString(" → ") { a -> a.target?.let { "${a.tool}($it)" } ?: a.tool })
        append(" (used ${successCount}×)")
    }
}

/**
 * Structure-focused, privacy-aware learned route hints.
 *
 * Goals are reduced to a small vocabulary of action verbs before storage, so a private goal
 * ("message Sarah that I'll be late") is stored only as "message". Routes are matched on the
 * foreground package plus that verb key.
 */
@Singleton
class RouteHintsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "agent/route_hints.json").also { it.parentFile?.mkdirs() }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false }
    private val lock = Any()

    fun record(packageName: String?, goal: String, actions: List<RouteAction>) {
        val clean = actions.filter { it.tool.isNotBlank() }.take(MAX_ACTIONS)
        if (clean.isEmpty()) return
        val key = goalKey(goal)
        val pkg = packageName.orEmpty()
        synchronized(lock) {
            val current = load().toMutableList()
            val existing = current.firstOrNull { it.packageName == pkg && it.goalKey == key }
            if (existing != null) {
                current.remove(existing)
                current += existing.copy(
                    actions = clean,
                    successCount = existing.successCount + 1,
                    lastUsedAt = System.currentTimeMillis(),
                )
            } else {
                current += RouteHint(
                    id = "rh-${System.currentTimeMillis()}-${current.size}",
                    packageName = pkg,
                    goalKey = key,
                    actions = clean,
                )
            }
            save(current.sortedByDescending { it.lastUsedAt }.take(MAX_HINTS))
        }
    }

    fun hintsFor(goal: String, packageName: String?, limit: Int = 2): List<RouteHint> {
        val key = goalKey(goal)
        val pkg = packageName.orEmpty()
        val all = load()
        return all.asSequence()
            .filter { it.goalKey == key || (key == GENERIC && it.packageName == pkg) }
            .sortedWith(
                compareByDescending<RouteHint> { it.packageName == pkg }
                    .thenByDescending { it.successCount }
                    .thenByDescending { it.lastUsedAt },
            )
            .take(limit)
            .toList()
    }

    /** Prompt block for matching routes, or empty string when there are none. */
    fun promptBlock(goal: String, packageName: String?): String {
        val hints = hintsFor(goal, packageName)
        if (hints.isEmpty()) return ""
        return buildString {
            appendLine("Routes that worked before (structure only, re-verify the screen first):")
            hints.forEach { appendLine("- ${it.summary()}") }
            appendLine("Treat a hint as a candidate, not a guaranteed layout; confirm every step.")
        }.trim()
    }

    fun clear() {
        synchronized(lock) { runCatching { file.delete() } }
    }

    private fun load(): List<RouteHint> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<RouteHint>>(file.readText())
    }.getOrDefault(emptyList())

    private fun save(hints: List<RouteHint>) {
        runCatching { file.writeText(json.encodeToString(hints)) }
    }

    companion object {
        private const val MAX_HINTS = 80
        private const val MAX_ACTIONS = 16
        private const val GENERIC = "generic"

        /** Action verbs we are willing to persist. Everything else is dropped. */
        private val VOCAB = setOf(
            "open", "launch", "install", "uninstall", "search", "find", "play", "pause", "stop",
            "send", "message", "sms", "call", "dial", "email", "share", "download", "upload",
            "browse", "navigate", "follow", "subscribe", "like", "comment", "post", "login",
            "settings", "wifi", "bluetooth", "alarm", "timer", "reminder", "battery", "brightness",
            "volume", "notification", "notifications", "clipboard", "copy", "paste", "screenshot",
            "tap", "click", "scroll", "type", "read", "verify", "record", "camera", "photo",
        )

        /**
         * Reduce an arbitrary private goal to a structural key. Unknown words (names, message
         * bodies, numbers) are discarded by design.
         */
        fun goalKey(goal: String): String {
            val tokens = goal.lowercase()
                .split(Regex("[^a-z]+"))
                .filter { it.length >= 3 && it in VOCAB }
                .distinct()
                .sorted()
            return if (tokens.isEmpty()) GENERIC else tokens.joinToString("-")
        }

        /**
         * Structural selector for a tool call. Prefers resource-id/class/role, then index.
         * Never returns typed text or user content.
         */
        fun structuralTarget(args: kotlinx.serialization.json.JsonObject): String? {
            fun pick(vararg keys: String): String? {
                for (k in keys) {
                    val v = (args[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (!v.isNullOrBlank()) return v.take(60)
                }
                return null
            }
            return pick("resource_id", "resourceId", "view_id", "class", "role", "som_id", "index")
        }
    }
}
