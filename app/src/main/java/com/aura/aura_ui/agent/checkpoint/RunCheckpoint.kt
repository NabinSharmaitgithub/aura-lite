package com.aura.aura_ui.agent.checkpoint

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Resumable run checkpoint snapshot. Stored atomically by RunCheckpointStore so a paused or
 * interrupted run can be restored and re-verified before acting again.
 */
data class RunCheckpoint(
    val sessionId: String,
    val goal: String,
    val stepCount: Int,
    val outcomeHint: String?,
    val ledgerJson: String?,
    val screenGeneration: Long,
    val screenSignature: String?,
    val foregroundPackage: String?,
    val pendingQuestion: String?,
    val pendingApproval: String?,
    val checkpointReason: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("session_id", sessionId)
        put("goal", goal)
        put("step_count", stepCount)
        put("outcome_hint", outcomeHint ?: "")
        put("ledger_json", ledgerJson ?: "")
        put("screen_generation", screenGeneration)
        put("screen_signature", screenSignature ?: "")
        put("foreground_package", foregroundPackage ?: "")
        put("pending_question", pendingQuestion ?: "")
        put("pending_approval", pendingApproval ?: "")
        put("checkpoint_reason", checkpointReason)
        put("created_at", createdAt)
    }

    companion object {
        fun fromJson(o: JsonObject): RunCheckpoint? {
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val sessionId = str("session_id") ?: return null
            return RunCheckpoint(
                sessionId = sessionId,
                goal = str("goal").orEmpty(),
                stepCount = str("step_count")?.toIntOrNull() ?: 0,
                outcomeHint = str("outcome_hint")?.ifBlank { null },
                ledgerJson = str("ledger_json")?.ifBlank { null },
                screenGeneration = str("screen_generation")?.toLongOrNull() ?: -1L,
                screenSignature = str("screen_signature")?.ifBlank { null },
                foregroundPackage = str("foreground_package")?.ifBlank { null },
                pendingQuestion = str("pending_question")?.ifBlank { null },
                pendingApproval = str("pending_approval")?.ifBlank { null },
                checkpointReason = str("checkpoint_reason").orEmpty(),
                createdAt = str("created_at")?.toLongOrNull() ?: System.currentTimeMillis(),
            )
        }
    }
}
