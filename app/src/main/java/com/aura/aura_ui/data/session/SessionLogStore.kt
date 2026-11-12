package com.aura.aura_ui.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object TraceType {
    const val SESSION_START = "session_start"
    const val THOUGHT = "thought"
    const val PLAN = "plan"
    const val TOOL_CALL = "tool_call"
    const val TOOL_RESULT = "tool_result"
    const val GATE = "gate"
    const val SETTLE = "settle"
    const val OBSERVE = "observe"
    const val COMPLETION = "completion"
    const val POLICY = "policy"
    const val VERIFY = "verify"
    const val SCREENSHOT = "screenshot"
    const val ASK_USER = "ask_user"
    const val USER_ANSWER = "user_answer"
    const val ERROR = "error"
    const val INTERRUPT = "interrupt"
    const val ANSWER = "answer"
    const val SESSION_END = "session_end"
}

object SessionOutcome {
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val CANCELLED = "cancelled"
    const val BUDGET_EXHAUSTED = "budget_exhausted"
    const val PERMISSION_DENIED = "permission_denied"
}

data class TraceEvent(
    val t: Long,
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val name: String? = null,
    val args: JsonObject? = null,
    val ok: Boolean? = null,
    val changed: Boolean? = null,
    val durationMs: Long? = null,
    val observed: JsonObject? = null,
    val path: String? = null,
    val outcome: String? = null,
    val steps: Int? = null,
    val generation: Int? = null,
    val error: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("t", t)
        put("type", type)
        if (id != null) put("id", id)
        if (text != null) put("text", text)
        if (name != null) put("name", name)
        if (args != null) put("args", args)
        if (ok != null) put("ok", ok)
        if (changed != null) put("changed", changed)
        if (durationMs != null) put("duration_ms", durationMs)
        if (observed != null) put("observed", observed)
        if (path != null) put("path", path)
        if (outcome != null) put("outcome", outcome)
        if (steps != null) put("steps", steps)
        if (generation != null) put("generation", generation)
        if (error != null) put("error", error)
    }

    companion object {
        fun parse(line: String): TraceEvent? = runCatching {
            val o = Json.parseToJsonElement(line).jsonObject
            fun str(key: String) = o[key]?.jsonPrimitive?.content
            TraceEvent(
                t = o["t"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                type = str("type") ?: "event",
                id = str("id"),
                text = str("text"),
                name = str("name"),
                args = o["args"] as? JsonObject,
                ok = o["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
                changed = o["changed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
                durationMs = o["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull(),
                observed = o["observed"] as? JsonObject,
                path = str("path"),
                outcome = str("outcome"),
                steps = o["steps"]?.jsonPrimitive?.content?.toIntOrNull(),
                generation = o["generation"]?.jsonPrimitive?.content?.toIntOrNull(),
                error = str("error"),
            )
        }.getOrNull()
    }
}

data class SessionSummary(
    val id: String,
    val goal: String,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: String?,
    val steps: Int,
    val provider: String,
    val model: String,
) {
    val durationMs: Long get() = (endedAt ?: System.currentTimeMillis()) - startedAt
    val isRunning: Boolean get() = endedAt == null
}

/** Streaming JSONL writer. Every event is flushed, so a process kill still leaves a trace. */
class SessionWriter internal constructor(
    val sessionId: String,
    private val file: File,
    private val dir: File,
) {
    private val lock = Any()

    init {
        dir.mkdirs()
    }

    fun emit(event: TraceEvent) {
        synchronized(lock) {
            runCatching { file.appendText(event.toJson().toString() + "\n") }
        }
    }

    fun thought(text: String) = emit(TraceEvent(System.currentTimeMillis(), TraceType.THOUGHT, text = text))
    fun plan(text: String) = emit(TraceEvent(System.currentTimeMillis(), TraceType.PLAN, text = text))
    fun toolCall(id: String, name: String, args: JsonObject) =
        emit(TraceEvent(System.currentTimeMillis(), TraceType.TOOL_CALL, id = id, name = name, args = args))

    fun toolResult(id: String, name: String, ok: Boolean, changed: Boolean, durationMs: Long, observed: JsonObject?) =
        emit(
            TraceEvent(
                System.currentTimeMillis(), TraceType.TOOL_RESULT,
                id = id, name = name, ok = ok, changed = changed,
                durationMs = durationMs, observed = observed,
            ),
        )

    fun verify(id: String, text: String, ok: Boolean) =
        emit(TraceEvent(System.currentTimeMillis(), TraceType.VERIFY, id = id, text = text, ok = ok))

    fun answer(text: String) = emit(TraceEvent(System.currentTimeMillis(), TraceType.ANSWER, text = text))
    fun error(message: String) = emit(TraceEvent(System.currentTimeMillis(), TraceType.ERROR, error = message, text = message))
    fun interrupt(reason: String) = emit(TraceEvent(System.currentTimeMillis(), TraceType.INTERRUPT, text = reason))

    fun screenshot(file: File, generation: Int? = null): String {
        val name = file.name
        emit(
            TraceEvent(
                System.currentTimeMillis(), TraceType.SCREENSHOT,
                path = name, generation = generation,
            ),
        )
        return name
    }

    fun end(outcome: String, steps: Int, summary: String? = null) =
        emit(
            TraceEvent(
                System.currentTimeMillis(), TraceType.SESSION_END,
                outcome = outcome, steps = steps, text = summary,
            ),
        )

    fun screenshotDir(): File = File(dir, "screens").also { it.mkdirs() }

    fun writeLedger(json: String) {
        synchronized(lock) { runCatching { File(dir, "ledger.json").writeText(json) } }
    }

    fun readLedger(): String? = runCatching { File(dir, "ledger.json").takeIf { it.exists() }?.readText() }.getOrNull()
}

@Singleton
class SessionLogStore @Inject constructor(@ApplicationContext context: Context) {

    private val root = File(context.filesDir, "sessions").also { it.mkdirs() }
    private val interruption = File(root.parentFile, "session_interruption.json")

    /** Marks that a run began so we can tell the user if the process died mid-task. */
    fun writeInterruption(sessionId: String, goal: String) {
        runCatching {
            interruption.writeText(
                buildJsonObject {
                    put("sessionId", sessionId)
                    put("goal", goal)
                    put("t", System.currentTimeMillis())
                }.toString(),
            )
        }
    }

    /** Returns the interrupted (sessionId, goal) pair if any, or null when there's nothing to report. */
    fun readInterruption(): Pair<String, String>? {
        if (!interruption.exists()) return null
        return runCatching {
            val obj = Json.parseToJsonElement(interruption.readText()).jsonObject
            val id = obj["sessionId"]?.jsonPrimitive?.content ?: return null
            val goal = obj["goal"]?.jsonPrimitive?.content ?: ""
            id to goal
        }.getOrNull()
    }

    /** Called on every clean end path; clearing means no spurious "interrupted" banner. */
    fun clearInterruption() {
        runCatching { interruption.delete() }
    }

    fun newSession(goal: String, provider: String, model: String): SessionWriter {
        val id = "s_" + UUID.randomUUID().toString().take(8)
        val dir = File(root, id).also { it.mkdirs() }
        val file = File(dir, "session.jsonl")
        val writer = SessionWriter(id, file, dir)
        writer.emit(
            TraceEvent(
                System.currentTimeMillis(), TraceType.SESSION_START,
                id = id, text = goal, name = provider,
            ),
        )
        // Provider and model are stored on the start line as observed metadata.
        writer.emit(
            TraceEvent(
                System.currentTimeMillis(), TraceType.PLAN,
                text = "provider=$provider model=$model",
            ),
        )
        return writer
    }

    fun sessions(limit: Int = 100): List<SessionSummary> {
        if (!root.exists()) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { summarize(it) }
            ?.sortedByDescending { it.startedAt }
            ?.take(limit)
            .orEmpty()
    }

    private fun summarize(dir: File): SessionSummary? {
        val file = File(dir, "session.jsonl")
        if (!file.exists()) return null
        var goal = ""
        var started = file.lastModified()
        var ended: Long? = null
        var outcome: String? = null
        var steps = 0
        var provider = ""
        var model = ""
        runCatching {
            file.forEachLine { line ->
                val event = TraceEvent.parse(line) ?: return@forEachLine
                when (event.type) {
                    TraceType.SESSION_START -> {
                        goal = event.text ?: ""
                        started = event.t
                        provider = event.name ?: ""
                    }
                    TraceType.PLAN -> {
                        val text = event.text ?: ""
                        if (text.startsWith("provider=")) {
                            provider = text.substringAfter("provider=").substringBefore(" ")
                            model = text.substringAfter("model=", "")
                        }
                    }
                    TraceType.SESSION_END -> {
                        ended = event.t
                        outcome = event.outcome
                        steps = event.steps ?: steps
                    }
                    TraceType.TOOL_CALL -> steps += 1
                }
            }
        }
        return SessionSummary(dir.name, goal, started, ended, outcome, steps, provider, model)
    }

    fun events(sessionId: String): List<TraceEvent> {
        val file = File(File(root, sessionId), "session.jsonl")
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().mapNotNull { TraceEvent.parse(it) } }.getOrDefault(emptyList())
    }

    fun screenshotFile(sessionId: String, name: String): File? {
        val f = File(root, "$sessionId/screens/$name")
        return if (f.exists()) f else null
    }

    fun writerFor(sessionId: String): SessionWriter? {
        val dir = File(root, sessionId)
        if (!dir.isDirectory) return null
        return SessionWriter(sessionId, File(dir, "session.jsonl"), dir)
    }

    fun delete(sessionId: String): Boolean = File(root, sessionId).deleteRecursively()

    /** Returns the number of sessions removed. */
    fun prune(days: Int): Int {
        val cutoff = System.currentTimeMillis() - days.coerceAtLeast(1) * 86_400_000L
        var removed = 0
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val file = File(dir, "session.jsonl")
            if (file.exists() && file.lastModified() < cutoff) {
                if (dir.deleteRecursively()) removed++
            }
        }
        return removed
    }

    fun usageBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun relativeTime(t: Long): String {
        val delta = System.currentTimeMillis() - t
        return when {
            delta < 60_000 -> "just now"
            delta < 3_600_000 -> "${delta / 60_000}m ago"
            delta < 86_400_000 -> "${delta / 3_600_000}h ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
        }
    }
}
