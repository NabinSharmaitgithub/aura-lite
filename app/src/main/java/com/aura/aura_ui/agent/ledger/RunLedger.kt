package com.aura.aura_ui.agent.ledger

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
enum class PlanStepStatus(val wire: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    DONE("done"),
    BLOCKED("blocked"),
    SKIPPED("skipped"),
    FAILED("failed"),
    ;

    val isSettled: Boolean get() = this == DONE || this == BLOCKED || this == SKIPPED || this == FAILED

    companion object {
        fun fromWire(value: String?): PlanStepStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: PENDING
    }
}

@Serializable
data class PlanStep(
    val text: String = "",
    val status: String = "pending",
    val evidence: String? = null,
    val failReason: String? = null,
    val attempts: Int = 0,
    val id: String = "",
    val description: String = text,
    val note: String? = null,
) {
    val settled: Boolean get() = typedStatus == PlanStepStatus.DONE || typedStatus == PlanStepStatus.FAILED || typedStatus == PlanStepStatus.SKIPPED
    val typedStatus: PlanStepStatus get() = PlanStepStatus.fromWire(status)

    constructor(id: String, description: String, status: PlanStepStatus, note: String? = null) :
        this(text = description, status = status.wire, id = id, description = description, note = note)
}

@Serializable
data class StepRecord(
    val tool: String,
    val label: String? = null,
    val ok: Boolean = true,
    val screenChanged: Boolean? = null,
)

@Serializable
data class Finding(
    val item: String = "",
    val quote: String = "",
    val afterReads: Int = 0,
    val key: String = item,
    val value: String = quote,
)

@Serializable
data class ResearchNote(
    val source: String,
    val text: String,
)

@Serializable
data class RunOutcome(
    val claimed: String? = null,
    val verdict: String = UNVERIFIED,
    val why: String? = null,
    val enforced: Boolean = false,
    val proven: Int = 0,
    val target: Int? = null,
) {
    companion object {
        const val SUCCESS = "success"
        const val PARTIAL = "partial"
        const val FAIL = "fail"
        const val UNVERIFIED = "unverified"
    }
}

@Serializable
data class LedgerFact(
    val key: String,
    val value: String,
    val source: String? = null,
    val at: Long = System.currentTimeMillis(),
)

object RunLedgerCaps {
    const val MAX_PLAN_STEPS = 12
    const val MAX_STEP_ATTEMPTS = 3
    const val CHECKPOINT_EVERY = 5
    const val MAX_FACTS = 12
    const val MAX_DEAD_ENDS = 6
    const val MAX_RECENT_STEPS = 5
    const val MAX_FINDINGS = 10
    const val MAX_DIRECTIVE_CHARS = 300
    const val MAX_FINDING_CHARS = 200
    const val MAX_QUOTE_CHARS = 200
    const val MAX_DELIVERABLE_CHARS = 200
    const val MAX_FAIL_REASON_CHARS = 200
    const val MAX_FACT_CHARS = 200
    const val MAX_EVIDENCE_CHARS = 200
    const val MAX_TARGET_COUNT = 50
}

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
 */
@Serializable
data class RunLedger(
    val runId: String = "",
    val goal: String = "",
    val provider: String = "",
    val modelId: String = "",
    val startedAtMs: Long = System.currentTimeMillis(),
    val planVersion: Int = 0,
    val planSteps: MutableList<PlanStep> = mutableListOf(),
    val settledSteps: Int = 0,
    val progressAtCall: Int = 0,
    val toolCalls: Int = 0,
    val stalledCalls: Int? = null,
    val surprise: String? = null,
    val surpriseAtCall: Int = 0,
    val facts: MutableList<String> = mutableListOf(),
    val deadEnds: MutableList<String> = mutableListOf(),
    val recentSteps: List<StepRecord> = emptyList(),
    val totalSteps: Int = 0,
    val failedSteps: Int = 0,
    val directive: String? = null,
    val directiveAtMs: Long? = null,
    val deliverable: String? = null,
    val targetCount: Int? = null,
    val findings: List<Finding> = emptyList(),
    val outcome: RunOutcome? = null,
    val research: ResearchNote? = null,
    val endedAtMs: Long? = null,
    val endReason: String? = null,
    val resumedFrom: String? = null,
) {
    val state: RunLedgerState get() = RunLedgerState(
        runId = runId,
        goal = goal,
        createdAt = startedAtMs,
        updatedAt = System.currentTimeMillis(),
        steps = planSteps.toMutableList(),
        facts = facts.map { LedgerFact(it, it) }.toMutableList(),
        deadEnds = deadEnds.toMutableList(),
        outcome = outcome?.claimed,
    )

    fun finish(outcome: Any? = null, finalAnswer: String? = null) = Unit

    fun addFact(key: String, value: String, source: String? = null) {
        facts.add(key + ": " + value)
    }

    fun addDeadEnd(note: String) {
        deadEnds.add(note)
    }

    fun setPlan(stepDescriptions: List<String>) {
        planSteps.clear()
        stepDescriptions.forEachIndexed { idx, desc ->
            planSteps.add(PlanStep(id = "step_" + (idx + 1), description = desc, status = PlanStepStatus.PENDING))
        }
    }

    fun updateStep(id: String, status: PlanStepStatus, note: String? = null) {
        val idx = planSteps.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = planSteps[idx]
            planSteps[idx] = old.copy(status = status.wire, note = note)
        }
    }

    fun revision(): Long = (planVersion + totalSteps + facts.size + deadEnds.size).toLong()

    fun toPromptBlock(maxFacts: Int = 12, maxDeadEnds: Int = 6): String =
        RunLedgerRenderer.render(this)

    fun snapshotJson(): String =
        Json { encodeDefaults = true; ignoreUnknownKeys = true }.encodeToString(serializer(), this)

    fun withDirective(text: String): RunLedger {
        val trimmed = text.trim().take(RunLedgerCaps.MAX_DIRECTIVE_CHARS)
        return if (trimmed.isEmpty()) copy(directive = null, directiveAtMs = null)
        else copy(directive = trimmed, directiveAtMs = System.currentTimeMillis())
    }

    companion object {
        const val STORE_NAME = "run_ledgers"
        const val ABANDONED = "abandoned"
        const val MIN_RECORDED_STEPS = 2
        const val RESUMABLE_WINDOW_MS = 24 * 60 * 60 * 1000L

        fun create(runId: String, goal: String): RunLedger = RunLedger(runId = runId, goal = goal)

        fun fromJson(raw: String): RunLedger? = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), raw)
        }.getOrNull()
    }
}
