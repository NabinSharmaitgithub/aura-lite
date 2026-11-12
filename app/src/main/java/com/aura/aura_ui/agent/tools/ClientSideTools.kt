package com.aura.aura_ui.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client-side/agent-local helper tools and capabilities:
 * - load_tools (deferred tool loading)
 * - ask_user (HITL bridge, app-side)
 * - ledger/memory/proof/skills/research helpers (runtime-scoped)
 *
 * These are exposed to the agent runtime as local helpers (not all are MCP server tools).
 */
object ClientSideTools {

    interface LocalTool {
        val name: String
        val description: String
        suspend fun execute(args: JsonObject): JsonObject
    }

    class LoadTools(private val loader: suspend (Set<String>) -> JsonObject) : LocalTool {
        override val name = "load_tools"
        override val description = "Load deferred tool groups/schemas into the current agent context."
        override suspend fun execute(args: JsonObject): JsonObject = loader(
            (args["tools"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }?.toSet() ?: emptySet()
        )
    }

    class AskAuraHandoff(private val submit: suspend (String, Boolean) -> JsonObject) : LocalTool {
        override val name = "ask_aura"
        override val description = "Conversation handoff: delegate a device action request to AURA agent."
        override suspend fun execute(args: JsonObject): JsonObject {
            val goal = (args["goal"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val voice = (args["voice"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            return submit(goal, voice)
        }
    }

    fun unsupported(name: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", "$name is not available in this runtime context")
    }
}
