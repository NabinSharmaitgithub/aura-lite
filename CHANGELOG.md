# Changelog

All notable changes to AURA Lite are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are set by CI from `git rev-list --count HEAD` (see `app/build.gradle.kts`).

## [Unreleased]

### Fixed
- Voice: mic permission is now checked before the foreground service launches; missing permission
  or a speech-recognition failure now posts a high-priority "Voice capture failed" notification
  with the reason instead of silently exiting. `AssistantForegroundService` also uses the
  `microphone` foreground type, and the Home `AURA_LISTEN` entry gives the user feedback when
  speech recognition is unavailable on the device.
- Voice: a saved TTS voice/engine setting is now re-applied to the live speech engine
  (`VoiceEngine.refreshTtsVoice`) instead of only taking effect on the next speak call, so
  changing the voice or TTS model from Settings → Voice takes effect immediately.

### Added
- `install_app` tool (apps domain, mutating + risky): opens the Google Play Store listing for an
  app that is not installed yet (`https://play.google.com/store/apps/details?id=<package>`), so the
  agent can complete goals like "open the Play Store and install Free Fire" (Play Store listing →
  user taps Install). Free Fire / Free Fire Max aliases and Play Store deep links added to
  `AppResolver` and `deeplink_catalog.json`.
- `install_app` added to the CORE toolset scope and to the MCP tool guide/instructions so install
  goals are planned with the right tool.
- `browser_open` search guidance for in-app music/video searches (YouTube results URL) in the MCP
  tool guide.
- `ask_user` tool: the agent can pause the run, ask the user a question in-app or via notification,
  and resume with the answer. Available to the in-app agent and MCP clients.
- MCP `prompts/` and `resources/` for the LAN HTTPS bridge and the in-process client:
  `aura://safety/policy`, `aura://tools/guide`, `aura://device/status` (resources);
  `research_then_act`, `whatsapp_message`, `compare_products`, `book_ride`, `summarize_page` (prompts).
- On-device report screen (developer mode → "On-device report"): runs, success rate, average steps,
  top provider, and total duration — computed from session traces stored on-device.
- Interrupted-run detection: if the process dies mid-task, AURA notifies on next launch and clears
  the marker on clean completions.
- System-level banking / payment / authenticator package blocklist in
  `RestrictedAppsRepository.SYSTEM_BLOCKED_PACKAGES`; cannot be opened even if the user asks.
- Optional Firebase Remote Config gate (`kill_switch_enabled`, `min_supported_version_code`);
  agent refuses work on unsupported versions and when the maintainers flip the switch.
- `benchmark/` module and `tools/mcp_session.py` reference MCP client.
- Research-first doctrine in the agent's system prompt: unfamiliar app flows trigger a
  `web_search` before acting.
- Three doctrine skills (research_the_web, act_without_screen, aura_skill_doctrine) bundled as
  standard skills.

### Changed
- `README.md` rewritten for public release; quick start, privacy manifesto, highlights table.
- `docs/LAUNCH.md` and `RELEASE.md` document the launch & release process.

### Compliance
- AGPL-3.0 licensed. Source offered publicly. Linking exception covers Google Play services, Firebase,
  and ML Kit only.

### Sixth pass — explicit state machines
- Replaced ~40 implicit state machines (scattered booleans, counters, free-form status strings and
  independent nullable flags) with explicit, guarded state types and transition functions.
- `AgentRunState` is now phase-first (`RunPhase`); `running`/`paused`/`status` derived, transitions
  validated by `RunTransitions`.
- `ControlLock`, `ConfirmationBroker`, `AskUserBroker`, `WakeWordListeningService`,
  `ScreenCaptureManager`, `BrowserController`, `RemoteGate`, `PinGate`, `McpServerController`,
  `WebRtcTransport` peers, `McpToolRegistry` (tool stage per result), `StatusNarrator`,
  `ActiveRunRegistry`, `SessionWriter`, `FirebaseSync`, `B2Service`, `InstallFlow`, `VoiceEngine`,
  `GeminiLiveConversationSession`, `AuraViewModel.evalRun`, `OnboardingScreen`, overlay pill and
  foreground/projection services all carry explicit canonical state.
- `PlanStepStatus`, `McpServerState`, `PeerPhase`, `PinGateState`, `ToolStage` and `ScreenFreshness`
  enums added; `ToolResult` now records the producing pipeline stage.
- `docs/MISSING_FEATURES_IMPLEMENTED.md` updated with a sixth-pass section.

