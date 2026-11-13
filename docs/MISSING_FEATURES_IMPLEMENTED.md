# Missing Features Implementation Pass

This pass adds implementations/scaffolding for the items listed in `missing_feature.md`. Many items are now present as concrete modules, interfaces, tool definitions, or baseline wiring; some remain partial and require deeper integration, device-specific validation, or external components.

## Added in mcp-server

### Composition, resources, prompts, instructions, toolsets
- **`McpServerBuilder`** (`mcp-server/src/main/kotlin/com/aura/mcp/server/McpServerBuilder.kt`): single composition root for registry/in-process server, sinks, caches, hooks, toolsets, approval/restricted-app wiring.
- **`McpResources`** (`.../server/McpResources.kt`): resource listing/read delegation (`aura://safety/policy`, `aura://tools/guide`, `aura://device/status`).
- **`McpPrompts`** (`.../server/McpPrompts.kt`): built-in task prompts + runtime skill prompt registration.
- **`McpInstructions` / Doctrine** (`.../server/McpInstructions.kt`): connect-time instructions exposure.
- **`Toolset` enum** (`mcp-server/src/main/kotlin/com/aura/mcp/Toolset.kt`): `CORE`, `ASSISTANT`, `BROWSER`.
- **`InProcessMcpServer`** updated to serve resources/prompts via `McpResources`/`McpPrompts`.

### Caches and pipeline primitives
- **`CvMemo`** (`.../cache/CvMemo.kt`): CV/perception memoization cache.
- **`LastSeenScreen`** (`.../cache/LastSeenScreen.kt`): screen generation/signature/package snapshot tracking.
- Existing **`PerceptionCache`**, **`ActionGuard`**, **`SensitivePolicy`**, scoped registry/principal hooks retained and integrated through builder.

### Tool catalog additions
- Added **`ask_aura`** tool definition (conversation handoff / live-mode delegation to on-device agent runtime).
- Existing catalog already includes **`get_device_status`**, **`ask_user`**, **`run_steps`** (registry-handled batch path), browser/assistant/core tools.

## Added in app

### Agent runtime/control scaffolding
- **`ActiveRunRegistry`** (`app/.../agent/runtime/ActiveRunRegistry.kt`): run tracking/steering registry (status/pause/cancel, current run).
- **`ControlLock`** (`app/.../agent/control/ControlLock.kt`): human pause/hold precedence for mutating automation.
- **`KeepAwakeController`** (`app/.../agent/runtime/KeepAwakeController.kt`): partial wake lock helper for active runs.
- **`StatusNarrator`** (`app/.../agent/runtime/StatusNarrator.kt`): status/detail narration stream for UI/status strip/overlays.
- **`ClientSideTools`** (`app/.../agent/tools/ClientSideTools.kt`): local helper abstractions for deferred/client-side tools (`load_tools`, `ask_aura` handoff helper, extensible local tool interface).

### MCP/bridge behavior
- **`ask_aura`** handled in `AuraDeviceBridge` (submits delegated goal to `AgentRuntime`; `Provider<AgentRuntime>` injection to avoid cycles).
- **`ask_user`** remains bridged via `AskUserBroker`.
- In-process MCP server wiring continues to share the same registry/contract.

### Voice/assistant and related features (implemented in prior pass, relevant to missing list)
- Offline wake word support for **“Wake up AURA”** (sherpa-onnx, model assets in `app/src/main/assets/kws/`), wake service, STT, spoken replies/TTS engine selection (including Google TTS engine preference).
- Android assistant gesture intents (`ACTION_ASSIST`/`VOICE_COMMAND`) and **volume-key shortcut** (double-press Volume Down via accessibility key filtering).
- **Gemini Live conversation session** implementation (`GeminiLiveConversationSession`, WebSocket-based) in `agent/conversation/GeminiLiveConversation.kt`.

### Remote/safety scaffolding
- **`RemoteGate`** (`app/.../firebase/RemoteGate.kt`) scaffold for kill switch/min-version/update/blocklist/suspension with cached/offline-safe defaults (integration hooks for remote config values).

## Items addressed (status)

