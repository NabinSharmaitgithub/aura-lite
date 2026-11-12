# Implementation Playbook — AURA Lite Missing Features

Recommended implementation order for `missing_feature.md`, with dependencies and acceptance gates.

## Phase 0 — Foundations
1. Adopt `McpServerBuilder` everywhere (in-process, HTTP LAN, WebRTC). Inject `McpCaches`, `McpSinks`, `ToolPipelineHooks`.
2. Normalize refusal/error envelopes and tool-phase instrumentation at the registry boundary.
3. Add centralized permission registry and wire UI/feature gating to it.
4. Add `docs/INVARIANTS.md`/`docs/PERMISSION_MATRIX.md` to CI review checklist.

**Exit:** all transports build through the builder; policy/guard codes consistent; permission gating works from one registry.

## Phase 1 — Safety & control
1. `ForegroundGuard` + `OtpSighting` enforced in the pipeline (done: guards added; verify coverage).
2. Spoken-request screening hooks at STT→goal boundary.
3. Policy decision logging into audit/trail sinks (redacted).
4. `ControlLock` + pause pill integration; mutating tools yield to user pause/hold.
5. `KeepAwakeController` lifecycle wiring in run start/resume/end.
6. Completion evidence + proof checks before success on high-stakes goals.

**Exit:** adversarial calls are blocked with structured reasons; success claims require evidence; user pause wins immediately.

## Phase 2 — Agent reliability
1. Screen generation/signature/activity tracking propagated into tool results/caches.
2. Screenshot re-injection + stale perception eviction in message assembly.
3. Ledger re-injection + debounced persistence with critical flushes.
4. Defect repair/bounded nudges (defective turns/no-progress/tool-call-as-text).
5. Context compaction heuristics.
6. Perception tiering/escalation rules; tap reaction observation/semantic fallback.
7. Deferred tool loading (`load_tools`) + client-side helper tools (ledger/memory/proof/skills/research).

**Exit:** long runs remain bounded/consistent; stale data cannot drive actions; deferred tools load safely.

## Phase 3 — UX & resumability
1. `StatusNarrator` → Status strip UI; structured status/report model.
2. Pause checkpoints + `RunCheckpointStore`; resumed-run preamble + forced re-verification.
3. Run report card (markdown-capable), pause pill, control-lock diagnostics.
4. Centralized permission matrix UI.

**Exit:** users can see, pause, resume, and understand runs; resumed actions are re-verified.

## Phase 4 — Remote MCP parity
1. Harden phone signaling endpoints and WebRTC pairing/reconnect (SAS/HMAC/trust).
2. Per-client sessions/principals and toolset scoping; trusted-device revocation UX.
3. Desktop `aura-mcp-connect`: daemon (`127.0.0.1:<port>/mcp`), stdio shim, discovery (saved/mDNS/subnet), trust store (`~/.aura/webrtc.json`), optional `aura-adb`.

**Exit:** desktop MCP client can discover/pair/connect reliably; policy/audit semantics match in-process.

## Phase 5 — Observability, eval, gating
1. Structured trace schemas + chokepoint instrumentation (gate/settle/observe/completion correlation).
2. Separate user reports vs forensic logs.
3. Eval debug cockpit + harness integration + verdict taxonomy (`pass/fail/refused/invalid`).
4. Tool bench with mock phone + HTML reports.
5. Remote gate enforcement wired to Remote Config (kill switch/min-version/update/blocklist/suspension) with cached offline-safe behavior.
6. Debug spike receiver and UI stream/debug hooks (debug builds).

**Exit:** eval/bench reproducible; gating effective and offline-safe; diagnostics available without leaking sensitive data.

## Acceptance gates (every phase)
- Code wired for enabled flows; no dead feature flags in UI without explanation.
- Safety/policy checks enforced in code at the chokepoint.
- Tests: unit + adversarial/mock-bridge cases for new gates/tools; device validation notes for permission/OEM/network-sensitive behavior.
- Docs updated (`AGENT.md`, `docs/INVARIANTS.md`, `docs/PERMISSION_MATRIX.md`, feature docs).
