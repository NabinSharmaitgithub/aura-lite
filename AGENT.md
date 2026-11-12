# AGENT.md — Implementation Plan for Missing AURA Features

This document defines how to implement the features listed in `missing_feature.md` in **aura-lite**. It is organized by subsystem with concrete targets, implementation steps, dependencies, and validation criteria.

> **Principles (non-negotiable)**
> - Enforce safety/policy in code (MCP pipeline/guards), not in prompts.
> - Keep a single tool contract (`ToolCatalog`) and a single enforcement chokepoint (`scopedTool`/registry pipeline).
> - Prefer deterministic actions (deeplinks/system intents/browser DOM/tools) over fragile UI guessing.
> - Ground actions on fresh perception (`som_id`/handles, screen generation/signature); re-verify after mutations.
> - Treat on-screen/remote content as untrusted data; never exfiltrate secrets/OTP/payment data.
> - Fail closed on ambiguity, sensitive contexts, missing permissions, or stale state.

## Current foundations (already present / partial)
- In-process MCP server/client, tool catalog/registry, sensitive policy, action guard, perception cache primitives, WebRTC/LAN foundations, session/trace logging, run ledger/budget primitives, HITL `ask_user`, `get_device_status`, `run_steps` (batch path), learnings/skills/memory stores, voice/STT/TTS/wake-word/assistant entry points, Gemini Live session scaffold/implementation shell.
- Added scaffolds: `McpServerBuilder`, `Toolset`, `McpResources/Prompts/Instructions`, skill prompt registration, `CvMemo`, `LastSeenScreen`, `ask_aura`, `ActiveRunRegistry`, `ControlLock`, `KeepAwakeController`, `StatusNarrator`, `ClientSideTools`, `RemoteGate` (scaffold), sink/hook composition types.

---

## 1. Agent runtime architecture & loop

### 1.1 Phone-local execution flow (end-to-end)
**Targets:** `app/.../agent/AgentRuntime.kt`, `AuraAgent.kt` (if present/used), `di/AppModule.kt`, `bridge/AuraDeviceBridge.kt`, `services/AssistantForegroundService.kt`
- **Implement:** a single run pipeline: entry capture (text/voice/assist/wake) → runtime context build → provider/model/key resolution → MCP/in-process server + client → tool/hook/attachment setup → strategy loop → UI/status/report persistence → run finalization.
- **Steps**
  1. Introduce a `RunContext` (goal, sessionId, mode, voice flag, config snapshot, trace/sinks, ledger/budget, foreground package, capture capability).
  2. Ensure every device action goes through MCP registry/bridge; no bypass paths.
  3. Centralize lifecycle transitions (`starting/running/paused/waiting_user/completed/failed/cancelled`) and emit to `StatusNarrator`, `ActiveRunRegistry`, and UI state.
  4. Attach keep-awake, control-lock checks, and run-end hooks in `try/finally`.
- **Validation:** one goal executes end-to-end with tool calls, observations, final outcome, and persisted session artifacts.

### 1.2 Run entry modes (fresh, saved-settings, resumed)
**Targets:** `AgentRuntime.kt`, `data/session/*`, `presentation/*`
- **Implement:** `startFresh(goal)`, `startWithSavedSettings(goal|preset)`, `resume(sessionId/checkpointId)`.
- **Steps**
  1. Persist checkpoint snapshots (ledger, step count, last screen signature/generation, last observation summary, pending question/approval, config/model).
  2. On resume, restore state, show/record preamble (“resuming…”), and force re-verification (`read_screen`/`perceive_screen` + foreground/app checks) before mutating actions.
  3. Reject resume if environment/config is incompatible (missing permissions, different critical constraints) with a clear user-facing reason.
- **Validation:** paused/interrupted run can resume safely and does not act on stale screen assumptions.

### 1.3 Pause checkpoints
**Targets:** `AgentRuntime.kt`, `ActiveRunRegistry`, `AskUserBroker`, session store
- **Implement:** checkpoint on user pause, HITL prompts, approval waits, recoverable failures, and controlled stops.
- **Steps**
  1. Serialize minimal resumable state atomically (temp file + rename).
  2. Mark checkpoint reason and timestamp; expose in UI/history.
  3. Flush critical ledger/session writes on checkpoint boundaries.

