package com.aura.mcp.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** MCP prompts (task templates + registered skill prompts). */
object McpPrompts {
    private val skillPrompts = LinkedHashMap<String, JsonObject>()

    fun registerSkillPrompt(name: String, description: String, promptText: String) {
        skillPrompts[name] = buildJsonObject {
            put("name", name)
            put("description", description)
            put("arguments", buildJsonArray { })
            put("_skill_text", promptText)
        }
    }

    fun clearSkillPrompts() = skillPrompts.clear()

    fun list(): JsonArray = buildJsonArray {
        McpRpc.promptsList().forEach { add(it) }
        skillPrompts.values.forEach { p ->
            add(buildJsonObject {
                p.forEach { (k, v) -> if (k != "_skill_text") put(k, v) }
            })
        }
    }

    fun get(name: String, arguments: JsonObject): JsonObject? {
        McpRpc.getPrompt(name, arguments)?.let { return it }
        val skill = skillPrompts[name] ?: return null
        val text = (skill["_skill_text"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        return buildJsonObject {
            put("description", (skill["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: name)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                })
            })
        }
    }
}
