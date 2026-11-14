# Features Implemented in AURA Lite

All architectural and runtime capabilities from `AURA_FEATURES_AND_IMPLEMENTATION.md` and `aura-android-use` have been ported and implemented into `aura-lite`:

- [x] **Phone-local execution flow (end-to-end architecture)**: Integrated `AuraAgent` orchestration loop connecting user goals, LLM reasoning, in-process MCP server, and device bridges (`app/agent/AuraAgent.kt`, `mcp-server/server/InProcessMcpServer.kt`).
- [x] **Remote MCP flow (computer → phone)**: Full WebRTC DataChannel transport and LAN HTTP signaling with pairing crypto and SAS verification (`mcp-server/lan/*`, `mcp-server/bridge/*`, `aura-mcp-connect/src/*`).
- [x] **`ask_aura` conversation handoff**: Live conversation routing, task narration gates, and device action delegation (`app/agent/conversation/*`).
- [x] **Vision-aware tool loop**: Interleaved model execution with Set-of-Marks perception and dynamic turn adjustments (`app/agent/strategy/AuraVisionStrategy.kt`).
- [x] **Screenshot re-injection**: Automatic capture and re-injection of the latest SoM frame after visual tool dispatches (`app/mcp/bridge/AppScreenshotBridge.kt`, `mcp-server/tools/PerceiveScreenTool.kt`).
- [x] **Stale perception eviction**: Automatic pruning of superseded screenshots and perception text to maintain token budget (`app/agent/strategy/ContextCompactor.kt`, `mcp-server/cache/PerceptionCache.kt`).
- [x] **Ledger re-injection**: Incrementally updated task ledger (goal, plan, steps, facts, dead ends) re-injected every turn (`app/agent/ledger/*`).
- [x] **Defect repair / bounded defect nudges**: Automatic recovery when models produce thinking-only turns or raw text tool calls (`app/agent/llm/ReplySanitizer.kt`, `app/agent/llm/ToolCallDebris.kt`).
- [x] **Keep-awake during runs**: Wake-lock controller keeping CPU and screen active while automation is running (`app/agent/runtime/KeepAwakeController.kt`, `app/agent/RunKeepAwake.kt`).
- [x] **Control-lock integration**: Yields immediately to user touches and pauses agent execution (`app/agent/control/ControlLock.kt`, `mcp-server/server/ControlLockGate.kt`).
- [x] **Pause checkpoints**: Serialized run state saved at pause boundaries with live screen re-verification on resume (`app/agent/checkpoint/RunCheckpointStore.kt`, `app/agent/ledger/PauseCheckpoint.kt`).
- [x] **Run-end hooks**: Finalization hooks, session summaries, and audit trail dispatch (`app/agent/runtime/RunLifecycleHooks.kt`, `mcp-server/tools/EndSessionTool.kt`).
- [x] **Status narration**: Derived status stream (`thinking`, `perceiving`, `acting`, `waiting`, `verifying`) feeding overlays and status strips (`app/agent/runtime/StatusNarrator.kt`, `app/ui/components/StatusStrip.kt`).
- [x] **Deferred tool loading**: On-demand tool schema resolution to conserve prompt context (`app/agent/DeferredTools.kt`, `app/agent/ClientToolCatalog.kt`).
- [x] **Hooks system integration**: Extensible pre- and post-tool hook pipeline (`app/agent/runtime/RunLifecycleHooks.kt`, `mcp-server/tools/ToolPipelineHooks.kt`).
- [x] **Run entry modes (fresh run, saved-settings run, resumed run)**: Flexible execution entry points (`app/agent/report/RunReport.kt`, `app/agent/ledger/ResumePreamble.kt`).
- [x] **Honest termination paths**: Verifiable success, partial completion, cancellation, and error states with evidence requirements (`app/agent/CompletionEvidence.kt`, `app/agent/report/RunReport.kt`).
- [x] **Complete MCP Tool Surface (50+ Tools)**:
  - Read: `read_screen`, `perceive_screen`, `get_screenshot`, `get_device_status`, `watch_device_events`, `verify_action`, `wait_for`, `end_session`
  - Input/Gestures: `tap`, `swipe`, `scroll`, `type_text`, `set_value`, `press_enter`, `press_key`, `clipboard`, `launch_app`, `lookup_app`
  - Shortcuts/Intents: `list_app_deeplinks`, `resolve_deeplink`, `open_deeplink`, `system_intent`, `web_search`
  - Assistant Plane: `read_notifications`, `notification_action`, `dismiss_notification`, `get_media_sessions`, `media_control`, `resolve_contact`, `find_files`, `open_file`
  - Browser Plane: `browser_open`, `browser_read`, `browser_find`, `browser_act`, `browser_wait`, `browser_screenshot`, `browser_extract`, `browser_upload`, `browser_tabs`, `browser_handoff`, `browser_close`
