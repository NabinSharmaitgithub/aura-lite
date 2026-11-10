# Implementation Plan — AURA for Android (rebuild from scratch)

> **Goal:** reach parity with the shipped app *without* inheriting its defects, in the shortest
> safe order. Each phase ends with a runnable APK and a measurable acceptance check.
> **Reference size:** 912 Kotlin files / ~137k LOC in the original — do not copy that volume;
> build the 12 subsystems below and the screens that expose them.

---

## 0. Rules of the rebuild

1. **Runnable at every phase.** No phase may end with a non-building tree.
2. **No cloud.** No accounts, sync, analytics, crash reporting, remote config, kill switch.
3. **No secrets in git.** Provider keys are entered by the user at runtime.
4. **Memory is an acceptance test, not a later optimisation** (budget in TRD §15).
5. **Design tokens before screens.** Every screen uses `Mono` + `Shape*`; no raw hex.
6. **One tool contract** for the agent and external MCP clients.

---

## Phase 0 — Scaffold & guards (0.5 day)

**Tasks**
- [ ] Gradle project `aura_ui` with `:app` + `:mcp-server`.
- [ ] Kotlin 2.x, Compose BOM, Hilt, KSP, serialization, parcelize.
- [ ] `minSdk 26`, `compileSdk 36`, `targetSdk 36`, JDK 17.
- [ ] `applicationId com.aura.aura_ui.feature`, debug suffix `.debug`.
- [ ] Auto-versioning (`aura.versionBase` + commit count).
- [ ] sherpa AAR fetch guard; `abiFilters` arm64 + armv7 (+ x86_64 debug).
- [ ] CI workflow: build debug, build release (optional signing), upload `aura-android-apks`.

**Acceptance:** `./gradlew assembleDebug` green on CI; APK installs and shows a blank Compose screen.

---

## Phase 1 — Design system + shell (2 days)

**Tasks**
- [ ] `ui/theme/`: `Mono.kt` (palette, spacing, shape, motion, `MonoScheme` light/dark), `Type.kt` (Plus Jakarta Sans + JetBrains Mono, full scale).
- [ ] Components: `InkButton`, `SurfaceCard`, `GroupedList`/`GroupedRow`, `StatusChip`, `Switches`, `SectionHeader`, `EmptyState`, `TraceAtom`.
- [ ] `AuraAppShell`: Scaffold + floating capsule (`CapsuleTabs`), route titles map, back behaviour.
- [ ] Placeholder screens for the 4 tabs.
- [ ] Motion tokens wired (`tween(220)` transitions, `150` press).

**Acceptance:** capsule works (4 icons ↔ back+title), dark mode toggles, 1.3× font scale reflows without clipping.

---

## Phase 2 — Persistence & settings (2 days)

**Tasks**
- [ ] Room: `AuraDatabase` v1, `AuraContactEntity`, `SttCorrectionEntity`, DAOs, `Converters`.
- [ ] `aura_settings` SharedPreferences wrapper (typed getters, defaults from Backend §3).
- [ ] `EncryptedSharedPreferences` wrapper: `provider_key_<id>`, `tavily_api_key`.
- [ ] Session JSONL writer/reader + retention prune worker.
- [ ] Learnings JSON store + skills loader.

**Acceptance:** kill the process mid-write → trace file still parses; prefs survive reinstall of debug build; keys are unreadable in a plaintext dump.

---

## Phase 3 — Accessibility engine (4–5 days) ← highest risk

**Tasks**
- [ ] `AuraAccessibilityService` + `accessibility_service_config.xml` (canRetrieveWindowContent, events, flags).
- [ ] `UITreeExtractor`: indexed compact tree (bounds, text, desc, class, clickable, checkable), node/depth caps, stable indices.
- [ ] `GestureInjector`: tap / long-press / swipe / scroll / set text / key events via `dispatchGesture` + `ACTION_SET_TEXT`.
- [ ] `ScreenCaptureManager`: MediaProjection consent, single-frame capture, downscale, recycle.
- [ ] `TouchWatchOverlay`: human-touch signal (ACTION_OUTSIDE) to pause automation.
- [ ] `ScreenGeneration` counter bumped on content change + debounce (list settling).
- [ ] `SystemControlsHelper`: volume, brightness, toggles, alarms, media.

