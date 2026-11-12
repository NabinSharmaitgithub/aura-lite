package com.aura.aura_ui.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Completion evidence: tracks observations/tool outcomes used to back a success claim.
 * The runtime must not report success for high-stakes goals without evidence.
 */
class CompletionEvidence {
    data class Item(
        val step: Int,
        val kind: String,
        val detail: String,
        val ok: Boolean,
        val screenSignature: String? = null,
        val at: Long = System.currentTimeMillis(),
    )

    private val items = ArrayDeque<Item>()

    fun record(step: Int, kind: String, detail: String, ok: Boolean, screenSignature: String? = null) {
        items.addLast(Item(step, kind, detail.take(500), ok, screenSignature))
        while (items.size > 64) items.removeFirst()
    }

    fun snapshot(): List<Item> = items.toList()

    fun hasAnySuccess(): Boolean = items.any { it.ok }

    fun hasSuccess(kind: String): Boolean = items.any { it.ok && it.kind == kind }

    /** Evidence that at least one mutating tool call succeeded (used for completion claims). */
    fun hasMutationSuccess(): Boolean = hasSuccess("mutation")

    /** A mutating action followed by an observed screen change. */
    fun hasVerifiedMutation(): Boolean = hasSuccess("verified_mutation")

    fun hasObservation(): Boolean = hasSuccess("observation")

    fun lastObservation(): Item? = items.lastOrNull { it.kind == "observation" && it.ok }

    fun toJson(): JsonObject = buildJsonObject {
        put("count", items.size)
        put("has_success", hasAnySuccess())
        put("last_observation", lastObservation()?.detail ?: "")
    }

    fun clear() = items.clear()
}