### 1.4 Honest termination paths
**Targets:** `AgentRuntime.kt`, `RunBudget`, `RemoteGate`, UI/report models
- **Implement:** explicit outcomes: `success`, `partial`, `failure`, `cancelled`, `quota_exhausted`, `budget_exhausted`, `time_exceeded`, `permission_denied`, `policy_blocked`.
- **Steps**
  1. Map exceptions/gates/budget/remote-gate decisions to typed outcomes.
  2. Require evidence-backed success for high-stakes/completion claims.
  3. Always emit user-facing summary + forensic trace reason codes.

### 1.5 Keep-awake during runs
**Targets:** `agent/runtime/KeepAwakeController.kt`, `AssistantForegroundService.kt`, `AgentRuntime.kt`
- **Implement:** acquire wake lock/foreground hosting for active runs; release in `finally`.
- **Steps:** start keep-awake on run start/resume, refresh if needed, release on terminal states and process-safe cleanup.

### 1.6 Control-lock integration & control-lock/touch signals
**Targets:** `agent/control/ControlLock.kt`, `accessibility/*`, `overlay/*`, `McpToolRegistry` hooks
- **Implement:** user pause/hold precedence over automation.
- **Steps**
  1. Feed UI pause pill, overlay touch/hold, and accessibility/user-interaction signals into `ControlLock`.
  2. Registry/hook pre-check: deny/defer mutating tools while locked/paused (return `paused_by_user`/`control_locked` as appropriate).
  3. Record control-lock diagnostics events (who/why/when).

### 1.7 Run-end hooks
**Targets:** `McpServerBuilder` sinks, `AgentRuntime.kt`, session/learnings/backup integrations
- **Implement:** `onRunStart`, `onRunEnd`, `onToolCall`, `onCheckpoint`, `onOutcome` hooks.
- **Steps:** invoke hooks with structured payloads; ensure hooks are best-effort/non-fatal and audited.

### 1.8 Status narration
**Targets:** `agent/runtime/StatusNarrator.kt`, `AgentRuntime.kt`, strategy, UI status strip/overlay
- **Implement:** concise live status updates (planning, reading screen, tapping, waiting, verifying, asking user, blocked, done).
- **Steps:** emit on state transitions/tool phases; throttle/debounce noisy updates; keep user-facing text separate from model text.

### 1.9 `ActiveRunRegistry` (run steering/registry)
**Targets:** `agent/runtime/ActiveRunRegistry.kt`, `AgentRuntime.kt`, UI/services
- **Implement:** register active runs, support pause/resume/cancel steering, expose current run snapshot.
- **Steps:** integrate with `AssistantForegroundService` actions/notifications and UI controls.

---

## 2. Strategy, perception, grounding, and reliability

### 2.1 Vision-aware tool loop
**Targets:** `app/.../agent/strategy/*`, `AgentRuntime.kt`, `perception/*`, `cache/*`
- **Implement:** strategy loop that alternates model reasoning with tool execution and perception refresh.
- **Steps**
  1. After vision-producing/mutating tools, refresh screen state and inject latest observation.
  2. Prefer structural tree first; escalate to visual perception/OCR when tree is insufficient/ambiguous.
  3. Track turn state to avoid repeated identical perception/tool calls.

### 2.2 Screenshot re-injection & stale perception eviction
**Targets:** strategy, prompt/message assembly, caches
- **Implement:**
  - Re-inject only the latest relevant screenshot/annotated perception for current generation.
  - Evict stale screenshot/perception text when superseded by newer screen generation/signature.
- **Steps:** tag messages/artifacts with `screen_generation`/`screen_signature`; prune history before each model request.

