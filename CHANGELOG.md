# Changelog

All notable changes to AURA Lite are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are set by CI from `git rev-list --count HEAD` (see `app/build.gradle.kts`).

## [Unreleased]

### Added
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
