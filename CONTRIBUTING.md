# Contributing to AURA Lite

Thanks for considering a contribution. AURA Lite is an open Android agent; we keep it
rebuildable, auditable, and kind to fork drivers. By contributing you agree to the
[CLA](CLA.md).

## Getting set up

```sh
git clone https://github.com/<org>/aura-lite.git
cd aura-lite
```

### Prerequisites

- JDK 17 (Temurin recommended)
- Android SDK with `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`
- Android Studio or `ANDROID_SDK_ROOT` on PATH
- Network access for the first build (fetches sherpa AAR + release AAR deps via Gradle)

Build and test:

```sh
gradle :app:assembleDebug          # debug APK (uses the fetched sherpa AAR)
gradle :app:testDebugUnitTest      # unit tests
gradle :mcp-server:test            # MCP server unit tests
```

There is no committed `gradlew`; use a local Gradle 8.11.1 or let Android Studio materialise it.

### Repository layout

- `app/` — Android application (agent runtime, UI, automation bridges, MCP in-process)
- `mcp-server/` — the shared tool surface + MCP server (LAN HTTPS, in-process client)
- `backblaze/` — B2 file storage docs (upload/download flows, capabilities)
- `firebase/` — optional sync (Firebase Auth, Firestore, FCM, Remote Config); decision-gated
- `docs/`, `benchmarks/` — evaluation and reference material

### What we accept

- Bug fixes with a regression test where feasible (`testDebugUnitTest`)
- Features that match the scope in `01-PRD.md` and the tool contract in `mcp-server/…/ToolCatalog.kt`
- Better accessibility, performance, and reliability work
- Documentation corrections and additions

### What we do not accept

- New permission requests that aren't in the current manifest without justification
- Network calls to endpoints the user didn't ask for
- Telemetry on by default
- Screenshots/videos of private device data in tests or assets
- Large dependency additions without discussion

## Reporting bugs

Include: Android version, device model, what you expected, what happened, steps to reproduce,
and (if relevant) a logcat extract. Avoid pasting private contents; the agent runs often see
personal data and the report goes to a public tracker.

## Pull requests

- One logical change per PR.
- Keep the tool contract in `ToolCatalog.kt` stable unless the PR is the contract change; if it is,
  update `docs/tools.md` or the README in the same commit.
- Run `gradle :app:testDebugUnitTest :mcp-server:test` before opening.
- Open a draft early if you want feedback on direction before finishing.

## Development notes

- The agent loop is `AgentRuntime` over the OpenAI-compatible streaming client.
- The tool surface is a single `ToolCatalog` shared by the in-app agent and the LAN MCP server.
- Keys live only in `EncryptedSharedPreferences`; never log them, never print them into traces.
- Release APKs are produced only by CI from tags/dispatch with the keystore from
  `AURA_KEYSTORE_BASE64` (currently `1.0.x`, `AURA Release` key).