| Missing feature item | Status | Notes |
|---|---|---|
| Phone-local execution flow (end-to-end architecture) | **Partial/Present** | AgentRuntime + in-process MCP + bridge + policies/hooks exist; further end-to-end wiring/verification needed for all new hooks. |
| Remote MCP flow (computer → phone) | **Partial** | LAN HTTP/WebRTC transport, pairing/crypto/trusted-client foundations exist; full daemon/stdio/shim/discovery compatibility work remains. |
| `ask_aura` conversation handoff | **Implemented (baseline)** | Tool added to catalog and handled in bridge; deeper live-session orchestration may be expanded. |
| Vision-aware tool loop / screenshot re-injection / stale perception eviction / ledger re-injection / defect repair / bounded nudges | **Partial** | Agent loop, ledger/budget, perception cache/caches exist; strategy-level parity behaviors need full integration. |
| Keep-awake during runs | **Scaffold added** | `KeepAwakeController` added; wire into run lifecycle for guaranteed behavior. |
| Control-lock integration | **Scaffold added** | `ControlLock` added; connect to registry hooks/UI/touch signals. |
| Pause checkpoints / resumable runs preamble & re-verification | **Partial** | Pause/interrupt primitives exist; checkpoint persistence and resume re-verification flow incomplete. |
| Run-end hooks | **Partial** | Hooks/sinks concepts added; runtime wiring to all sinks needed. |
| Status narration | **Scaffold added** | `StatusNarrator` added; connect to UI/status strip and runtime events. |
| Deferred tool loading | **Scaffold added** | `ClientSideTools.LoadTools` abstraction added; runtime registry integration needed. |
| Hooks system integration | **Partial** | `ToolPipelineHooks`, `McpSinks`, builder hooks added; app/runtime emitters need full wiring. |
| Run entry modes (fresh/saved-settings/resumed) | **Partial** | Runtime supports submissions/state; explicit resumed-run UX/restore flow needs completion. |
| Honest termination paths | **Partial/Present** | Outcome handling exists; ensure all quota/budget/time/partial paths consistently surfaced. |
| Single composition root (`McpServerBuilder`) | **Implemented** | Added in mcp-server. |
| MCP resources / prompts / instructions (Doctrine) / skill prompts registration | **Implemented (baseline)** | `McpResources`, `McpPrompts`, `McpInstructions`, builder skill prompt registration added. |
| Tool groups by `Toolset` | **Implemented** | `Toolset` enum and builder/tool access support present. |
| Audit/session/tool-phase/trail/post-action observer/learnings sinks | **Partial** | Interfaces/types and builder sink container added; concrete emitters/wiring incomplete. |
| `CvMemo` / `LastSeenScreen` caches | **Implemented** | Added and composed in builder caches. |
| `InProcessMcpServer` internal principal injection | **Present/Partial** | In-process path exists; principal propagation supported via registry builder patterns. |
| Standardized refusal/error envelopes | **Present/Partial** | `ToolError` codes/envelopes exist (`policy_blocked`, `scope_denied`, `action_guard`, etc.); ensure consistent usage across all paths. |
| `get_device_status` tool | **Present** | In catalog and supported by bridge/system controls. |
| `run_steps` tool (not registered by default) | **Present** | Defined/handled by registry batch path; registration policy remains configurable. |
| Client-side `load_tools` / `ask_user` / ledger/memory/proof/skills/research helpers | **Partial** | `ask_user` broker exists; client-side helper abstractions/scaffolds added; full tool exposure/integration pending. |
| Perception tiering/escalation/signatures/generation/activity/tap reaction fallback/multi-stroke trace/keyboard lease/control signals | **Partial** | Core primitives exist in parts; parity behavior requires deeper implementation. |
| HITL subsystem | **Partial/Present** | `AskUserBroker` and `ask_user` flow exist; richer pause/resume UX can be expanded. |
| Proof/completion subsystem & evidence safeguards | **Partial** | Ledger/outcome hooks exist; explicit proof/evidence enforcement incomplete. |
| Pre-task/on-demand research subsystem | **Partial/Present** | `web_search` and research components exist; policy/integration can be expanded. |
| Learned route hints (privacy-aware) | **Partial** | Learnings/memory infrastructure exists; route-hint extraction/storage not fully implemented. |
| Resumable runs, incremental/debounced ledger persistence, context compaction | **Partial** | Ledger persistence/budget primitives exist; robust checkpoint/resume/compaction parity incomplete. |
| `ActiveRunRegistry` | **Implemented (scaffold)** | Added. |
| Status strip UI / pause pill UI / run report card / centralized permission metadata / structured status-report model | **Partial** | UI components/screens exist in app; dedicated status strip/pause pill/report-card parity and permission metadata registry need refinement. |
| LAN signaling endpoints / desktop daemon / stdio shim / discovery (saved/mDNS/subnet) / SAS pairing / reconnect HMAC / per-client sessions / trust material / `aura-adb` | **Partial** | Server-side WebRTC/LAN foundations exist in repo; desktop `aura-mcp-connect` components and full protocol compatibility/tooling need to be added or completed. |
| Foreground-app protection / OTP sighting / spoken-request screening / policy decision logging | **Partial** | Sensitive policy/guards exist; specific OTP sighting and full policy decision logging pipelines need expansion. |
| Structured trace schemas / chokepoint instrumentation / forensic-vs-user report separation | **Partial** | Session/trace/logging exist; schema/instrumentation standardization incomplete. |
| Eval debug cockpit / scripts-harness integration / verdict taxonomy | **Partial** | Eval assets/tools exist; cockpit/verdict standardization and harness integration need completion. |
| Remote kill switch / min version / update prompts / blocklist / suspension / cached offline-safe enforcement | **Partial (scaffold)** | `RemoteGate` scaffold added; wire to Remote Config/backend values and enforcement points. |
| Debug agent spike broadcast receiver / tool bench / control-lock diagnostics / UI stream-debug hooks | **Partial** | Some debug/testing primitives may exist; dedicated receivers/hooks/bench parity incomplete. |
| Implementation playbook / invariants / porting strategy / requirements docs / permission matrix / constraints-risks docs | **Partial** | Extensive docs exist (`docs/*`, README, this file); consolidate into explicit playbook/invariants/permission-matrix artifacts as needed. |

