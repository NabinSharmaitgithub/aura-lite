package com.aura.aura_ui.agent.report

import com.aura.aura_ui.agent.ledger.PlanStep
import kotlinx.serialization.Serializable

/**
 * How a run was entered. The runtime keeps this explicit so reports can distinguish a fresh
 * request from a run restored from saved settings or a checkpoint.
 */
@Serializable
enum class RunEntryMode {
    FRESH,
    SAVED_SETTINGS,
    RESUMED,
}

/**
 * Honest termination taxonomy. Every terminal path maps to exactly one of these values; the
 * user-facing report and the forensic trace both use this instead of free-form model text.
 */
@Serializable
enum class RunTermination(val wire: String) {
    SUCCESS("success"),
    PARTIAL("partial"),
    FAILURE("failure"),
    CANCELLED("cancelled"),
    QUOTA_EXHAUSTED("quota_exhausted"),
    BUDGET_EXHAUSTED("budget_exhausted"),
    TIME_EXHAUSTED("time_exhausted"),
    PERMISSION_DENIED("permission_denied"),
    REMOTE_BLOCKED("remote_blocked"),
    ;

    companion object {
        fun fromWire(value: String?): RunTermination? =
            entries.firstOrNull { it.wire == value }
    }
}

/** Structured evidence summary backing a report (never derived from model prose). */
@Serializable
data class ReportEvidence(
    val toolSteps: Int = 0,
    val mutations: Int = 0,
    val observations: Int = 0,
    val verifiedMutation: Boolean = false,
)

/**
 * Structured run report, decoupled from model text. UI surfaces render [markdown], while the
 * forensic log keeps the individual evidence items.
 */
@Serializable
data class RunReport(
    val sessionId: String? = null,
    val goal: String = "",
    val entryMode: RunEntryMode = RunEntryMode.FRESH,
    val termination: RunTermination = RunTermination.FAILURE,
    val status: String = "error",
    val steps: Int = 0,
    val startedAt: Long = 0L,
    val endedAt: Long = System.currentTimeMillis(),
    val summary: String? = null,
    val error: String? = null,
    val plan: List<PlanStep> = emptyList(),
    val facts: List<String> = emptyList(),
    val deadEnds: List<String> = emptyList(),
    val evidence: ReportEvidence = ReportEvidence(),
    val routeHintUsed: String? = null,
) {
    val succeeded: Boolean get() = termination == RunTermination.SUCCESS
    val partial: Boolean get() = termination == RunTermination.PARTIAL
    val refused: Boolean get() = termination == RunTermination.PERMISSION_DENIED
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0L)

    /** Lightweight markdown intended for [com.aura.aura_ui.ui.components.RunReportCard]. */
    fun markdown(): String = buildString {
        appendLine("# Run report")
        appendLine("- Goal: ${goal.ifBlank { "(none)" }}")
        appendLine("- Entry: ${entryMode.name.lowercase()}")
        appendLine("- Termination: ${termination.wire}")
        appendLine("- Steps: $steps")
        appendLine("- Duration: ${"%.1f".format(durationMs / 1000.0)}s")
        if (!summary.isNullOrBlank()) {
            appendLine()
            appendLine("## Summary")
            appendLine(summary)
        }
        if (!error.isNullOrBlank()) {
            appendLine()
            appendLine("## Error")
            appendLine(error)
        }
        if (plan.isNotEmpty()) {
            appendLine()
            appendLine("## Plan")
            plan.forEach { step ->
                appendLine("- [${step.status}] ${step.id}: ${step.description}")
            }
        }
        if (facts.isNotEmpty()) {
            appendLine()
            appendLine("## Facts")
            facts.forEach { appendLine("- $it") }
        }
        if (deadEnds.isNotEmpty()) {
            appendLine()
            appendLine("## Dead ends")
            deadEnds.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("## Evidence")
        appendLine("- Mutations: ${evidence.mutations}")
        appendLine("- Observations: ${evidence.observations}")
        appendLine("- Verified mutation: ${if (evidence.verifiedMutation) "yes" else "no"}")
        if (!routeHintUsed.isNullOrBlank()) {
            appendLine("- Route hint: $routeHintUsed")
        }
    }.trim()

    companion object {
        fun from(
            sessionId: String?,
            goal: String,
            entryMode: RunEntryMode,
            outcome: String?,
            status: String,
            steps: Int,
            startedAt: Long,
            summary: String?,
            error: String?,
            plan: List<PlanStep>,
            facts: List<String>,
            deadEnds: List<String>,
            evidence: ReportEvidence,
            routeHintUsed: String? = null,
        ): RunReport = RunReport(
            sessionId = sessionId,
            goal = goal,
            entryMode = entryMode,
            termination = terminationFor(outcome, steps, error),
            status = status,
            steps = steps,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            summary = summary,
            error = error,
            plan = plan,
            facts = facts,
            deadEnds = deadEnds,
            evidence = evidence,
            routeHintUsed = routeHintUsed,
        )

        /** Map a legacy [com.aura.aura_ui.data.session.SessionOutcome] wire value to a taxonomy. */
        fun terminationFor(outcome: String?, steps: Int, error: String?): RunTermination =
            when (outcome) {
                "success" -> RunTermination.SUCCESS
                "partial" -> RunTermination.PARTIAL
                "cancelled" -> RunTermination.CANCELLED
                "quota_exhausted" -> RunTermination.QUOTA_EXHAUSTED
                "budget_exhausted" -> if (steps > 0) RunTermination.PARTIAL else RunTermination.BUDGET_EXHAUSTED
                "time_exhausted" -> RunTermination.TIME_EXHAUSTED
                "permission_denied" -> RunTermination.PERMISSION_DENIED
                "remote_blocked" -> RunTermination.REMOTE_BLOCKED
                else -> if (error != null) RunTermination.FAILURE else RunTermination.FAILURE
            }
    }
}
