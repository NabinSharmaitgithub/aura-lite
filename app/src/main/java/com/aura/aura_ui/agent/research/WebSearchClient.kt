package com.aura.aura_ui.agent.research

import com.aura.aura_ui.agent.llm.ProviderKeyStore
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Tavily web search. The only non-LLM endpoint the app can reach, and only with a key the user
 * supplied. Without a key the tool fails fast with a fix path.
 */
@Singleton
class WebSearchClient @Inject constructor(
    private val http: OkHttpClient,
    private val keys: ProviderKeyStore,
) {
    suspend fun search(query: String, maxResults: Int): ToolResult = withContext(Dispatchers.IO) {
        val key = keys.tavilyKey
            ?: return@withContext ToolResult.permission(
                "No Tavily search key configured",
                "settings/tavily",
            )
        val body = buildJsonObject {
            put("api_key", key)
            put("query", query)
            put("max_results", maxResults)
            put("include_answer", true)
            put("search_depth", "basic")
        }.toString()
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ToolResult.fail(
                        ToolError(
                            if (response.code == 401 || response.code == 403) "permission_denied" else "internal",
                            "Tavily HTTP ${response.code}: ${raw.take(200)}",
                            if (response.code == 401) "settings/tavily" else null,
                        ),
                    )
                }
                val root = Json.parseToJsonElement(raw).jsonObject
                val results = (root["results"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                ToolResult.ok(
                    observed = buildJsonObject {
                        put("answer", root["answer"]?.jsonPrimitive?.content ?: "")
                        put(
                            "results",
                            buildJsonArray {
                                results.forEach { result ->
                                    add(buildJsonObject {
                                        put("title", result["title"]?.jsonPrimitive?.content.orEmpty())
                                        put("url", result["url"]?.jsonPrimitive?.content.orEmpty())
                                        put("content", result["content"]?.jsonPrimitive?.content.orEmpty().take(600))
                                    })
                                }
                            },
                        )
                        put("count", results.size)
                    },
                )
            }
        }.getOrElse { error ->
            ToolResult.fail(ToolError.internal(error.message ?: "Search failed"))
        }
    }
}
