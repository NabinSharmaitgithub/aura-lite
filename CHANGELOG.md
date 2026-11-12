# Changelog

All notable changes to AURA Lite are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are set by CI from `git rev-list --count HEAD` (see `app/build.gradle.kts`).

## [Unreleased]

### Added
- `ask_user` tool: the agent can pause the run, ask the user a question in-app or via notification,
  and resume with the answer. Available to in-app agent and MCP clients.
- MCP `prompts/` and `resources/` support for the LAN HTTPS bridge and the in-process client:
  `aura://safety/policy`, `aura://tools/guide`, `aura://device/status` (resources);
  `research_then_act`, `whatsapp_message`, `compare_products`, `book_ride`, `summarize_page` (prompts).
- `LICENSE`, `LICENSE-EXCEPTION.md`, `CLA.md`, `COMMERCIAL-LICENSE.md`, contribution and security docs,
  CLA enforcement workflow, onboarding checklist in `CONTRIBUTING.md`.
- Unit tests for agent, MCP registry, learnings store, session log store, SIGV4 signer, destructive goal.

### Changed
- `README.md` rewritten with live status, architecture overview, build and test instructions.

### Compliance
- AGPL-3.0 licensed. Source offered publicly. Linking exception covers Google Play services, Firebase,
  and ML Kit only.
