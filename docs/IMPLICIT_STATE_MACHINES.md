# Implicit state machines in AURA Lite

An "implicit state machine" here means behaviour whose correct operation depends on a
combination of **booleans, nullable fields, free-form status strings, counters and
`@Volatile` flags** spread across several objects, with no single type that names the legal
states or guards the transitions.

This document catalogues every one found in the app/module code. It is organised by domain.
Each entry lists the states, the transition triggers, the owning code, and the specific risk of
leaving it implicit.

> Scope: `app/src/main/java/**` and `mcp-server/src/main/kotlin/**`. UI-only derived values are
> listed only where they hide a real state machine.

---

## 1. Executive summary

The app contains **~40 implicit state machines**. The highest-risk ones are:

| Rank | State machine | Why it matters |
|---|---|---|
| 1 | Agent run lifecycle (`running/paused/status/outcome/termination`) | Four overlapping sources of truth for "what is the run doing". Illegal combinations are representable. |
| 2 | HITL approvals (`ConfirmationBroker` + notification gate) | A dropped/duplicated response can leave a run suspended or approve the wrong request. |
| 3 | WebRTC peer handshake (`approved/initSeen/challenge/sas/enroll/dead`) | Security-sensitive protocol with no transition guard. |
| 4 | MCP server controller (`server`/`webRtcTransport` nullability + `running`) | "Running" is inferred from two nullable transport fields plus a health flag. |
| 5 | Voice/wake-word (`recording`/`pausedForRun`/`job`) | A missed reset leaves the microphone open or the service dead. |
| 6 | Screen capture consent (`consentRequested` counter + `projection`) | The counter is a UI event, not a state; capture can be attempted while consent is stale. |
| 7 | Plan steps (`PlanStep.status` free-form string) | `updateStep` accepts any string; the UI has to guess unknown states. |

A recurring pattern is **duplicated state**: the same fact is stored in
`AgentRunState`, `ActiveRunRegistry`, `RunLedger`, `StatusNarrator`, `RunReport`, and the
session trace. They can disagree.

---

## 2. Agent execution

### 2.1 Agent run lifecycle (the central one)

**Where:** `app/src/main/java/com/aura/aura_ui/agent/AgentRuntime.kt`,
`app/src/main/java/com/aura/aura_ui/agent/AgentState.kt`

**State variables**

| Variable | Values | Stored in |
|---|---|---|
| `AgentRunState.running` | `true/false` | `AgentRunState` |
| `AgentRunState.paused` | `true/false` | `AgentRunState` |
| `AgentRunState.status` | `"idle"`, `"planning"`, `"acting"`, `"verifying"`, `"paused"`, `"done"`, `"partial"`, `"error"` | `AgentRunState` |
| `AgentRunState.outcome` | `SessionOutcome.*` (`success`, `partial`, `failure`, `cancelled`, `budget_exhausted`, `quota_exhausted`, `time_exhausted`, `permission_denied`, `remote_blocked`) | `AgentRunState` |
| `AgentRunState.termination` | `RunTermination` enum | `AgentRunState` |
| `AgentRuntime.paused` | `true/false` | runtime (drives loop suspension) |
| `AgentRuntime.cancelRequested` | `true/false` | runtime (drives loop exit) |
| `AgentRuntime.job` | `Job?` | runtime (coroutine handle) |

**Transitions**

```
idle
  └─ submit(goal) ───────────────► planning
planning
  ├─ tool call ──────────────────► acting
  ├─ final answer ───────────────► done | partial | error
  └─ remote blocked ─────────────► error (never enters running)
acting
  ├─ mutating result ────────────► verifying
  └─ non-mutating result ────────► planning
verifying
  └─ next model request ─────────► planning
any running phase
  ├─ pause() ────────────────────► paused
  ├─ stop() ─────────────────────► cancelling → cancelled
  ├─ budget stopKind ────────────► partial | quota_exhausted | time_exhausted | failure
  └─ throwable ──────────────────► error
paused
  └─ resume() ───────────────────► planning
terminal
  └─ dismiss()/new submit ───────► idle
```

**Why it is implicit**

