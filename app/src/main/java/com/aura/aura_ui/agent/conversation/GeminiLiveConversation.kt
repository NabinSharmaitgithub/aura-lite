package com.aura.aura_ui.agent.conversation

import com.aura.aura_ui.state.LiveSessionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live conversation session.
 *
 * Provides a realtime WebSocket session shell for Gemini Live (BidiGenerateContent).
 * Audio capture/playback and full tool orchestration are integrated by the host UI/service;
 * this class handles connection lifecycle, setup, text turns, and tool responses.
 */
interface LiveConversationSession {
    val isActive: Boolean
    fun events(): Flow<LiveEvent> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun start(config: LiveConfig): Result<Unit>
    suspend fun sendUserText(text: String): Result<Unit>
    suspend fun sendToolResult(toolCallId: String, resultJson: String): Result<Unit>
    suspend fun stop()
}

data class LiveConfig(
    val provider: String = "gemini",
    val model: String? = null,
    val apiKey: String? = null,
    val systemInstruction: String? = null,
    val enableAudioInput: Boolean = true,
    val enableAudioOutput: Boolean = true,
    /** Override websocket endpoint for compatible gateways/proxies. */
    val endpoint: String? = null,
)

sealed interface LiveEvent {
    data class Status(val state: String, val detail: String? = null) : LiveEvent
    data class Transcript(val role: String, val text: String, val partial: Boolean = false) : LiveEvent
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LiveEvent
    data class Error(val message: String, val recoverable: Boolean = false) : LiveEvent
}

class GeminiLiveConversationSession(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) : LiveConversationSession {

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var liveState: LiveSessionState = LiveSessionState.CLOSED
    @Volatile private var socket: WebSocket? = null
    @Volatile private var currentConfig: LiveConfig? = null

    /** Explicit session lifecycle; replaced the old `active` boolean. */
    val state: LiveSessionState get() = liveState
    override val isActive: Boolean get() = liveState.canSend
    override fun events(): Flow<LiveEvent> = _events.asSharedFlow()

    override suspend fun start(config: LiveConfig): Result<Unit> = runCatching {
        if (config.provider.lowercase() != "gemini") {
            throw UnsupportedOperationException("Only Gemini Live is supported in this build")
        }
        val key = config.apiKey?.trim().orEmpty()
        if (key.isBlank()) throw IllegalStateException("Gemini API key is required for Live mode")
        stop()
        currentConfig = config
        liveState = LiveSessionState.CONNECTING
        val model = config.model?.ifBlank { null } ?: "models/gemini-2.0-flash-exp"
        val modelName = if (model.startsWith("models/")) model else "models/$model"
        val url = config.endpoint?.ifBlank { null }
            ?: "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        val request = Request.Builder().url(url).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                liveState = LiveSessionState.CONNECTED
                _events.tryEmit(LiveEvent.Status("connected", modelName))
                val setup = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", modelName)
                        if (!config.systemInstruction.isNullOrBlank()) {
                            put("systemInstruction", JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().put("text", config.systemInstruction)))
                            })
                        }
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().apply {
                                put(if (config.enableAudioOutput) "AUDIO" else "TEXT")
                            })
                        })
                    })
                }
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                liveState = LiveSessionState.CLOSING
                _events.tryEmit(LiveEvent.Status("closing", "$code $reason"))
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                liveState = LiveSessionState.CLOSED
                socket = null
                _events.tryEmit(LiveEvent.Status("closed", "$code $reason"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                liveState = LiveSessionState.FAILED
                socket = null
                _events.tryEmit(LiveEvent.Error(t.message ?: "Gemini Live connection failed", recoverable = true))
                _events.tryEmit(LiveEvent.Status("failed"))
            }
        })
        socket = ws
    }

    private fun handleServerMessage(text: String) {
        runCatching {
            val root = JSONObject(text)
            if (root.has("setupComplete")) {
                liveState = LiveSessionState.READY
                _events.tryEmit(LiveEvent.Status("ready"))
            }
            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                val calls = toolCall.optJSONArray("functionCalls") ?: JSONArray()
                for (i in 0 until calls.length()) {
                    val c = calls.optJSONObject(i) ?: continue
                    val id = c.optString("id", "call_$i")
                    val name = c.optString("name", "")
                    val args = c.optJSONObject("args")?.toString() ?: c.optString("args", "{}")
                    if (name.isNotBlank()) _events.tryEmit(LiveEvent.ToolCall(id, name, args))
                }
            }
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val mt = serverContent.optJSONObject("modelTurn")
                val parts = mt?.optJSONArray("parts") ?: JSONArray()
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    val txt = p.optString("text", "")
                    if (txt.isNotBlank()) {
                        _events.tryEmit(LiveEvent.Transcript("model", txt, serverContent.optBoolean("turnComplete", false).not()))
                    }
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                    if (liveState.canSend) liveState = LiveSessionState.TURN_COMPLETE
                    _events.tryEmit(LiveEvent.Status("turn_complete"))
                }
            }
        }.onFailure {
            // Some frames may be binary/audio-only; ignore parse errors here.
        }
    }

    override suspend fun sendUserText(text: String): Result<Unit> = runCatching {
        if (!liveState.canSend) throw IllegalStateException("Live session is not ready ($liveState)")
        val ws = socket ?: throw IllegalStateException("Live session not connected")
        val msg = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }))
                put("turnComplete", true)
            })
        }
        if (!ws.send(msg.toString())) throw IllegalStateException("Failed to send message")
    }

    override suspend fun sendToolResult(toolCallId: String, resultJson: String): Result<Unit> = runCatching {
        if (!liveState.canSend) throw IllegalStateException("Live session is not ready ($liveState)")
        val ws = socket ?: throw IllegalStateException("Live session not connected")
        val responseObj = runCatching { JSONObject(resultJson) }.getOrElse { JSONObject().put("result", resultJson) }
        val msg = JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", JSONArray().put(JSONObject().apply {
                    put("id", toolCallId)
                    put("name", "")
                    put("response", responseObj)
                }))
            })
        }
        if (!ws.send(msg.toString())) throw IllegalStateException("Failed to send tool response")
    }

    override suspend fun stop() {
        if (liveState != LiveSessionState.CLOSED) liveState = LiveSessionState.CLOSING
        runCatching { socket?.close(1000, "client stop") }
        socket = null
        currentConfig = null
        liveState = LiveSessionState.CLOSED
        _events.tryEmit(LiveEvent.Status("stopped"))
    }
}

class NoopLiveConversationSession : LiveConversationSession {
    override val isActive: Boolean = false
    override suspend fun start(config: LiveConfig): Result<Unit> =
        Result.failure(UnsupportedOperationException("Gemini Live conversation is not configured in this build."))
    override suspend fun sendUserText(text: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not active"))
    override suspend fun sendToolResult(toolCallId: String, resultJson: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not active"))
    override suspend fun stop() {}
}

