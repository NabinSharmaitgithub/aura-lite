# AURA for Android — source reconstruction

A complete, source-only rebuild of **AURA**, the on-device Android agent described by the six
specification documents in this repository root. The app reads and drives the phone through an
Accessibility Service, plans and executes multi-step tasks with the user's own LLM provider, and
exposes the same device tools to external MCP clients over a paired local channel.

No accounts. No cloud. No analytics, crash reporting, remote config, or device registry. Network
traffic only ever goes to the provider the user configured, the optional Tavily search API, or a
paired local MCP client.

> **Build status:** the full source tree, resources, assets and CI are in place, but this
> environment has no JDK, Android SDK, or Gradle installation, so it has not been compiled here.
> `.github/workflows/android.yml` is the verification path: it assembles debug and release APKs on
> every push and publishes them as artifacts.

---

## Specification documents

| # | File | Contents |
|---|---|---|
| 1 | [`01-PRD.md`](01-PRD.md) | Product definition, functional requirements, acceptance criteria, non-goals |
| 2 | [`02-TRD.md`](02-TRD.md) | Architecture, modules, dependency list, build config, agent runtime, tool surface, memory budgets |
| 3 | [`03-APP-FLOW.md`](03-APP-FLOW.md) | Every screen, route, state machine and navigation diagram |
| 4 | [`04-DESIGN-UI-UX-BRIEF.md`](04-DESIGN-UI-UX-BRIEF.md) | MONO design tokens: colour, type scale, spacing, components, layouts |
| 5 | [`05-BACKEND-SCHEMA.md`](05-BACKEND-SCHEMA.md) | Room schema, preference keys, session log format, MCP API, provider registry |
| 6 | [`06-IMPLEMENTATION-PLAN.md`](06-IMPLEMENTATION-PLAN.md) | Phase order, effort estimates, acceptance tests, do-not-repeat defect list |

The README is the map of the implementation; those six files remain the authority on behaviour.

---

## Quick facts

| Item | Value |
|---|---|
| Language / UI | Kotlin 2.1.20, Jetpack Compose (BOM 2025.04.01), Material 3 |
| Package / applicationId | `com.aura.aura_ui` / `com.aura.aura_ui.feature` (debug suffix `.debug`) |
| Modules | `:app` (Android application), `:mcp-server` (Android library) |
| SDKs | minSdk 26, compileSdk 36, targetSdk 36, JDK 17 |
| Build | AGP 8.10.1, Gradle 8.11.1, KSP, Hilt 2.56.1 |
| Source size | 80 Kotlin files, ~14,200 lines |
| Device tools | 47 tool definitions in one catalog (spec text says 45; the catalog covers every name listed there plus `run_steps` and browser helpers) |
| LLM providers | 14 built-in endpoints + custom OpenAI-compatible endpoint |
| Design | MONO: white canvas, black ink, blood-red semantics, floating capsule navigation |
| Storage | Room v1, DataStore preferences, EncryptedSharedPreferences, JSONL sessions, JSON learnings |
| Release ABIs | `arm64-v8a`, `armeabi-v7a` |

---

## Repository map

```text
.
├── 01-PRD.md … 06-IMPLEMENTATION-PLAN.md   authoritative specs
├── README.md                               this file
├── build.gradle.kts                        root plugins
├── settings.gradle.kts                     repositories + :app, :mcp-server
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml                  version catalog (single source of dependency truth)
│   └── wrapper/gradle-wrapper.properties   Gradle 8.11.1 (no wrapper jar is committed)
├── app/
│   ├── build.gradle.kts                    application config, signing, sherpa AAR fetch
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                         skills, eval tasks, onboarding copy, deeplinks, judge prompt
│       ├── java/com/aura/aura_ui/
│       └── res/                            MONO resources, icons, XML configs
├── mcp-server/
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── src/main/kotlin/com/aura/mcp/
└── .github/workflows/android.yml           CI: secret scan, debug + release, artifacts, releases
```

### `:app` packages