### 2.3 Ledger re-injection
**Targets:** `agent/ledger/RunLedger.kt`, `AgentRuntime.kt`, strategy
- **Implement:** periodically re-inject compact ledger block (goal, current plan step, key facts, dead ends, constraints).
- **Steps:** cap size; prioritize recent facts/failures; update on each significant tool result/checkpoint.

### 2.4 Defect repair / bounded defect nudges
**Targets:** strategy/runtime
- **Implement:** detect defective turns (no actionable tool call when required, tool-call emitted as text, invalid args loops, repeated no-progress reasoning) and apply bounded corrective nudges.
- **Steps:** classify defect, inject corrective instruction once per defect type within limits, escalate to failure/ask_user when budget exceeded.

### 2.5 Deferred tool loading
**Targets:** `agent/tools/ClientSideTools.kt`, runtime tool registry wiring, `load_tools`
- **Implement:** load optional tool groups/schemas on demand to reduce prompt/tool overhead.
- **Steps**
  1. Maintain core toolset always available; defer heavy/rare tools.
  2. Implement `load_tools` local/client tool to request groups (`BROWSER`, research, etc.) and expose returned schemas/capabilities.
  3. Cache loaded state per run and audit loads.

### 2.6 Perception tiering heuristics (`tree_only` vs `full`)
**Targets:** `read_screen`/`perceive_screen` handlers, strategy
- **Implement:** choose `tree_only` by default, `full` (screenshot/CV/OCR merge) when tree is empty, low-confidence, canvas/custom-drawn, or explicitly required.
- **Steps:** score tree quality (interactive nodes, labels, bounds coverage, changes); log decision reason.

### 2.7 Forced perception escalation rules
**Targets:** strategy, action guard/policy hooks
- **Implement:** force escalation after repeated misses, ambiguous targets, stale handles, or verification uncertainty.
- **Steps:** track consecutive ambiguous/no-change outcomes per screen signature and escalate or ask user.

### 2.8 Screen signatures/generation/activity tracking
**Targets:** `UiTreeExtractor`, `cache/LastSeenScreen.kt`, `cache/PerceptionCache.kt`, registry hooks
- **Implement:** stable `screen_generation`, `screen_signature`, activity/settle timestamps, and foreground package linkage.
- **Steps:** increment generation on observed UI changes/mutations; invalidate caches on mismatch; include in tool results.

### 2.9 Tap reaction observation and semantic fallback
**Targets:** `tap` handling/bridge, post-action observer
- **Implement:** after tap, observe expected reaction (focus change, dialog/navigation, text/state change); if no reaction, try bounded semantic fallback (re-read, alternative target by label/role, scroll-into-view) or fail with actionable error.

### 2.10 Multi-stroke trace/draw gesture semantics
**Targets:** `swipe`/gesture tools, `GestureInjector`
- **Implement:** support multi-point/multi-stroke traces for drawing/signature/pattern flows with timing and path validation.
- **Steps:** extend schema and injector API; ensure bounds/safety checks and verification hooks.

### 2.11 Keyboard suppression/restore (keyboard lease)
**Targets:** accessibility/input bridge, IME integration, browser/input tools
- **Implement:** keyboard lease to prevent IME contention during automated typing; suppress/restore predictably.
- **Steps:** acquire lease before input sequences, restore original IME/focus state on completion/failure/pause.

---

## 3. MCP server core, tools, and hooks

### 3.1 Single composition root (`McpServerBuilder`) adoption
**Targets:** `mcp-server/.../McpServerBuilder.kt`, app DI/server startup, `McpServerController`
- **Implement:** build all server modes (in-process, HTTP LAN, WebRTC) through builder with shared caches/sinks/hooks/policies.
- **Steps:** replace ad-hoc registry construction in DI/startup; inject `McpCaches`, `McpSinks`, `ToolPipelineHooks`, toolsets.

### 3.2 MCP resources/prompts/instructions/skill prompts
**Targets:** `McpResources`, `McpPrompts`, `McpInstructions`, skill store/loader
- **Implement:** complete, stable MCP resource/prompt surfaces and runtime skill prompt registration.
- **Steps:** load skills on startup and on changes; expose `aura://...` resources and prompt argument schemas; keep transport responses consistent.

