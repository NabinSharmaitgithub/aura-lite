package com.aura.aura_ui.agent.ledger

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class PlanStep(
    val id: String,
    val description: String,
    var status: String = "pending", // pending|in_progress|done|blocked|skipped
    var note: String? = null,
)

@Serializable
data class LedgerFact(
    val key: String,
    val value: String,
    val source: String? = null,
    val at: Long = System.currentTimeMillis(),
)

@Serializable
data class RunLedgerState(
    val runId: String,
    val goal: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val steps: MutableList<PlanStep> = mutableListOf(),
    val facts: MutableList<LedgerFact> = mutableListOf(),
    val deadEnds: MutableList<String> = mutableListOf(),
    var outcome: String? = null,
    var summary: String? = null,
)

/**
 * Per-run ledger: tracks plan/facts/dead-ends for prompt injection and resumability.
 * Persist snapshots at checkpoints (pause/end/failure) so runs can resume with re-verification.
 */
class RunLedger(
    val state: RunLedgerState,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false },
) {
    private val revision = AtomicLong(0)

    fun revision(): Long = revision.get()

    fun setPlan(descriptions: List<String>) {
        state.steps.clear()
        descriptions.forEachIndexed { i, d ->
            state.steps += PlanStep(id = "s${i + 1}", description = d)
        }
        touch()
    }

    fun updateStep(id: String, status: String, note: String? = null) {
        state.steps.firstOrNull { it.id == id }?.let {
            it.status = status
            if (note != null) it.note = note
            touch()
        }
    }

    fun addFact(key: String, value: String, source: String? = null) {
        state.facts.removeAll { it.key == key }
        state.facts += LedgerFact(key, value, source)
        touch()
    }

    fun addDeadEnd(reason: String) {
        if (reason.isNotBlank() && state.deadEnds.none { it == reason }) {
            state.deadEnds += reason.take(300)
            touch()
        }
    }

    fun finish(outcome: String, summary: String?) {
        state.outcome = outcome
        state.summary = summary
        touch()
    }

    fun toPromptBlock(maxFacts: Int = 12, maxDeadEnds: Int = 6): String = buildString {
        appendLine("RUN LEDGER")
        appendLine("Goal: ${state.goal}")
        if (state.steps.isNotEmpty()) {
            appendLine("Plan:")
            state.steps.forEach { s ->
                append("- [${s.status}] ${s.id}: ${s.description}")
                if (!s.note.isNullOrBlank()) append(" (${s.note})")
                appendLine()
            }
        }
        if (state.facts.isNotEmpty()) {
            appendLine("Facts:")
            state.facts.takeLast(maxFacts).forEach { f -> appendLine("- ${f.key}: ${f.value}") }
        }
        if (state.deadEnds.isNotEmpty()) {
            appendLine("Dead ends (do not repeat):")
            state.deadEnds.takeLast(maxDeadEnds).forEach { d -> appendLine("- $d") }
        }
        state.outcome?.let { appendLine("Outcome: $it") }
    }.trim()

    fun snapshotJson(): String {
        state.updatedAt = System.currentTimeMillis()
        return json.encodeToString(state)
    }

    private fun touch() {
        state.updatedAt = System.currentTimeMillis()
        revision.incrementAndGet()
    }

    companion object {
        fun create(runId: String, goal: String): RunLedger = RunLedger(RunLedgerState(runId = runId, goal = goal))

        fun fromJson(raw: String, json: Json = Json { ignoreUnknownKeys = true }): RunLedger? = runCatching {
            RunLedger(json.decodeFromString<RunLedgerState>(raw), json)
        }.getOrNull()
    }
}