- `running=false` can mean *idle*, *done*, *error*, or *never started*.
- `paused=true` is valid while `running=false` (after `stop()`), which makes `displayStatus`
  return `"paused"` for a non-running run.
- `status` is a free string; nothing rejects `"acting"` with `paused=true`.
- `outcome` and `termination` are derived from each other by a `when` ladder
  (`RunReport.terminationFor`), not by construction.
- The same lifecycle is mirrored in `ActiveRunRegistry` (see 2.2), `StatusNarrator` (2.6),
  `RunLedger.state.outcome`, and the session trace (5.1).

**Recommended extraction**

```kotlin
sealed interface RunState {
    data object Idle : RunState
    data class Planning(val step: Int) : RunState
    data class Acting(val step: Int, val tool: String) : RunState
    data class Verifying(val step: Int) : RunState
    data class Paused(val previous: RunState) : RunState
    data class Terminated(val report: RunReport) : RunState
}
```

Keep `pause()`/`resume()`/`stop()` as the only mutators and assert illegal transitions.

---

### 2.2 Active run registry

**Where:** `app/src/main/java/com/aura/aura_ui/agent/runtime/ActiveRunRegistry.kt`

**States:** `running`, `paused`, `cancelling`, plus the implicit "removed/absent".
**Variables:** `status: String`, `paused: Boolean`, `cancelled: Boolean`, `currentRunId`.

**Transitions**

```
start(id) ─► running
update(paused=true, status="paused") ─► paused
update(paused=false, status="running") ─► running
update(status="cancelling", cancelled=true) ─► cancelling
finish(id) ─► (removed)
```

**Risks:** `status`, `paused`, and `cancelled` can disagree; `finish` is the only authoritative
removal, and `currentRunId` falls back to an arbitrary `runs.keys.firstOrNull()` when the current
run is removed while another exists.

---

### 2.3 Run budget / stop kind

**Where:** `app/src/main/java/com/aura/aura_ui/agent/ledger/RunBudget.kt`

**States:** `within budget`, `STEPS`, `TIME`, `REQUESTS`, `FAILURES`.
It is a pure predicate over counters (`requests`, `consecutiveFailures`, `elapsedMs`).

**Transitions**

```
within ── steps >= maxSteps ─────────────► STEPS
within ── elapsed >= maxWallClockMs ─────► TIME
within ── requests >= maxModelRequests ──► REQUESTS
within ── failures >= maxConsecutive ────► FAILURES
any stop ── mapped in AgentRuntime ──────► SessionOutcome / RunTermination
```

**Risk:** the mapping from `BudgetStopKind` to `Termination` is duplicated in
`AgentRuntime` and `RunReport.terminationFor`, so they can drift.

---

### 2.4 Run termination taxonomy

**Where:** `app/src/main/java/com/aura/aura_ui/agent/report/RunReport.kt`,
`app/src/main/java/com/aura/aura_ui/data/session/SessionLogStore.kt`

Two parallel vocabularies describe the same terminal state:

- `SessionOutcome` string constants.
- `RunTermination` enum (`SUCCESS`, `PARTIAL`, `FAILURE`, `CANCELLED`, `QUOTA_EXHAUSTED`,
  `BUDGET_EXHAUSTED`, `TIME_EXHAUSTED`, `PERMISSION_DENIED`, `REMOTE_BLOCKED`).

`RunReport.terminationFor` is a hand-written mapping, and `RunLeader.finish` stores the raw
outcome string. This is effectively a state machine with two encodings.

---

### 2.5 Checkpoint / resume

**Where:** `app/src/main/java/com/aura/aura_ui/agent/checkpoint/RunCheckpointStore.kt`,
`AgentRuntime.resumeLatestCheckpoint()`

**States:** no in-memory state, but the persisted checkpoint lifecycle is:

```
run active ── pause/error/budget/cancel ──► checkpoint written
clean success ─────────────────────────────► checkpoint cleared
app restart ── latest() exists ────────────► resumed run (RunEntryMode.RESUMED)
```

`RunEntryMode` (`FRESH`, `SAVED_SETTINGS`, `RESUMED`) is the only explicit part; the
"has a checkpoint vs has not" state is implied by file existence and `latest()`.

---

### 2.6 Plan-step status

