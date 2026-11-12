# AURA — Features, Functions, How It Works, Implementation Guide, and Requirements

**Repository:** `aura-android-use`  
**Analysis date:** 2026-11-12  
**Purpose:** Deep reference covering AURA's features/functions, runtime behavior, implementation guidance, and requirements for reusing/integrating them in another project.

> **Scope note:** This document is based on static analysis of the repository source, configs, and docs. Some behavior depends on device/OEM, provider capabilities, installed apps, permissions, and Firebase/project configuration.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture & Data Flow](#2-architecture--data-flow)
3. [Repository Modules](#3-repository-modules)
4. [Feature & Function Catalog](#4-feature--function-catalog)
   - [4.1 On-Device Agent Runtime](#41-on-device-agent-runtime)
   - [4.2 MCP Server Core & Tool Execution Pipeline](#42-mcp-server-core--tool-execution-pipeline)
   - [4.3 MCP Tools (Complete Functional Surface)](#43-mcp-tools-complete-functional-surface)
   - [4.4 Screen Perception & Grounding](#44-screen-perception--grounding)
   - [4.5 Accessibility, Gestures & Device Control](#45-accessibility-gestures--device-control)
   - [4.6 Deterministic Action Planes (System Intents, Deep Links, Notifications, Media, Contacts, Files)](#46-deterministic-action-planes-system-intents-deep-links-notifications-media-contacts-files)
   - [4.7 Browser Automation Plane](#47-browser-automation-plane)
   - [4.8 Voice, Wake Word & Conversation (Gemini Live)](#48-voice-wake-word--conversation-gemini-live)
   - [4.9 Memory, Skills, Learnings, Research, HITL & Proof](#49-memory-skills-learnings-research-hitl--proof)
   - [4.10 Run Ledger, Resumability, Budgets & Reliability Controls](#410-run-ledger-resumability-budgets--reliability-controls)
   - [4.11 Overlay, App UI & User Controls](#411-overlay-app-ui--user-controls)
   - [4.12 Remote Computer Control (LAN MCP + Desktop Bridge)](#412-remote-computer-control-lan-mcp--desktop-bridge)
   - [4.13 Safety, Privacy, Policy & Compliance Enforcement](#413-safety-privacy-policy--compliance-enforcement)
   - [4.14 Logging, Tracing, Diagnostics, Eval & Remote Gating](#414-logging-tracing-diagnostics-eval--remote-gating)
   - [4.15 Debug/Developer/Internal Functions](#415-debugdeveloperinternal-functions)
5. [How to Implement in Your Project (Cross-Cutting Playbook)](#5-how-to-implement-in-your-project-cross-cutting-playbook)
6. [Requirements (Build, Runtime, Permissions, Dependencies, Config)](#6-requirements-build-runtime-permissions-dependencies-config)
7. [Key Files & Starting Points](#7-key-files--starting-points)
8. [Known Constraints & Risks](#8-known-constraints--risks)

---

## 1. Project Overview

AURA is an on-device Android automation assistant and MCP server. It can:
- Understand user goals (voice/text)
- Perceive the current screen (UI tree + optional vision/OCR)
- Choose and execute actions via tools (gestures, app launches, deeplinks, system intents, browser DOM actions, notifications/media/files/contacts)
- Verify outcomes and report results
- Expose the same capabilities to local-network MCP clients on a computer through a secure pairing/bridge flow

**Core principles (implemented in code):**
- **On-device agent loop** with BYOK LLM/provider selection
- **Single MCP tool chokepoint** for scope/policy/gating/audit/observation
- **Perceive → act → verify** discipline (ground by `som_id`, settle after actions)
- **Deterministic shortcuts first** (system intents, deep links, notifications/media/files/contacts, browser DOM) before fragile pixel/gesture navigation
- **Local-network-only remote control** (no AURA relay), with pairing and user approval
- **Hard safety boundaries** for banking/payment/auth/password/OTP-sensitive flows

---

## 2. Architecture & Data Flow

### 2.1 Phone-local execution flow
1. **Entry:** user invokes AURA via wake word, volume-key shortcut, assist gesture, overlay text/voice, or app UI.
2. **Intent/goal capture:** voice/text normalized into a goal; conversation mode may delegate actions via `ask_aura` handoff.
3. **Agent run (`AuraAgent`)**: resolves saved provider/model/key/config, builds prompt + tool registry + hooks + ledger/memory/skills/proof attachments, starts vision-aware tool loop.
4. **In-process MCP server** (`McpServerBuilder` + `InProcessMcpServer`): receives tool calls from agent/client over in-memory transport.
5. **`scopedTool` pipeline**: applies driver/control signals, scope/policy/lock/foreground/handoff/action-guard checks, executes handler, settles screen, appends post-action observation, audits/logs/traces, updates caches/evidence/learnings.
6. **Android bridges** (`app/mcp/bridge`, accessibility, notifications, browser, etc.) perform real actions and collect observations.
7. **Loop continuation/end:** strategy re-injects vision/ledger context, evicts stale perception, handles defects/budgets; accepted `end_session` can short-circuit to completion.
8. **UI/reporting:** status strip, overlays, run report, logs/traces, optional memory diary writes.

### 2.2 Remote MCP flow (computer → phone)
1. Phone starts LAN signaling/WebRTC MCP server and shows pairing PIN/approval UI.
2. `aura-mcp-connect` discovers phone (saved host/mDNS/subnet scan), pairs once (PIN + SAS/verification code), stores trust token material.
3. Local daemon (`127.0.0.1:<port>/mcp`) owns single WebRTC link to phone; stdio clients proxy through it.
4. Each approved client gets a fresh MCP session/principal with configured toolsets/scopes; tool calls traverse same server safety pipeline.

### 2.3 Design boundaries
- **Prompt guidance is advisory; enforcement is server-side** (`scopedTool`, policy gates, guards).
- **UI tree is structural truth; vision is fallback/augmentation** (especially for tree-blind surfaces).
- **`som_id` is the action handle**; coordinates are resolved server-side from cache.
- **Remote access is capability-scoped and revocable**.

---

## 3. Repository Modules

| Module/Path | Responsibility | Key Technologies |
|---|---|---|
| `aura-android/app` | Android app: UI, agent, accessibility/gestures, perception impl, voice, overlay, services, remote gating, app↔MCP bridges | Kotlin, Compose, Hilt, Room, DataStore, WorkManager, Koog, MCP client, ONNX Runtime, ML Kit, sherpa-onnx, Firebase |
| `aura-android/mcp-server` | On-device MCP server: tools, safety/policy, prompts/resources, LAN/WebRTC transport, pairing, caches | Kotlin library, MCP Kotlin SDK server, WebRTC, kotlinx.serialization/coroutines |
| `aura-mcp-connect` | Desktop local daemon + stdio shim + pairing/discovery + optional `aura-adb` | Node.js (>=18), `@modelcontextprotocol/sdk`, `@roamhq/wrtc` |
| `benchmark` | AndroidWorld/AURA evaluation harness, runners, reporting | Python/HTML/assets |
| `scripts` | Eval, MCP tests, conversion/bench/report utilities | Python/Node/PowerShell |
| `docs`, `models`, `website`, `design` | Eval docs (generated), model metadata, website/design assets | Markdown/static assets/config |

---

## 4. Feature & Function Catalog

## 4.1 On-Device Agent Runtime

### Features
- **Goal-driven automation run** on a real device (fresh run, saved-settings run, resumed run).
- **Provider/model-agnostic LLM execution** with BYOK credentials and endpoint catalog/custom endpoints.
- **Vision-aware tool loop** with screenshot re-injection, stale perception eviction, ledger re-injection, defect repair, and budget-aware termination.
- **Integrated subsystems**: tools (MCP), hooks, deferred tool loading, skills, memory, learnings hints, research, HITL (`ask_user`), proof/completion, status narration, tracing/logging.
- **Destructive-goal pre-dispatch refusal** for bulk destructive intents.

### How it works
- `AuraAgent` (`app/agent/AuraAgent.kt`) is the orchestration entry point:
  - Resolves endpoint/model/generation and validates saved configuration.
  - Creates app bridges and connects an in-process MCP server/client.
  - Builds gated tool registry + hook chain + optional remote MCP attachment + skills/memory/ledger/ask-user/proof/research attachments.
  - Assembles system prompt (doctrine + device/profile context + tool inventory) and user-channel preambles/hints.
  - Runs `auraVisionToolLoopStrategy` (`agent/strategy/AuraVisionStrategy.kt`) to interleave model reasoning, tool execution, perception re-injection, and completion.
  - Manages run lifecycle: keep-awake, control-lock integration, pause checkpoints, run-end hooks, finalization/reporting, cancellation/error handling.
- Strategy specifics:
  - Re-injects current SoM screenshot after vision-producing tool turns (keeps only latest).
  - Evicts stale perception text and prior screenshot when superseded.
  - Repairs defective turns (thinking-only, tool-call-as-text) within bounded nudges.
  - Applies token/wall-clock/request budget logic and context compaction heuristics.

### How to implement
1. **Create an agent orchestrator** that builds one run context (goal, identity, config, tool registry, sinks, trace).
2. **Use MCP as the action boundary** (even in-process) so every action passes the same policy pipeline.
3. **Implement a tool-loop strategy** with:
   - tool-call execution + result injection
   - perception image/text lifecycle management
   - prompt compaction/eviction
   - failure/defect repair and bounded retries
4. **Attach state systems**: ledger, memory, skills, HITL, proof/completion, and logging.
5. **Add honest termination paths**: success/partial/failure/cancelled/quota/budget/time with user-facing summaries.

### Requirements
- **Code/components:** agent orchestrator, LLM client/provider registry, MCP client/server integration, strategy graph/loop, run state store, trace logger.
- **Config:** API key/provider/model selection, generation/runtime limits, feature toggles.
- **Runtime:** coroutine-capable runtime, secure storage for keys (recommended), persistent storage for ledgers/logs if resumability/history needed.

## 4.2 MCP Server Core & Tool Execution Pipeline

### Features
- **Single composition root** for server capabilities (`McpServerBuilder`).
- **Per-request enforcement chokepoint** (`scopedTool`) for all tools.
- **Per-server sinks/gates/caches**: audit, session, tool-phase, trail, post-action observer, foreground/control/handoff gates, action guard, learnings dispatch.
- **In-process and remote-serving modes** sharing the same tool/policy semantics.
- **MCP resources/prompts/instructions** for agent guidance.

### How it works
- `McpServerBuilder.build(...)` registers:
  - Tool groups by `Toolset` (`CORE`, `ASSISTANT`, `BROWSER`)
  - Resources (`McpResources`) and prompts (`McpPrompts`, skill prompts)
  - Shared caches (`PerceptionCache`, `CvMemo`, `LastSeenScreen`) and settle/observation hooks
- `scopedTool(...)` (`server/ScopedToolRegistration.kt`) wraps every handler with:
  1. Driver/control signaling and principal/scope checks
  2. `SensitivePolicy` evaluation (fail-closed)
  3. `ControlLockGate` (human pause precedence), `BrowserHandoff` rules, `ForegroundGuard`
  4. `ActionGuard` (grounding/loop/finish/resource-required checks)
  5. Tool start/end audit/session/phase events
  6. Handler execution + optional post-action settle/observation append
  7. Screen generation/completion-evidence/OTP/learnings updates and result normalization
- `InProcessMcpServer` provides linked in-memory transports and injects an internal principal for local agent calls.

### How to implement
1. **Define one tool registration wrapper** and forbid bypass paths.
2. **Order gates deliberately** (permanent denials vs temporary pauses; cheap checks before expensive reads).
3. **Standardize result/error envelopes** (`policy_blocked`, `scope_denied`, `action_guard`, etc.).
4. **Centralize cross-cutting sinks** (audit, tracing, UI state, session logs).
5. **Attach post-action observation** for mutating UI tools to reduce verification round trips.

### Requirements
- **Dependencies:** MCP server SDK, JSON serialization, concurrency primitives.
- **State:** per-server sink bundle, per-session/per-client principal context, screen caches/generation counters.
- **Testing:** unit tests for gate order and refusal semantics; mock/in-process tool-bench scenarios.

## 4.3 MCP Tools (Complete Functional Surface)

> **Source of truth in code:** `McpToolCatalog`, `McpToolScopes`, `McpServerBuilder`, `tools/*`, and `Toolset`.  
> **Note:** Availability can vary by toolset (remote client config), host bridge support, permissions, and build flags (e.g. `run_steps` is not registered by default in real servers).

### 4.3.1 See / Verify / Session (READ-oriented)

| Tool | Function | How it works | Implementation notes | Requirements |
|---|---|---|---|---|
| `get_device_status` | Reports device/runtime status (battery/network/model/screen/service/foreground signals where available) | Reads from device bridge/system state | Expose only non-sensitive status; keep schema stable | Device info APIs; service state |
| `read_screen` | Default low-cost screen understanding with actionable `som_id`s | Settles UI, extracts tree elements, renders positional/grid payload, caches som map/signature; supports `changes_only`, `query`, optional annotated image | Must ground taps; share settle logic with post-action observer; cheap-first path | Accessibility/UI tree access; optional screenshot/annotate stack |
| `perceive_screen` | Escalated visual perception (annotated screenshot + element list) | Captures screen, tree-first element extraction, optional CV/OCR merge, tiering/escalation/memoization, role-colored boxes; returns `e` array (index = `som_id`) | Use when tree insufficient/visual target; keep payload token-efficient | Screenshot capture, UI tree, (optional) ONNX/OCR models |
| `get_screenshot` | Returns raw screenshot image | Captures current frame via screenshot bridge | No element numbering guarantee for tapping; treat as visual evidence only | Screen capture permission/path |
| `request_screen_capture_permission` | Requests capture consent/capability | Triggers host flow to grant capture access | Must be user-consented; return clear status | Android MediaProjection/host consent flow |
| `watch_device_events` | Streams/drains recent UI/device events | Buffers accessibility/system events with bounded window | Useful for wait/observe flows; avoid unbounded buffers | Accessibility event capture |
| `verify_action` | Collects post-action evidence (foreground, counts, labels, keyboard/loading hints) | Reads current UI state and returns evidence without verdict | Encourage use only when post-action observation is ambiguous | UI tree access |
| `wait_for` | Waits for text/conditions or loading changes | Polls UI state until condition/timeout | Use for slow async transitions (not after every tap) | UI snapshots, timer/coroutines |
| `end_session` | Closes a task/session with outcome/reason/report | Validates completion evidence for certain success claims, records session boundary, restores keyboard state, dispatches learnings hooks | Must not allow dishonest success on high-stakes goals; `reason` is user-facing | Session logger/sinks, completion evidence state |

### 4.3.2 Touch / Navigation / Input (WRITE-oriented)

| Tool | Function | How it works | Implementation notes | Requirements |
|---|---|---|---|---|
| `tap` | Tap/double-tap/long-press by `som_id` (and supported styles) | Resolves element from perception cache, dispatches gesture, verifies/reconciles as implemented, settles and observes | `som_id` must be fresh; refuse stale/ambiguous targets | Accessibility gestures, perception cache |
| `swipe` | Directional swipe or draw/trace strokes | Computes gesture path over element/screen; supports drag/draw semantics where exposed | Use for carousels, drags, signatures/patterns (per schema) | Gesture injector, bounds resolution |
| `scroll` | Scroll container or screen in direction | Targets scrollable element or viewport; may return updated view/state | Prefer element-targeted scroll when needed | Gesture/UI semantics |
| `type_text` | Types text into focused/targeted field | Uses accessibility input and/or IME fallback path depending on target/app behavior | Subject to `SensitivePolicy`; may require AURA keyboard for some apps | Accessibility service, optional IME |
| `set_value` | Sets field/switch value deterministically where supported | Applies direct value change/interaction and confirms result | Safer than blind typing/toggling loops when available | UI node actions/state access |
| `press_enter` | Submits/commits current field | Sends enter/submit action to focused input | Useful after search/recipient entry | Input/IME/action support |
| `press_key` | Global navigation keys (`back`, `home`, `recents`) | Dispatches system/global accessibility actions | Works across apps/screens | Accessibility/global actions |
| `clipboard` | Read/write clipboard (per implementation/support) | Bridges clipboard manager operations | Android clipboard access constraints apply (foreground/IME restrictions possible) | Clipboard permission/context constraints |
| `launch_app` | Opens app by package/name resolution | Resolves/launches intent for app; may return learned hints/app-link context | Refuses if already foreground in some flows (to avoid resetting user state) unless forced by design | Package manager/query, intent launch |
| `lookup_app` | Resolves user-facing app name to installed package/app info | Searches installed apps and returns matches/candidates | Used by deep-link/app resolution helpers | Package visibility/query permission |

### 4.3.3 Shortcuts / Deterministic Navigation

| Tool | Function | How it works | Implementation notes | Requirements |
|---|---|---|---|---|
| `list_app_deeplinks` | Discovers deep links/shortcuts for an app | Reads manifest/link metadata and/or known catalogs/providers | Prefer high-trust/verified entries; inspect templates/requirements | Package query, deeplink catalog/introspection |
| `resolve_deeplink` | Validates/resolves deeplink target before opening | Parses URI/template and checks resolvability/safety constraints | Use before risky/templated opens | Intent resolution, URI parsing |
| `open_deeplink` | Opens deep link directly | Fires VIEW/shortcut intent with screening/pinning and post-action observation | Can be blocked by sensitive policy/foreground rules | Intent dispatch, URI permissions |
| `system_intent` | Executes standard system actions | Plans and dispatches intents for `set_alarm`, `set_timer`, `dial`, `compose_sms`, `add_calendar_event`, `share_text`, `navigate` | Many actions open prefilled UI requiring user confirmation; alarms/timers may apply directly | Target apps/providers, Android intent support |
| `web_search` | Web search for grounding/task guidance | Calls configured search bridge/provider (Tavily in AURA app wiring) and returns results | Useful for unfamiliar flows; treat results as untrusted content | API key/network (Tavily), bridge config |

### 4.3.4 Assistant Plane (notifications, media, contacts, files)

| Tool | Function | How it works | Implementation notes | Requirements |
|---|---|---|---|---|
| `read_notifications` | Reads notifications from listener | Queries notification bridge/store snapshot | Filter/scrub sensitive notifications as policy requires | Notification Listener access |
| `notification_action` | Triggers notification action (including direct reply when supported) | Invokes action PendingIntent/RemoteInput path via bridge | Useful for quick replies without opening app | Notification action support/permissions |
| `dismiss_notification` | Dismisses notification | Sends cancel command via listener bridge | May not apply to all ongoing/system notifications | Notification Listener access |
| `get_media_sessions` | Lists active media sessions/playback state | Reads media session manager/bridge state | Prefer before issuing media controls | Notification/media session access |
| `media_control` | Controls playback (play/pause/next/prev/stop etc.) | Sends transport commands to active/target session | Cross-app; no UI gestures needed | Media session permissions/access |
| `resolve_contact` | Resolves person/name to contact details/number | Searches contacts provider and returns matches/disambiguation | Never guess when ambiguous; ask user | `READ_CONTACTS` permission |
| `find_files` | Searches local files/media | Queries storage/media providers/indexes with filters/query | Return URIs/metadata suitable for follow-up actions | Media/file read permissions (scoped by Android version) |
| `open_file` | Opens file in target app/viewer | Dispatches content/file intent with grants/chooser handling | Validate URI/source and app availability | File/content URI access, viewer app |

### 4.3.5 Browser Plane (DOM-level)

| Tool | Function | How it works | Implementation notes | Requirements |
|---|---|---|---|---|
| `browser_open` | Opens/navigates to URL or search target in AURA browser | Creates/reuses browser session/tab in app-controlled WebView/browser surface | Prefer over screenshot-tapping web pages | Browser bridge + WebView/window capability |
| `browser_read` | Returns page content and interactive elements with `el_id`s | Extracts DOM/text/element snapshot via injected scripts/models | Default page observation call; generation-sensitive handles | Page script bridge, active tab |
| `browser_find` | Finds text/element in page | Searches DOM/content and returns matching target info/scrolls as needed | Faster than manual scroll loops | DOM query support |
| `browser_act` | Performs DOM action (click/type/select/submit/scroll/back/etc.) by handle | Resolves `el_id`/selector mapping, executes action, returns resulting page/state | Stale handles refused; act+observe in one call | JS bridge/page control, current generation |
| `browser_wait` | Waits for text/state/load condition | Polls page until condition/timeout | Use for async page changes | Page polling hooks |
| `browser_screenshot` | Captures browser page image | Renders/captures current page view | Use only when visual/layout matters; DOM read preferred | WebView capture support |
| `browser_extract` | Extracts structured rows/data from page | Runs extraction heuristics/scripts against DOM | Useful for lists/comparisons | Extraction scripts/models |
| `browser_upload` | Uploads file into page input | Bridges file chooser/upload path using provided URI | Requires valid file URI and page input | File access + WebView upload handling |
| `browser_tabs` | Lists/opens/switches/closes tabs | Manages in-app browser tab/session state | Enables compare workflows without losing context | Tab/session manager |
| `browser_handoff` | Hands page to user for credentials/OTP/CAPTCHA/payment/confirmation | Shows live page UI for user action; enforces handoff read/write rules while active | AURA must not complete login/payment secrets | Overlay/window UI, handoff state tracking |
| `browser_close` | Closes browser session/surface | Tears down browser state/resources | Call when web task done | Session lifecycle management |

### 4.3.6 Optional/Internal/Context-Dependent Tools
| Tool / Function | Status | Notes |
|---|---|---|
| `run_steps` | Present in codebase, **not registered by default** for real servers | Batch step execution path exists for tests/experiments; enabling changes tool descriptions/routing and requires consistency updates. |
| Client-side agent tools (e.g. `load_tools`, `ask_user`, ledger/memory/proof/skills/research helpers) | Exposed in agent-side registry/runtime (not all as MCP server tools) | Implement as local tool wrappers/hooks in your agent runtime; keep them policy-safe and auditable. |

## 4.4 Screen Perception & Grounding

### Features
- **Two-tier perception:** `read_screen` (default, cheap, structural) and `perceive_screen` (visual escalation).
- **Set-of-Marks grounding:** actionable elements identified by `som_id`; server resolves coordinates.
- **UI-tree + CV/OCR fusion:** tree-first authoritative bounds/labels, OmniParser/ML Kit augmentation for tree-blind or missing elements.
- **Settle-aware reads and post-action observation integration** for lower latency and fewer redundant checks.
- **Caching and escalation logic:** perception cache, CV memo, screen signatures/generation/activity.

### How it works
- **`read_screen` (`mcp-server/tools/ReadScreenTool.kt`)**
  - Waits/settles screen via shared settle logic.
  - Extracts and renders actionable elements/flags/containment/offscreen hints.
  - Supports focused views (`changes_only`, `query`) and optional annotated image (tree-only role boxes).
  - Updates `PerceptionCache` and last-seen screen state.
- **`perceive_screen` (`mcp-server/tools/PerceiveScreenTool.kt`)**
  - Captures screenshot + UI tree concurrently.
  - Decides tier (`tree_only` vs `full`) using sufficiency/degradation/repeat heuristics (and forced escalation rules).
  - Runs detection/OCR when needed, merges tree and CV elements (with dedupe/label/promotion logic), renumbers/flags, annotates image, returns compact `e` payload.
  - Caches CV/som results by screen signature and records escalation metadata.
- **App perception bridges (`app/mcp/bridge`)**
  - `AppScreenshotBridge`: frame capture and permission/readiness handling.
  - `AppUiTreeBridge`/`UITreeExtractor`: tree snapshots/events.
  - `AppPerceptionBridge`: YOLO/ONNX detection + ML Kit OCR + annotation rendering; provider/timing diagnostics.

### How to implement
1. **Implement UI tree extraction** with element bounds, text/desc/role/state, interactivity, scrollability, visibility, and stable labeling heuristics.
2. **Implement `read_screen` payload format** optimized for model use (`som_id`, flags, containment, query/diff modes).
3. **Add screenshot capture pipeline** with consent, serialization, and timeouts.
4. **Add optional CV/OCR escalation** only when tree coverage is insufficient or visual reasoning is required.
5. **Maintain a perception cache** keyed by screen signature/generation; invalidate on UI-mutating actions.
6. **Enforce `som_id`-only action targeting** in gesture tools.

### Requirements
- **Permissions:** Accessibility service; screen capture/MediaProjection path for screenshots/perception.
- **Libraries/models:** ONNX Runtime and detector model, OCR (e.g. ML Kit), image processing/rendering utilities.
- **Performance:** background inference, time budgets, memoization/caching, payload size controls.
- **Device:** sufficient CPU/GPU/NPU support for chosen runtime providers.

## 4.5 Accessibility, Gestures & Device Control

### Features
- **Accessibility-backed UI observation and action execution** (service, event stream, node traversal/focus).
- **Gesture set:** tap, double tap, long press, swipe, scroll, multi-stroke traces/draw paths, global keys.
- **Tap robustness:** dispatch result checks, reaction observation, semantic fallback when gesture injection is ignored.
- **Text entry robustness:** accessibility text actions, targeted typing, IME fallback for node-less/custom-rendered inputs, keyboard suppression/restore.
- **Device/app controls:** launch/lookup apps, volume/media-adjacent actions, clipboard, device/foreground status, recents/home/back.

### How it works
- `AuraAccessibilityService` (`app/accessibility/AuraAccessibilityService.kt`) is the execution core:
  - Captures accessibility events (buffered for MCP), tracks foreground/app/window context, filters self/overlay noise.
  - Dispatches gestures via injector/dispatcher/builder stack (`accessibility/gesture/*`).
  - Verifies tap effects using event/repaint signals and can perform semantic click fallback.
  - Coordinates overlays/cursor/feedback, keyboard lease/restore, and control-lock/touch signals.
- `AppDeviceBridge` (`app/mcp/bridge/AppDeviceBridge.kt`) adapts MCP `DeviceBridge` to service/app APIs:
  - Synchronous/awaited gesture outcomes for agent/tool reliability.
  - App lookup/launch, clipboard, device status, focused/coordinate-based typing with self-heal/fallback attempts.

### How to implement
1. **Implement an accessibility service** with permissions, settings/onboarding flow, and event ingestion.
2. **Build a gesture layer** supporting coordinates, directions, durations, and multi-stroke paths; marshal to main/UI-safe execution as required.
3. **Add verification hooks** (did UI change? was gesture accepted?) and fallback strategies.
4. **Implement text input adapters** for native nodes and IME fallback; handle focus and keyboard lifecycle explicitly.
5. **Expose a device bridge interface** consumed by MCP tools to decouple policy from Android specifics.

### Requirements
- **Permissions:** `BIND_ACCESSIBILITY_SERVICE`, overlay/system permissions as needed, input method enablement for IME fallback.
- **Android APIs:** AccessibilityService, gesture dispatch, package manager, clipboard/input method services.
- **Reliability:** concurrency control, timeout/await semantics, OEM-specific behavior testing.

## 4.6 Deterministic Action Planes (System Intents, Deep Links, Notifications, Media, Contacts, Files)

### Features
- **Prefer deterministic/non-visual channels** to avoid brittle UI navigation.
- **System intents:** alarms/timers/calls/SMS/calendar/share/navigation.
- **Deep links & app shortcuts:** discover, resolve, and open direct routes.
- **Assistant plane:** notification read/action/reply/dismiss, media session inspection/control.
- **Context helpers:** contact resolution, file search/open.

### How it works
- `SystemIntentTools` + planner/bridge validate actions/args and dispatch Android intents (some prefilled/confirmation-only, some direct-apply).
- `DeepLinkTools` + `AppDeepLinkBridge` enumerate/resolve URIs and open with safety screening and package pinning where possible.
- `NotificationTools`, `MediaTools`, `ContactTools`, `FileTools` call corresponding app bridges/providers and return structured results with hints/fallback guidance.
- Tool descriptions/prompts/instructions prioritize these routes over gestures when applicable.

### How to implement
1. **Model each plane as explicit tools** with strict schemas and result contracts.
2. **Add intent planners/validators** (required args, normalization, confirmation semantics).
3. **Implement deeplink discovery/resolution** (manifest introspection + curated catalogs if useful) and enforce URI/scheme screening.
4. **Integrate Android providers/managers**: NotificationListenerService, MediaSessionManager, ContactsContract, MediaStore/SAF/file viewers.
5. **Return actionable errors/hints** (permission missing, no handler, ambiguous contact, not found).

### Requirements
- **Permissions:** notification access, contacts, media/files (scoped by API level), `SET_ALARM` and relevant app capabilities.
- **System dependencies:** installed target apps/handlers, active media sessions, accessible providers.
- **Policy:** screen sensitive content and block restricted targets/channels.

## 4.7 Browser Automation Plane

### Features
- **DOM-level browser control** in AURA-managed browser surface (not pixel tapping on arbitrary browser UIs).
- **Tools:** `browser_open`, `browser_read`, `browser_find`, `browser_act`, `browser_wait`, `browser_screenshot`, `browser_extract`, `browser_upload`, `browser_tabs`, `browser_handoff`, `browser_close`.
- **Stateful element handles (`el_id`) with generation/staleness checks**.
- **User handoff mode** for login/OTP/CAPTCHA/payment/manual steps.

### How it works
- `BrowserTools` (`mcp-server/tools/BrowserTools.kt`) defines the model-facing contract and handle/session mapping.
- `BrowserBridge` abstraction (`mcp-server/bridge/BrowserBridge.kt`) isolates engine implementation (`scratch`/`mine` modes supported by contract).
- App implementation (`app/mcp/bridge/AppBrowserBridge*`, `browser/*`) drives WebView/page scripts, tab/session lifecycle, extraction, uploads, screenshots, and handoff UI/window state.
- `BrowserHandoff` gate in server core restricts writes while user controls the page and allows safe polling/reads.

### How to implement
1. **Create a browser bridge interface** with open/read/act/find/wait/extract/tabs/upload/screenshot/handoff/close.
2. **Inject page scripts** to extract text/elements and execute DOM actions; assign opaque, generation-scoped handles.
3. **Return page snapshots after actions** (act+observe) to reduce round trips.
4. **Implement handoff state machine** (enter, poll, user-done/auto-end, resume with fresh page state).
5. **Add security checks** for URL schemes, upload sources, and sensitive actions.

### Requirements
- **Android/UI:** WebView, overlay/window management, JS bridge, file chooser/upload handling.
- **State:** tab/session store, handle-to-selector map, generation counters.
- **UX/Safety:** visible handoff prompts, no credential exfiltration/automation in restricted steps.

## 4.8 Voice, Wake Word & Conversation (Gemini Live)

### Features
- **Offline wake word detection** ("Wake up AURA") using sherpa-onnx keyword spotting.
- **Voice command capture/STT/TTS pipeline** with listening mode coordination.
- **Real-time conversation mode** (Gemini Live) with audio streaming and tool handoff to agent.
- **Mic arbitration** (single active audio consumer) and narration/task audio gating.

### How it works
- `WakeWordDetector`/`SherpaKwsWakeWordDetector` runs mic audio loop, detects keyword tokens, stops and hands off on trigger.
- `WakeWordListeningService` and voice controllers manage lifecycle/mode transitions (passive wake vs active capture vs live session).
- `VoiceCaptureController`, `ListeningModeController`, local STT/TTS components handle command-mode speech interactions.
- `agent/conversation/*` (`LiveSession`, protocol/tool declarations, live controller, narration/task gates) manages Gemini Live session state and routes device actions through controlled tool paths (e.g. `ask_aura`/agent delegation).

### How to implement
1. **Implement wake word engine** (offline preferred) with explicit mic start/stop and callback handoff.
2. **Centralize audio mode control** to prevent concurrent mic users.
3. **Build STT/TTS command mode** with interruption/stop handling and overlay state feedback.
4. **Add streaming conversation mode** (optional) with protocol parser, tool declarations, and guarded action delegation to your automation agent.
5. **Expose user controls**: enable/disable wake word, permissions, voice selection, conversation mode settings.

### Requirements
- **Permissions:** `RECORD_AUDIO`, foreground service/mic service types, notifications (service visibility).
- **Assets/models:** wake-word model/token/keyword files, STT/TTS models if local.
- **Network/keys:** provider APIs/keys for cloud STT/LLM/live conversation as configured.
- **Performance:** low-latency audio processing, robust lifecycle handling.

## 4.9 Memory, Skills, Learnings, Research, HITL & Proof

### Features
- **User memory** (recall/save/forget semantics) for durable assistant context.
- **Action/history logging** for prior task outcomes (opt-in/context-dependent).
- **Skills system** (bundled/user skills) for reusable playbooks; surfaced to agent and MCP prompts.
- **Learned hints / route learnings** from successful runs (structure-focused, privacy-aware).
- **Pre-task/on-demand research** (web/help lookup) to ground unfamiliar flows.
- **HITL (`ask_user`)** for clarifications/confirmations and user choices.
- **Proof/completion subsystem** to support verified outcomes and reduce false success claims.

### How it works
- Memory/services/tools operate on encrypted/local stores and inject relevant memory into user-channel context with safety fencing.
- Skills are attached additively (prompt/tool/resource/prompt exposure) and do not bypass policy/tool gating.
- Learnings hooks/gateway capture route evidence and re-surface hints on future matching goals/sessions (with sanitization/constraints).
- Research components query/search and synthesize guidance (often in parallel or auxiliary paths) without directly mutating device state.
- HITL broker/UI integration pauses for user response and resumes run context.
- Proof modules track observed evidence/findings/step checks and inform completion judgments/reports.

### How to implement
1. **Separate durable memory from run ledger** (cross-run facts vs per-run state).
2. **Fence untrusted/model-/screen-derived content** when injecting into prompts (user channel, labeled blocks).
3. **Implement skill loading and invocation** as additive guidance, never privilege escalation.
4. **Capture learnings from verified outcomes only** and scrub sensitive data.
5. **Add HITL primitives** with timeout/cancel semantics and UI integration.
6. **Track evidence for completion claims** (especially for high-stakes outcomes).

### Requirements
- **Storage:** encrypted local store/DB/files for memory/skills/learnings metadata.
- **UI:** skill/memory management screens, HITL dialogs/overlays.
- **Policy:** PII/secrets scrubbing, no sensitive-content persistence in learnings where prohibited.

## 4.10 Run Ledger, Resumability, Budgets & Reliability Controls

### Features
- **Run ledger**: goal, plan steps, facts, dead ends, progress/outcome tracking.
- **Resumable runs**: checkpoint persisted state and resume with preamble/re-verification.
- **Budgeting**: wall-clock/request/token-estimate controls and graceful stop/closing behavior.
- **Pause/control-lock handling**: human interruption checkpoints and safe continuation semantics.
- **Loop quality controls**: stale-perception eviction, compaction, defect nudges/repair, action-guard feedback.

### How it works
- `agent/ledger/*` maintains mutable run state with persistence hooks (debounced writes, flush on critical points) and renders ledger into prompt each turn.
- `ActiveRunRegistry` and control-lock integration support steering/pause/checkpoint behavior.
- `RunBudget`/`AgentContextConfig` and strategy logic enforce limits, trigger compaction/closing turns, and prevent runaway loops.
- Resume flow seeds from prior ledger (not blind replay of stale UI/tool history) and instructs re-verification on live screen.

### How to implement
1. **Define a ledger schema** (goal, plan, step statuses, facts, blockers, outcome, timestamps, counters).
2. **Persist incrementally** and flush on pause/finalization/crash-sensitive boundaries.
3. **Inject a fresh ledger summary each turn** so compaction cannot lose task context.
4. **Implement resume as “continue with evidence re-check”**, not transcript replay.
5. **Track and expose budgets/limits** in traces and UI.

### Requirements
- **Storage:** durable local store (encrypted recommended), IDs/versioning.
- **Concurrency:** thread-safe controller/registry.
- **UX:** resume prompts, pause indicators, partial-result reporting.

## 4.11 Overlay, App UI & User Controls

### Features
- **System overlay assistant UI**: input/transcript, listening/thinking/acting states, HITL prompts, pause/stop controls.
- **Run status/report surfaces**: status strip, pause pill, run report card (markdown-capable).
- **App screens** for setup and management (provider/brain, tools/hooks/skills/memory, MCP center, trusted devices, restricted apps, permissions, activity/logs/traces, voice/companion, eval debug screens).
- **Permission and onboarding flows** (accessibility, overlay, mic, notifications, capture, etc.).

### How it works
- Overlay services/windows render Compose/views outside normal activity flow and must handle no-window/activity assumptions in some contexts.
- `AuraAppShell` and presentation modules route settings/agent/activity surfaces; permission registry centralizes permission metadata and launch targets.
- Control state is shared with runtime components (agent status, pause/control lock, remote run/report state).

### How to implement
1. **Separate in-app UI and overlay UI lifecycles**; design overlay components to run without activity dependencies.
2. **Centralize permission metadata** (description, rationale, settings intent, required/optional).
3. **Build a status/report model** decoupled from model text (use structured events/states).
4. **Add trusted-device and MCP control panels** if remote access is supported.
5. **Keep accessibility of controls high** (pause/stop always reachable).

### Requirements
- **Permissions:** `SYSTEM_ALERT_WINDOW`, notifications, foreground service types as applicable.
- **UI stack:** Compose/Views, window manager overlay handling, accessibility-aware UX.
- **State management:** observable stores/flows for run and service status.

## 4.12 Remote Computer Control (LAN MCP + Desktop Bridge)

### Features
- **Phone-hosted MCP server over local network** (WebRTC DataChannel, LAN signaling HTTP endpoints).
- **Pairing & trust management**: one-time PIN, user approval, verification code/SAS, reconnect challenge-response, trusted device list, revocation.
- **Per-client principals and toolsets** (`CORE`, `ASSISTANT`, `BROWSER`) with server-side enforcement.
- **Desktop bridge daemon** (`aura-mcp-connect`): discovery, pairing, shared local MCP endpoint, stdio shim for MCP clients.
- **Optional host-side `aura-adb` tool** (developer opt-in) in bridge daemon.

### How it works
- **Phone side (`mcp-server/lan`, `bridge`, `McpServerController`)**
  - Binds LAN signaling server (`/aura/info`, `/aura/connect`) on local port range, validates peer/network/request constraints.
  - Handles pairing PIN/token-hash flows, approval prompts, trust persistence, and WebRTC session setup.
  - Creates per-client MCP server sessions bound to token principals and allowed toolsets.
- **Desktop side (`aura-mcp-connect/src`)**
  - Discovers phone (saved address, mDNS, subnet scan), performs pairing and reconnect auth.
  - Runs local daemon (Streamable HTTP on loopback) sharing one phone link; stdio shim proxies client requests.
  - Stores pairing/trust material locally (e.g. `~/.aura/webrtc.json`).

### How to implement
1. **Implement LAN-only signaling and peer validation** (no public relay; reject browser-origin abuse paths).
2. **Design pairing as two-step trust**: possession/entry (PIN) + user-verifiable approval (SAS/fingerprint code).
3. **Use reconnect proofs** (challenge-response/HMAC) without resending long-term secrets.
4. **Bind each remote session to a principal** with scopes/toolsets and audit labels.
5. **Provide a local daemon abstraction** for multi-client reuse and startup resilience.
6. **Add device management UI** (approve/deny/revoke, rename, toolset toggles, status).

### Requirements
- **Networking:** local network reachability, mDNS/broadcast allowances (network-dependent), WebRTC support.
- **Crypto/security:** secure random tokens, hashing/HMAC, DTLS/WebRTC, protected local credential storage.
- **Desktop runtime:** Node.js >= 18, native WebRTC dependency support.
- **Mobile UX:** pairing screen/PIN, approval dialog, trusted devices management.

## 4.13 Safety, Privacy, Policy & Compliance Enforcement

### Features
- **Hard-block sensitive actions** (fail-closed) at MCP tool boundary.
- **Scope-based authorization** and per-client toolset restrictions.
- **Human control precedence** (pause/control lock) and browser handoff restrictions.
- **Foreground-app protection** for sensitive app contexts.
- **OTP/credential/card/SSN-style exfiltration guards** on relevant outgoing/input channels.
- **Completion/evidence safeguards** to reduce false claims of success.

### How it works
- `SensitivePolicy` classifies and blocks tool calls/args (apps/packages/schemes/hosts/text/URLs/forms/queries) including spoken-request screening hooks.
- `ScopedToolRegistration` enforces gate order and returns structured refusals.
- `ForegroundGuard` prevents interaction/perception in protected foreground contexts where applicable.
- `ControlLockGate` and pause mechanisms yield to user interaction; `BrowserHandoff` limits agent writes during user takeover.
- `ActionGuard`, `CompletionEvidence`, `OtpSighting`, and audit/trail sinks provide behavioral and forensic controls.

### How to implement
1. **Implement policy as code, not prompt-only rules**; evaluate before handler execution.
2. **Use explicit refusal codes and messages** for model/user handling and audits.
3. **Separate temporary vs permanent denials** (`paused_by_user` vs `policy_blocked`) for correct retry behavior.
4. **Screen all exfiltration paths** (typing, clipboard, intents, browser fields/URLs, notifications/messages, uploads).
5. **Log policy decisions** with minimal sensitive data exposure.

### Requirements
- **Data:** maintained blocklists/patterns/schemes, category taxonomy, policy tests.
- **Architecture:** central chokepoint, principal/scope context, audit logging.
- **Privacy controls:** redaction/scrubbing policies for logs, learnings, traces.

## 4.14 Logging, Tracing, Diagnostics, Eval & Remote Gating

### Features
- **Audit and session logging** for tool calls, refusals, outcomes, and evidence.
- **Agent run traces** (LLM/perception/tool events, reports, verdict metadata).
- **UI for activity/log review** (session lists/details, traces, retention settings).
- **Eval support** (debug cockpit, task assets, scripts/harness integration).
- **Remote gating/controls** (kill switch, min version, update prompts, blocklist/suspension) with cached/offline-safe behavior (Firebase-dependent in current app).

### How it works
- Sinks/hooks (`McpAuditLogger`, session sinks, tool trail/phase sinks, agent taps/loggers) capture runtime events at chokepoints.
- App UI screens render sessions/traces/reports; retention/cleanup components manage stored artifacts.
- Eval assets and scripts define tasks/verdicts and enable reproducible evaluation workflows.
- `remote/*` components evaluate gate inputs/verdicts, persist/hydrate state, and enforce blocks across entry points/services.

### How to implement
1. **Instrument at the chokepoint** (tool start/end, gate decision, settle/observe, completion) instead of ad hoc logging.
2. **Define structured trace schemas** (timestamps, principal, tool, args summary, outcome, latency, refusal reason).
3. **Separate user-facing reports from forensic logs** and apply redaction rules.
4. **Build eval tasks with truth checks** and a verdict taxonomy (`pass/fail/refused/invalid`).
5. **Implement remote gates with safe defaults** (fetch fail-open where appropriate, cached block enforcement, version checks).

### Requirements
- **Storage:** log DB/files, retention policies, export/redaction utilities.
- **UI:** trace/session viewers, diagnostics settings.
- **Optional services:** Firebase Remote Config/Firestore/Auth/Crashlytics/Analytics (or alternatives).
- **Testing:** log schema tests, eval harness scripts, regression baselines.

## 4.15 Debug/Developer/Internal Functions

> These are useful for development/testing but may be gated, disabled by default, or unsuitable for production exposure.

| Function/Area | Purpose | How it works | Implementation caution |
|---|---|---|---|
| Debug agent spike broadcast receiver | Trigger on-device agent test runs externally in debug builds | `AssistantForegroundService` registers exported receiver when `BuildConfig.DEBUG` | Keep debug-only; never ship exported privileged test hooks in release |
| Eval cockpit/screens/assets | Run and judge evaluation tasks on-device | Debug UI + `assets/eval/tasks.json` + logging/reporting integration | Isolate from production UX and ensure no benchmark data pollutes user memory/log semantics |
| Tool bench (`mcp-server` tests) | Adversarial validation of tool behavior with mock phone | Scenarios run against mock bridge implementations and generate reports | Maintain alongside tool/policy changes |
| `run_steps` (gated/disabled by default) | Batch multiple actions in one tool call (experimental/tested path) | Server builder flag can register it; strategy/guardrails differ from default loop | Enabling requires prompt/catalog/safety/routing consistency updates |
| Control-lock diagnostics | Debug pause attribution and self-action filtering | Diagnostic logging/store instrumentation (tag/flags in dev contexts) | Can be noisy/sensitive; gate behind debug flags |
| UI stream/debug hooks (where present) | Inspect live UI tree/state for development | Local debug server/stream components around accessibility/UI extraction | Must be opt-in and local-only |
| `aura-adb` (desktop bridge, optional) | Host-side raw ADB access via MCP tool | `aura-mcp-connect` can register tool when explicitly armed; runs local `adb` via `execFile` | Bypasses phone-side MCP policy; high risk, dev-only, require explicit user intent and warnings |

---

## 5. How to Implement in Your Project (Cross-Cutting Playbook)

### 5.1 Recommended implementation order
1. **MCP core + policy chokepoint** (`scopedTool`, scopes, refusals, audit hooks)
2. **UI tree extraction + `read_screen` + som cache** (grounded actions)
3. **Basic gestures/device controls + settle/observation**
4. **Deterministic planes** (system intents, deep links, notifications/media/files/contacts)
5. **Agent loop + ledger/budgets + verification/proof**
6. **Vision escalation (`perceive_screen`, CV/OCR)** for tree-blind surfaces
7. **Browser DOM plane + handoff**
8. **Voice/wake/live conversation** (optional)
9. **Remote MCP pairing/daemon** (optional, high trust impact)
10. **Eval, diagnostics, and hardening**

### 5.2 Non-negotiable invariants to preserve
- **All tool calls pass one enforcement wrapper** (no side channels).
- **Sensitive actions are blocked in code** before dispatch, fail-closed.
- **Actions target `som_id`/handles resolved server-side**, never model-guessed coordinates.
- **Mutating actions produce settled observations** for verification.
- **Human control and consent take precedence** (pause, approvals, handoffs, logins/payments).
- **Completion claims must be evidence-backed** for consequential tasks.

### 5.3 Porting strategy (if adapting AURA code patterns)
- Reuse the **interfaces/ports** model to isolate Android/platform specifics.
- Keep **tool schemas/result shapes stable** and version carefully.
- Start with a **mock device/bridge test harness** before full UI/device automation.
- Add **trace IDs and audit correlation** early (tool call ↔ observation ↔ verdict).

---

## 6. Requirements (Build, Runtime, Permissions, Dependencies, Config)

## 6.1 Development & build requirements

### Android app (`aura-android`)
- **JDK:** 17
- **Android SDK:** API 36 (compile/target 36 in project config)
- **Gradle:** project wrapper (run from `aura-android/`)
- **Kotlin/AGP/Compose:** as defined in `aura-android` Gradle config/version catalog
- **NDK/ABIs:** native libs for ONNX/sherpa/WebRTC (ABI filters differ by build type)
- **Local files (git-ignored):**
  - `aura-android/local.properties` (SDK path; optional release signing; optional local backend defaults)
  - `aura-android/app/google-services.json` (Firebase config; placeholder/example possible for limited debug flows)

### Desktop bridge (`aura-mcp-connect`)
- **Node.js:** >= 18
- **npm:** to install/run package
- **Network:** PC and phone on same local/private network (Wi-Fi, hotspot, Tailscale/private VPN path as supported)

### Benchmark/scripts (optional)
- **Python 3.x** for many scripts (`agent_eval.py`, judges, MCP tests, conversions)
- **PowerShell** for Windows-oriented runners where provided
- Additional script-specific dependencies as required by individual tools

## 6.2 Android runtime & device requirements
- **Android version:** minSdk 26+ (project config; some features gated by API level)
- **Services/capabilities:** Accessibility service, overlay support, notification listener (for assistant plane), IME enablement (for fallback typing), microphone access (voice), screen capture capability (perception/screenshots)
- **Device constraints:** OEM-specific overlay/background/gesture behavior varies; performance depends on CPU/RAM and model runtime provider support

## 6.3 Permissions and system access (feature-dependent)

| Feature area | Typical permissions / system grants |
|---|---|
| Core automation | Accessibility service binding/enablement |
| Overlay UI | `SYSTEM_ALERT_WINDOW`, foreground service notifications |
| Voice/wake word | `RECORD_AUDIO`, foreground microphone service, audio focus handling |
| Screen perception/screenshots | MediaProjection/user consent flow, foreground service type(s) as configured |
| Notifications assistant plane | Notification Listener access (`BIND_NOTIFICATION_LISTENER_SERVICE`) |
| Contacts | `READ_CONTACTS` (resolve contact) |
| Files/media search/open | Media/file read permissions (API-level dependent, scoped storage rules) |
| Alarms/timers/intents | `SET_ALARM` and relevant intent handlers/apps |
| System/network/device state | network state, Wi-Fi/Bluetooth/audio settings (only if corresponding controls are implemented/exposed) |
| Boot/reminders/keep-awake | boot receive, wake/lock/foreground service permissions as used |

> Exact manifest entries and runtime flows are defined in `aura-android/app/src/main/AndroidManifest.xml`, permission registry/screens, and service implementations.

## 6.4 Key dependencies (representative)

### Android
- **Agent/LLM/MCP:** Koog agents, MCP Kotlin SDK (client/server), Ktor (HTTP engine)
- **UI/app:** Jetpack Compose, Material, Navigation, Hilt, Room, DataStore
- **Networking/storage/security:** OkHttp/Retrofit, kotlinx.serialization, AndroidX Security/Biometric (as used)
- **Perception/voice:** ONNX Runtime, ML Kit Text Recognition, sherpa-onnx (vendored AAR), audio/VAD components
- **Transport:** WebRTC (on-device LAN MCP transport)
- **Observability/remote config (app-dependent):** Firebase Auth/Firestore/Remote Config/Crashlytics/Analytics

### Desktop bridge
- `@modelcontextprotocol/sdk` for MCP server/client transport integration
- `@roamhq/wrtc` for WebRTC on Node
- Node standard libs for HTTP/CLI/process management

## 6.5 Configuration & secrets
- **LLM/provider keys:** configured in-app (BYOK); stored via app key stores/preferences mechanisms.
- **Search integrations:** e.g. Tavily key for `web_search` (if enabled).
- **Firebase:** `google-services.json` and backend project setup/rules for sign-in, registry, remote gating, diagnostics features.
- **Remote MCP:** per-device trust records and toolset settings managed on phone; desktop trust stored locally by bridge.
- **Build-time local config:** `local.properties` for SDK path/signing and optional local defaults.

---

## 7. Key Files & Starting Points

### Architecture & docs
- `README.md` — product overview, setup, feature summary
- `AGENTS.md` — repo conventions/build/test guidance
- `aura-android/app/src/main/java/com/aura/aura_ui/presentation/README.md` — UI map
- `aura-android/app/src/main/java/com/aura/aura_ui/remote/README.md` — remote gate/registry design
- `aura-mcp-connect/README.md` — bridge/daemon/pairing/adb-tool docs

### Agent runtime (app)
- `app/.../agent/AuraAgent.kt` — main run orchestration
- `app/.../agent/strategy/AuraVisionStrategy.kt` — vision-aware loop strategy
- `app/.../agent/llm/*` — provider/model clients, retries, taps, sanitization
- `app/.../agent/ledger/*` — run state, persistence, resume
- `app/.../agent/memory/*`, `skills/*`, `hitl/*`, `proof/*`, `research/*` — supporting subsystems

### MCP server core
- `mcp-server/.../McpServerBuilder.kt` — server composition/registration
- `mcp-server/.../server/ScopedToolRegistration.kt` — enforcement chokepoint
- `mcp-server/.../server/SensitivePolicy.kt` — hard safety policy
- `mcp-server/.../server/ActionGuard.kt` — wasted-turn/grounding/completion guards
- `mcp-server/.../server/AuraInstructions.kt`, `Doctrine.kt`, `McpToolCatalog.kt`, `Toolset.kt`
- `mcp-server/.../McpServerController.kt`, `server/InProcessMcpServer.kt`

### Tools (MCP)
- `mcp-server/.../tools/ReadScreenTool.kt`, `PerceiveScreenTool.kt`
- `mcp-server/.../tools/GestureTools.kt`, `DeviceTools.kt`, `KeyTools.kt`, `StepTools.kt`
- `mcp-server/.../tools/DeepLinkTools.kt`, `SystemIntentTools.kt`
- `mcp-server/.../tools/BrowserTools.kt`
- `mcp-server/.../tools/NotificationTools.kt`, `MediaTools.kt`, `ContactTools.kt`, `FileTools.kt`
- `mcp-server/.../tools/WaitForTool.kt`, `VerifyActionTool.kt`, `EndSessionTool.kt`

### Android bridges & execution
- `app/.../mcp/bridge/*` (app implementations of MCP ports, browser/perception bridges)
- `app/.../accessibility/AuraAccessibilityService.kt`, `accessibility/gesture/*`
- `app/.../voice/*`, `app/.../services/WakeWordListeningService.kt`, `app/.../agent/conversation/*`
- `app/.../overlay/*`, `app/.../presentation/*`

### Remote transport / bridge
- `mcp-server/.../lan/*`, `bridge/WebRtcTransport.kt`, `bridge/TrustedClientsStore.kt`, `bridge/PairingCrypto.kt`
- `aura-mcp-connect/src/daemon.js`, `stdio-shim.js`, `lan.js`, `pair.js`, `phone-transport.js`, `proxy-core.js`

### Eval / testing
- `app/src/main/assets/eval/tasks.json`, `docs/agent-review/EVAL_TASKS.md`
- `scripts/agent_eval.py`, `scripts/judge_eval.py`, `scripts/run_eval_suite.ps1`
- `mcp-server/src/test/kotlin/com/aura/mcp/bench/*`

---

## 8. Known Constraints & Risks

- **Accessibility variability:** some apps/UIs ignore injected gestures or expose incomplete trees; fallback paths and escalation are required.
- **Protected surfaces:** banking/payment/auth/password/FLAG_SECURE/login flows may be blocked or require user handoff by design.
- **Model variability:** tool-calling quality, latency, context limits, and vision support differ by provider/model; budgets and repair logic are necessary.
- **Performance cost:** CV/OCR, screenshots, and large contexts increase latency/token usage; prefer `read_screen` and deterministic routes first.
- **Remote control trust:** pairing, approval, revocation, scope/toolset limits, and daemon hardening must be preserved.
- **Privacy/logging:** traces and screenshots can contain sensitive data; enforce redaction/retention and avoid persisting secrets/OTPs.
- **Firebase coupling (current implementation):** sign-in/remote gating/registry/diagnostics depend on Firebase setup/rules unless abstracted.
- **OEM/background restrictions:** wake-word/overlay/foreground behavior can differ across devices and Android versions. 

---

*End of document.*