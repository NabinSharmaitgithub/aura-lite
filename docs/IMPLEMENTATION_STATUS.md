# AURA Feature Implementation Status in aura-lite

**Date:** 2026-11-12  
**Goal:** Implement features described in `docs/AURA_FEATURES_AND_IMPLEMENTATION.md` (original AURA spec/analysis) into `aura-lite`.

## Summary

`aura-lite` now includes substantial feature-parity work across MCP safety/pipeline, grounding contracts, remote transport foundations, agent reliability primitives, browser behavior, and scaffolds for remaining advanced systems. Some areas remain **partially implemented** or **environment/asset-dependent** (see below), but core gaps identified earlier have been actively filled with concrete code and integration points.

## Implemented in this pass

### 1) MCP server core, safety, and execution pipeline
- **Scoped enforcement pipeline** in `McpToolRegistry` (`mcp-server/.../tools/McpToolRegistry.kt`):
  - Principal/scope checks (`ToolPrincipal`, allowed tools/toolsets)
  - Pause hook (`ToolPipelineHooks.isPaused`)
  - Sensitive policy gate (fail-closed)
  - Restricted/sensitive foreground checks
  - Risky-action approval gate
  - Action guard (stale/missing target handles)
  - Execute + audit
  - Post-mutation settle/invalidation hooks
  - Structured refusals (`policy_blocked`, `scope_denied`, `paused_by_user`, `action_guard`, etc.)
- **Sensitive policy**: `mcp-server/.../server/policy/SensitivePolicy.kt`
  - Sensitive app/foreground blocking heuristics
  - Sensitive goal/credential-harvest intent blocking
  - Outgoing secret/OTP/payment-data screening for exfiltration-prone tools
  - Sensitive URI and system-intent checks
- **Action guard**: `mcp-server/.../guard/ActionGuard.kt`
  - Stale/missing `som_id`/`index` refusal when perception cache is populated
- **Perception cache**: `mcp-server/.../cache/PerceptionCache.kt`
  - Generation/signature/element handle cache primitives for grounded actions
- **Error taxonomy expanded**: `mcp-server/.../bridge/DeviceBridge.kt` (`POLICY_BLOCKED`, `SCOPE_DENIED`, `PAUSED_BY_USER`, `ACTION_GUARD`)

### 2) Tool contracts, grounding, and perception metadata parity
- **`som_id` grounding support**:
  - Tool schemas accept `som_id` (preferred) and `index` alias (`ToolCatalog` locator props)
  - Device bridge maps `som_id` -> `index` for `tap`, `scroll`, `set_value`, `type_text`, `verify_action`
- **`read_screen` output parity improvements** (`UiTreeExtractor`):
  - Adds `screen_generation`, `screen_signature`, per-node `som_id`, and `elements` alias
  - Preserves existing node/index fields for compatibility
- **Perception cache DI wiring**:
  - `PerceptionCache` provided as singleton and injected into `McpToolRegistry` (`app/di/AppModule.kt`)

### 3) Remote MCP / access control and transport parity foundations
- **Per-client principal/toolset scoping**:
  - `mcp-server/.../server/ToolAccess.kt` maps scopes (`*`, `CORE`, `ASSISTANT`, `BROWSER`, explicit tool names) to allowed tools
  - `HttpMcpServer` now dispatches `tools/list` and `tools/call` through principal-scoped registries based on trusted device scopes
- **WebRTC LAN MCP transport (aura-mcp-connect-compatible foundations) added**:
  - `mcp-server/.../bridge/PairingCrypto.kt` (protocol crypto: binding/commit/SAS/proof/token hash)
  - `mcp-server/.../bridge/TokenPrincipal.kt`, `McpAuditLogger.kt` (bridge audit interface), `ConnectionTracker.kt`
  - `mcp-server/.../bridge/TrustedClientsStore.kt` (WebRTC trusted client ledger + toolsets)
  - `mcp-server/.../bridge/WebRtcTransport.kt` (LAN signaling + WebRTC offer/answer/DataChannel, pairing handshake, reconnect proof, SAS/enrollment, promotion/drop, chunked frame send)
  - `mcp-server/.../lan/PinGate.kt`, `LanPolicy.kt`, `ConnectAuthorizer.kt`, `LanNetwork.kt`, `LanSignalingServer.kt` (`/aura/info`, `/aura/connect`, LAN-only/Origin/content-type/peer checks)
  - `mcp-server/.../server/WebRtcJsonRpcBridge.kt` (newline-delimited JSON-RPC framing + dispatch over WebRTC to aura-lite registry/principals)
  - `McpServerController` extended with `startWebRtc()`/`stopWebRtc()` lifecycle and WebRTC trusted-client store integration
- **Build/dependency updates** for WebRTC support:
  - Added WebRTC dependency entries in `gradle/libs.versions.toml` and `mcp-server/build.gradle.kts`

### 4) Agent runtime reliability (ledger, budget, hooks integration)
- **Run ledger**: `app/.../agent/ledger/RunLedger.kt`
  - Goal/plan steps/facts/dead-ends/outcome tracking
  - Prompt block rendering and JSON snapshot/restore helpers
- **Run budget**: `app/.../agent/ledger/RunBudget.kt`
  - Step/wall-clock/request/consecutive-failure budget checks and stop reasons