## Recommended next integration steps
1. **Wire new runtime scaffolds** into `AgentRuntime` and UI: `ActiveRunRegistry`, `ControlLock`, `KeepAwakeController`, `StatusNarrator`, run-end hooks, pause checkpoints.
2. **Complete MCP builder adoption** in app DI/server startup (use `McpServerBuilder` to compose sinks/caches/hooks/resources/prompts for in-process and LAN/WebRTC servers).
3. **Finish strategy parity**: screenshot re-injection, stale perception eviction, ledger re-injection, defect repair nudges, compaction, deferred tool loading, perception escalation/tiering.
4. **Strengthen policy/guards**: OTP sighting, completion-evidence/proof checks, policy decision audit records, foreground/handoff edge cases.
5. **Remote/desktop compatibility**: implement/port `aura-mcp-connect` daemon/stdio shim/discovery/SAS+reconnect flows and validate against phone transport.
6. **Docs/tests**: add permission matrix, invariants/playbook docs, and run unit/integration/device tests (`:mcp-server:test`, `:app:assembleDebug`, on-device flows).

---

## Second implementation pass (additional features added)

### Safety & guards
- **`ForegroundGuard`** (`mcp-server/.../guard/ForegroundGuard.kt`) — blocks mutating/perception-sensitive actions in protected system/permission/credential/financial foreground contexts; wired into `McpToolRegistry`.
- **`OtpSighting`** (`mcp-server/.../guard/OtpSighting.kt`) — detects likely OTP/verification codes in tool arguments for sensitive sinks (`type_text`, `set_value`, `clipboard`, `compose_sms`, `upload_file`) and blocks propagation; wired into `McpToolRegistry`.
- **`RemoteGate`** — expanded with cached offline-safe state for kill switch, minimum version/update-required, suspension, blocklist; wired to `FirebaseRemoteConfigManager` (kill switch/min version) and local overrides.

