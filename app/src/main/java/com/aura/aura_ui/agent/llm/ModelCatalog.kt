package com.aura.aura_ui.agent.llm

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class ModelCatalog @Inject constructor(private val http: OkHttpClient) {

    suspend fun fetch(endpoint: LlmEndpoint, key: String): Result<List<ModelInfo>> =
        Companion.fetch(endpoint, key, http)

    companion object {
        class ModelFetchError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

        fun reasoningCapableForWire(endpoint: LlmEndpoint, modelId: String): Boolean = true

        suspend fun fetch(endpoint: LlmEndpoint, key: String, client: OkHttpClient = OkHttpClient()): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(endpoint.modelsEndpoint)
                    .get()
                    .applyAuth(endpoint, key)
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw ModelFetchError("Model list failed: HTTP ")
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
                    vision = inferVision(id),
                    reasoning = inferReasoning(id),
                    cheap = inferCheap(id),
                )
            }
        }

        fun inferVision(id: String): Boolean {
            val m = id.lowercase()
            return listOf("vision", "omni", "vl", "flash", "gpt-4", "claude-3").any { m.contains(it) }
        }

        fun inferReasoning(id: String): Boolean {
            val m = id.lowercase()
            return listOf("o1", "o3", "r1", "reason", "thinking", "qwen-2.5-coder", "qwq").any { m.contains(it) }
        }

        fun inferCheap(id: String): Boolean {
            val m = id.lowercase()
            return listOf("mini", "flash", "haiku", "small", "lite", "8b", "7b", "3b").any { m.contains(it) }
        }
    }
}

internal fun Request.Builder.applyAuth(endpoint: LlmEndpoint, key: String): Request.Builder = apply {
    header("Content-Type", "application/json")
    header("Accept", "application/json")
    endpoint.profile.extraHeaders.forEach { (name, value) -> header(name, value) }
    when (endpoint.authScheme) {
        AuthScheme.BEARER -> if (key.isNotBlank()) header("Authorization", "Bearer ")
        AuthScheme.X_GOOG_API_KEY -> if (key.isNotBlank()) header("x-goog-api-key", key)
        AuthScheme.ANTHROPIC_X_API_KEY -> if (key.isNotBlank()) header("x-api-key", key)
        AuthScheme.NONE -> Unit
    }
}

fun LlmEndpoint.authHeaders(key: String): Map<String, String> = buildMap {
    putAll(profile.extraHeaders)
    when (authScheme) {
        AuthScheme.BEARER -> if (key.isNotBlank()) put("Authorization", "Bearer ")
        AuthScheme.X_GOOG_API_KEY -> if (key.isNotBlank()) put("x-goog-api-key", key)
        AuthScheme.ANTHROPIC_X_API_KEY -> if (key.isNotBlank()) put("x-api-key", key)
        AuthScheme.NONE -> Unit
    }
}