**Where:** `app/src/main/java/com/aura/aura_ui/agent/ledger/RunLedger.kt` (`PlanStep`, `setPlan`,
`updateStep`), rendered in `presentation/components/TraceAtoms.kt`.

**States:** `pending`, `in_progress`, `done`, `blocked`, `skipped` (documented in a comment only).

**Risk:** `updateStep(id, status, note)` stores any string. The UI's `when` falls through to
`PENDING` for unknown values, so a typo silently regresses a step. `setPlan` also resets all
existing steps, which can lose progress on a repeated plan.

---

### 2.7 Status narration

**Where:** `app/src/main/java/com/aura/aura_ui/agent/runtime/StatusNarrator.kt`

Free-form `status` + `detail` strings: `"idle"`, `"starting"`, `"planning"`, `"acting"`,
`"verifying"`, `"paused"`, `"resuming"`, `"stopping"`. This is a **second** run-state machine
that is not synchronised with `AgentRunState.status`.

---

### 2.8 Control lock

**Where:** `app/src/main/java/com/aura/aura_ui/agent/control/ControlLock.kt`

**States:** `unlocked`, `paused`, `holding` (two independent booleans). `canAutomateMutations`
is `!paused && !holding`. Diagnostics were added, but there is still no single state; both flags
can be true and the "reason" is a free string.

---

## 3. Human-in-the-loop / approval

### 3.1 Confirmation broker

**Where:** `app/src/main/java/com/aura/aura_ui/agent/AgentState.kt` (`ConfirmationBroker`)

**States:** `idle`, `pending(request)`, `approved`, `denied`, `cancelled`.
**Variables:** `_pending: MutableStateFlow<ConfirmationRequest?>`,
`waiters: MutableMap<String, CompletableDeferred<Boolean>>`.

**Transitions**

```
idle ── request(title, detail, tool) ──► pending
pending ── respond(id, true) ──────────► approved
pending ── respond(id, false) ─────────► denied
pending ── cancelAll() ────────────────► denied (all)
pending ── timeout in caller ──────────► (caller-side) denied
```

**Risks:** more than one waiter can be created for the same id; `request()` overwrites
`_pending` even if another request is outstanding (the UI shows only the last one); a response for
an old id is silently ignored, leaving the old waiter blocked until its caller times out.

---

### 3.2 Ask-user broker

**Where:** `app/src/main/java/com/aura/aura_ui/agent/AskUserBroker.kt`

**States:** `idle`, `asked`, `answered`, `dismissed`, `timed_out`.
`ask()` refuses a second question by returning a string instead of a typed error.

**Transition:** `ask()` sets `_pending`, posts a notification, awaits a `CompletableDeferred`,
then always clears in `finally`. The terminal state is not exposed; callers infer it from the
returned string.

---

### 3.3 Notification approval gate

**Where:** `app/src/main/java/com/aura/aura_ui/agent/NotificationApprovalGate.kt`

An adapter over `ConfirmationBroker`:

```
request ─► post notification ─► await 120s ─┬─ answer ─► approved/denied
                                             └─ timeout ─► denied
                                          └─ cancel notification
```

The 120 s "timeout = deny" rule and the notification id are the only state markers.

---

### 3.4 Run action receiver

**Where:** `app/src/main/java/com/aura/aura_ui/notifications/RunActionReceiver.kt`

Stateless intent dispatcher that drives run transitions:
`TOGGLE_PAUSE` → `pause()`/`resume()`, `STOP_RUN` → `stop()`,
`MCP_APPROVE`/`MCP_DENY` → `confirmation.respond()`.

**Risk:** it toggles based on `runtime.state.value.paused`; if that flag is stale, a tap can
resume a run that is terminating or pause an already-stopped run.

---

## 4. Voice, wake word and overlay

### 4.1 VoiceEngine

**Where:** `app/src/main/java/com/aura/aura_ui/voice/VoiceEngine.kt`

**States:** `idle`, `listening`, `success`, `error`, `cancelled`, plus the TTS sub-state
`ttsReady`.
**Variables:** `owner: AtomicBoolean`, `_listening`, `_speaking`, `ttsReady`, nullable
`recognizer`/`tts`.