| Package | Responsibility |
|---|---|
| `accessibility/` | Accessibility Service, UI tree extraction, gesture injection, screen capture, system controls |
| `agent/` | Plan → act → verify → answer loop, prompt builder, destructive-goal guard, approval gate, reminders, web search |
| `agent/llm/` | Provider catalog, key store, model catalog, streaming HTTP client |
| `automation/` | App/contact resolvers, calendar/alarm/media/notification data tools |
| `bridge/` | `AuraDeviceBridge` — implements the MCP `DeviceBridge` against Android APIs |
| `browser/` | Dedicated WebView `BrowserActivity` + JS-backed controller |
| `data/` | Room (`db/`), DataStore settings, secure keys, session JSONL, learnings, skills, eval catalog, restricted apps |
| `di/` | Hilt module wiring singletons and the in-process MCP client |
| `ime/` | Optional AURA keyboard (voice dictation surface) |
| `notifications/` | Notification listener, channels, approval action receiver |
| `overlay/` | Assistant bubble and optional visual-feedback overlay |
| `perception/` | ONNX icon detector + ML Kit OCR, lazily loaded and released per run |
| `presentation/` | Shared `AuraViewModel`, navigation shell, routes, all Compose screens, onboarding |
| `receivers/` | Boot rescheduling for reminders and workers |
| `services/` | Assistant foreground service, wake-word listening service |
| `ui/` | MONO theme tokens, typography, shared components |
| `voice/` | Wake word, Silero VAD, STT/TTS engine |
| `worker/` | Retention pruning worker |
| root | `AuraApplication`, `MainActivity` (consent launcher, deep links) |

### `:mcp-server` packages

| Package | Responsibility |
|---|---|
| `bridge/` | `DeviceBridge`, `ToolResult`, `ToolDefinition`, `ToolDomain` — the only Android dependency boundary |
| `tools/` | `ToolCatalog` (47 schemas) and `McpToolRegistry` (validation, dispatch, `tools/list`, `tools/call`) |
| `server/` | JSON-RPC handling, `InProcessMcpServer` / `McpClient` (agent path), Ktor HTTPS `HttpMcpServer` (LAN path) |
| `lan/` | TLS identity, QR pairing + token crypto, mDNS advertisement |
| `audit/` | Append-only MCP audit log |
| root | `McpServerController`, `McpServerHealth` |

`ToolCatalog` is authored once. The in-app agent and remote clients both resolve tools through
`McpToolRegistry`, so schemas and validation cannot drift.

---

## Building

### Prerequisites

- JDK 17
- Android SDK with platform 36 and build-tools installed
- Gradle 8.11.1+ (or let Android Studio generate the wrapper)
- Network access on the first build, to fetch the pinned sherpa-onnx AAR (or place it in `app/libs`)

### Commands

```bash
# debug (x86_64 included for emulators)
gradle :app:assembleDebug

# release (R8 + resource shrinking; arm64-v8a and armeabi-v7a)
gradle :app:assembleRelease

# unit tests
gradle :app:testDebugUnitTest
```

There is **no committed `gradlew`/`gradle-wrapper.jar`**; only
`gradle/wrapper/gradle-wrapper.properties` is present. Use a local Gradle 8.11.1 installation, or
run `gradle wrapper` once / open the project in Android Studio to materialise the wrapper.

Release signing is optional. If `keystore.properties` exists at the repository root, or
`AURA_KEYSTORE_PATH` + `AURA_KEYSTORE_PASSWORD` + `AURA_KEY_ALIAS` + `AURA_KEY_PASSWORD` are set,
the release build uses that production key. Otherwise it falls back to the debug signing key so the
APK still installs; the build report records which key signed it. None of those files or values are
(or should be) committed.

