package com.aura.aura_ui.agent.llm

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OpenAI chat-completions client used for every provider. Streaming is the only path: the
 * agent needs tokens and tool-call fragments as they arrive.
 */
@Singleton
class LlmClient @Inject constructor(private val http: OkHttpClient) {

    fun stream(
        endpoint: LlmEndpoint,
        key: String,
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec> = emptyList(),
        temperature: Float = 0.2f,
        maxTokens: Int? = null,
        toolChoice: String? = "auto",
        extraBody: JsonObject? = null,
    ): Flow<LlmStreamEvent> = callbackFlow {
        val bodyJson = buildJsonObject {
            put("model", model)
            put("messages", kotlinx.serialization.json.JsonArray(messages.map { it.toJson() }))
            put("temperature", temperature)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            if (maxTokens != null) put("max_tokens", maxTokens)
            if (tools.isNotEmpty()) {
                put("tools", tools.toOpenAiTools())
                if (toolChoice != null) put("tool_choice", toolChoice)
            }
            endpoint.profile.reasoningParam?.let { param ->
                if (param == "reasoning_effort") put(param, "medium")
            }
            if (extraBody != null) {
                extraBody.forEach { (k, v) -> put(k, v) }
            }
        }.toString()

        val request = Request.Builder()
            .url(endpoint.chatUrl)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .applyAuth(endpoint, key)
            .header("Accept", "text/event-stream")
            .build()

        val call = http.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    trySend(LlmStreamEvent.Failed(e.message ?: "Network error", retryable = true))
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val text = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                            trySend(
                                LlmStreamEvent.Failed(
                                    "HTTP ${resp.code}: ${text.take(400).ifBlank { resp.message }}",
                                    retryable = resp.code == 429 || resp.code >= 500,
                                ),
                            )
                            close()
                            return
                        }
                        val source = resp.body?.source()
                        if (source == null) {
                            trySend(LlmStreamEvent.Failed("Empty response body"))
                            close()
                            return
                        }
                        val content = StringBuilder()
                        val reasoning = StringBuilder()
                        val calls = linkedMapOf<Int, MutableToolCall>()
                        var usage = LlmUsage()
                        var finish: String? = null
                        var responseModel = model

                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty()) continue
                                if (data == "[DONE]") break
                                val chunk = runCatching {
                                    LlmJson.json.parseToJsonElement(data) as? JsonObject
                                }.getOrNull() ?: continue

                                chunk["error"]?.let { error ->
                                    val message = if (error is JsonObject) {
                                        error.string("message") ?: error.toString()
                                    } else {
                                        error.toString()
                                    }
                                    trySend(LlmStreamEvent.Failed(message, retryable = true))
                                    close()
                                    return
                                }

                                chunk.string("model")?.let { responseModel = it }
                                (chunk["usage"] as? JsonObject)?.let { u ->
                                    usage = LlmUsage(
                                        promptTokens = u.int("prompt_tokens") ?: 0,
                                        completionTokens = u.int("completion_tokens") ?: 0,
                                    )
                                }

                                val choice = chunk.array("choices")?.objects()?.firstOrNull() ?: continue
                                val delta = (choice["delta"] as? JsonObject) ?: (choice["message"] as? JsonObject)
                                if (delta != null) {
                                    delta.string("content")?.let {
                                        content.append(it)
                                        trySend(LlmStreamEvent.TextDelta(it))
                                    }
                                    (delta.string("reasoning_content") ?: delta.string("reasoning"))?.let {
                                        reasoning.append(it)
                                        trySend(LlmStreamEvent.ReasoningDelta(it))
                                    }
                                    delta.array("tool_calls")?.objects()?.forEach { tool ->
                                        val index = tool.int("index") ?: 0
                                        val current = calls.getOrPut(index) { MutableToolCall() }
                                        tool.string("id")?.let { current.id = it }
                                        val fn = tool["function"] as? JsonObject
                                        fn?.string("name")?.let {
                                            if (current.name.isEmpty()) {
                                                current.name = it
                                                trySend(LlmStreamEvent.ToolCallStarted(index, it))
                                            }
                                        }
                                        fn?.string("arguments")?.let { current.arguments.append(it) }
                                    }
                                }
                                choice.string("finish_reason")?.let { finish = it }
                            }

                            val parsedCalls = calls.entries.sortedBy { it.key }.mapNotNull { (_, c) ->
                                if (c.name.isBlank()) return@mapNotNull null
                                LlmToolCall(
                                    id = c.id.ifBlank { "call_${System.nanoTime()}" },
                                    name = c.name,
                                    arguments = LlmJson.parseToolArguments(c.arguments.toString()),
                                    rawArguments = c.arguments.toString(),
                                )
                            }
                            trySend(
                                LlmStreamEvent.Completed(
                                    LlmResponse(
                                        content = content.toString(),
                                        toolCalls = parsedCalls,
                                        usage = usage,
                                        model = responseModel,
                                        finishReason = finish,
                                    ),
                                ),
                            )
                            close()
                        } catch (t: Throwable) {
                            trySend(LlmStreamEvent.Failed(t.message ?: "Stream parse error"))
                            close()
                        }
                    }
                }
            },
        )
        awaitClose { call.cancel() }
    }

    suspend fun complete(
        endpoint: LlmEndpoint,
        key: String,
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec> = emptyList(),
        temperature: Float = 0.2f,
        maxTokens: Int? = null,
    ): Result<LlmResponse> {
        val terminal = stream(endpoint, key, model, messages, tools, temperature, maxTokens)
            .first { it is LlmStreamEvent.Completed || it is LlmStreamEvent.Failed }
        return when (terminal) {
            is LlmStreamEvent.Completed -> Result.success(terminal.response)
            is LlmStreamEvent.Failed -> Result.failure(IOException(terminal.message))
            else -> Result.failure(IOException("No response from provider"))
        }
    }

    private class MutableToolCall(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