`listen()` uses `owner.compareAndSet(false, true)` as a mutex and releases it in `finish()` or
`invokeOnCancellation`. A missed release would permanently disable voice.

### 4.2 Wake-word service

**Where:** `app/src/main/java/com/aura/aura_ui/services/WakeWordListeningService.kt`

**States:** `stopped`, `starting`, `detecting`, `pausedForRun`, `stopping`.
**Variables:** `job`, `engine`, `recording`, `pausedForRun`.

```
start ─► startDetection ─► detecting (recording=true)
detecting ── keyword hit ─► pausedForRun=true ─► AssistantForegroundService.listen()
          └─ job ends ─► delay(25s) ─► pausedForRun=false ─► detecting
detecting ── model missing / AudioRecord init fails ─► stopSelfSafely()
```

**Risks:** `recording` and `pausedForRun` are separate booleans; `onDestroy` does not reset
`recording`. The 25 s resume delay is a timing assumption, not a state handshake with the
foreground service.

### 4.3 Foreground service

**Where:** `app/src/main/java/com/aura/aura_ui/services/AssistantForegroundService.kt`

**States:** `created`, `running` (with a goal), `listening`, `paused`, `stopped`.
The goal intent and action intents are the transition inputs; the run state itself lives in
`AgentRuntime`.

### 4.4 Overlay pill

**Where:** `app/src/main/java/com/aura/aura_ui/overlay/OverlayPillView.kt`

**States:** `idle`, `listening`, `speaking`, with `status` + `transcript` strings.
The visual state is derived on every `onDraw` from `listening`/`speaking`; there is no
transition object, and a stale `update()` call can leave the dot in the wrong colour.

### 4.5 MediaProjection service

**Where:** `app/src/main/java/com/aura/aura_ui/services/MediaProjectionService.kt`

**States:** `created`, `foreground(mediaProjection)`, `stopped`. `onStartCommand` always enters
the foreground state; there is no error path (it is wrapped in `runCatching` by callers).

---

## 5. Screen, accessibility and perception

### 5.1 Screen capture consent + capture

**Where:** `app/src/main/java/com/aura/aura_ui/accessibility/ScreenCaptureManager.kt`

**States:** `no_consent`, `consent_requested`, `granted`, `capturing`, `released`.

**Variables:** `projection: MediaProjection?`, `_consentRequested: MutableStateFlow<Int>`,
`mutex`.

```
no_consent ── requestConsent() ─► consentRequested++ (UI collects it)
consentRequested ── handleConsentResult(OK, data) ─► granted
consentRequested ── handleConsentResult(not OK) ─► no_consent
granted ── captureToFile() ─► capturing ─► granted
granted ── release()/onStop() ─► no_consent
```

**Risks:** the consent request is an incrementing counter, so it cannot represent "request in
flight"; a second request while one is pending is indistinguishable. `projection` is mutated
without the same lock as `captureToFile`.

### 5.2 Screen generation / last-seen cache

**Where:** `app/src/main/java/com/aura/aura_ui/accessibility/ScreenModel.kt`
(`ScreenGeneration`, `ScreenState`), `mcp-server/.../cache/PerceptionCache.kt`,
`LastSeenScreen.kt`.

**States:** `no snapshot`, `fresh snapshot (generation N)`, `stale snapshot (generation < N)`.
Generation is a monotonic counter bumped by accessibility events (debounced 90 ms). Staleness is
inferred by comparing generations/signatures, not by a state object.

### 5.3 Perception tiering

**Where:** `app/src/main/java/com/aura/aura_ui/agent/strategy/PerceptionTiering.kt`

**States:** `TREE_ONLY`, `FULL`, chosen by a pure decision table over seven signals. This one is
already close to explicit; the missing piece is persistence of "current tier" per step.

### 5.4 Accessibility service

**Where:** `app/src/main/java/com/aura/aura_ui/accessibility/AuraAccessibilityService.kt`

**States:** `disconnected`, `enabled but disconnected`, `connected`.
`instance` nullable is the source of truth; `ScreenGeneration` is reset on connect and cleared on
destroy. `ScreenState.lastPickedPackage` is a separate cache.

### 5.5 Keyboard lease

