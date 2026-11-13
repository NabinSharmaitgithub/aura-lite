# AURA Lite — Feature Inventory

This document lists the features currently present in this repository (`aura-lite`), based on the implemented code and assets.

## Core agent runtime
- **On-device agent loop** (`app/src/main/java/com/aura/aura_ui/agent/AuraAgent.kt`, `AgentRuntime.kt`) — goal → plan/act/verify/answer with bounded steps, loop detection, pause/resume/stop, and structured run state.
- **LLM integration (BYOK)** (`app/src/main/java/com/aura/aura_ui/agent/llm/*`) — provider catalog, per-provider keys in encrypted storage, model selection, streaming client, custom OpenAI-compatible endpoint support.
- **Prompt/system assembly** (`app/src/main/java/com/aura/aura_ui/agent/PromptBuilder.kt`) — device context, learnings/skills, recent failures, ledger/budget notes, hooks; supports system prompt override.
- **Run ledger & budget** (`app/src/main/java/com/aura/aura_ui/agent/ledger/*`) — tracks goal/plan/facts/dead-ends/outcome, enforces step/wall-clock/request/failure budgets.
- **Destructive-goal pre-checks & confirmations** (`app/src/main/java/com/aura/aura_ui/agent/ConfirmationBroker.kt`, agent guards) — blocks/refuses or requires confirmation for destructive intents.

## MCP server & tool surface
- **Single MCP tool contract** (`mcp-server/src/main/kotlin/com/aura/mcp/tools/ToolCatalog.kt`, `McpToolRegistry.kt`) — schemas + validation used by in-app agent and remote clients; `scopedTool` enforcement chokepoint.
- **Policy/safety pipeline** (`mcp-server/src/main/kotlin/com/aura/mcp/server/policy/SensitivePolicy.kt`, `mcp-server/src/main/kotlin/com/aura/mcp/guard/ActionGuard.kt`) — fail-closed sensitive app/credential/payment/OTP/secret screening, stale/invalid `som_id`/handle refusals, pause/control/foreground/handoff checks.
- **Device tool catalog** (`ToolCatalog.kt`) covering UI, apps/navigation, system/media/notifications/contacts/files, browser, and more. Examples:
  - **See/verify:** `read_screen`, `perceive_screen`, `get_screenshot`, `request_screen_capture_permission`, `verify_action`, `wait_for`, `watch_device_events`, `end_session`
  - **Touch/input/navigation:** `tap`, `swipe`, `scroll`, `set_value`, `type_text`, `press_key`, `press_enter`, `clipboard`, `launch_app`, `lookup_app`, `install_app`
  - **Shortcuts/intents:** `open_deeplink`, `resolve_deeplink`, `list_app_deeplinks`, `system_intent`, `web_search`
  - **Assistant plane:** `read_notifications`, `notification_action`, `dismiss_notification`, `get_media_sessions`, `media_control`, `resolve_contact`, `find_files`, `open_file`
  - **Browser:** `browser_open`, `browser_read`, `browser_find`, `browser_act`, `browser_wait`, `browser_screenshot`, `browser_extract`, `browser_upload`, `browser_tabs`, `browser_handoff`, `browser_close`
- **Transports:**
  - In-process MCP (`mcp-server/src/main/kotlin/com/aura/mcp/server/InProcessMcpServer.kt`, `McpClient` integration)
  - LAN MCP over HTTP/JSON-RPC (`mcp-server/src/main/kotlin/com/aura/mcp/server/HttpMcpServer.kt`)
  - LAN MCP over WebRTC + signaling (`mcp-server/src/main/kotlin/com/aura/mcp/bridge/WebRtcTransport.kt`, `mcp-server/src/main/kotlin/com/aura/mcp/server/WebRtcJsonRpcBridge.kt`, `mcp-server/src/main/kotlin/com/aura/mcp/lan/*`) with pairing/crypto/trusted clients.