To enable production signing in CI, set the repository secrets `AURA_KEYSTORE_BASE64`,
`AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, and `AURA_KEY_PASSWORD`.

Versioning: `versionCode = 534 + git commit count`, `versionName = 1.0.<versionCode>`, with
`BuildConfig.AURA_BUILD_SHA` carrying the short SHA.

### CI

`.github/workflows/android.yml` runs on pushes to `main`/`master`, tags, pull requests and manual
dispatch:

1. rejects committed provider-key patterns with `git grep` (keys live only in
   EncryptedSharedPreferences at runtime);
2. prepares optional production signing from repository secrets;
3. assembles debug and release APKs with Gradle 8.11.1 + JDK 17, teeing both logs;
4. writes `build-report/certificates.txt` with APK size, SHA-256 and the signing certificate
   `Owner`, `SHA1` and `SHA256` fingerprints, plus `build-report/REPORT.md` (also the job summary);
5. always uploads `aura-apks-<run>` and `aura-build-report-<run>` artifacts, including failed logs;
6. publishes a GitHub release with both APKs and the report on `v*` tags, or when a manual run sets
   `release = true` (tag defaults to `v<versionName>`).

---

## Optional binaries (not in git)

The build stays valid when these are absent; the related feature simply reports itself as
unavailable.

| Path | Purpose | Behaviour when missing |
|---|---|---|
| `app/libs/sherpa-onnx-static-link-onnxruntime-1.12.28.aar` | STT/TTS + wake-word runtime | `fetchSherpaAar` downloads it before `preBuild`. The voice layer imports sherpa directly, so a missing AAR fails the build with instructions instead of unresolved-reference noise. `app/libs/*.aar` is gitignored. |
| `app/src/main/assets/models/omniparser_icon_detect.onnx` | Optional icon detector for perception | `PerceptionEngine` reports `icon_model = not_bundled`; OCR still works. |
| `app/src/main/assets/kws/` | Wake-word keywords + model | Wake word is disabled; no service auto-starts. |

Both asset paths are intentionally excluded by `.gitignore` to keep the repository small. Drop
real files in place to enable the features.

### Bundled runtime assets

| Path | Contents |
|---|---|
| `assets/skills/index.json` | Built-in skill seeds (name, description, prompt fragment, tools) |
| `assets/eval/tasks.json` | 20-task acceptance suite used by the Eval screen |
| `assets/onboarding/copy.json` | Onboarding step copy |
| `assets/deeplink_catalog.json` | Accepted `aura://` deep links |
| `assets/judge/answer.md` | Judge/verification prompt for answer scoring |

---

## Architecture notes

### Agent loop

`AgentRuntime` runs `plan → act → verify → answer` with a bounded step budget. It streams model
output, records a step-by-step trace in `AgentRunState`, detects repeated failing actions, honours
pause/cancel between steps, and requires confirmation for destructive goals
(`DestructiveGoal` + `ConfirmationBroker`). Confirmations are delivered as notification actions
(`RunActionReceiver`) — never through a server.

`PromptBuilder` injects the system prompt, user hook instructions, learned facts, active skills and
the tool catalog. Hooks are deterministic compiled rules plus user-editable before-run instructions
stored in `AuraSettings.hookInstructions`; they are appended even when a system-prompt override is
set.

Runs are persisted as JSONL by `SessionLogStore`; the Activity and session-detail screens read the
same trace the agent produced.

### Tool surface and transports

- One 47-tool catalog covering screen reading, gestures, app launch, text entry, system settings,
  media, notifications, contacts, calendar/alarms, files, browser, perception, memory, skills,
  reminders and search.
- `InProcessMcpServer` + `McpClient` serve the agent without sockets.
- `HttpMcpServer` serves LAN clients over HTTPS/JSON-RPC with mDNS discovery, QR pairing, hashed
  tokens, trusted-device management and an audit log.
- `run_steps` is handled directly by the registry (batch execution); the Android bridge treats a
  direct call as unsupported by design.

### Storage

| Store | Where | Notes |
|---|---|---|
| Room v1 (`aura.db`) | `data/db/` | contacts cache, STT corrections, reminders, user-skill inventory |
| SharedPreferences (`aura_settings`) | `data/prefs/AuraSettings.kt` | onboarding, provider/model selection, hooks, voice, MCP config, retention |
| SharedPreferences (`aura_restricted_apps`) | `data/prefs/RestrictedAppsRepository.kt` | per-app restriction flags |
| EncryptedSharedPreferences (`aura_secure`) | `data/prefs/SecureKeyStore.kt` | provider keys, Tavily key, MCP pairing secret |
| EncryptedSharedPreferences (`aura_mcp_devices`) | `mcp-server/.../lan/Pairing.kt` | trusted MCP clients (SHA-256 token hashes only) |
| JSONL (`files/sessions/*.jsonl`) | `data/session/SessionLogStore.kt` | append-only run traces, pruned by the retention worker |
| JSON (`files/learnings/learnings.json`) | `data/learnings/LearningsStore.kt` | learned facts + voice corrections, one document, rewritten atomically |
| JSON (`files/skills/`) | `data/skills/SkillsRepository.kt` | user skill packs, seeded from `assets/skills/index.json` |
| JSONL (`files/mcp/audit.jsonl`) | `mcp-server/.../audit/McpAuditLogger.kt` | MCP tool calls, pruned by retention |
| PNG (`files/screen/current.png`) | `accessibility/ScreenCaptureManager.kt` | one transient screenshot, overwritten per capture |

### Security and privacy

- Keys are entered at runtime and stored only in EncryptedSharedPreferences; the repository and CI
  contain no keys, and CI fails on common provider-key patterns.
- LAN MCP requires explicit pairing, a pinned self-signed TLS identity, a hashed device token and
  per-device revocation.
- Audit records never include API keys or pairing secrets.
- No telemetry, no crash SDK, no remote config, no account system.
- Restricted-app rules are enforced before actions run.

### Design system

`ui/theme/Mono.kt` is the only place raw colours are defined; every Compose screen and Android View
uses those tokens (or `MonoArgb` for `Paint`/`Color` APIs). Navigation is a single floating black
capsule: four top-level icons, switching to back + title on subpages. No drawer, top app bar or tab
bar.

---

## Feature inventory

| Area | Implemented |
|---|---|
| Shell & navigation | Capsule nav, all routes from `03-APP-FLOW.md`, deep links (`aura://mcp/oauth`) |
| Onboarding | 10 steps, persisted progress, permission steps poll live state and auto-advance |
| Agent | Streaming loop, pause/resume/stop, trace, destructive confirmation, hooks, skills, loop detection, step budget |
| Brain & models | 14 provider presets + custom, model catalog, key verification, per-provider keys |
| Tools | 47-tool catalog, tool browser, JSON schema display, MCP audit, trusted devices, LAN server health |
| Memory | Learned facts per scope, manual add/delete, voice corrections, clear actions |
| Companion & voice | Wake word (optional), Silero VAD, STT/TTS (sherpa), overlay pill, visual feedback |
| Perception | ML Kit OCR + optional ONNX icon detection, downscaled single-frame capture |
| Screen control | Accessibility tree, gestures, global actions, notifications, quick settings, media, keyboard |
| Automation | Apps, contacts, SMS compose, calendar, alarms, reminders, files, browser tabs, web search |
| Activity | Session list, detail trace, delete, retention pruning |
| Settings | Tavily, trusted devices, restricted apps, permissions, privacy, terms, licences |
| Eval | 20-task suite from assets, expected-tool validation through the normal agent path |

### Intentional deviations

- Settings are stored in `SharedPreferences` (`AuraSettings`) rather than DataStore so screens can
  read them synchronously during composition. DataStore remains in the dependency catalogue.
- The agent loop lives in `AgentRuntime` over an OpenAI-compatible streaming client. The MCP
  Kotlin SDK client is declared in the locked stack but does not own the loop; the unified tool
  registry is what guarantees one contract for local and remote callers. The Koog aggregator is
  intentionally not bundled: `ai.koog:koog-agents:1.0.0` declares `minSdk 35`, which conflicts with
  the required `minSdk 26`, and no runtime code imports it.
- Direct phone calls and direct SMS sending are disabled because `CALL_PHONE` and `SEND_SMS` are not
  declared; the agent opens the dialer or the SMS compose screen instead.
- `browser_upload` returns an explicit unsupported/handoff result rather than faking a file chooser.
- Browser tabs are represented by the current URL history (single WebView), not a multi-tab model.
- Fonts use `SansSerif`/`Monospace` system families until TTFs are added to `res/font` and wired in
  `AuraFonts`.
- On-device verification of the memory gates (idle ≤250 MB PSS, run ≤400 MB, cold start ≤1.8 s) and
  the 20-task suite requires a physical device; CI only proves it builds.

---

## Verification checklist

- [ ] `gradle :app:assembleDebug` succeeds from a clean clone (CI does this).
- [ ] All routes open from the capsule and deep links resolve.
- [ ] Provider key can be saved, verified, and used without appearing in logs.
- [ ] Onboarding permission steps advance when permissions are granted in system settings.
- [ ] A multi-step agent task completes and its trace survives process death.
- [ ] MCP pairing works from a second device; revoked devices are rejected.
- [ ] With models/KWS absent, wake word and icon detection degrade cleanly rather than crash.
- [ ] Idle/run PSS and cold start meet the TRD §15 gates on a 4 GB device.