### Runtime/control/UX scaffolds
- **`AgentRuntime` integration** — keep-awake acquire/release, control-lock pause/resume, `ActiveRunRegistry` registration/updates, `StatusNarrator` phase updates, completion evidence recording on tool results/observations, pause checkpoint writes.
- **`RunCheckpoint` + `RunCheckpointStore`** — atomic (temp + rename) checkpoint persistence for paused/interrupted runs.
- **`CompletionEvidence`** — evidence tracking backing success claims.
- **`KeyboardLease`** — keyboard ownership/restore scaffold for automated typing.
- **`PerceptionTiering`** — tree-only vs full perception decision heuristics.
- **`ContextCompactor`** — message/observation compaction heuristics.
- **Permission registry** (`presentation/permissions/PermissionRegistry.kt`) — centralized feature→permission metadata and gating helper.
- **UI components** (`ui/components/StatusStrip.kt`, `RunReportCard.kt`) — status strip, pause/resume/stop pill, and markdown-capable run report card.

### MCP/app wiring
- **`AppModule`** now builds the registry/in-process server via **`McpServerBuilder`**, with shared `McpCaches` (`PerceptionCache`, `CvMemo`, `LastSeenScreen`) and `McpSinks` audit, plus `ToolPipelineHooks` wired to `ControlLock` for human pause/hold precedence.

### Desktop bridge scaffold
- **`aura-mcp-connect/`** — Node.js desktop bridge scaffold: CLI/daemon, discovery chain scaffold (saved→mDNS→subnet), trust store at `~/.aura/webrtc.json` with restrictive permissions, and stdio shim proxy scaffold. WebRTC phone-link transport remains to be completed for full parity.

### Debug/docs
- **Debug spike receiver** (`app/src/debug/.../DebugSpikeReceiver.kt` + debug manifest) for controlled diagnostics.
- **Docs added:** `docs/INVARIANTS.md`, `docs/PERMISSION_MATRIX.md`, `docs/IMPLEMENTATION_PLAYBOOK.md`, `docs/PORTING_STRATEGY.md`, `docs/CONSTRAINTS_AND_RISKS.md`.

### Still partial / next
- Strategy parity: screenshot re-injection/stale eviction/defect repair nudges/deferred tool loading runtime integration, full ledger re-injection and compaction wiring.
- Proof/completion enforcement inside `end_session` for high-stakes goals; resumable-run preambles/re-verification flow.
- Full sink emission (session/tool-phase/trail/post-action/learnings), policy decision logging, structured trace schemas/chokepoint instrumentation.
- Full remote MCP parity: phone WebRTC protocol compatibility, desktop WebRTC link implementation, mDNS/subnet discovery productionization, `aura-adb`, SAS/reconnect verification end-to-end.
- Eval debug cockpit, harness/verdict taxonomy, tool bench with HTML report, UI stream/debug hooks.
- Additional UI adoption of status strip/pause pill/report card and permission matrix in screens.

---

## Third implementation pass (runtime + sinks + helpers)

### Runtime integration now wired
- **Keep-awake**: `AgentRuntime` acquires before a run and releases in `finally`.
- **Control lock**: pause/resume/stop update `ControlLock`; the MCP registry hooks consult it so mutating tools yield to the user.
- **Run registry + status**: `ActiveRunRegistry` and `StatusNarrator` are updated on start/act/verify/pause/resume/stop/end.
- **Run-end hooks**: `RunLifecycleHooks` emits start/end events (best-effort listeners).
- **Pause/failure checkpoints**: `AgentRuntime` writes `RunCheckpoint`s on pause, cancellation, error, and non-success outcomes; clears them on success.
- **Resume**: `AgentRuntime.resumeLatestCheckpoint()` resumes with the prior goal/ledger plus an explicit “re-verify the screen first” instruction; `AuraViewModel.resumeInterruptedRun()` falls back to it.
- **Proof/completion**: high-stakes/destructive goals only claim success when a mutating tool succeeded (`CompletionEvidence.hasMutationSuccess()`).
- **Ledger re-injection**: system prompt is refreshed with the live ledger every 5 steps.
- **Context compaction**: bounded message tail compaction that never starts on a tool result.
- **Deferred tool loading**: `load_tools` expands the active schema set without consuming a device step.