**Where:** `app/src/main/java/com/aura/aura_ui/accessibility/KeyboardLease.kt`

**States:** `released`, `held(by)`. It is a single `leaseHolder` string with **no reference
count**, so nested acquires can release someone else's lease.

---

## 6. Browser automation

**Where:** `app/src/main/java/com/aura/aura_ui/browser/BrowserActivity.kt`
(`BrowserController`)

**States:** `detached`, `attached`, `loading`, `loaded`, `handoff`, `destroyed`.

**Variables:** `activityRef`, `webViewRef`, `pageWaiter`, `currentUrl`, `generation`,
`handoffActive`.

```
detached ── attach() ─► attached
attached ── open()/loadUrl ─► loading (pageWaiter set)
loading ── onPageFinished() ─► loaded (generation++, waiters cleared)
loaded ── handoff() ─► handoffActive=true (act() refuses mutations)
handoff ── clearHandoff() ─► loaded
any ── detach()/destroy ─► detached
```

**Risk:** `pageWaiter` is a single slot; a second `open()` before the first finishes overwrites
the waiter and leaves the first coroutine suspended until cancellation.

---

## 7. MCP / LAN / pairing

### 7.1 MCP server controller

**Where:** `mcp-server/src/main/kotlin/com/aura/mcp/McpServerController.kt`,
`McpServerHealth.kt`

**States:** `stopped`, `starting`, `running_http`, `running_webrtc`, `error`, `stopped`.

**Variables:** `server: HttpMcpServer?`, `webRtcTransport: WebRtcTransport?`,
`_health: MutableStateFlow<McpServerHealth>`.

`isRunning` is `server != null || webRtcTransport != null`. `start()` returns early only if the
HTTP server is non-null, so HTTP and WebRTC can both be started. `stop()` stops both. `health.running`
can be true while one transport is null, and `lastError` is sticky.

### 7.2 PIN gate lockout

**Where:** `mcp-server/src/main/kotlin/com/aura/mcp/lan/PinGate.kt`

**States:** `active`, `wrong(triesLeft)`, `locked(retryAfterMs)`.

```
active ── wrong ─► wrong (failures++)
wrong ── failures >= 5 ─► locked (exponential backoff, new PIN)
locked ── clock passes lockedUntil ─► active
active ── correct PIN ─► active with a fresh PIN
```

`lockLevel` is never reset except on a successful PIN, so the backoff survives long idle periods.
`onPinChanged` is a side-channel callback, not a state event.

### 7.3 WebRTC peer handshake

**Where:** `mcp-server/src/main/kotlin/com/aura/mcp/bridge/WebRtcTransport.kt`

**States (per `Peer`):** `new`, `offer_answered`, `init_seen`, `challenge_sent`,
`proof_received`, `sas_sent`, `revealed`, `enroll_sent`, `token_received`, `approved`, `dead`.
**Variables:** `approved`, `initSeen`, `challenge`, `sas`, `enroll`, `client`, `dead`,
`gathered`, plus the outer `active`/`pending` slots.

```
onConnect ─► new ─► answerOffer ─► offer_answered
datachannel open ─► init ─► init_seen
  ├─ token auth ─► challenge ─► proof ─► approved
  └─ PIN auth   ─► sas ─► reveal ─► enroll ─► token ─► approved
approved ─► JSON-RPC bytes
any ─► close ─► dead
```

**Risks:** this is a security boundary encoded as seven independent nullable fields. An out-of-order
`reveal`/`enroll`/`proof` is handled by whatever `when` branch happens to run; there is no
per-peer state enum or "must be in state X" assertion. `replacePending` can close a peer that is
already approved if its `pending` slot was never cleared.

### 7.4 Tool execution pipeline

**Where:** `mcp-server/src/main/kotlin/com/aura/mcp/tools/McpToolRegistry.kt`

**States:** `received`, `scope_checked`, `paused`, `policy_blocked`, `foreground_blocked`,
`approval_pending`, `approved`, `denied`, `otp_blocked`, `guard_blocked`, `executing`,
`settled`, `done`, `failed`.

Each gate is an early `return fail(...)`; the state is the position in the function. `run_steps`
re-enters the same pipeline for each nested tool, so a batch is a nested state machine.

