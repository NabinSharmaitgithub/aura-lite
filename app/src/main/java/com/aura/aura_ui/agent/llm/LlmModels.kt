package com.aura.aura_ui.agent.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class AuthScheme { BEARER, X_GOOG_API_KEY, ANTHROPIC_X_API_KEY, NONE }

enum class ModelListStyle { OPENAI_DATA, GEMINI_MODELS }

enum class VisionInference { NEVER, ALWAYS, BY_MODEL_ID, BY_PROVIDER }

enum class ReasoningInference { NONE, BY_MODEL_ID, BY_PROVIDER }

data class ProviderProfile(
    val reasoningParam: String? = null,
    val visionContentShape: String = "openai",
    val mergeSystemIntoFirstUser: Boolean = false,
    val toolChoiceAuto: Boolean = true,
    val repairToolArguments: Boolean = true,
    val extraHeaders: Map<String, String> = emptyMap(),
)

data class LlmEndpoint(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val chatCompletionsPath: String,
    val modelsUrl: String,
    val authScheme: AuthScheme,
    val modelListStyle: ModelListStyle,
    val visionInference: VisionInference,
    val reasoningInference: ReasoningInference,
    val profile: ProviderProfile,
    val isBuiltIn: Boolean,
    val keyHint: String,
    val keyUrl: String?,
    val defaultModelId: String?,
) {
    val chatUrl: String get() = baseUrl.trimEnd('/') + chatCompletionsPath
    val modelsEndpoint: String get() = baseUrl.trimEnd('/') + modelsUrl
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int?,
    val vision: Boolean,
    val reasoning: Boolean,
    val cheap: Boolean,
) {
    val badges: List<String>
        get() = buildList {
            if (vision) add("vision")
            if (reasoning) add("reasoning")
            if (cheap) add("cheap")
        }
}

data class LlmToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

sealed interface LlmMessageContent {
    data class Text(val text: String) : LlmMessageContent
    data class Image(val base64Png: String) : LlmMessageContent
}

data class LlmMessage(
    val role: String,
    val content: List<LlmMessageContent> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
) {
    val text: String get() = content.filterIsInstance<LlmMessageContent.Text>().joinToString("\n") { it.text }

    fun toJson(): JsonObject = buildJsonObject {
        put("role", role)
        if (toolCallId != null) put("tool_call_id", toolCallId)
        if (toolName != null) put("name", toolName)
        if (toolCalls.isNotEmpty()) {
            put(
                "tool_calls",
                buildJsonArray {
                    toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.arguments.toString())
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        val images = content.filterIsInstance<LlmMessageContent.Image>()
        val text = content.filterIsInstance<LlmMessageContent.Text>().joinToString("\n") { it.text }
        if (images.isEmpty()) {
            put("content", text)
        } else {
            put(
                "content",
                buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                    images.forEach { image ->
                        add(
                            buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject {
                                        put("url", "data:image/png;base64,${image.base64Png}")
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    companion object {
        fun system(text: String) = LlmMessage("system", listOf(LlmMessageContent.Text(text)))
        fun user(text: String) = LlmMessage("user", listOf(LlmMessageContent.Text(text)))
        fun assistant(text: String) = LlmMessage("assistant", listOf(LlmMessageContent.Text(text)))
        fun tool(callId: String, name: String, result: String) =
            LlmMessage("tool", listOf(LlmMessageContent.Text(result)), toolCallId = callId, toolName = name)
    }
}

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
    val rawArguments: String = arguments.toString(),
)

data class LlmUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

data class LlmResponse(
    val content: String,
    val toolCalls: List<LlmToolCall>,
    val usage: LlmUsage,
    val model: String = "",
    val finishReason: String? = null,
)

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class ReasoningDelta(val text: String) : LlmStreamEvent
    data class ToolCallStarted(val index: Int, val name: String) : LlmStreamEvent
    data class Completed(val response: LlmResponse) : LlmStreamEvent
    data class Failed(val message: String, val retryable: Boolean = false) : LlmStreamEvent
}

object LlmJson {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Repairs the most common small-model tool argument mistakes. */
    fun parseToolArguments(raw: String): JsonObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        runCatching { return json.parseToJsonElement(trimmed) as JsonObject }
        val repaired = trimmed
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .replace('\'', '"')
            .let { if (it.startsWith("{")) it else trimmed }
        return runCatching { json.parseToJsonElement(repaired) as JsonObject }
            .getOrElse { buildJsonObject { put("_raw", JsonPrimitive(trimmed)) } }
    }
}

fun List<LlmToolSpec>.toOpenAiTools(): JsonArray = buildJsonArray {
    forEach { spec ->
        add(
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", spec.name)
                        put("description", spec.description)
                        put("parameters", spec.inputSchema)
                    },
                )
            },
        )
    }
}