### 3.3 Tool groups by `Toolset`
**Targets:** `Toolset`, `ToolAccess`, `McpServerBuilder`, trusted client config/UI
- **Implement:** enforce `CORE`, `ASSISTANT`, `BROWSER` (and explicit tool allowlists) per principal/client.
- **Steps:** filter `tools/list`; deny `tools/call` by scope; persist per-client toolsets.

### 3.4 Sinks and cross-cutting hooks
**Targets:** `McpSinks`, registry/pipeline hooks, session/trace/learnings modules
- **Implement and wire:**
  - **Audit sink:** every call decision/outcome (allow/deny/block), principal, tool, sanitized args, duration.
  - **Session sink:** session lifecycle/run events.
  - **Tool-phase sink:** start/validate/execute/settle/observe/end phases.
  - **Trail sink:** structured forensic trail entries.
  - **Post-action observer sink:** append observation after mutating UI/browser actions.
  - **Learnings dispatch sink:** emit privacy-safe successful patterns/facts for learning.
- **Steps:** define typed payloads, avoid sensitive leakage, and make sink failures non-fatal.

### 3.5 Caches (`CvMemo`, `LastSeenScreen`) integration
**Targets:** `CvMemo`, `LastSeenScreen`, perception pipeline, read/perceive tools
- **Implement:** memoize CV/OCR by screen signature/region hash; track last seen screen for diff/eviction decisions.

### 3.6 `InProcessMcpServer` internal principal injection
**Targets:** `InProcessMcpServer`, `McpClient`, `McpToolRegistry`
- **Implement:** inject trusted internal principal (`clientId=local-agent`, full/internal scopes as configured) for in-process agent calls.
- **Steps:** distinguish internal vs remote principals in audit/policy while preserving enforcement.

### 3.7 Standardized refusal/error envelopes
**Targets:** `ToolError`, `ToolResult`, registry/server transports
- **Implement:** consistent envelopes and codes for `policy_blocked`, `scope_denied`, `action_guard`, `paused_by_user`, `permission_denied`, `stale_screen`, `not_found`, `timeout`, etc.
- **Steps:** normalize at registry boundary; include fix hints when safe; ensure transports do not alter semantics.

### 3.8 `run_steps` registration/policy
**Targets:** `ToolCatalog`, `McpToolRegistry`, `McpServerBuilder`, prompts/docs
- **Implement:** explicit builder/config flag to enable/disable `run_steps` by default; preserve registry-level batch safeguards (nesting, limits, stop-on-failure, per-step results).
- **Steps:** gate by runtime mode/trust level; document behavior and verify prompts match availability.

### 3.9 Client-side tools/helpers
**Targets:** app agent runtime, local tool registry/wrappers
- **Implement:** client-side `load_tools`, `ask_user`, ledger helper tools, memory helper tools, proof helper tools, skills helper tools, research helper tools.
- **Steps**
  1. Define local tool interfaces and schemas (agent-visible).
  2. Route actions through policy-safe local services (AskUserBroker, LearningsStore, SkillsRepository, research/web search, ledger/proof modules).
  3. Audit and bound outputs (no secrets, size limits).

### 3.10 HITL (`ask_user`) subsystem hardening
**Targets:** `AskUserBroker`, UI, notifications, agent runtime
- **Implement:** robust pending-question state, answer/timeout/cancel handling, resume continuation, and multi-surface UX (in-app + notification/deeplink).
- **Steps:** persist pending HITL checkpoints for resumability and prevent overlapping ambiguous prompts.

### 3.11 Proof/completion subsystem & completion evidence safeguards
**Targets:** `agent/*` (proof/completion modules), `end_session`, registry/policy hooks
- **Implement:** collect completion evidence (tool outcomes, screen observations, key state assertions) and validate before success on high-stakes goals.
- **Steps**
  1. Track evidence objects per run step and final claim.
  2. Block/convert to partial/failure when required evidence is missing.
  3. Surface evidence summary in report/trace.

