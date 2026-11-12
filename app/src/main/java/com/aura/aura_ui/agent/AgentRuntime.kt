package com.aura.aura_ui.agent

import com.aura.aura_ui.agent.ledger.RunBudget
import com.aura.aura_ui.agent.ledger.RunLedger
import com.aura.aura_ui.agent.llm.LlmClient
import com.aura.aura_ui.agent.llm.LlmEndpointCatalog
import com.aura.aura_ui.agent.llm.LlmMessage
import com.aura.aura_ui.agent.llm.LlmMessageContent
import com.aura.aura_ui.agent.llm.LlmStreamEvent
import com.aura.aura_ui.agent.llm.LlmToolSpec
import com.aura.aura_ui.agent.llm.ProviderKeyStore
import com.aura.aura_ui.data.learnings.LearningsStore
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.session.SessionLogStore
import com.aura.aura_ui.data.session.SessionOutcome
import com.aura.aura_ui.data.session.SessionWriter
import com.aura.aura_ui.data.session.TraceType
import com.aura.aura_ui.data.skills.SkillsRepository
import com.aura.aura_ui.BuildConfig
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.ToolResult
import com.aura.mcp.server.McpClient
import com.aura.mcp.tools.ToolCatalog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * plan → act → verify → answer.
 *
 * Enforces: no blind taps (system prompt + step budget), verify after mutation, loop detection,
 * immediate pause, destructive-goal confirmation, bounded trace, JSONL persistence.
 */