**Risk:** there is no record of "which gate rejected" beyond the error code; a new gate inserted
in the wrong order changes the security semantics silently.

### 7.5 Remote gate

**Where:** `app/src/main/java/com/aura/aura_ui/firebase/RemoteGate.kt`

**States:** `unknown`, `ok`, `update_recommended`, `update_required`, `suspended`,
`blocklisted`, `kill_switched`, `offline_cached`.

`GateState` is a data class of booleans (`blocked`, `updateRecommended`, `updateRequired`,
`suspended`, `blocklisted`), and `refresh()` composes them. `blocked = killed || suspended ||
blocklisted` is recomputed; `updateRequired` is duplicated in two fields
(`updateRecommended` and `updateRequired` are assigned the same value).

---

## 8. Onboarding, settings and eval

### 8.1 Onboarding wizard

**Where:** `app/src/main/java/com/aura/aura_ui/presentation/onboarding/OnboardingScreen.kt`,
`AuraSettings.onboardingStep`

**States:** steps `0..9` (`LAST_STEP = 9`) plus `complete`.
Transitions are `go(step ± 1)`, auto-advance when a permission flips (`LaunchedEffect`), and
`completeOnboarding()`.

**Risk:** the persisted integer is the only state; permission readiness is external and polled.
A user can jump back/forward into a step whose prerequisite is no longer granted, and the
auto-advance can fire twice (550 ms delay) if recomposition races.

### 8.2 Eval run

**Where:** `app/src/main/java/com/aura/aura_ui/presentation/AuraViewModel.kt` (`runEvalTask`),
`EvalSuiteScreen.kt`, `data/eval/EvalVerdictRecorder.kt`

**States:** `idle`, `submitted`, `running`, `terminal`, `classified`, `recorded`.

The transition is a coroutine that waits for `runtime.state`:
`first { running && goal match }` → `first { !running && outcome != null }` → `EvalVerdict.classify`
→ `evalRecorder.append`.

**Risks:** if the run finishes before the first predicate observes `running`, the wait times out
(20 s) and the code still waits for a terminal state, which may belong to a different run.
`EvalVerdict.classify` is a pure table, but `expectedRefusal` is inferred from note text.

### 8.3 Firebase sync

**Where:** `app/src/main/java/com/aura/aura_ui/firebase/FirebaseSync.kt`

**States:** `firebase_unavailable`, `not_configured`, `unauthenticated`, `syncing`, `done`,
`partial`, `error`.

`Report(pushed, pulled, skipped, errors, detail, ok)` is the state; `ok = errors.isEmpty()`.
There is no explicit "in progress" state; the UI infers it from the coroutine.

---

## 9. Session trace / persistence

### 9.1 Event-sourced session log

**Where:** `app/src/main/java/com/aura/aura_ui/data/session/SessionLogStore.kt`

**States (event types):** `SESSION_START`, `THOUGHT`, `PLAN`, `TOOL_CALL`, `TOOL_RESULT`,
`VERIFY`, `GATE`, `SETTLE`, `OBSERVE`, `COMPLETION`, `POLICY`, `ANSWER`, `ERROR`, `INTERRUPT`,
`SCREENSHOT`, `SESSION_END`.

This is a genuine event-sourced state machine, but the legal order is not enforced. The
`summarize()` function reconstructs outcome/steps by scanning the file and treats a missing
`SESSION_END` as "interrupted".

### 9.2 Interruption marker

**Where:** `SessionLogStore.writeInterruption/readInterruption/clearInterruption`

**States:** `no_marker`, `marked(sessionId, goal)`, `cleared`.
Written at run start, cleared on every clean end, and read on next launch to show the
"interrupted run" banner. A crash between the two leaves the marker, which is the intended
signal but is indistinguishable from "app killed during a long run".

### 9.3 Install flow

**Where:** `app/src/main/java/com/aura/aura_ui/automation/AppResolver.kt` (`install`)

**States:** `unresolved`, `listing_opened`, `user_installing`, `installed`, `cancelled`.
Only `unresolved` and `listing_opened` exist in code; the rest happen inside the Play Store and
are not observed. The agent can only report "listing opened", never "installed".

---