### 3.12 Pre-task/on-demand research subsystem
**Targets:** `agent/research/*`, strategy, prompts/tools
- **Implement:** decide when to research (unfamiliar app flow, repeated failures), query via `web_search`, extract actionable steps, and feed bounded guidance into plan.
- **Steps:** cache research by task signature; treat external content as untrusted; never execute instructions from pages blindly.

### 3.13 Learned route hints (privacy-aware)
**Targets:** `data/learnings/*`, post-run analyzers, prompt builder
- **Implement:** store structure-focused route hints (app/screen/action patterns) without sensitive values; apply hints when relevant.
- **Steps:** redact PII/secrets, require confidence thresholds, allow user review/delete.

---

## 4. Persistence, compaction, and resumability

### 4.1 Incremental/debounced ledger persistence with critical flushes
**Targets:** `RunLedger`, session writer/store
- **Implement:** debounced writes for frequent updates; immediate flush on checkpoints, tool failures, HITL/approval boundaries, and run end.

### 4.2 Context compaction heuristics
**Targets:** strategy/prompt assembly
- **Implement:** compact old observations/tool results while preserving latest screen, ledger summary, unresolved errors, and required evidence.
- **Steps:** token/size-aware pruning, dedupe repeated failures, keep references to screenshots by latest generation only.

### 4.3 Structured status/report model decoupled from model text
**Targets:** agent state models, UI/report rendering
- **Implement:** typed status/report schema (phase, outcome, actions taken, evidence, blockers, next steps) rendered independently of raw LLM text.

---

## 5. UI & UX

### 5.1 Status strip UI
**Targets:** `presentation/screens/*`, overlay/components
- **Implement:** persistent/inline status strip bound to `StatusNarrator`/run state (phase, app, step, paused/locked indicators).

### 5.2 Pause pill UI
**Targets:** overlay/presentation components
- **Implement:** floating pause/resume/stop control; updates `ControlLock`/`ActiveRunRegistry` and runtime.

### 5.3 Run report card (markdown-capable)
**Targets:** report UI component, session detail screen
- **Implement:** markdown-capable report card rendering structured outcome, summary, steps/evidence, and failures with safe formatting.

### 5.4 Centralized permission metadata registry
**Targets:** `presentation/permissions/*` (new), settings/onboarding/feature gating
- **Implement:** single registry mapping features/tools to permissions, rationale, fix path, and runtime checks.

### 5.5 Feature-dependent permission matrix (docs + runtime source)
**Targets:** `docs/PERMISSION_MATRIX.md`, permission registry
- **Implement:** generate/maintain matrix from registry where possible; keep UI/runtime behavior consistent.

---

## 6. Remote MCP, discovery, pairing, and desktop bridge

### 6.1 LAN signaling HTTP endpoints (`/aura/info`, `/aura/connect`)
**Targets:** `mcp-server/.../lan/LanSignalingServer.kt`, `LanPolicy`, `ConnectAuthorizer`
- **Implement/verify:** local-network-only signaling endpoints, request validation (origin/content-type/peer/policy), and info/connect responses compatible with desktop client.

### 6.2 WebRTC transport parity (phone side)
**Targets:** `bridge/WebRtcTransport.kt`, `PairingCrypto.kt`, `TrustedClientsStore.kt`, `WebRtcJsonRpcBridge.kt`, `McpServerController`
- **Implement:** stable pairing handshake, SAS/verification, reconnect challenge-response (HMAC), per-client sessions/principals, trusted-client revocation/toolsets, chunking/reliability, and audit logging.
- **Steps:** align protocol fields and state transitions with desktop bridge expectations; add robust timeout/retry/error handling.

### 6.3 Desktop bridge daemon (`aura-mcp-connect`) and local MCP endpoint
**Targets:** `aura-mcp-connect/` (add module/package in repo) or external companion package maintained with protocol compatibility
- **Implement:** Node.js daemon exposing local `http://127.0.0.1:<port>/mcp` (Streamable HTTP/MCP transport as required), maintaining a single phone WebRTC link.
- **Steps**
  1. Implement config, logging, daemon lifecycle, and reconnect logic.
  2. Multiplex/serialize MCP client requests safely over phone link; return responses/errors faithfully.
  3. Enforce local-only binding and secure defaults.