**Acceptance:** can read a tree from 5 apps (WhatsApp, Settings, Chrome, Photos, a Flutter app); can tap by index with ≥ 95 % hit rate; screenshot capture doesn't leak bitmaps (PSS flat over 50 captures).

---

## Phase 4 — Tool surface in `:mcp-server` (5–6 days)

**Tasks**
- [ ] JSON-RPC MCP server (`InProcessMcpServer` first — no sockets).
- [ ] `McpToolRegistry` + schema parser + `ScopedToolRegistration`.
- [ ] Tools by domain: UI, apps/deeplinks, files, contacts, media, notifications, device, clipboard, system_intent, browser, perception, web_search, session.
- [ ] Result envelope + error codes (Backend §6.6).
- [ ] `PostActionObservation` (settle + verify), `SensitivePolicy` (destructive detection), `RestrictedAppsStore`.
- [ ] Approval path for risky tools.

**Acceptance:** a scripted client executes a 6-step flow (open app → search → tap result → read screen) with structured results; blocked app returns `blocked_app`; unapproved destructive call never executes.

---

## Phase 5 — LLM layer + agent loop (5–6 days)

**Tasks**
- [ ] `LlmEndpointCatalog` with the 14 built-ins + custom endpoint model.
- [ ] `ProviderKeyStore` (encrypted), migration-free start.
- [ ] `ModelCatalog.fetch` for `OPENAI_DATA` and `GEMINI_MODELS`.
- [ ] `ProviderProfile` shims: auth headers, vision content shape, reasoning params, tool-arg repair.
- [ ] Koog-based agent with a custom driver: context assembly, plan, tool calls, verify, answer.
- [ ] Streaming events → UI state flow.
- [ ] Loop detection, step budget, pause flag, interrupt.
- [ ] Timeout + backoff (429/5xx), stalled-request abort.

**Acceptance:** with a Groq **and** an NVIDIA key: 10-task suite completes ≥ 7; a deliberately stalled server aborts within 30 s; pausing blocks the next tool call within 100 ms.

---

## Phase 6 — Screens: Home, Agent, Activity, Settings (4–5 days)

**Tasks**
- [ ] `AuraHomeScreen`: greeting, status chips, composer, marquee, active run card, recent runs.
- [ ] `AgentHubScreen` + Brain (`ProviderSettingsScreen`), Tools, Hooks, Skills, Memory, Companion, Voice.
- [ ] `Activity` list + `Session detail` (trace atoms, filters, re-run/delete).
- [ ] `MonoSettingsScreen` + Permissions health, Restricted apps, Trusted devices, Tavily, Privacy/Terms/Licences.
- [ ] All five states per screen: loading, empty, error, permission-missing, offline.

**Acceptance:** every route in App Flow §2 is reachable and back-navigates correctly; no screen shows a raw exception.

---

## Phase 7 — Onboarding (2 days)

**Tasks**
- [ ] 10-step wizard with persisted index and auto-advancing permission steps (App Flow §3).
- [ ] Consent gate, name capture, key entry + live verify.
- [ ] Restricted-settings guidance (Android 13+ sideload).
- [ ] Re-entry path from Settings → Privacy (reset).

**Acceptance:** fresh install → first successful run in under 3 minutes without reading docs; killing the app at step 5 resumes at step 5.

---

## Phase 8 — Voice (4 days)

**Tasks**
- [ ] sherpa-onnx KWS with `assets/kws` model, foreground service (`microphone|specialUse`).
- [ ] Silero VAD, STT, TTS; single recorder owner (`RecordableVoiceStore`).
- [ ] Overlay: idle / listening / thinking / speaking states.
- [ ] **Do not auto-start wake word** from `Application` or the Accessibility service; start only when `wake_word_enabled` and the user enables it.
- [ ] Audio-focus handling and ducking.

**Acceptance:** wake → ask → act → speak round-trip ≤ 6 s on device; disabling voice leaves zero mic-related services running.

