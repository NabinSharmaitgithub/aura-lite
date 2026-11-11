package com.aura.aura_ui.agent.llm

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the live model catalogue. Two list shapes are supported: OpenAI `data[]` and
 * Gemini `models[]`. Context window falls back to `max_input_tokens` when needed.
 */
@Singleton
class ModelCatalog @Inject constructor(private val http: OkHttpClient) {

    suspend fun fetch(endpoint: LlmEndpoint, key: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint.modelsEndpoint)
                .get()
                .applyAuth(endpoint, key)
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Model list failed: HTTP ${response.code}")
                }
                parse(endpoint, body)
            }
        }
    }

    fun parse(endpoint: LlmEndpoint, body: String): List<ModelInfo> {
        val root = LlmJson.json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return when (endpoint.modelListStyle) {
            ModelListStyle.OPENAI_DATA -> parseOpenAi(root)
            ModelListStyle.GEMINI_MODELS -> parseGemini(root)
        }.distinctBy { it.id }.sortedBy { it.id }
    }

    private fun parseOpenAi(root: JsonObject): List<ModelInfo> {
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val context = listOf("context_length", "context_window", "max_context_length", "max_input_tokens")
                .firstNotNullOfOrNull { key -> o[key]?.jsonPrimitive?.content?.toIntOrNull() }
            val name = o["name"]?.jsonPrimitive?.content ?: id
            ModelInfo(
                id = id,
                displayName = name,
                contextWindow = context,
                vision = inferVision(id),
                reasoning = inferReasoning(id),
                cheap = inferCheap(id),
            )
        }
    }

    private fun parseGemini(root: JsonObject): List<ModelInfo> {
        val models = root["models"] as? JsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val raw = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val id = raw.removePrefix("models/")
            ModelInfo(
                id = id,
                displayName = o["displayName"]?.jsonPrimitive?.content ?: id,
                contextWindow = o["inputTokenLimit"]?.jsonPrimitive?.content?.toIntOrNull(),
                vision = inferVision(id) || id.startsWith("gemini"),
                reasoning = inferReasoning(id),
                cheap = id.contains("flash"),
            )
        }
    }

    fun inferVision(id: String): Boolean {
        val m = id.lowercase()
        return listOf("vision", "4o", "llava", "pixtral", "qwen-vl", "gemini", "claude-3", "claude-4", "minicpm", "internvl", "llama-3.2-11b-vision", "llama-3.2-90b-vision")
            .any { m.contains(it) }
    }

    fun inferReasoning(id: String): Boolean {
        val m = id.lowercase()
        return listOf("reason", "r1", "thinking", "qwq", "o1", "o3", "o4", "glm-4.5", "magistral")
            .any { m.contains(it) }
    }

    fun inferCheap(id: String): Boolean {
        val m = id.lowercase()
        return listOf("mini", "flash", "haiku", "small", "lite", "8b", "7b", "3b").any { m.contains(it) }
    }
}

internal fun Request.Builder.applyAuth(endpoint: LlmEndpoint, key: String): Request.Builder = apply {
    header("Content-Type", "application/json")
    header("Accept", "application/json")
    endpoint.profile.extraHeaders.forEach { (name, value) -> header(name, value) }
    when (endpoint.authScheme) {
        AuthScheme.BEARER -> if (key.isNotBlank()) header("Authorization", "Bearer $key")
        AuthScheme.X_GOOG_API_KEY -> if (key.isNotBlank()) header("x-goog-api-key", key)
        AuthScheme.ANTHROPIC_X_API_KEY -> if (key.isNotBlank()) header("x-api-key", key)
        AuthScheme.NONE -> Unit
    }
}

fun LlmEndpoint.authHeaders(key: String): Map<String, String> = buildMap {
    putAll(profile.extraHeaders)
    when (authScheme) {
        AuthScheme.BEARER -> if (key.isNotBlank()) put("Authorization", "Bearer $key")
        AuthScheme.X_GOOG_API_KEY -> if (key.isNotBlank()) put("x-goog-api-key", key)
        AuthScheme.ANTHROPIC_X_API_KEY -> if (key.isNotBlank()) put("x-api-key", key)
        AuthScheme.NONE -> Unit
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