@Singleton
class AgentRuntime @Inject constructor(
    private val settings: AuraSettings,
    private val keyStore: ProviderKeyStore,
    private val llm: LlmClient,
    private val sessions: SessionLogStore,
    private val learnings: LearningsStore,
    private val skills: SkillsRepository,
    private val confirmations: ConfirmationBroker,
    private val mcp: McpClient,
    private val bridge: DeviceBridge,
    private val perception: com.aura.aura_ui.perception.PerceptionEngine,
    private val b2: com.aura.aura_ui.b2.B2Service,
    private val remoteGate: com.aura.aura_ui.firebase.RemoteGate,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ends a run and, when the user enabled it in Settings → Backblaze, uploads its screenshots. */
    private fun endSession(session: com.aura.aura_ui.data.session.SessionWriter, outcome: String, steps: Int, answer: String?) {
        runCatching { session.end(outcome, steps, answer) }
        runCatching { sessions.clearInterruption() }
        scope.launch { runCatching { b2.backupSession(session.sessionId) } }
    }
    private val promptBuilder = PromptBuilder(settings, learnings)

    private val _state = MutableStateFlow(AgentRunState())
    val state: StateFlow<AgentRunState> = _state.asStateFlow()

    private var job: Job? = null
    private var writer: SessionWriter? = null
    private var seq = 0L
    @Volatile private var paused = false
    @Volatile private var cancelRequested = false
    private val recentFailures = ArrayDeque<String>()

    val isRunning: Boolean get() = job?.isActive == true

    fun submit(goal: String, voice: Boolean = false) {
        if (isRunning) return
        if (goal.isBlank()) return
        cancelRequested = false
        paused = false
        job = scope.launch {
            runSession(goal.trim(), voice)
        }
    }

    fun pause() {
        if (!isRunning) return
        paused = true
        _state.value = _state.value.copy(paused = true, status = "paused")
        writer?.interrupt("Paused by user")
    }

    fun resume() {
        if (!isRunning) return
        paused = false
        _state.value = _state.value.copy(paused = false, status = "planning")
    }

    fun stop() {
        cancelRequested = true
        paused = false
        job?.cancel()
    }

    fun dismiss() {
        if (!isRunning) _state.value = AgentRunState()
    }

    private suspend fun runSession(goal: String, voice: Boolean) {
        if (remoteGate.isBlocked()) {
            _state.value = AgentRunState(
                running = false,
                goal = goal,
                status = "error",
                error = "AURA has been remotely disabled by its maintainers. Please update the app.",
                outcome = SessionOutcome.PERMISSION_DENIED,
                startedAt = System.currentTimeMillis(),
            )
            return
        }
        if (BuildConfig.VERSION_CODE < remoteGate.minSupportedVersionCode().toInt()) {
            _state.value = AgentRunState(
                running = false,
                goal = goal,
                status = "error",
                error = "AURA Lite " + BuildConfig.VERSION_NAME + " is no longer supported; please update.",
                outcome = SessionOutcome.PERMISSION_DENIED,
                startedAt = System.currentTimeMillis(),
            )
            return
        }
        val endpoint = if (settings.llmEndpointId == "custom") {
            LlmEndpointCatalog.fromCustomJson(settings.customEndpointJson) ?: LlmEndpointCatalog.custom
        } else {
            LlmEndpointCatalog.byId(settings.llmEndpointId)
        }
        val key = keyStore.keyFor(endpoint)
        val model = settings.llmModelId.ifBlank { endpoint.defaultModelId.orEmpty() }

        seq = 0
        _state.value = AgentRunState(
            running = true,
            goal = goal,
            status = "planning",
            provider = endpoint.displayName,
            model = model,
            startedAt = System.currentTimeMillis(),
        )

        if (key.isNullOrBlank()) {
            failEarly("No API key for ${endpoint.displayName}. Add one in Brain.", SessionOutcome.PERMISSION_DENIED)
            return
        }
        if (model.isBlank()) {
            failEarly("No model selected for ${endpoint.displayName}. Pick one in Brain.", SessionOutcome.FAILURE)
            return
        }

        val session = sessions.newSession(goal, endpoint.id, model)
        sessions.writeInterruption(session.sessionId, goal)
        writer = session
        _state.value = _state.value.copy(sessionId = session.sessionId)

        val ledger = RunLedger.create(session.sessionId, goal)
        val budget = RunBudget(maxSteps = settings.maxSteps)
        runCatching { session.writeLedger(ledger.snapshotJson()) }

        val foreground = runCatching { bridge.foregroundPackage() }.getOrNull()
        val toolDefs = mcp.listTools()
        val toolSpecs = toolDefs.map { LlmToolSpec(it.name, it.description, it.inputSchema) }
        val enabledSkills = runCatching { skills.enabled() }.getOrDefault(emptyList())
        val system = promptBuilder.systemPrompt(
            endpoint = endpoint,
            modelId = model,
            foregroundPackage = foreground,
            skills = enabledSkills,
            recentFailures = recentFailures.toList(),
            screenshotCapable = settings.screenCaptureEnabled,
            ledgerBlock = ledger.toPromptBlock(),
            budgetNote = "Max steps ${settings.maxSteps}. Verify after mutating actions.",
        )
        val messages = mutableListOf(
            LlmMessage.system(system),
            promptBuilder.goalMessage(goal, voice),
        )

        var steps = 0
        var confirmed = !DestructiveGoal.isDestructive(goal)
        var finalAnswer: String? = null
        var outcome = SessionOutcome.SUCCESS
        var lastCallKey: String? = null
        var lastObserved: String? = null

        try {
            val maxSteps = settings.maxSteps
            while (!cancelRequested && steps < maxSteps) {
                val stopReason = budget.shouldStop(steps)
                if (stopReason != null) {
                    outcome = SessionOutcome.BUDGET_EXHAUSTED
                    finalAnswer = "Stopped: $stopReason"
                    session.error(finalAnswer!!)
                    ledger.finish(outcome, finalAnswer)
                    runCatching { session.writeLedger(ledger.snapshotJson()) }
                    break
                }
                awaitResume()
                if (cancelRequested) break

                _state.value = _state.value.copy(status = "planning", streamText = "")
                session.thought("Planning the next action.")
                budget.onRequest()
                val response = callModel(
                    endpoint = endpoint,
                    key = key,
                    model = model,
                    messages = messages,
                    tools = toolSpecs,
                    writer = session,
                )

                if (response.content.isNotBlank()) {
                    addTrace(TraceType.THOUGHT, response.content.trim(), name = "thought")
                }

                if (response.toolCalls.isEmpty()) {
                    finalAnswer = response.content.trim().ifBlank { "I could not complete that." }
                    outcome = SessionOutcome.SUCCESS
                    session.answer(finalAnswer)
                    break
                }

                messages += LlmMessage(
                    role = "assistant",
                    content = listOf(LlmMessageContent.Text(response.content)),
                    toolCalls = response.toolCalls,
                )

                var batchAborted = false
                for (call in response.toolCalls) {
                    if (cancelRequested || steps >= maxSteps) {
                        batchAborted = true
                        break
                    }
                    awaitResume()
                    if (cancelRequested) {
                        batchAborted = true
                        break
                    }

                    val definition = toolDefs.firstOrNull { it.name == call.name }
                    if (definition == null) {
                        val note = "Unknown tool `${call.name}`. Use only the listed tools."
                        session.error(note)
                        addTrace(TraceType.ERROR, note, ok = false, name = call.name)
                        messages += LlmMessage.tool(call.id, call.name, """{"ok":false,"error":"unknown_tool"}""")
                        continue
                    }

                    if (!confirmed && definition.mutating) {
                        val request = confirmations.request(
                            title = "Allow this run?",
                            detail = "${DestructiveGoal.reason(goal)}\n\nAURA will start with `${call.name}`.",
                            tool = call.name,
                        )
                        val approved = confirmations.await(request).await()
                        if (!approved) {
                            outcome = SessionOutcome.CANCELLED
                            finalAnswer = "Stopped before the first change."
                            session.interrupt("User declined the confirmation card")
                            break
                        }
                        confirmed = true
                    }

                    steps++
                    _state.value = _state.value.copy(status = "acting", stepCount = steps)
                    val callId = call.id.ifBlank { "c$steps" }
                    session.toolCall(callId, call.name, call.arguments)
                    addTrace(
                        type = TraceType.TOOL_CALL,
                        title = ToolCatalog.byName[call.name]?.description ?: call.name,
                        name = call.name,
                        args = call.arguments.toString(),
                    )

                    val dispatchKey = call.name + call.arguments.toString()
                    val started = System.currentTimeMillis()
                    val result = runCatching { mcp.callTool(call.name, call.arguments) }
                        .getOrElse { throwable ->
                            ToolResult.fail(
                                com.aura.mcp.bridge.ToolError.internal(throwable.message ?: "Tool crashed"),
                            )
                        }
                    val duration = System.currentTimeMillis() - started

                    session.toolResult(
                        id = callId,
                        name = call.name,
                        ok = result.ok,
                        changed = result.changed,
                        durationMs = duration,
                        observed = result.observed,
                    )
                    addTrace(
                        type = TraceType.TOOL_RESULT,
                        title = if (result.ok) "ok" else "error",
                        name = call.name,
                        detail = summarize(result),
                        ok = result.ok,
                        durationMs = duration,
                    )

                    messages += LlmMessage.tool(call.id, call.name, result.toJson().toString())

                    if (!result.ok) {
                        budget.onFailure()
                        val message = result.error?.let { "${call.name}: ${it.code} — ${it.message}" }
                            ?: "${call.name} failed"
                        recentFailures.addFirst(message)
                        while (recentFailures.size > 8) recentFailures.removeLast()
                        ledger.addDeadEnd(message)
                        runCatching { session.writeLedger(ledger.snapshotJson()) }
                    } else {
                        budget.onSuccess()
                    }

                    if (definition.mutating) {
                        _state.value = _state.value.copy(status = "verifying")
                        val observation = runCatching {
                            mcp.callTool(
                                "read_screen",
                                buildJsonObject { put("changes_only", true); put("max_nodes", 60) },
                            )
                        }.getOrNull()
                        val changed = observation?.observed?.get("changed")?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            ?: observation?.changed
                            ?: false
                        val detail = if (changed) "Screen changed after ${call.name}." else "No screen change detected after ${call.name}."
                        session.verify(callId, detail, changed || !result.ok)
                        addTrace(
                            type = TraceType.VERIFY,
                            title = detail,
                            name = "verify",
                            ok = changed || !result.ok,
                        )
                    }

                    // Loop detection: identical call, identical state, no change.
                    val observedNow = result.observed?.toString()
                    if (dispatchKey == lastCallKey && observedNow == lastObserved && result.changed.not()) {
                        outcome = SessionOutcome.FAILURE
                        finalAnswer = "I stopped because `${call.name}` repeated with no change to the screen."
                        session.error(finalAnswer)
                        batchAborted = true
                        break
                    }
                    lastCallKey = dispatchKey
                    lastObserved = observedNow

                    if (cancelRequested) {
                        batchAborted = true
                        break
                    }
                }
                if (batchAborted) break
            }

            if (finalAnswer == null) {
                outcome = if (cancelRequested) SessionOutcome.CANCELLED else SessionOutcome.BUDGET_EXHAUSTED
                finalAnswer = if (cancelRequested) "Stopped." else "I used all $maxSteps steps without finishing. Nothing else was changed."
            }

            ledger.finish(outcome, finalAnswer)
            runCatching { session.writeLedger(ledger.snapshotJson()) }
            endSession(session, outcome, steps, finalAnswer)
            _state.value = _state.value.copy(
                running = false,
                paused = false,
                status = if (outcome == SessionOutcome.SUCCESS) "done" else "error",
                answer = finalAnswer,
                outcome = outcome,
                stepCount = steps,
                streamText = "",
            )
            if (outcome == SessionOutcome.SUCCESS) {
                _state.value.trace.lastOrNull { it.type == TraceType.ANSWER }
            }
        } catch (cancel: CancellationException) {
            endSession(session, SessionOutcome.CANCELLED, steps, "Cancelled")
            _state.value = _state.value.copy(
                running = false,
                paused = false,
                status = "error",
                outcome = SessionOutcome.CANCELLED,
                answer = "Stopped.",
                streamText = "",
            )
            throw cancel
        } catch (t: Throwable) {
            val message = t.message ?: "The provider returned an error."
            runCatching { session.error(message) }
            endSession(session, SessionOutcome.FAILURE, steps, message)
            addTrace(TraceType.ERROR, message, ok = false)
            _state.value = _state.value.copy(
                running = false,
                paused = false,
                status = "error",
                error = message,
                outcome = SessionOutcome.FAILURE,
                answer = null,
                streamText = "",
            )
        } finally {
            writer = null
            paused = false
            cancelRequested = false
            // ONNX vision sessions hold tens of MB; free them between runs to stay inside the
            // memory budget. They are lazily recreated by the next perceive call.
            runCatching { perception.release() }
        }
    }

    private suspend fun callModel(
        endpoint: com.aura.aura_ui.agent.llm.LlmEndpoint,
        key: String,
        model: String,
        messages: List<LlmMessage>,
        tools: List<LlmToolSpec>,
        writer: SessionWriter,
    ): com.aura.aura_ui.agent.llm.LlmResponse {
        var lastError = "Provider did not respond"
        repeat(3) { attempt ->
            if (cancelRequested) throw CancellationException("cancelled")
            val stateBuilder = StringBuilder()
            var failure: LlmStreamEvent.Failed? = null
            var completed: com.aura.aura_ui.agent.llm.LlmResponse? = null
            llm.stream(
                endpoint = endpoint,
                key = key,
                model = model,
                messages = messages,
                tools = tools,
                temperature = settings.temperature,
            ).collect { event ->
                when (event) {
                    is LlmStreamEvent.TextDelta -> {
                        stateBuilder.append(event.text)
                        _state.value = _state.value.copy(
                            streamText = stateBuilder.toString().takeLast(4000),
                            status = "planning",
                        )
                    }
                    is LlmStreamEvent.ReasoningDelta -> Unit
                    is LlmStreamEvent.ToolCallStarted -> Unit
                    is LlmStreamEvent.Completed -> completed = event.response
                    is LlmStreamEvent.Failed -> failure = event
                }
            }
            completed?.let { return it }
            val failed = failure
            lastError = failed?.message ?: lastError
            if (failed?.retryable == true && attempt < 2) {
                addTrace(
                    TraceType.ERROR,
                    "Provider busy — retrying (${attempt + 1}/3). $lastError",
                    ok = false,
                )
                writer.error("retry ${attempt + 1}: $lastError")
                delay(1000L * (attempt + 1))
            } else {
                throw IOException(lastError)
            }
        }
        throw IOException(lastError)
    }

    private suspend fun awaitResume() {
        while (paused && !cancelRequested) {
            delay(80)
        }
    }

    private fun addTrace(
        type: String,
        title: String,
        detail: String? = null,
        ok: Boolean? = null,
        name: String? = null,
        args: String? = null,
        durationMs: Long? = null,
    ) {
        seq += 1
        val line = TraceLine(
            seq = seq,
            type = type,
            title = title.take(600),
            detail = detail?.take(4000),
            ok = ok,
            name = name,
            args = args?.take(4000),
            durationMs = durationMs,
        )
        val next = (_state.value.trace + line).takeLast(200)
        _state.value = _state.value.copy(trace = next)
    }

    private fun summarize(result: ToolResult): String {
        if (!result.ok) return result.error?.let { "${it.code}: ${it.message}" } ?: "Tool failed"
        val observed = result.observed
        val summary = (observed?.get("summary") as? JsonPrimitive)?.content
        return summary ?: observed?.toString()?.take(1200) ?: "ok"
    }

    private fun failEarly(message: String, outcome: String) {
        _state.value = _state.value.copy(
            running = false,
            status = "error",
            error = message,
            outcome = outcome,
            answer = null,
        )
        addTrace(TraceType.ERROR, message, ok = false)
    }
}