### 6.4 Stdio shim proxy
**Targets:** `aura-mcp-connect` CLI/shim
- **Implement:** stdio MCP shim that proxies to local daemon endpoint for clients requiring stdio transport.

### 6.5 Phone discovery (saved address, mDNS, subnet scan)
**Targets:** `aura-mcp-connect` discovery module
- **Implement:** discovery priority/fallback chain: saved host → mDNS → subnet scan → manual host.
- **Steps:** validate candidates via `/aura/info`; cache last-known good address.

### 6.6 Pairing UX and trust material
**Targets:** app trusted-devices UI, phone signaling/transport, desktop bridge
- **Implement:** PIN pairing, SAS/verification code confirmation, reconnect proof, trusted device list/revocation, per-client toolsets.
- **Steps:** store desktop trust material in `~/.aura/webrtc.json` with restrictive permissions; support rotation/re-pair/revoke.

### 6.7 Host-side `aura-adb` tool (developer opt-in)
**Targets:** `aura-mcp-connect` optional tools, docs
- **Implement:** explicitly armed debug-only tool with clear warnings and isolation from phone-side policy pipeline.
- **Steps:** require explicit flag/config + runtime confirmation; audit invocations locally.

---

## 7. Safety, privacy, and policy enforcement

### 7.1 Foreground-app protection (`ForegroundGuard`)
**Targets:** `mcp-server/.../guard/ForegroundGuard.kt` (new), registry pipeline
- **Implement:** guard mutating/perception-sensitive actions when protected/restricted foreground apps or contexts are active.
- **Steps:** integrate with restricted apps store, sensitive policy, browser handoff states.

### 7.2 OTP sighting guard (`OtpSighting`)
**Targets:** `mcp-server/.../guard/OtpSighting.kt` (new), screen/read/perceive pipeline
- **Implement:** detect likely OTP/verification-code exposure and prevent unsafe propagation/tool actions (e.g., typing/sending/copying to untrusted sinks).
- **Steps:** use redaction-safe detection signals; log policy decisions without storing raw OTP values.

### 7.3 Spoken-request screening hooks
**Targets:** voice/STT ingestion, agent prompt pre-processing, `SensitivePolicy` hooks
- **Implement:** screen spoken/transcribed requests for destructive/sensitive intents before execution planning.
- **Steps:** normalize transcripts, apply same intent/policy checks as text goals, require confirmation/HITL when needed.

### 7.4 Policy decision logging
**Targets:** audit/trail sinks, registry/policy/guard modules
- **Implement:** structured, minimally sensitive logs for allow/deny/block reasons (gate, code, tool, principal, screen/app context metadata).
- **Steps:** redact arguments/content; correlate with session/trace IDs.

---

## 8. Observability, eval, and debugging

### 8.1 Structured trace schemas
**Targets:** `data/session/*`, mcp sinks, docs (`docs/TRACE_SCHEMA.md`)
- **Implement:** versioned schemas for run events, tool phases, gate decisions, observations, outcomes, and errors.
- **Steps:** include correlation IDs (run/session/step/tool-call), timestamps, and redaction rules.

### 8.2 Chokepoint instrumentation
**Targets:** registry pipeline, observers, completion/proof hooks
- **Implement:** instrumentation at gate decisions, settle/observe, and completion correlation points.

### 8.3 Separation of user-facing reports and forensic logs
**Targets:** UI/report generation, session export, logging
- **Implement:** user report (concise, actionable, non-sensitive) vs forensic trace/log bundle (structured, redacted, diagnostic).

### 8.4 Eval debug cockpit
**Targets:** debug UI/screens, eval services
- **Implement:** developer-only cockpit to inspect tasks, runs, traces, tool calls, verdicts, and artifacts.