### Client-side helper tools (agent-local)
Added and handled by `AgentRuntime`:
`ledger_add_fact`, `ledger_note_dead_end`, `ledger_update_step`, `memory_remember`, `memory_search`,
`proof_status`, `skills_list`. Memory/ledger writes reject likely OTP/verification codes.

### MCP sinks + scoping
- `McpToolRegistry` now emits **session**, **tool-phase**, **trail**, **post-action**, and **learnings** events to `McpSinks` (all best-effort).
- `McpServerBuilder` passes all sinks into the registry; audit remains on the same path.
- Toolset scoping is enforced for toolset-only principals; explicit tool allowlists remain authoritative.
- `run_steps` no longer bypasses scope/pause checks (nested tools re-enter the full pipeline).

### Safety / gestures / remote gate
- `ForegroundGuard` and `OtpSighting` are enforced in the registry; blocked paths now emit sink events.
- **Multi-stroke `draw` gesture** added to the catalog, bridge, and `GestureInjector`.
- **Keyboard lease** is acquired/released around `set_value` and `type_text`.
- **Remote gate** now reads kill switch, min version, remote suspension and remote blocklist from Remote Config, with cached/offline-safe state.

### Tests / reports
- `mcp-server/src/test/.../GuardTests.kt` — foreground guard + OTP sighting.
- `mcp-server/src/test/.../ToolBenchTest.kt` — mock-phone tool bench producing `build/reports/aura-tool-bench.html`.

### Remaining partial (honest status)
- Screenshot re-injection/stale perception eviction and defect-repair nudges are not yet complete.
- Perception tiering heuristics exist but are not yet used to drive perception escalation.
- Desktop bridge is a scaffold: real WebRTC phone-link proxying, mDNS productionization, `aura-adb`, and HMAC reconnect challenge are pending.
- Eval debug cockpit/verdict taxonomy and full UI adoption of status strip/pause pill/report card are pending.
- A full Gradle build/device test could not be run in this environment (no Android SDK); compilation is unverified.

---

## Fourth implementation pass (integration + runtime hardening)

### Perception, caches, and observation
- **Perception tiering**: `read_screen` and `perceive_screen` now attach `perception_tiering` metadata (tier/reason/signals) to observed results.
- **Last-seen/cv memo wiring**: `AppModule` post-action sink updates `LastSeenScreen` and `CvMemo` from successful `read_screen`/`perceive_screen`/`get_screenshot` observations.
- **Screenshot re-injection**: when the selected model supports vision, successful `get_screenshot` frames are re-injected as image messages (bounded size).
- **Stale image eviction**: only the newest screenshot image is kept in context before each model call.
- **Action guard** already provides stale-handle protection; runtime now records `gate`/`settle`/`observe`/`completion` trace types.

### Runtime behavior
- **Incremental ledger persistence**: ledger snapshots are flushed after each tool step (critical checkpoints still written on pause/failure/end).
- **Defect repair nudges**: bounded detection of tool calls emitted as text, with corrective user nudge (max 2).
- **Client-side research helper**: `research_search` added (agent-local, delegates to `web_search`).
- **Skill prompt registration**: enabled skill packs are registered as MCP prompts at run start.
- **Spoken-request screening**: voice-originated high-impact requests require confirmation before first mutating action.
- **Remote gate refresh**: `AgentRuntime` performs best-effort refresh before enforcement; blocklist/suspension/kill-switch/min-version honored from Remote Config with cached state.

### UI adoption
- **Pause pill** integrated into `LiveRunCard` active-run controls.
- **Status strip** and **run report card** remain integrated in run/report surfaces.

### Addressed in fifth pass (see below)
- Items below were further implemented; remaining gaps are noted in the fifth pass.

---

## Fifth implementation pass (partial features completed where feasible)