## Screen perception, grounding & automation
- **Set-of-Marks grounding** — elements indexed with `som_id`/indices; tools prefer grounded handles (`tap`, `scroll`, `set_value`, `type_text`, `verify_action`).
- **UI tree extraction** (`app/src/main/java/com/aura/aura_ui/accessibility/UiTreeExtractor.kt`) — structured elements with bounds/roles; enriched with `screen_generation`, `screen_signature`, `som_id`, and `elements` alias.
- **Perception/caching** (`mcp-server/src/main/kotlin/com/aura/mcp/cache/PerceptionCache.kt`, `app/src/main/java/com/aura/aura_ui/perception/*`) — caches screen/handles, supports escalation to visual perception; post-action settle/observation integration.
- **Accessibility service** (`app/src/main/java/com/aura/aura_ui/accessibility/AuraAccessibilityService.kt`) — UI tree access, gesture injection, screen capture capability, event signals, key-event filtering for shortcuts.
- **Gesture/input/system controls** (`app/src/main/java/com/aura/aura_ui/accessibility/GestureInjector.kt`, `SystemControlsHelper.kt`) — taps/swipes/scrolls/typing/keys, volume/media/DND/system intents.

## Voice, wake word & assistant entry points
- **Offline wake word (sherpa-onnx)** (`app/src/main/java/com/aura/aura_ui/voice/WakeWordEngine.kt`, `app/src/main/java/com/aura/aura_ui/services/WakeWordListeningService.kt`)
  - Uses model files in `app/src/main/assets/kws/` (`encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt`, `keywords.txt`)
  - VAD gate, foreground microphone service, fail-closed when model is missing
  - Configured/target phrase: **“Wake up AURA”** (keyword label read from `keywords.txt` when available)
- **Speech-to-text (STT)** (`app/src/main/java/com/aura/aura_ui/voice/VoiceEngine.kt`) — platform speech recognizer with partial/results handling and single-owner capture.
- **Text-to-speech (TTS) / spoken replies** (`app/src/main/java/com/aura/aura_ui/voice/VoiceEngine.kt`, `app/src/main/java/com/aura/aura_ui/services/AssistantForegroundService.kt`)
  - Platform TTS with engine/voice selection
  - Supports targeting Google TTS engine (`com.google.android.tts`, e.g. `google` setting) and preferred voice names
  - Spoken replies for completed agent responses
- **Assistant entry points**
  - **Android assistant gesture / assist intents:** `MainActivity` handles `ACTION_ASSIST` / `VOICE_COMMAND` (and related assist flow) to submit text or start listening (`app/src/main/java/com/aura/aura_ui/MainActivity.kt`, `app/src/main/AndroidManifest.xml`)
  - **Volume-key shortcut:** double-press Volume Down triggers listening when Accessibility service is enabled (`app/src/main/java/com/aura/aura_ui/accessibility/AuraAccessibilityService.kt`, `app/src/main/res/xml/accessibility_service_config.xml`)
  - **Wake word service:** starts listening on detection when enabled
  - **Overlay/companion surfaces:** floating assistant/feedback overlays (`app/src/main/java/com/aura/aura_ui/overlay/*`)
- **Gemini Live conversation mode** (`app/src/main/java/com/aura/aura_ui/agent/conversation/GeminiLiveConversation.kt`)
  - `LiveConversationSession` interface and event/config models
  - `GeminiLiveConversationSession` (OkHttp WebSocket-based realtime session shell: connect/setup, text turns, tool responses, status/transcript/tool-call events)
  - `NoopLiveConversationSession` fallback

## Memory, learnings, skills & evaluation
- **Learnings / app memory** (`app/src/main/java/com/aura/aura_ui/data/learnings/LearningsStore.kt`) — durable facts + voice corrections, app/global scoping, prompt injection (`promptBlock`), export/import for sync.
- **Skills registry** (`app/src/main/java/com/aura/aura_ui/data/skills/*`, `app/src/main/assets/skills/index.json`) — reusable procedures, prompt injection, UI management.
- **Evaluation suite** (`app/src/main/java/com/aura/aura_ui/data/eval/*`, `app/src/main/assets/eval/tasks.json`) — built-in tasks/validation for agent behavior.
- **Session logging & tracing** (`app/src/main/java/com/aura/aura_ui/data/session/*`) — JSONL sessions, traces, outcomes, retention.