### 8.5 Eval scripts/harness integration & verdict taxonomy
**Targets:** `benchmark/`, `scripts/`, `app/.../eval/*`
- **Implement:** unified harness execution and verdict taxonomy: `pass`, `fail`, `refused`, `invalid` with reason codes.
- **Steps:** standardize result schema, artifacts, and reporting.

### 8.6 Debug agent spike broadcast receiver
**Targets:** `app/src/main/AndroidManifest.xml` (debug), `debug/*`
- **Implement:** debug-only broadcast receiver/hooks to trigger controlled diagnostic agent actions/spikes.

### 8.7 Tool bench (`mcp-server` tests with mock phone and HTML report)
**Targets:** `mcp-server/src/test/...`, `scripts/` or test utilities
- **Implement:** adversarial/mock-bridge tool bench scenarios covering policy refusals, stale handles, permissions, batching, and transport behaviors; generate HTML report artifacts.

### 8.8 Control-lock diagnostics & UI stream/debug hooks
**Targets:** control-lock module, debug UI/overlay, runtime
- **Implement:** diagnostics view/logs for lock state transitions and optional UI event stream hooks (debug builds only).

---

## 9. Remote gating and release controls

### 9.1 Remote kill switch / min version / update prompts / blocklist / suspension
**Targets:** `firebase/RemoteGate.kt`, Remote Config integration, app startup/runtime gating
- **Implement:** fetch/cache gate config, enforce offline-safe defaults, and apply at startup and before runs/actions as appropriate.
- **Steps**
  1. Define config keys/schema and precedence.
  2. Cache last known good state with TTL/metadata.
  3. Provide user-facing messages and remediation (update/retry/contact support).

### 9.2 Cached/offline-safe enforcement
**Targets:** `RemoteGate`, network/config layer
- **Implement:** fail-open/allow-safe-default behavior when config unavailable (unless explicitly cached-blocked by policy) and avoid bricking offline use.

---

## 10. Documentation deliverables

Create/maintain the following docs (consolidate existing material where possible):
- **`docs/IMPLEMENTATION_PLAYBOOK.md`** — recommended implementation order, milestones, and dependencies.
- **`docs/INVARIANTS.md`** — non-negotiable safety/architecture invariants.
- **`docs/PORTING_STRATEGY.md`** — module boundaries and steps to port features to other projects/branches.
- **`docs/BUILD_AND_RUNTIME_REQUIREMENTS.md`** — build toolchain, runtime, permissions, model/assets, optional services.
- **`docs/PERMISSION_MATRIX.md`** — feature-dependent permission matrix (permission, purpose, required/optional, fallback, fix path).
- **`docs/CONSTRAINTS_AND_RISKS.md`** — known constraints, OEM/device risks, security/privacy risks, and mitigations.

---

## Suggested implementation order (phased)

1. **Core correctness & safety:** builder adoption, hook/sink wiring, standardized errors, control-lock/keep-awake/status narration integration, proof/completion evidence, OTP/foreground guards, policy decision logging.
2. **Strategy reliability:** screen generation/signature tracking, stale eviction, screenshot/ledger re-injection, defect repair nudges, compaction, perception tiering/escalation, tap reaction fallback.
3. **Resumability & UX:** pause checkpoints, resumed-run flow, structured status/report model, status strip/pause pill/report card, permission metadata registry/matrix.
4. **Remote MCP parity:** phone signaling/WebRTC compatibility hardening, trusted-device UX, desktop `aura-mcp-connect` daemon/stdio/discovery/pairing/reconnect/trust store.
5. **Eval/debug/gating:** trace schemas/instrumentation, tool bench, eval harness/verdict taxonomy, debug cockpit/hooks, remote gate enforcement, final docs consolidation.

## Definition of done (per feature)
- Code implemented and wired (no dead paths for enabled flows)
- Policy/safety checks enforced at chokepoints
- Structured logs/traces and user-facing states updated
- Unit/integration tests added or updated (including adversarial cases where applicable)
- Docs updated (`README`/feature docs/playbook/permission matrix as relevant)
- Manual/device validation notes captured for permission-, OEM-, and network-sensitive behavior
