package com.aura.mcp.guard

import com.aura.mcp.cache.PerceptionCache
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lightweight pre-dispatch guard for wasted turns and obvious misuse.
 * Extend with loop detection, completion-evidence checks, and resource requirements as needed.
 */
class ActionGuard(
    private val perceptionCache: PerceptionCache? = null,
) {
    data class Decision(val allowed: Boolean, val code: String = "action_guard", val reason: String? = null) {
        companion object {
            val Allow = Decision(true)
            fun deny(reason: String) = Decision(false, "action_guard", reason)
        }
    }

    private val targetTools = setOf("tap", "swipe", "scroll", "set_value", "type_text", "verify_action")

    fun evaluate(tool: String, args: JsonObject): Decision {
        if (tool !in targetTools || perceptionCache == null) return Decision.Allow
        val somId = args["som_id"]?.jsonPrimitive?.intOrNull
            ?: args["index"]?.jsonPrimitive?.intOrNull
        if (somId != null && perceptionCache.get(somId) == null && perceptionCache.size() > 0) {
            // Only refuse when cache is populated but handle missing (likely stale).
            return Decision.deny("Target som_id/index $somId is not in the current perception cache. Call read_screen/perceive_screen again.")
        }
        return Decision.Allow
    }
}
