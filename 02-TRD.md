# TRD — AURA for Android (Technical Requirements Document)

> Rebuild reference for `com.aura.aura_ui` v1.0.601. Language: Kotlin. UI: Jetpack Compose.
> Everything here is derived from the shipped build; where a value must be re-verified, it is marked **[verify]**.

---

## 1. Architecture in one picture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        :app  (com.aura.aura_ui)                      │
│                                                                      │
│  Presentation (Compose)          Domain / Agent                      │
│  ├ AuraAppShell + NavHost        ├ AuraAgent (Koog + custom driver)  │
│  ├ Home / Activity / Agent hub   ├ strategy, ledger, hitl, memory    │
│  ├ Settings / Onboarding         ├ tool inventory + deferred tools   │
│  └ Overlays (pill, voice)        └ LLM layer (providers, catalog)    │
│                                                                      │
│  Platform services               Data                                 │
│  ├ AuraAccessibilityService      ├ Room (AuraDatabase)               │
│  ├ MediaProjection (capture)     ├ DataStore / SharedPreferences     │
│  ├ NotificationListener          ├ EncryptedSharedPreferences (keys) │
│  ├ WakeWord / Assistant FGS      └ file stores (traces, screenshots)  │
│  └ Keyboard (IME)                                                    │
│                                                                      │
│  MCP client  ──────────────┐                                         │
└────────────────────────────┼─────────────────────────────────────────┘
                             │  in-process HTTPS / localhost