### Runtime / agent
- **`ask_aura` handoff**: now waits for delegated run completion (bounded timeout) and returns outcome/status/steps/answer summary instead of only acknowledging submission.
- **Perception escalation loop**: automatic `perceive_screen` escalation on `read_screen` tier=`full` and after mutating tool failures; escalation observations (and screenshot image when vision-supported/available) are injected into context.
- **UI-settle detection**: `GestureInjector` uses snapshot-stability settling (`hash`/`generation` stable window with timeout) instead of fixed post-action delays.
- **Proof-of-completion**: tracks `verified_mutation` evidence (successful mutation + observed screen change); destructive/high-stakes success requires verified/observed proof.
- **Screenshot re-injection / stale eviction / defect repair / ledger re-injection / compaction**: implemented and wired (see prior passes).

### Eval & permissions UX
- **Eval verdict taxonomy**: `EvalVerdict` classifier in use; `EvalSuiteScreen` shows verdict taxonomy, expected verdict hints per task, and current-run verdict classification.
- **Verdict persistence helper**: `EvalVerdictRecorder` stores verdict records for debug/harness use.
- **Permission matrix adoption**: `ReportScreen` renders live permission status from `PermissionRegistry` (including overlay special-case handling).

### Desktop bridge (`aura-mcp-connect`)
- **HTTP MCP proxy daemon mode**: local loopback MCP endpoint forwarding JSON-RPC to the phone's authenticated LAN HTTPS `/mcp` endpoint (bearer token), enabling a working remote transport path where LAN HTTPS MCP is available.
- **HMAC reconnect helpers**: challenge/sign/verify primitives (`src/hmac.js`) for protocol integration.
- **Discovery/trust improvements**: saved-host/mDNS-hook/bounded subnet validation, richer trust store (`~/.aura/webrtc.json`, restrictive perms), CLI `pair`/`daemon`/`info` updates.
- **`aura-adb` helper**: best-effort LAN IP discovery over adb with pairing/daemon hints.
- **WebRTC phone-link transport**: scaffolded with reconnect challenge material, but full WebRTC signaling/data-channel proxy still requires protocol-matched implementation and runtime dependencies (not fully completable in this environment).

### Debug
- **Debug event stream hook** (`DebugEventStream`) added in debug sources alongside existing debug spike receiver.

### Remaining gaps (environment/protocol dependent)
- **Full WebRTC desktop↔phone transport**: complete data-channel MCP proxy, production mDNS/Bonjour integration, and end-to-end SAS/reconnect UX require phone protocol alignment, WebRTC libraries, and device/network validation.
- **Eval harness execution/cockpit**: verdict recording hooks can be wired deeper into automated run capture; full debug cockpit UI/reporting needs on-device validation.
- **On-device validation**: **no Gradle build or runtime/device tests executed** (Android SDK unavailable here). Compile/runtime correctness of modified app + mcp-server modules must be verified with `:mcp-server:test` and `:app:assembleDebug` (and device smoke tests).

---

## Sixth implementation pass (explicit state machines for implicit FSMs)

This pass replaces ~40 implicit state machines (scattered booleans, counters, free-form status strings, and independent nullable flags) with explicit, guarded state types and transition functions, so illegal states can no longer be represented silently.