## Apps, browser, data & integrations
- **App resolution and launch** (`app/src/main/java/com/aura/aura_ui/automation/AppResolver.kt`) — installed app discovery, lookup by name/package/alias, launch, install (Play Store listing open for not-yet-installed apps), deeplink resolve/open/list (with resilient WhatsApp/package resolution fallbacks).
- **Data tools** (`app/src/main/java/com/aura/aura_ui/automation/DataTools.kt`) — contacts, files/media, clipboard, SMS/dial helpers (permission-gated).
- **Browser automation** (`app/src/main/java/com/aura/aura_ui/browser/BrowserActivity.kt`, browser controller/bridge integration) — in-app WebView with DOM read/act/find/extract/upload/tabs/handoff and generation tracking.
- **Notifications & media** — notification listener/actions/dismiss and media session listing/control (`app/src/main/java/com/aura/aura_ui/notifications/*`, system/media bridge paths).
- **System intents** — alarms/timers/dial/SMS/calendar/share/navigation and related system actions.

## Storage, sync & remote capabilities
- **Local-first storage** — Room, DataStore preferences, EncryptedSharedPreferences, JSONL sessions, learnings/skills data.
- **Optional Firebase integration (opt-in)** (`app/src/main/java/com/aura/aura_ui/firebase/*`, `firebase/*`) — auth, Firestore sync hooks, FCM, Remote Config, Crashlytics, analytics wrappers.
- **Optional Backblaze B2 storage (opt-in)** (`app/src/main/java/com/aura/aura_ui/b2/*`, `backblaze/*`) — S3-compatible upload/download/backup support.
- **LAN remote control / MCP access** — pairing (PIN/SAS/crypto), trusted clients, toolset scoping, revocation, and audit logging (`mcp-server/src/main/kotlin/com/aura/mcp/lan/*`, `bridge/*`).

## UI, services & platform
- **Jetpack Compose UI** (`app/src/main/java/com/aura/aura_ui/presentation/*`, `ui/*`) — MONO design, onboarding, home/agent, voice, settings, memory, activity, eval, trusted devices, permissions screens.
- **Foreground/background services**
  - `AssistantForegroundService` — agent/voice run host and spoken replies
  - `WakeWordListeningService` — offline wake word detection
  - `AuraOverlayService` / `VisualFeedbackOverlayService` — overlay UX
  - `MediaProjectionService` — screen capture consent/session
  - `AuraNotificationListenerService` — notification access/actions
  - `AuraFirebaseMessagingService` — FCM handling
  - `AuraKeyboardService` (IME) — optional voice/input surface
- **Receivers/workers** — boot/package-replaced handling, reminder receiver, run action receiver, retention/background workers.
- **Permissions, safety & gating** — runtime permission flows, remote gate, restricted apps, sensitive-content/tool access controls.
- **Build & release** — Gradle multi-module project (`:app`, `:mcp-server`), signing support, CI workflow (`.github/workflows/android.yml`), release artifacts/checksums/fingerprints.

## Key entry points
- **App:** `app/src/main/java/com/aura/aura_ui/MainActivity.kt`, `app/src/main/java/com/aura/aura_ui/AuraApplication.kt`
- **Agent runtime:** `app/src/main/java/com/aura/aura_ui/agent/AgentRuntime.kt`
- **Device/MCP bridge:** `app/src/main/java/com/aura/aura_ui/bridge/AuraDeviceBridge.kt`
- **MCP server core:** `mcp-server/src/main/kotlin/com/aura/mcp/*`
- **Voice & wake word:** `app/src/main/java/com/aura/aura_ui/voice/*`, `app/src/main/java/com/aura/aura_ui/services/WakeWordListeningService.kt`
- **Wake word assets:** `app/src/main/assets/kws/*`

## Notes
- Some features depend on device/OEM capabilities, granted permissions, installed apps/engines, network access, provider keys, and optional backend configuration.
- Offline wake word requires the sherpa-onnx model files and a `keywords.txt` configured for the desired phrase in `app/src/main/assets/kws/`.
- Gemini Live conversation mode includes a realtime session implementation, but full end-to-end audio/UI orchestration may require additional host integration depending on the desired experience.
