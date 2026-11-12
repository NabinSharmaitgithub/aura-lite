package com.aura.aura_ui.agent.conversation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Gemini Live / realtime conversation scaffold (feature-parity placeholder).
 *
 * Intended responsibilities (per AURA spec):
 * - Manage realtime audio/text session with provider (e.g. Gemini Live).
 * - Handle mic capture/streaming, playback, interruptions, and narration/task audio arbitration.
 * - Expose tool declarations and route device-action requests via guarded agent handoff (e.g. ask_aura),
 *   never bypassing MCP policy/safety gates.
 * - Coordinate with wake-word/listening modes and UI state.
 *
 * Full implementation requires provider session protocol, auth/key wiring, audio pipeline integration,
 * and UI/service lifecycle handling.
 */
interface LiveConversationSession {
    val isActive: Boolean
    fun events(): Flow<LiveEvent> = emptyFlow()
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
)

sealed interface LiveEvent {
    data class Status(val state: String, val detail: String? = null) : LiveEvent
    data class Transcript(val role: String, val text: String, val partial: Boolean = false) : LiveEvent
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LiveEvent
    data class Error(val message: String, val recoverable: Boolean = false) : LiveEvent
}

class NoopLiveConversationSession : LiveConversationSession {
    override val isActive: Boolean = false
    override suspend fun start(config: LiveConfig): Result<Unit> =
        Result.failure(UnsupportedOperationException("Gemini Live conversation is not configured in this build."))
    override suspend fun sendUserText(text: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not active"))
    override suspend fun sendToolResult(toolCallId: String, resultJson: String): Result<Unit> = Result.failure(UnsupportedOperationException("Not active"))
    override suspend fun stop() {}
}