┌────────────────────────────┼─────────────────────────────────────────┐
│                    :mcp-server (com.aura.mcp)                        │
│  McpServerController · server (Ktor/Netty) · LAN + TLS               │
│  tools/*  → bridges/*  → app callbacks                               │
│  cache · audit log · pairing crypto · health                         │
└──────────────────────────────────────────────────────────────────────┘
                             ▲
                    paired external MCP client (LAN)
```

**Key design decision — the app talks to itself over MCP.** The agent is an MCP *client*; the tool implementations live in `:mcp-server`. This gives one tool contract for both the on-device agent and remote clients, and keeps the tools testable in isolation. The wiring is in-process, so there is no network hop for the local agent.

---

## 2. Modules & build configuration

### 2.1 Gradle
```
rootProject.name = "aura_ui"
include(":app")
include(":mcp-server")
```
- Repositories: `google()`, `mavenCentral()`, `flatDir { dirs("app/libs") }`.
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
- Root `app/build.gradle.kts` is in `aura-android/`.

### 2.2 Android config (`:app`)
| Setting | Value |
|---|---|
| `namespace` | `com.aura.aura_ui` |
| `applicationId` | `com.aura.aura_ui.feature` |
| debug `applicationIdSuffix` | `.debug` |
| `compileSdk` | 36 |
| `minSdk` | 26 (Netty requires it; Koog AARs declare 35 → `tools:overrideLibrary`) |
| `targetSdk` | 36 |
| JVM target / compileOptions | 17 |
| `ndk.abiFilters` | `arm64-v8a`, `armeabi-v7a` (+ `x86_64` **debug only**) |
| BuildConfig | version fields injected via `buildConfigField` |

### 2.3 Versioning (must be reproduced)
```
versionCode = gradleProperty("aura.versionBase") + git commit count
versionName = "1.0.$versionCode"      // debug appends "-<shortSha>"
```
`aura.versionBase` lives in `gradle.properties` and exists because earlier releases shipped up to 534; Android rejects a lower `versionCode` on upgrade. Do **not** hand-bump versions.

### 2.4 Plugins
`com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`, `org.jetbrains.kotlin.plugin.serialization`, `kotlin-parcelize`.

### 2.5 Dependency set (exact, from `libs.versions.toml` + inline)
| Area | Artifact | Version |
|---|---|---|
| Agent framework | `ai.koog:koog-agents` | 1.0.0 |
| MCP client | `io.modelcontextprotocol:kotlin-sdk-client` | 0.8.3 |
| HTTP engine | `io.ktor:ktor-client-okhttp` | [verify] |
| Core | `androidx.core:core-ktx` | [verify] |
| Lifecycle | `lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx`, `lifecycle-service` | 2.7.0 |
| Compose | BOM + `ui`, `ui-graphics`, `ui-tooling-preview`, `material3` | BOM [verify] |
| Icons | `compose.material:material-icons-extended` | 1.5.4 |
| Fonts | `compose.ui:ui-text-google-fonts` | 1.6.0 |
| Views | `appcompat` 1.6.1, `material` 1.10.0, `constraintlayout` 2.1.4 | — |
| Navigation | `navigation-compose` 2.7.5 | — |
| Splash | `core-splashscreen` 1.0.1 | — |
| DI | Hilt (+ `hilt-navigation-compose`) | [verify] |
| Network | Retrofit 2.9.0 + gson converter, OkHttp 4.12.0 + logging | — |
| Secure storage | `androidx.security:security-crypto` 1.1.0-alpha06 | — |
| Background | `androidx.work:work-runtime-ktx` | [verify] |
| Serialization | `kotlinx-serialization-json` | [verify] |
| Biometrics / TLS | `biometric` 1.2.0-alpha05, `bcpkix-jdk18on` 1.77 | — |
| QR | `zxing:core` 3.5.3 | — |
| Coroutines | `kotlinx-coroutines-android` 1.7.3 | — |
| Media | `androidx.media:media` 1.7.0 | — |
| Wake word | **local AAR** `sherpa-onnx-static-link-onnxruntime-1.12.28.aar` (git-ignored, auto-downloaded from the pinned GitHub release at configuration time) | 1.12.28 |
| Storage | DataStore preferences 1.0.0, Room 2.7.0 (runtime/ktx/compiler via KSP) | — |
| Text utils | `commons-codec` 1.16.1 (Double Metaphone) | — |
| Permissions | `permissionx` 1.7.1 | — |
| Animation | Lottie 6.1.0, `compose-shimmer` 1.3.3 | — |
| Perception | `onnxruntime-android` 1.19.0, `mlkit:text-recognition` 16.0.1 | — |
| Test | JUnit4, Robolectric 4.11.1, Mockito 5.6.0, mockito-kotlin 5.1.0, coroutines-test 1.7.3, `mockwebserver3` 5.2.1, ui-test-junit4, uiautomator | — |

> **Do not** use `onnxruntime-android-qnn`: it was measured to tie CPU on a Snapdragon 7+ Gen 3 and it drops NNAPI/XNNPACK for other devices.

### 2.6 Build types
| | debug | release |
|---|---|---|
| `applicationIdSuffix` | `.debug` | — |
| ABI | + `x86_64` | `arm64-v8a`, `armeabi-v7a` |
| Signing | default debug keystore | CI keystore via secrets, else unsigned |
| Minify | off | R8 + keep rules **[verify]** |
| Crash reporting | none | none (removed) |

### 2.7 Required Gradle guards
1. **sherpa AAR fetch** — if `app/libs/sherpa-onnx-static-link-onnxruntime-1.12.28.aar` is missing, download it from
`https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.28/sherpa-onnx-static-link-onnxruntime-1.12.28.aar`
at configuration time. The AAR is intentionally not in git.
2. **`local.properties` reader** — `localProperty(key, default)` for LAN IPs/dev endpoints. Never commit.

---

## 3. Manifest contract

`android:name=".AuraApplication"`, `allowBackup`, `usesCleartextTraffic="true"`, network security config, `supports-screens` explicitly declared (fixes MIUI overlay scaling).

**Permissions:** RECORD_AUDIO, SYSTEM_ALERT_WINDOW, INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE + MICROPHONE + CONNECTED_DEVICE + SPECIAL_USE + MEDIA_PROJECTION, POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS, WAKE_LOCK, TURN_SCREEN_ON, VIBRATE, RECEIVE_BOOT_COMPLETED, DISABLE_KEYGUARD, SCHEDULE_EXACT_ALARM, SET_ALARM, BIND_ACCESSIBILITY_SERVICE, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, QUERY_ALL_PACKAGES, READ_EXTERNAL_STORAGE (≤29), READ_CONTACTS, READ_MEDIA_IMAGES/VIDEO/AUDIO, ACCESS/C HANGE_WIFI_STATE, BLUETOOTH(+CONNECT), CAMERA, WRITE_SETTINGS, MODIFY_AUDIO_SETTINGS, ACCESS_NOTIFICATION_POLICY, ACCESS_FINE/COARSE_LOCATION, CHANGE_NETWORK_STATE, WRITE_SECURE_SETTINGS.

**Components:**
| Component | Type | Notes |
|---|---|---|
| `.MainActivity` | activity | `singleTask`, `showWhenLocked`, `turnScreenOn`, splash theme; deep link `aura://mcp/oauth` |
| `.accessibility.AuraAccessibilityService` | service | `BIND_ACCESSIBILITY_SERVICE`, `mediaProjection`, `accessibilityServiceInfo` XML |
| `.overlay.AuraOverlayService` | service | `microphone\|specialUse` |
| `.overlay.VisualFeedbackOverlayService` | service | draws tap feedback |
| `.services.WakeWordListeningService` | service | `microphone\|specialUse` |
| `.services.AssistantForegroundService` | service | agent run host |
| `.notifications.AuraNotificationListenerService` | service | `BIND_NOTIFICATION_LISTENER_SERVICE` |
| `.ime.AuraKeyboardService` | service | `BIND_INPUT_METHOD` |
| `.agent.memory.ReminderReceiver` | receiver | exact alarms |
| `.receivers.BootReceiver` | receiver | `BOOT_COMPLETED`, sticky restart |

Also required: `res/xml/accessibility_service_config.xml`, `network_security_config.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, keyboard `method.xml`.

---

## 4. Source layout (mirror this)

```
app/src/main/java/com/aura/aura_ui/
├── Accessibility/          AuraAccessibilityService, UITreeExtractor, GestureInjector,
│                           ScreenCaptureManager, SystemControlsHelper, TouchWatchOverlay
├── agent/                  AuraAgent, strategy, ledger, hitl, memory, proof, research,
│   ├── llm/                LlmEndpointCatalog, ProviderKeyStore, ModelCatalog, clients
│   ├── mcpbridge/          McpTool, McpToolRegistry, McpToolSource, telemetry
│   ├── conversation/       companion tools, live tool declarations
│   └── skills/
├── audio/ voice/           recorders, players, VAD, STT/TTS
├── automation/             gesture primitives, app resolution
├── contacts/ data/         Room, prefs, stores
├── executor/               step executor
├── ime/                    keyboard service
├── mcp/                    client-side MCP UI state, server status
├── notifications/
├── overlay/                pill, glow, visual feedback, voice surface
├── presentation/
│   ├── navigation/         AuraAppShell (routes + capsule bottom bar)
│   ├── screens/            all feature screens (+ onboarding/)
│   └── theme refs
├── receivers/ remote/ services/ telemetry/ ui/theme/ utils/ uistream/
```

`mcp-server/src/main/kotlin/com/aura/mcp/` → `McpServerController.kt`, `McpServerHealth.kt`, `server/`, `lan/`, `cache/`, `tools/`, `bridge/`.

---

## 5. Agent runtime (behavioural contract)

### 5.1 Loop
```
buildContext()
  device facts → foreground app → tool inventory → memory (RAG) →
  skills → learned prefs → recent failures → user goal
        ↓
LLM (stream) ──► plan / tool_calls
        ↓
McpToolRegistry.execute(name, args)  ──► :mcp-server ──► device
        ↓
verify_action(expectation)  ──► observe (a11y tree / screenshot)
        ↓
stream event → UI (thinking | tool | result | error | screenshot)
        ↓
repeat until final answer or step budget exhausted
```

### 5.2 Rules the runtime must enforce
1. **No blind taps.** Prefer `run_steps` batches with element indices from `read_screen`; coordinates only as fallback.
2. **Verify after mutate.** `tap/type_text/swipe/set_value` must be followed by an observation.
3. **Step budget + loop detection.** Same tool+args twice in a row without state change ⇒ abort with explanation.
4. **Interruptibility.** A pause flag is checked before every tool dispatch; pause never waits for the LLM.
5. **Destructive-goal gate.** `DestructiveGoal` classifier routes to `AskUser` confirmation card.
6. **Restricted apps.** `.mcp-server.bridge.RestrictedAppsStore` is consulted for the foreground package; refusal is a normal tool error.
7. **Trace everything.** Every tool call writes to the session log with args, result hash, duration, and (optionally) screenshot.

### 5.3 LLM layer
- `LlmEndpointCatalog` — immutable list of `LlmEndpoint(id, displayName, baseUrl, chatCompletionsPath, modelsUrl, authScheme, modelListStyle, visionInference, reasoningInference, profile, isBuiltIn, keyHint, keyUrl, defaultModelId)`.
- **Auth schemes:** `BEARER`, `X_GOOG_API_KEY` (Gemini), `ANTHROPIC_X_API_KEY` (models endpoint only), `NONE` (Ollama-style).
- **Model list parsing:** `OPENAI_DATA`, `GEMINI_MODELS`; context window fallback to `max_input_tokens`.
- **Provider profiles** encode per-vendor quirks: reasoning param name, vision content shape, tool-arg repair, message merging.
- Built-ins (id → base URL):
  `groq`→`api.groq.com`, `openrouter`, `gemini`→`generativelanguage.googleapis.com/v1beta/openai`, `openai`, `anthropic`, `mistral`→`api.mistral.ai`, `dashscope`→`dashscope-intl.aliyuncs.com`, `cerebras`→`api.cerebras.ai`, `zai`→`api.z.ai`, `xai`→`api.x.ai`, `together`→`api.together.xyz`, `moonshot`→`api.moonshot.ai`, `deepseek`→`api.deepseek.com`, `nvidia`→`integrate.api.nvidia.com`.
- `defaultModelId` only where verified live; blank = user must pick from the fetched catalogue.
- **Key storage:** `ProviderKeyStore` over EncryptedSharedPreferences, one key per endpoint id, migration from the legacy plaintext store (`LlmEndpointCatalogTest` guards the migration contract).

---

## 6. MCP server (`:mcp-server`)

| Requirement | Detail |
|---|---|
| Transport | HTTPS (self-signed cert generated with BouncyCastle), localhost + LAN bind |
| Discovery | mDNS advertisement + QR payload |
| Pairing | `PairingCrypto` — ephemeral key exchange, token issued to client |
| Auth | Bearer token per client; principals tracked by `TokenPrincipal` |
| Approval | risky/high-impact tools raise a notification (`McpApprovalNotifier`) and wait for user approval |
| Audit | `McpAuditLogger` + `SessionLogSink` → on-device session log |
| Health | `McpServerHealth` → status chip |
| Caching | `cache/` for repeated screen reads keyed by UI generation |
| Tools | `tools/*.kt` — one file per domain, schemas as JSON |
| Bridges | `bridge/*` — app-side implementations: Browser, Contacts, DeepLink, Device, Files, Media, Notification, Perception, Screenshot, SystemIntent |

**Tool result envelope (all tools):**
```json
{ "ok": true, "changed": true, "observed": { "...": "..." }, "error": null,
  "meta": { "duration_ms": 412, "screenshot": "sess/…/step7.png" } }
```

---

## 7. Device tool surface

### 7.1 UI automation
`read_screen` (a11y tree, optional `changes_only`), `tap` (bounds/coords/index), `swipe`, `scroll`, `set_value`, `type_text`, `press_key`, `press_enter`, `wait_for` (condition + timeout), `verify_action`, `run_steps` (batch), `get_screenshot`, `perceive_screen` (vision model), `request_screen_capture_permission`.

### 7.2 Apps & navigation
`launch_app`, `lookup_app`, `open_deeplink`, `resolve_deeplink`, `list_app_deeplinks`.

### 7.3 Data
`find_files`, `open_file`, `clipboard` (read/write), `resolve_contact`, `dial`, `compose_sms`.

### 7.4 System
`get_device_status` (battery, network, model, API level, next alarm, a11y running), `volume`, `system_intent` (settings toggles, alarm/timer, share), `watch_device_events`.

### 7.5 Media & notifications
`media_control`, `get_media_sessions`, `read_notifications`, `notification_action`, `dismiss_notification`.

### 7.6 Browser
`browser_open/read/find/act/extract/tabs/wait/screenshot/upload/handoff/close` — runs in a dedicated handoff window; WebView-backed; "I'm done" returns control.

### 7.7 Web & session
`web_search` (Tavily, key in secure prefs), `end_session`.

---

## 8. Perception subsystem
1. **Accessibility tree** — primary. `UITreeExtractor` walks `AccessibilityNodeInfo`, emits a compact indexed tree with bounds, text, content-desc, class, clickable/checkable state. Bounded depth/node count.
2. **Vision (optional)** — `omniparser_icon_detect.onnx` (20.5 MB) with ONNX Runtime; INT8; CPU/NNAPI/XNNPACK selected by per-device calibration; output = icon boxes + labels.
3. **OCR** — ML Kit text recognition for screens where the tree is empty (canvas/WebView).
4. **Frame policy** — never hold more than one full-resolution frame; downscale before vision; recycle bitmaps. *This is the main memory risk in the current build.*

---

## 9. Voice pipeline
```
mic → VAD (silero_vad.onnx, 2.3 MB) → KWS (sherpa-onnx, assets/kws/)
     → wake ⇒ AssistantForegroundService → STT → goal → agent → TTS
```
- Wake word disabled by default when `wake_word_enabled=false`; **do not auto-start from Application or AccessibilityService** (causes LMK pressure).
- Audio focus, ducking, and a single recorder owner enforced via `RecordableVoiceStore`.

---

## 10. Data & persistence

| Store | Technology | Contents |
|---|---|---|
| Runs / traces | Room (`AuraDatabase`) | sessions, steps, tool calls, outcomes |
| Contacts cache | Room `AuraContactEntity` | name ↔ number mapping |
| STT corrections | Room `SttCorrectionEntity` | user-fixed transcriptions |
| Settings | `SharedPreferences("aura_settings")` | booleans/durations (see PRD/Backend doc) |
| Provider keys | EncryptedSharedPreferences | per-endpoint API keys |
| Tavily key | EncryptedSharedPreferences | search key |
| Screen images | files + `ReadScreenImageStore` | last-frames, pruned |
| Traces/screens | `filesDir/sessions/<id>/` | JSONL + PNG, retention-pruned by WorkManager |
| Skills | `assets/skills` + user dir | prompt-side skill definitions |
| Learnings | JSON store | durable user preferences extracted from runs |

---

## 11. Concurrency & lifecycle
- Single `applicationScope` (SupervisorJob + Dispatchers.Default) for background work; `serviceScope` per service.
- Agent runs on `Dispatchers.IO`; UI collects `StateFlow`.
- All Room access off the main thread.
- Accessibility events debounced (a settling list can emit hundreds of events).
- MCP server has its own Netty event loops; never block them on UI.

---

## 12. Error handling contract
| Failure | Behaviour |
|---|---|
| No API key / invalid key | Blocking card → opens Provider settings; no silent retry |
| Provider 429/5xx | Exponential backoff (3 tries), then surface error in trace |
| Tool permission missing | Structured `permission_denied` + deep link to the exact settings page |
| Screen capture revoked | Re-request via `request_screen_capture_permission` |
| Accessibility disabled mid-run | Pause + status chip alert |
| Process killed | Session persisted; resume prompt on next launch |
| LLM stalls | Request timeout (stalled-server test) + abort with message |

---

## 13. Testing requirements
- **Unit:** provider catalog/migration, model parsing, argument repair, step budget, restricted-app gate, version code math.
- **Robolectric:** tool schema validation, prefs migration.
- **Instrumented (UiAutomator):** overlay reachability on OEM skins, browser handoff button, onboarding permission auto-advance.
- **MockWebServer:** stalled LLM (never answers) must not hang the run.
- **Eval harness:** 20-task suite, pass/fail per task, stored in `assets/eval`.

---

## 14. CI/CD
Workflow `Android Build and Release` (`ubuntu-latest`, JDK 17):
1. checkout, setup JDK, setup Android SDK
2. Gradle cache
3. `chmod +x gradlew`
4. *prepare config* — copy example config if a real one is absent (**never commit real keys**)
5. print debug/release certificate SHA-1/SHA-256 (for provider restrictions)
6. `./gradlew assembleDebug`
7. `assembleRelease` (signed if keystore secrets present, else unsigned)
8. collect APKs, upload artifact `aura-android-apks`
9. optional GitHub Release on tag

**Secret policy:** provider keys are user-supplied at runtime. CI holds only the signing keystore. No Google API key, no `google-services.json` in git.

---

## 15. Performance budget (hard gates)
| Metric | Budget | Instrumentation |
|---|---|---|
| Cold start to first frame | ≤ 1.8 s | `ActivityTaskManager` log |
| Idle PSS | ≤ 250 MB | `dumpsys meminfo` |
| Run PSS | ≤ 400 MB | same, sampled at step 10 |
| Model load | lazy, on demand only | log around ONNX session create |
| Bitmap held | ≤ 1 full frame | allocation canary |
| Wake word idle CPU | ≤ 3 %/h | Battery Historian |

> The current build exceeds these (≈505 MB) and is killed by LMK at ~42 % thrashing on a 3.6 GB device. A rebuild must treat these numbers as acceptance criteria, not aspirations.
