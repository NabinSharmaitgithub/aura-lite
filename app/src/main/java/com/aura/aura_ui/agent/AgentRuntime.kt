package com.aura.aura_ui.agent

import android.content.Context
import com.aura.aura_ui.agent.checkpoint.RunCheckpoint
import com.aura.aura_ui.agent.checkpoint.RunCheckpointStore
import com.aura.aura_ui.agent.control.ControlLock
import com.aura.aura_ui.agent.runtime.ActiveRunRegistry
import com.aura.aura_ui.agent.runtime.KeepAwakeController
import com.aura.aura_ui.agent.runtime.RunLifecycleHooks
import com.aura.aura_ui.agent.runtime.StatusNarrator
import com.aura.aura_ui.agent.ledger.RunBudget
import com.aura.aura_ui.agent.ledger.RunLedger
import com.aura.aura_ui.agent.llm.LlmClient
import com.aura.aura_ui.agent.llm.LlmEndpointCatalog
import com.aura.aura_ui.agent.llm.LlmMessage
import com.aura.aura_ui.agent.llm.LlmMessageContent
import com.aura.aura_ui.agent.llm.LlmStreamEvent
import com.aura.aura_ui.agent.llm.LlmToolSpec
import com.aura.aura_ui.agent.llm.ProviderKeyStore
import com.aura.aura_ui.bridge.JsonArgs
import com.aura.aura_ui.data.learnings.LearningsStore
import com.aura.aura_ui.data.learnings.LearningFact
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.skills.Skill
import com.aura.aura_ui.data.session.SessionLogStore
import com.aura.aura_ui.data.session.SessionOutcome
import com.aura.aura_ui.data.session.SessionWriter
import com.aura.aura_ui.data.session.TraceType
import com.aura.aura_ui.data.skills.SkillsRepository
import com.aura.aura_ui.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.ToolResult
import com.aura.mcp.guard.OtpSighting
import com.aura.mcp.server.McpClient
import com.aura.mcp.tools.ToolCatalog
import java.io.File
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
    @ApplicationContext private val appContext: Context,
    private val checkpointStore: RunCheckpointStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keepAwake = KeepAwakeController(appContext)
    private val evidence = CompletionEvidence()
    private var currentRunId: String? = null

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
    private val clientSideTools = setOf(
        "ledger_add_fact",
        "ledger_note_dead_end",
        "ledger_update_step",
        "memory_remember",
        "memory_search",
        "proof_status",
        "skills_list",
        "research_search",
    )

    val isRunning: Boolean get() = job?.isActive == true

    fun submit(goal: String, voice: Boolean = false) {
        if (isRunning) return
        if (goal.isBlank()) return
        cancelRequested = false
        paused = false
        val runId = "run-${System.currentTimeMillis()}"
        currentRunId = runId
        ActiveRunRegistry.start(runId, goal)
        ControlLock.setUserPaused(false)
        StatusNarrator.update("starting", goal.take(80))
        job = scope.launch {
            try {
                runSession(goal.trim(), voice)
            } finally {
                // Early returns inside runSession (remote gate/key/model checks) skip its inner
                // finally, so always clean up the run registry/wake lock here as well.
                finishRun()
            }
        }
    }

    private fun finishRun() {
        keepAwake.release()
        ControlLock.setUserPaused(false)
        currentRunId?.let { ActiveRunRegistry.finish(it) }
        currentRunId = null
        StatusNarrator.update("idle", null)
    }

    fun pause() {
        if (!isRunning) return
        paused = true
        ControlLock.setUserPaused(true)
        currentRunId?.let { ActiveRunRegistry.update(it, status = "paused", paused = true) }
        StatusNarrator.update("paused", "run paused by user")
        _state.value = _state.value.copy(paused = true, status = "paused")
        writer?.interrupt("Paused by user")
        checkpoint(reason = "paused_by_user")
    }

    fun resume() {
        if (!isRunning) return
        paused = false
        ControlLock.setUserPaused(false)
        currentRunId?.let { ActiveRunRegistry.update(it, status = "running", paused = false) }
        StatusNarrator.update("resuming", "re-verifying screen state")
        _state.value = _state.value.copy(paused = false, status = "planning")
    }

    fun stop() {
        cancelRequested = true
        paused = false
        ControlLock.setUserPaused(false)
        StatusNarrator.update("stopping", null)
        currentRunId?.let { ActiveRunRegistry.update(it, status = "cancelling", cancelled = true) }
        job?.cancel()
    }

    fun dismiss() {
        if (!isRunning) _state.value = AgentRunState()
    }

    /**
     * Resume the most recent paused/interrupted run. The resumed goal includes the previous
     * ledger and an explicit instruction to re-verify screen state before acting.
     */
    fun resumeLatestCheckpoint(): Boolean {
        if (isRunning) return false
        val checkpoint = checkpointStore.latest() ?: return false
        val ledgerHint = checkpoint.ledgerJson
            ?.take(2_000)
            ?.let { "\nPrevious run ledger (re-verify before trusting it):\n$it" }
            .orEmpty()
        val resumeGoal = buildString {
            append("Resume this interrupted goal: ")
            append(checkpoint.goal)
            append("\nIt was paused after ${checkpoint.stepCount} steps.")
            if (!checkpoint.foregroundPackage.isNullOrBlank()) {
                append(" Last foreground app: ${checkpoint.foregroundPackage}.")
            }
            append("\nRe-read the screen and confirm the current state before performing any action.")
            append(ledgerHint)
        }
        submit(resumeGoal)
        return true
    }

    private suspend fun runSession(goal: String, voice: Boolean) {
        // Best-effort refresh; RemoteGate keeps the last known state when offline.
        runCatching { remoteGate.refresh() }
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

        StatusNarrator.update("planning", goal.take(80))
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

        keepAwake.acquire()
        evidence.clear()
        val session = sessions.newSession(goal, endpoint.id, model)
        sessions.writeInterruption(session.sessionId, goal)
        writer = session
        _state.value = _state.value.copy(sessionId = session.sessionId)
        RunLifecycleHooks.start(session.sessionId, goal)

        val ledger = RunLedger.create(session.sessionId, goal)
        val budget = RunBudget(maxSteps = settings.maxSteps)
        runCatching { session.writeLedger(ledger.snapshotJson()) }

        val foreground = runCatching { bridge.foregroundPackage() }.getOrNull()
        val toolDefs = mcp.listTools()
        val toolSpecs = toolDefs.map { LlmToolSpec(it.name, it.description, it.inputSchema) }.toMutableList()
        val enabledSkills = runCatching { skills.enabled() }.getOrDefault(emptyList())
        // Register enabled skill packs as MCP prompts so remote clients can invoke them too.
        enabledSkills.forEach { skill ->
            runCatching {
                com.aura.mcp.server.McpPrompts.registerSkillPrompt(
                    name = "skill_${skill.id}",
                    description = skill.trigger,
                    promptText = buildString {
                        appendLine(skill.name)
                        appendLine(skill.trigger)
                        skill.steps.forEach { appendLine("- $it") }
                        if (skill.guardrails.isNotEmpty()) {
                            appendLine("Guardrails:")
                            skill.guardrails.forEach { appendLine("- $it") }
                        }
                    }.trim(),
                )
            }
        }
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
        var defectNudges = 0
        var confirmed = !DestructiveGoal.isDestructive(goal) &&
            !SpokenRequestScreener.requiresConfirmation(goal, voice)
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

                // Re-inject the ledger periodically so long runs keep their plan/facts/dead-ends
                // in view even after older turns are trimmed or compacted.
                if (steps > 0 && steps % 5 == 0) {
                    messages[0] = LlmMessage.system(system + "\n\n" + ledger.toPromptBlock())
                    compactMessages(messages)
                }
                evictStaleImages(messages)

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

                // Bounded defect repair: models sometimes print a tool call as text instead of
                // using the native tool interface. Nudge once or twice, then let normal failure
                // handling take over rather than looping forever.
                if (response.toolCalls.isEmpty() && defectNudges < 2 && looksLikeToolCallText(response.content)) {
                    defectNudges++
                    addTrace(
                        TraceType.ERROR,
                        "Defect repair: tool call emitted as text (${defectNudges}/2)",
                        ok = false,
                    )
                    messages += LlmMessage.user(
                        "Your previous message described a tool call as text but did not use the tool interface. " +
                            "Call the tool directly now. Do not answer the user yet.",
                    )
                    _state.value = _state.value.copy(status = "planning", streamText = "")
                    continue
                }

                if (response.toolCalls.isEmpty()) {
                    val answer = response.content.trim().ifBlank { "I could not complete that." }
                    // Completion evidence safeguard: high-stakes/destructive goals must not be
                    // reported as successful without proof that something actually happened.
                    val requiresProof = DestructiveGoal.isDestructive(goal)
                    val hasProof = evidence.hasVerifiedMutation() ||
                        (evidence.hasMutationSuccess() && evidence.hasObservation())
                    if (requiresProof && !hasProof) {
                        outcome = SessionOutcome.FAILURE
                        finalAnswer = "I couldn't verify completion with observed evidence. $answer"
                        session.error(finalAnswer)
                    } else {
                        outcome = SessionOutcome.SUCCESS
                        finalAnswer = answer
                        session.answer(finalAnswer)
                    }
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

                    // Client-side helper tools operate on run state, memory and skills. They never
                    // reach the device bridge and do not consume a device-action step.
                    val clientSideResult = runClientTool(call.name, call.arguments, ledger, evidence, enabledSkills)
                    if (clientSideResult != null) {
                        messages += LlmMessage.tool(call.id, call.name, clientSideResult.toString())
                        runCatching { session.writeLedger(ledger.snapshotJson()) }
                        addTrace(TraceType.TOOL_RESULT, "Client tool: ${call.name}", name = call.name)
                        continue
                    }
                    if (call.name in clientSideTools) {
                        messages += LlmMessage.tool(
                            call.id,
                            call.name,
                            """{"ok":false,"error":"invalid arguments for ${call.name}"}""",
                        )
                        continue
                    }

                    // Deferred tool loading: expand the active tool schema set without spending a
                    // device-action step. This is a client-side operation, not a device action.
                    if (call.name == "load_tools") {
                        val requested = (call.arguments["tools"] as? kotlinx.serialization.json.JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.content }
                            .orEmpty()
                        val loaded = requested.mapNotNull { ToolCatalog.byName[it] }
                            .filter { def -> toolSpecs.none { it.name == def.name } }
                        toolSpecs.addAll(loaded.map { LlmToolSpec(it.name, it.description, it.inputSchema) })
                        messages += LlmMessage.tool(
                            call.id,
                            call.name,
                            buildJsonObject {
                                put("count", loaded.size)
                                put("loaded", loaded.joinToString(",") { it.name })
                            }.toString(),
                        )
                        addTrace(TraceType.TOOL_RESULT, "Loaded tools: ${loaded.joinToString { it.name }}", name = call.name)
                        continue
                    }

                    if (!confirmed && definition.mutating) {
                        val reason = DestructiveGoal.reason(goal).ifBlank {
                            SpokenRequestScreener.reason(goal)
                        }
                        val request = confirmations.request(
                            title = "Allow this run?",
                            detail = "$reason\n\nAURA will start with `${call.name}`.",
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
                    StatusNarrator.update("acting", call.name)
                    currentRunId?.let { ActiveRunRegistry.update(it, status = "acting") }
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
                    addTrace(
                        TraceType.GATE,
                        if (result.ok) "allowed" else "blocked/failed: ${result.error?.code ?: "unknown"}",
                        ok = result.ok,
                        name = call.name,
                        durationMs = duration,
                    )

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
                    // Screenshot re-injection: when the model can see images, feed the freshly
                    // captured frame back into the conversation so it can reason about pixels.
                    if (call.name == "get_screenshot" && result.ok &&
                        LlmEndpointCatalog.supportsVision(endpoint, model)
                    ) {
                        val path = (result.observed?.get("path") as? JsonPrimitive)?.content
                        val file = path?.let { File(it) }
                        if (file != null && file.exists() && file.length() in 1..1_500_000) {
                            runCatching {
                                val base64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
                                messages += LlmMessage(
                                    role = "user",
                                    content = listOf(LlmMessageContent.Image(base64)),
                                )
                            }
                        }
                    }
                    evidence.record(
                        steps,
                        if (definition.mutating) "mutation" else "tool",
                        "${call.name} ok=${result.ok}",
                        result.ok,
                    )

                    // Perception escalation: when the structural tree is insufficient or an action
                    // fails, pull a richer visual observation so the next decision is grounded.
                    run {
                        val tier = (result.observed?.get("perception_tiering") as? JsonObject)
                            ?.get("tier")?.jsonPrimitive?.content
                        val shouldEscalate = (!result.ok && definition.mutating) ||
                            (call.name == "read_screen" && tier == "full")
                        if (shouldEscalate) {
                            val esc = runCatching {
                                mcp.callTool("perceive_screen", buildJsonObject { put("mode", "both") })
                            }.getOrNull()
                            if (esc?.ok == true) {
                                messages += LlmMessage.user(
                                    "Perception escalation (${if (!result.ok) "after failure" else "tier=full"}): " +
                                        (esc.observed?.toString()?.take(3000) ?: "ok"),
                                )
                                addTrace(
                                    TraceType.OBSERVE,
                                    "Perception escalated via perceive_screen",
                                    ok = true,
                                    name = "perceive_screen",
                                )
                                // If a screenshot path is available and vision is supported, inject it.
                                if (LlmEndpointCatalog.supportsVision(endpoint, model)) {
                                    val path = (esc.observed?.get("path") as? JsonPrimitive)?.content
                                        ?: (esc.observed?.get("screenshot_path") as? JsonPrimitive)?.content
                                    val file = path?.let { File(it) }
                                    if (file != null && file.exists() && file.length() in 1..1_500_000) {
                                        runCatching {
                                            val base64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
                                            messages += LlmMessage(
                                                role = "user",
                                                content = listOf(LlmMessageContent.Image(base64)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!result.ok) {
                        budget.onFailure()
                        val message = result.error?.let { "${call.name}: ${it.code} — ${it.message}" }
                            ?: "${call.name} failed"
                        recentFailures.addFirst(message)
                        while (recentFailures.size > 8) recentFailures.removeLast()
                        ledger.addDeadEnd(message)
                    } else {
                        budget.onSuccess()
                    }
                    // Incremental ledger persistence with flush on each tool step.
                    runCatching { session.writeLedger(ledger.snapshotJson()) }

                    if (definition.mutating) {
                        _state.value = _state.value.copy(status = "verifying")
                        StatusNarrator.update("verifying", call.name)
                        addTrace(TraceType.SETTLE, "Settling after ${call.name}", name = call.name)
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
                        val observedOk = changed || !result.ok
                        evidence.record(steps, "observation", detail, observedOk)
                        if (result.ok && changed) {
                            evidence.record(steps, "verified_mutation", "Verified change after ${call.name}", true)
                        }
                        addTrace(
                            TraceType.OBSERVE,
                            detail,
                            ok = observedOk,
                            name = "observe",
                        )
                        session.verify(callId, detail, observedOk)
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
            addTrace(
                TraceType.COMPLETION,
                "Run ended: $outcome",
                detail = finalAnswer,
                ok = outcome == SessionOutcome.SUCCESS,
            )
            if (outcome == SessionOutcome.SUCCESS) {
                checkpointStore.clear(session.sessionId)
            } else {
                checkpoint("outcome_$outcome", steps)
            }
            _state.value = _state.value.copy(
                running = false,
                paused = false,
                status = if (outcome == SessionOutcome.SUCCESS) "done" else "error",
                answer = finalAnswer,
                outcome = outcome,
                stepCount = steps,
                streamText = "",
            )
        } catch (cancel: CancellationException) {
            checkpoint("cancelled", steps)
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
            checkpoint("error", steps)
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
            val endedSessionId = writer?.sessionId ?: _state.value.sessionId
            RunLifecycleHooks.end(
                endedSessionId,
                _state.value.outcome,
                _state.value.stepCount,
                _state.value.answer,
            )
            writer = null
            paused = false
            cancelRequested = false
            finishRun()
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

    /**
     * Stale perception eviction for image payloads: keep only the newest screenshot in context.
     * Older frames are expensive and can mislead the model after the UI has changed.
     */
    private fun evictStaleImages(messages: MutableList<LlmMessage>) {
        val imageIndices = messages.indices.filter { index ->
            messages[index].content.any { it is LlmMessageContent.Image }
        }
        if (imageIndices.size <= 1) return
        imageIndices.dropLast(1).sortedDescending().forEach { messages.removeAt(it) }
    }

    /** Heuristic: does this text claim to call a tool without using the native tool channel? */
    private fun looksLikeToolCallText(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return false
        val hasName = t.contains("\"name\"") || t.contains("tool_call") || t.contains("<tool")
        val hasArgs = t.contains("\"arguments\"") || t.contains("\"parameters\"") || t.contains("\"args\"")
        return hasName && hasArgs
    }

    /**
     * Bounded context compaction. Keeps the system+goal preamble and a recent contiguous tail;
     * never starts the tail on a tool result, so assistant tool-call/result pairing stays valid.
     */
    private fun compactMessages(
        messages: MutableList<LlmMessage>,
        maxMessages: Int = 60,
        keepRecent: Int = 32,
    ) {
        if (messages.size <= maxMessages) return
        val head = messages.take(2)
        var start = (messages.size - keepRecent).coerceAtLeast(2)
        while (start < messages.size && messages[start].role == "tool") start++
        if (start >= messages.size) return
        val tail = messages.subList(start, messages.size).toList()
        messages.clear()
        messages.addAll(head)
        messages.addAll(tail)
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

    /**
     * Handles agent-local helper tools. Returns null when [name] is not a client-side tool;
     * returns a JSON result envelope (ok/error) when it is.
     */
    private suspend fun runClientTool(
        name: String,
        args: JsonObject,
        ledger: RunLedger,
        evidence: CompletionEvidence,
        enabledSkills: List<Skill>,
    ): JsonObject? = when (name) {
        "ledger_add_fact" -> {
            val key = JsonArgs.str(args, "key")
            val value = JsonArgs.str(args, "value")
            if (key == null || value == null) {
                errorJson("ledger_add_fact requires key and value")
            } else if (OtpSighting.inspect(value).detected) {
                errorJson("Sensitive one-time codes must not be stored in the ledger")
            } else {
                ledger.addFact(key, value, JsonArgs.str(args, "source"))
                buildJsonObject { put("ok", true); put("key", key) }
            }
        }
        "ledger_note_dead_end" -> {
            val note = JsonArgs.str(args, "note")
            if (note == null) {
                errorJson("ledger_note_dead_end requires note")
            } else {
                ledger.addDeadEnd(note)
                buildJsonObject { put("ok", true) }
            }
        }
        "ledger_update_step" -> {
            val id = JsonArgs.str(args, "id")
            val status = JsonArgs.str(args, "status")
            if (id == null || status == null) {
                errorJson("ledger_update_step requires id and status")
            } else {
                ledger.updateStep(id, status, JsonArgs.str(args, "note"))
                buildJsonObject { put("ok", true); put("id", id); put("status", status) }
            }
        }
        "memory_remember" -> {
            val text = JsonArgs.str(args, "text")
            if (text == null) {
                errorJson("memory_remember requires text")
            } else if (OtpSighting.inspect(text).detected) {
                errorJson("Sensitive one-time codes must not be stored in memory")
            } else {
                val scope = JsonArgs.str(args, "scope") ?: "global"
                learnings.add(
                    LearningFact(
                        id = "agent-${System.currentTimeMillis()}",
                        scope = scope,
                        text = text,
                        confidence = 0.8f,
                        sources = listOf("agent"),
                    ),
                )
                buildJsonObject { put("ok", true); put("scope", scope) }
            }
        }
        "memory_search" -> {
            val query = JsonArgs.str(args, "query")
            if (query == null) {
                errorJson("memory_search requires query")
            } else {
                val limit = JsonArgs.int(args, "limit", 10) ?: 10
                val matches = learnings.facts()
                    .filter { it.text.contains(query, ignoreCase = true) || it.scope.contains(query, ignoreCase = true) }
                    .take(limit)
                    .joinToString("\n") { "- ${it.text} (scope=${it.scope}, confidence=${it.confidence})" }
                buildJsonObject {
                    put("ok", true)
                    put("query", query)
                    put("matches", matches)
                }
            }
        }
        "proof_status" -> buildJsonObject {
            put("ok", true)
            put("evidence", evidence.toJson())
        }
        "skills_list" -> buildJsonObject {
            put("ok", true)
            put("skills", enabledSkills.joinToString("\n") { "- ${it.name}: ${it.trigger}" })
        }
        "research_search" -> {
            val query = JsonArgs.str(args, "query")
            if (query == null) {
                errorJson("research_search requires query")
            } else {
                val maxResults = JsonArgs.int(args, "max_results", 5) ?: 5
                val result = mcp.callTool(
                    "web_search",
                    buildJsonObject {
                        put("query", query)
                        put("max_results", maxResults)
                    },
                )
                result.observed ?: errorJson(
                    result.error?.message ?: "Research search failed",
                )
            }
        }
        else -> null
    }

    private fun errorJson(message: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
    }

    private fun summarize(result: ToolResult): String {
        if (!result.ok) return result.error?.let { "${it.code}: ${it.message}" } ?: "Tool failed"
        val observed = result.observed
        val summary = (observed?.get("summary") as? JsonPrimitive)?.content
        return summary ?: observed?.toString()?.take(1200) ?: "ok"
    }

    /** Writes a resumable checkpoint atomically (best effort). */
    private fun checkpoint(reason: String, stepsOverride: Int? = null) {
        val state = _state.value
        val sessionId = state.sessionId ?: return
        runCatching {
            checkpointStore.write(
                RunCheckpoint(
                    sessionId = sessionId,
                    goal = state.goal,
                    stepCount = stepsOverride ?: state.stepCount,
                    outcomeHint = state.outcome,
                    ledgerJson = writer?.let { runCatching { it.readLedger() }.getOrNull() },
                    screenGeneration = -1L,
                    screenSignature = null,
                    foregroundPackage = com.aura.aura_ui.accessibility.ScreenState.lastPickedPackage
                        ?: com.aura.aura_ui.accessibility.ScreenState.snapshot?.packageName,
                    pendingQuestion = null,
                    pendingApproval = null,
                    checkpointReason = reason,
                ),
            )
        }
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