## 10. Conversation / network

### 10.1 Gemini Live session

**Where:** `app/src/main/java/com/aura/aura_ui/agent/conversation/GeminiLiveConversation.kt`

**States:** `closed`, `connecting`, `connected`, `setup_sent`, `ready`, `turn_complete`,
`closing`, `closed`, `failed`.
**Variables:** `active: AtomicBoolean`, `socket`, `currentConfig`, and `LiveEvent.Status(state)`
strings.

**Risk:** `active` is set on WebSocket open, before `setupComplete`; `sendUserText` only checks
`socket != null`, so it can send before the session is ready.

### 10.2 B2 backup

**Where:** `app/src/main/java/com/aura/aura_ui/b2/B2Service.kt`

**States:** `session_ended` → `upload_queued` → `uploaded` / `failed`. The upload is fire-and-forget
(`scope.launch { runCatching { ... } }`), so failures are only visible in logs.

---

## 11. UI-derived state (worth naming but low risk)

- `TraceAtoms.outcomeTone/outcomeLabel` map outcome strings to colours/labels.
- `ChipTone` (`Idle`, `Live`, `Broken`, `On`) is a visual state, not domain state.
- `RunReportCard` renders markdown built from `RunReport`.
- `AgentCursorState(x, y)` is animation state, not a state machine.
- `RunTodoList` derives `doneCount`/`activeStep` from `PlanStep.status` strings.

---

## 12. Cross-cutting problems

1. **Multiple sources of truth for the same state.**
   `AgentRunState.status`, `AgentRunState.outcome`, `RunLedger.state.outcome`,
   `ActiveRunRegistry.status`, `StatusNarrator.status`, and `SessionOutcome` all describe the run.

2. **Free-form strings instead of enums.**
   `AgentRunState.status`, `PlanStep.status`, `StatusNarrator.status`, `ActiveRunRegistry.status`,
   `EvalVerdictRecord.detail`, `LiveEvent.Status.state`, `RunHandle.status`.

3. **Boolean pairs where one enum belongs.**
   `running`+`paused`, `paused`+`holding`, `recording`+`pausedForRun`,
   `approved`+`initSeen`+`dead`, `updateRecommended`+`updateRequired`.

4. **Counters used as events.**
   `ScreenCaptureManager.consentRequested` (Int counter) is really a request token.

5. **Timing instead of handshakes.**
   Wake word resumes after a fixed 25 s; approvals deny after 120 s; eval waits 20 s for
   `running`.

6. **Nullable slots as connection state.**
   `BrowserController.pageWaiter`, `AskUserBroker.reply`, `WebRtcTransport.pending/active`.

7. **No transition guards.**
   Almost none of these state machines reject an illegal transition. `ConfirmationBroker.request`
   can overwrite a pending request; `RunLedger.updateStep` can set any status;
   `WebRtcTransport` accepts messages in any order.

---

## 13. Suggested extraction order

| Order | Extract | Type | Why first |
|---|---|---|---|
| 1 | Agent run lifecycle | `sealed interface RunState` | Highest fan-out; removes 5 duplicate sources |
| 2 | Plan-step status | `enum class PlanStepStatus` | Trivial, immediate UI correctness |
| 3 | Approval state | `sealed interface ApprovalState` | Security/correctness; prevents orphan waiters |
| 4 | WebRTC peer handshake | `enum class PeerPhase` + transition function | Security boundary |
| 5 | MCP server state | `enum class McpServerState` | Prevents HTTP+WebRTC double-start |
| 6 | Voice + wake word | `enum class VoiceSessionState`, `WakeWordState` | Microphone privacy |
| 7 | Screen capture consent | `sealed interface CaptureState` | Removes counter-as-state |
| 8 | Browser controller | `enum class BrowserState` | Removes single-waiter race |
| 9 | Firebase sync / eval run | `enum class SyncState` / `EvalRunState` | Observable progress |
| 10 | Status strings | Replace with derived strings from the enums above | Removes the duplicate narrator |

The general fix is mechanical:

```kotlin
enum class X { A, B, C }
fun X.next(event: Event): X = when (this) { ... } // throw on illegal
```

and make the **state type the only mutable field**, with everything else derived.