---

## Phase 9 — Perception (optional but parity) (3 days)

**Tasks**
- [ ] ONNX Runtime integration, CPU/NNAPI/XNNPACK selection with per-device calibration.
- [ ] `perceive_screen` tool: icon boxes + labels, merged with a11y tree.
- [ ] ML Kit OCR fallback for canvas/WebView screens.
- [ ] **Lazy session creation, explicit `close()` at run end, single-frame policy.**

**Acceptance:** vision model loads only on first `perceive_screen`; PSS returns to baseline within 2 s of run end; no OOM across 100 calls.

---

## Phase 10 — MCP over LAN + pairing (3 days)

**Tasks**
- [ ] HTTPS server (BouncyCastle self-signed), LAN bind, mDNS, QR payload.
- [ ] `/pair` → bearer token, trusted-device store, revoke.
- [ ] Audit log (hashed args), health endpoint, client count in status chip.
- [ ] Approval notifications for risky tools.

**Acceptance:** external MCP client pairs by QR in < 30 s; revoked token is rejected; audit rows appear for every call.

---

## Phase 11 — Hardening & release (3–4 days)

**Tasks**
- [ ] Memory pass against TRD §15 — profile idle, after onboarding, after a 10-step run, after 30 min.
- [ ] Startup pass: no eager ONNX, no eager contact sync, no eager network.
- [ ] Battery: wake-word idle ≤ 3 %/h.
- [ ] R8 keep rules; release APK ≤ 120 MB.
- [ ] Eval harness wired to the 20-task suite.
- [ ] Secret scan (`git log -S 'AIza'`, `sk-`, `nvapi-`) in CI.
- [ ] Legal screens content + third-party notices.

**Acceptance:** 30-minute continuous session on a 4 GB device with zero LMK kills; cold start ≤ 1.8 s; CI green; no secrets in history.

---

## Phase 12 — Parity verification (1–2 days)

Walk the PRD §4 acceptance table top to bottom on hardware. Anything failing is a bug, not a phase.

---

## Effort summary

| Phase | Days | Cumulative |
|---|---|---|
| 0 Scaffold | 0.5 | 0.5 |
| 1 Design + shell | 2 | 2.5 |
| 2 Persistence | 2 | 4.5 |
| 3 Accessibility | 5 | 9.5 |
| 4 Tools | 6 | 15.5 |
| 5 LLM + agent | 6 | 21.5 |
| 6 Screens | 5 | 26.5 |
| 7 Onboarding | 2 | 28.5 |
| 8 Voice | 4 | 32.5 |
| 9 Perception | 3 | 35.5 |
| 10 MCP LAN | 3 | 38.5 |
| 11 Hardening | 4 | 42.5 |
| 12 Verification | 2 | 44.5 |

≈ **9 working weeks** for one experienced Android engineer; ~5 weeks for two in parallel (split Phase 3–4 vs 5–6).

---

## Do-not-repeat list (defects observed in the shipped build)

| Defect | Cause | Rebuild rule |
|---|---|---|
| App killed by LMK at ~500 MB PSS | eager init + retained frames + full trace in memory | lazy init, single frame, bounded buffers, PSS gate in CI |
| Wake word auto-started from Application/Accessibility service | "always on" design | start only on explicit enable |
| `google-services.json` with a live API key committed | force-added for CI | never commit; use example + runtime user key |
| Cloud account surface tangled through UI gating | feature not core | no accounts at all |
| Hilt missing `@ApplicationContext` on a stubbed class | removal left a bare `Context` | inject `@ApplicationContext` or drop the class |
| Navigation brace/structure corruption after mass deletion | large mechanical edits | edit one screen per commit, build after each |

---

## Definition of done
- [ ] All PRD functional requirements pass on device.
- [ ] Memory/battery/startup budgets pass.
- [ ] CI produces debug + release APKs from a clean clone.
- [ ] No network destination except the user's LLM provider, Tavily, and the local MCP client.
- [ ] No secret in repo history.
- [ ] Docs (this set) updated to match the shipped build.