- **AgentRuntime integration** (`app/.../agent/AgentRuntime.kt`):
  - Creates ledger and budget per run
  - Injects ledger and budget context into system prompt
  - Records failures/dead ends and successful progress signals
  - Persists ledger snapshots (`SessionWriter.writeLedger/readLedger`, session `ledger.json`)
  - Enforces budget stop conditions during loop execution
  - Finalizes ledger outcome/summary on completion

### 5) Browser plane parity improvements
- **BrowserController enhancements** (`app/.../browser/BrowserActivity.kt`):
  - Page generation tracking and reset on navigation/load completion
  - Handoff state tracking (`handoffActive`, blocked mutating actions during handoff)
  - `browser_read` returns structured JSON payload including `url`, `title`, `generation`, text, and discovered interactive elements with `el_id` handles (`data-aura-el`)
  - `browser_act` resolves `el_*` handles and returns structured action results with generation/url context
  - Safer handoff flow (marks handoff active, external handoff intent)

### 6) Voice/live conversation scaffolding
- **Gemini Live conversation scaffold**: `app/.../agent/conversation/GeminiLiveConversation.kt`
  - `LiveConversationSession` interface, config/event models, and `NoopLiveConversationSession`
  - Defines integration points for realtime audio/session protocol, mic arbitration, transcript/tool-call events, and guarded tool handoff

## Partially implemented / still pending (by gap)

| Gap area | Current state | Remaining work |
|---|---|---|
| **WebRTC transport parity with `aura-mcp-connect`** | Core signaling/pairing/transport/bridge classes and controller hooks added | End-to-end protocol compatibility validation against `aura-mcp-connect`, UI/approval wiring for SAS flow, reconnect/session lifecycle edge cases, token/toolset management UX, and interoperability fixes on real devices/networks |
| **Vision strategy/runtime parity** | Perception/cache primitives and grounding metadata added; existing perception/agent loops remain | Full Aura vision strategy behavior parity (screenshot re-injection/eviction, defective-turn repair, compaction nuances), escalation/tiering/memoization parity, and model/provider calibration |
| **Perception cache end-to-end enforcement** | Cache + guard + DI available | Populate/update cache from `read_screen`/`perceive_screen` outputs, invalidate on all UI-mutating paths, and enforce stale-handle refusals consistently across all action tools |
| **Tool pipeline hooks (pause/settle/observe/control lock)** | Hook interfaces and registry integration points exist | Wire real pause/control-lock state, foreground/browser-handoff gates, post-action settle/observation generation, and UI signal propagation |
| **Browser plane full parity** | Generation/`el_id`/handoff basics added | Robust DOM element lifecycle across tabs/actions/waits/extract/upload, stale handle rejection semantics, richer extraction schemas, upload/handoff UX, and policy integration |
| **Gemini Live conversation** | Scaffold/interfaces only | Implement realtime session protocol, audio streaming/capture/playback arbitration, provider auth/model config, tool-call handoff path, and UI/service lifecycle |
| **Proof/HITL/resume wiring** | Ledger/budget primitives integrated into agent run; `ask_user` exists in app | Completion-evidence/proof system, checkpointed resumable runs with re-verification UX, richer HITL pause/resume semantics, and evidence-backed end-session enforcement |
| **Policy/guard depth** | Core sensitive policy and stale-target guard added | OTP sighting, completion-evidence checks, stronger loop detection, foreground-guard nuances, browser handoff write restrictions, and expanded sensitive-content/host/scheme coverage |
| **ONNX/voice/assets & calibration** | Existing perception/voice components remain; WebRTC deps added | Bundle/configure required models/assets (OmniParser, sherpa/wake/voice), runtime provider selection/tuning, permission/foreground-service validation, and device performance tuning |
| **Firebase/eval/debug/UI parity** | Existing aura-lite Firebase/eval/UI systems unchanged unless noted | Align original AURA-specific remote gating/eval/debug behaviors where required, add debug-only hooks/receivers/cockpit parity, and complete UI surfaces for new controls (WebRTC pairing/SAS/trusted clients/toolsets) |
| **Tests/validation** | No automated build/tests executed in this pass | Add/update unit/tool-bench/integration tests, run `:mcp-server:test` / app builds, and validate on-device (accessibility, capture, WebRTC pairing, browser, voice)

## Notes on compatibility and safety
- Existing HTTP MCP server path in aura-lite is preserved; WebRTC transport is added as an additional/compatible path.
- Enforcement remains code-first (registry/policy/guards) rather than prompt-only.
- WebRTC/remote and sensitive features must be treated as high-trust surfaces; keep explicit user approval, revocation, and least-privilege scopes/toolsets.

## Recommended next steps (to fully close remaining gaps)
1. **Wire runtime state into pipeline hooks**: pause/control lock, browser handoff, post-action settle observers, and perception cache updates/invalidation in `AuraDeviceBridge` + registry hooks.
2. **Finish remote parity**: validate/fix `WebRtcTransport` + `WebRtcJsonRpcBridge` against `aura-mcp-connect`, implement approval/SAS UI flow, and expose trusted-client/toolset management.
3. **Deepen agent reliability**: persist/load resumable checkpoints, re-verify on resume, and integrate completion-evidence/proof checks into `end_session`.
4. **Complete perception/browser/live features**: full read/act/observe contracts, stale-handle enforcement, Gemini Live session implementation, and model asset/runtime setup.
5. **Harden and verify**: expand policy/guard tests, run builds/tests, and perform device/OEM validation for accessibility, capture, network, and voice.