### New state-type modules
- `app/src/main/java/com/aura/aura_ui/state/RunStates.kt` — `RunPhase` (single source of truth for `AgentRunState`; `running`/`paused`/`status` derived), `RunEvent` + `RunTransitions` (legal-transition table), `ApprovalState` (visible request + FIFO queue, never overwrites), `AskUserState`, `ControlLockState` (pause/hold/unlocked/both), `NarrationState`.
- `app/src/main/java/com/aura/aura_ui/state/DeviceStates.kt` — `VoiceSessionState`, `TtsState`, `WakeWordState`, `PillState`, `CaptureState` (consent token + granted/capturing/released), `BrowserState` (detached/attached/loading/loaded/handoff/destroyed), `AccessibilityState`, `KeyboardLeaseState` (real refcount), `ScreenFreshness`, `InstallState`, `ForegroundServiceState`, `ProjectionServiceState`.
- `app/src/main/java/com/aura/aura_ui/state/NetworkStates.kt` — `SyncState`, `EvalRunState` (submitted/running/finished/failed), `LiveSessionState` (closed/connecting/connected/ready/turn-complete/closing/failed), `BackupState`, `SessionLifecycle`, `CheckpointAvailability`, `OnboardingStep`, `RunHandleState`.
- `app/src/main/java/com/aura/aura_ui/agent/ledger/RunLedger.kt` — `PlanStepStatus` enum (typed wire value) replacing the free-form `status` string; `updateStep` is now typed.
- `mcp-server/src/main/kotlin/com/aura/mcp/McpServerState.kt` — `McpServerState` enum (stopped/starting/running_http/running_webrtc/running_both/stopping/error) with `McpServerTransitions` guard table.
- `mcp-server/src/main/kotlin/com/aura/mcp/bridge/PeerPhase.kt` — `PeerPhase` enum (new/answered/init_seen/challenge_sent/sas_sent/enroll_sent/approved/dead) with `PeerTransitions.require` guards; out-of-order control messages are dropped instead of processed.
- `mcp-server/src/main/kotlin/com/aura/mcp/lan/PinGateState.kt` — `PinGateState` sealed (`Active(failures, level)` / `Locked(until, level)`); lockout always carries a deadline and preserves escalation level across expiry.
- `mcp-server/src/main/kotlin/com/aura/mcp/tools/ToolStage.kt` — `ToolStage` enum (received/scope_checked/paused/policy_blocked/foreground_blocked/approval_pending/approval_denied/otp_blocked/guard_blocked/executing/settling/done/failed); every `ToolResult` now carries the producing stage.
- `mcp-server/src/main/kotlin/com/aura/mcp/cache/ScreenFreshness.kt` — shared `ScreenFreshness` enum; `LastSeenScreen` and `PerceptionCache` expose `freshness(expectedGeneration)`.

### Key integrations
- `AgentRunState` is now phase-first; `pause()`/`resume()`/start/terminate go through `RunTransitions.next`, and illegal transitions are caught/logged instead of silently applied.
- `ControlLock` moved from two independent `AtomicBoolean`s to a single atomic `ControlLockState` (derived paused/holding/canAutomateMutations).
- `ConfirmationBroker` queues requests instead of overwriting; exposes `state: StateFlow<ApprovalState>`.
- `AskUserBroker` tracks `AskUserState` (open/answered/dismissed/timed-out/idle).
- `WakeWordListeningService` replaced `recording`/`pausedForRun` booleans with `WakeWordState` plus a companion `currentState`.
- `ScreenCaptureManager` replaced the consent counter with `CaptureState` (request token → granted → capturing → released).
- `BrowserController` tracks `BrowserState` and refuses concurrent navigations.
- `RemoteGate` uses a single `GateStatus` enum; `updateRecommended`/`updateRequired` are now distinct.
- `PinGate` state is now the sealed `PinGateState`; failures/lockout/level are no longer independent fields.
- `McpServerController` tracks `serverState` via `McpServerTransitions`-consistent guards and rejects double-starts.
- `WebRtcTransport` peers are phase-guarded (init/proof/reveal/token only in the phases that own them).
- `McpToolRegistry` records `ToolStage` on every gate and in every `ToolResult`.
- `PerceptionTiering.decideAndRecord` persists `currentTier`.
- `StatusNarrator` is now `NarrationState`-driven with a derived string stream.
- `ActiveRunRegistry.RunHandle.state` is canonical; `finish()` nulls `currentRunId`.
- `SessionWriter.end()` is idempotent via `SessionLifecycle`.
- `FirebaseSync.Report.state`, `B2Service.backupState`, `InstallFlow.state`, `VoiceEngine.session`/`ttsState`, `GeminiLiveConversationSession.state`, `AuraViewModel.evalRun` added.
- `OnboardingScreen` driven by `OnboardingStep` instead of bare integers.
- Overlay pill uses `PillState`; `StatusStrip`/report surfaces derive from run phase.

### Remaining gaps
- On-device Gradle build/runtime/device tests not executed locally (no Android SDK); compile correctness must be verified via CI (`:mcp-server:test`, `:app:assembleDebug`).
- `FEATURES.md` and this file describe the intended state; individual screens can adopt the new state flows progressively.

