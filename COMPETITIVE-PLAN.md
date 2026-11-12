# AURA Lite — Complete competitive plan: features, implementation & requirements

> Produced 2026-11-12 by direct analysis of `Dinesh210805/aura-android-use` (the competitor repo).
> **Internal strategy — do not commit to the public repo.** Replaces/supersedes `COMPETITIVE-ROADMAP.md`.
> Contains **no secrets**. Credential handling is described, never the values themselves.

---

## 1. Executive summary

AURA Lite today is an **on-device Android AI agent** with (a) a plan→act→verify loop over an
OpenAI-compatible streaming API, (b) an accessibility-tree reader and gesture executor, (c) 49 MCP
tools exposed in-process and over LAN HTTPS + (d) an offline-first privacy posture
(diagnostics opt-in, no required account, opt-in Firebase).

The competitor — `aura-android-use` — is the same product category, shipped faster in public:
launched 2026-09-22, 24 stars, ~850 APK downloads, a website, npm bridge, launch film, Discord,
and a published AndroidWorld benchmark. They are ahead on **distribution, proof, and legal
infrastructure** (AGPL license + commercial option + CLA + website + downloads) and roughly
even with us on raw features (both ~49–50-tool coverage).

We are ahead on **release engineering** (CI: secret scan → build → sign → verify → artifacts →
GitHub release), **privacy posture** (offline-first, diagnostics opt-in), **footprint** (91 MB vs
their 155 MB), and we add unique surfaces (B2 backup/export, Firebase sync as an opt-in layer).
We are behind on **supporting assets the public judges**: website, demos, community, measured
benchmark, unit tests (0 vs 240), license (none vs AGPL+commercial).

**The plan below wins on (a) legal + trust basics (days), (b) our own distribution + benchmark
(weeks), (c) closing the 3 real product gaps — `ask_user`, conversation mode, bundled perception
assets — and (d) a privacy-first market position.**

---

## 2. Feature-by-feature comparison

### 2.1 Product & distribution

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| D1 | Public website (landing, install guide, architecture page) | ❌ | ✅ aura-android-use.vercel.app (static HTML) | **gap** |
| D2 | Launch film / demo clips | ❌ | ✅ 4:25 launch film + 5 clips | **gap** |
| D3 | Download counter | ❌ | ✅ install-stats.json + Vercel analytics (96 installs, 33 countries) | **gap** |
| D4 | Discord community | ❌ | ✅ | **gap** |
| D5 | MCP bridge for desktop clients (npm) | ❌ | ✅ `aura-mcp-connect@0.11.0` (WebRTC, pairing) | **gap** |
| D6 | npm packaging & listing | ❌ | ✅ | **gap** |
| D7 | M8ven / directory listings | ❌ | ✅ | **gap** |
| D8 | Professional-looking README + badges + comparison table | 🟡 basic | ✅ | **gap** |
| D9 | Signed production APK | ✅ `AURA Release` key | ✅ (Stable-ish; no CI) | **win (ours)** |
| D10 | CI: secret scan, build, sign, verify, report, tag release | ✅ | ❌ (manual Windows builds) | **win** |
| D11 | Release notes / changelog | ✅ auto | ✅ manual | **tie** |
| D12 | GitHub-hosted releases | ✅ | ✅ | **tie** |
| D13 | Stars/forks | 0/0 | 24/8 | **gap** |

### 2.2 Assistant UX (phone-first experience)

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| U1 | Wake word "hey aura" | 🟡 code exists, no KWS model bundled | ✅ 5.1 MB sherpa-onnx KWS model in APK | **gap** |
| U2 | Push-to-talk / assistant gesture entry | ✅ | ✅ | **tie** |
| U3 | On-device STT (voice command → text) | 🟡 sherpa AAR, models not bundled | ✅ Moonshine (fully on-device) | **gap** |
| U4 | Voice replies (TTS) | 🟡 sherpa, models absent | ✅ on-device | **gap** |
| U5 | Silero VAD | 🟡 code, asset absent | ✅ bundled (2.3 MB ONNX) | **gap** |
| U6 | **Conversation mode** (real-time back-and-forth Gemini Live + `ask_aura` hand-off) | ❌ | ✅ `CompanionLiveController`, `LiveSession`, `LiveModelResolver` | **big gap** |
| U7 | Pause pill / cancel mid-run | ✅ | ✅ | **tie** |
| U8 | Assistant bubble overlay (ambient) | 🟡 basic overlay | ✅ rich set (`VoiceAssistantOverlay`, `AuraLogoBubble`) | **gap** |
| U9 | Pause / ask-user overlay cards | 🟡 basic | ✅ `AskUserCard`, `RunReportOverlay`, `PausePillOverlay` | **gap** |
| U10 | Keep-awake during run | ✅ | ✅ | **tie** |
| U11 | ScreenWakeActivity (turn screen on for agent) | ❌ | ✅ | **gap** |
| U12 | OverlayFixActivity (OEM quirks workaround) | ❌ | ✅ | **gap** |

### 2.3 Agent intelligence & reliability

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| A1 | Plan → Act → Verify loop | ✅ | ✅ | **tie** |
| A2 | Step budget + loop detection | ✅ | ✅ | **tie** |
| A3 | Destructive action confirmation | ✅ `DestructiveGoal` | ✅ `DestructiveGoal` equivalent | **tie** |
| A4 | Completion judge / `verify_action` before saying done | ✅ tool only | ✅ `proof/` package + judged completion | **gap** |
| A5 | Human-in-the-loop (`ask_user` — pause, ask, resume) | ❌ | ✅ `hitl/` + `AskUserCard` | **big gap** |
| A6 | Encrypted per-app learnings | ✅ | ✅ | **tie** |
| A7 | Skills library | ✅ 5 JSON packs | ✅ 6 doctrine skills (research-first, WhatsApp wa.me, email) | **depth** |
| A8 | Research-first doctrine via `web_search` | 🟡 tool exists, not enforced | ✅ built into every unknown-flow skill | **gap** |
| A9 | Deterministic deep-link shortcuts (wa.me etc.) | ✅ deeplink catalog | ✅ + wa.me hard-coded | **tie** |
| A10 | Session report card / on-device diagnostics | ❌ | ✅ `OnDeviceReport.kt` | **gap** |
| A11 | Reminders (Room + AlarmManager) | ✅ | ✅ | **tie** |
| A12 | Runs survive app death | 🟡 foreground service, no state persistence | ✅ marketed | **gap** |
| A13 | Skills as MCP prompts | ❌ | ✅ | **gap** |
| A14 | `run_steps` batch execution | ✅ | ✅ | **tie** |

### 2.4 Perception

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| P1 | Accessibility tree extraction | ✅ | ✅ | **tie** |
| P2 | ML Kit OCR | ✅ | ✅ | **tie** |
| P3 | On-device vision model (icon detection) | ❌ asset missing — reports `icon_model=not_bundled` | ✅ 20 MB `omniparser_icon_detect.onnx` bundled in APK | **big gap** |
| P4 | Screen change detection / generation counter | ✅ | ✅ | **tie** |
| P5 | Downscaled capture for vision | 🟡 | ✅ | **tie-ish** |
| P6 | WebView / games / maps handling | 🟡 (tree-only) | ✅ (icon detection sees them) | **gap** |

### 2.5 MCP / remote clients

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| M1 | In-process MCP server | ✅ | ✅ | **tie** |
| M2 | LAN MCP over HTTPS + JSON-RPC | ✅ | ✅ (WebRTC instead of plain HTTPS) | **tie-ish** |
| M3 | mDNS discovery / zero-config pair | ✅ | ✅ | **tie** |
| M4 | Pairing: one-tap PIN → bearer token | ✅ | ✅ PIN → verification code → secret proof + revoke | **gap** |
| M5 | Bridge for desktop clients (npm) | ❌ | ✅ `aura-mcp-connect` | **gap** |
| M6 | MCP `instructions` on connect (how to use server) | ❌ | ✅ | **gap** |
| M7 | MCP `resources` (safety policy, device status, guide) | ❌ empty | ✅ | **gap** |
| M8 | MCP `prompts` (task templates, user skills) | ❌ empty | ✅ | **gap** |
| M9 | Single one-tool contract for phone + PC | ✅ | ✅ | **tie** |
| M10 | Tool catalog browser in app | ✅ | ✅ `ToolsScreen` | **tie** |
| M11 | Tool schema count | 49 | 50+ | **tie** |
| M12 | Additional tools we added (`upload_file`, `download_file`) | ✅ | ❌ | **ours** |
| M13 | MCP audit log + trusted devices UI | ✅ | ✅ | **tie** |
| M14 | MCP kill switch (remote block) | ❌ | ✅ Firestore Remote Config `kill_switch_enabled` | **gap** |
| M15 | Banking / payment / authenticator apps always blocked | ❌ (we have blocklist but agent can open banks if user wants) | ✅ always-blocked hard list | **gap** |

### 2.6 Voice (detailed)

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| V1 | Wake word engine (sherpa-onnx) | 🟡 code wired | ✅ bundled | **gap** |
| V2 | Wake word model asset ships in APK | ❌ (~5 MB) | ✅ (~5 MB) | **gap** |
| V3 | STT model asset (on-device) | ❌ | ✅ Moonshine bundled | **gap** |
| V4 | TTS model asset (on-device) | ❌ | ✅ | **gap** |
| V5 | Volume-key push-to-talk | ✅ (code; needs hardware verify) | ✅ | **tie** |
| V6 | Assistant gesture role | ✅ | ✅ | **tie** |
| V7 | Conversation mode (streaming, LLM-driven) | ❌ | ✅ Gemini Live | **gap** |

### 2.7 Skills

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| S1 | Built-in skill packs | ✅ 5 JSON (send_photo, navigate_home, quiet_phone, reply_whatsapp, …) | ✅ 6 markdown doctrine (act-without-screen, send-email, whatsapp-message, research-the-web, text-entry, aura-skill doctrine) | **tie** |
| S2 | Custom user skills | ✅ | ✅ | **tie** |
| S3 | Skills as MCP prompts | ❌ | ✅ | **gap** |
| S4 | Skill runner/enforcer in agent loop | 🟡 | ✅ doctrine injected into system prompt | **depth** |

### 2.8 Cloud / data

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| C1 | Firebase required | ❌ (offline-first, opt-in) | ⚠️ required for voice/Google sign-in | **ours** |
| C2 | Firebase Auth (Google) | ✅ opt-in | ✅ required | **ours** |
| C3 | Email/password auth | ✅ opt-in | ✅ | **tie** |
| C4 | Firestore sync (facts/plans/skills LWW) | ✅ opt-in | ✅ | **tie** |
| C5 | FCM push | ✅ opt-in | ✅ | **tie** |
| C6 | Analytics/crash reporting | ✅ opt-in | ✅ on-by-default | **ours** |
| C7 | Remote Config kill switch | ❌ | ✅ | **gap** |
| C8 | Install tracking / device counter | ❌ | ✅ | **gap** |
| C9 | Session backup (B2) | ✅ `users/sessions/<id>/screens/*.png` | ❌ | **ours** |
| C10 | Session export bundles (B2) | ✅ `users/exports/*.zip` | ❌ | **ours** |
| C11 | Session pauses/resume after process death | 🟡 | ✅ | **gap** |

### 2.9 Design & UI depth

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| G1 | MONO monochrome design system | ✅ | ✅ (same lineage) | **tie** |
| G2 | Floating capsule navigation | ✅ | ✅ | **tie** |
| G3 | Onboarding 10 steps | ✅ | ✅ | **tie** |
| G4 | Brain/model picker + key verification | ✅ | ✅ | **tie** |
| G5 | Compose file count | ~12 screen files | ~30 screen files | **gap** |
| G6 | Permissions health UI (registry) | 🟡 single screen | ✅ full registry + OEM quirks | **gap** |
| G7 | Trace viewer (raw markdown) | ✅ basic | ✅ full (`TraceAtoms`, `RawBodyViewer`, `ReportMarkdown`) | **gap** |
| G8 | Eval suite UI | ✅ 20 tasks | ✅ + AndroidWorld runner | **gap** |
| G9 | Settings polish (Tavily key screen etc.) | ✅ | ✅ | **tie** |
| G10 | Keyboard (Aura Keyboard IME) | ✅ | ✅ | **tie** |

### 2.10 Engineering & ops

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| E1 | CI (build + sign + verify + release) | ✅ `android.yml` | ❌ none | **win** |
| E2 | Release signing keystore | ✅ `AURA Release`, stable since 2026-11-11 | ✅ | **tie** |
| E3 | apksigner verification enforced | ✅ | ❌ | **win** |
| E4 | Secret scan (key patterns) in CI | ✅ | ✅ (gitleaks, one-off) | **tie-ish** |
| E5 | Gradle config-cache off (correctness) | ✅ | ✅ | **tie** |
| E6 | Version scheme `534 + commit count` | ✅ | ✅ same scheme | **tie** |
| E7 | FOSS build (no Firebase) | 🟡 implicit (plugin conditional, CI `google-services.json` optional) | ❌ todo item | **ours** |
| E8 | Optimized APK size | ✅ 91 MB | 155 MB | **ours** |
| E9 | Crash-reporting flow | ✅ opt-in | ✅ default on | **ours** |
| E10 | Unit tests | ❌ **0** | ✅ 240 | **big gap** |
| E11 | Integration/eval harness | ✅ assets/eval/tasks.json (20) | ✅ + ToolBench + AndroidWorld | **gap** |
| E12 | AndroidWorld benchmark published | ❌ | ✅ | **big gap** |

### 2.11 Legal & governance

| # | Feature | Ours | Competitor | Verdict |
|---|---|---|---|---|
| L1 | LICENSE | ❌ | ✅ AGPL-3.0 | **gap** |
| L2 | Linking exception for Google Play/Firebase/ML Kit | ❌ | ✅ | **gap** |
| L3 | Commercial license option | ❌ | ✅ | **gap** |
| L4 | CLA for contributions | ❌ | ✅ | **gap** |
| L5 | CONTRIBUTING / SECURITY / PRIVACY / CODE_OF_CONDUCT | ❌ (README covers some) | ✅ | **gap** |
| L6 | CLA workflow (cla.yml) in CI | ❌ | ✅ | **gap** |
| L7 | Third-party notices | ❌ | ✅ | **gap** |

### 2.12 Permissions & privacy hygiene

| # | Permission | Ours | Competitor | Note |
|---|---|---|---|---|
| J1 | `INTERNET` | ✅ | ✅ | required |
| J2 | `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | ✅ | ✅ | required |
| J3 | `CAMERA` | ❌ not declared | ✅ declared (why?) | **ours** |
| J4 | `BLUETOOTH` / `BLUETOOTH_ADMIN` | ❌ | ✅ | **ours** |
| J5 | `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | ❌ | ✅ (unused per their TODO) | **ours** |
| J6 | `WRITE_SECURE_SETTINGS` | ❌ | ✅ | **ours** |
| J7 | `POST_PROMOTED_NOTIFICATIONS` | ❌ | ✅ | **ours** |
| J8 | `DISABLE_KEYGUARD` | ❌ | ✅ | **ours** |
| J9 | `QUERY_ALL_PACKAGES` | ✅ | ✅ | required for agent |
| J10 | `RECORD_AUDIO` | ✅ | ✅ | required |
| J11 | `SYSTEM_ALERT_WINDOW` (overlay) | ✅ | ✅ | required |
| J12 | `FOREGROUND_SERVICE*` (specialUse, microphone, mediaProjection, connectedDevice) | ✅ microphone, specialUse, mediaProjection | ✅ + connectedDevice | **tie** |
| J13 | Diagnostics on by default | ❌ | ✅ | **ours (privacy)** |

---

## 3. Gap inventory (actionable, ranked by impact)

### Tier 1 — must be closed before any public launch (days)

- **GAP-01** No LICENSE / CLA / CONTRIBUTING / SECURITY.md. Repo is legally unusable.
- **GAP-02** Zero tests. Every competitor has 240.
- **GAP-03** MCP `resources`, `prompts` and `instructions` are empty — clients think the server is dumb.
- **GAP-04** `ask_user` tool missing — agent can silently guess when unsure.
- **GAP-05** Public face: website, demo clips, README polish, npm bridge.

### Tier 2 — launch-critical (weeks)

- **GAP-06** AndroidWorld benchmark — the single number that builds trust.
- **GAP-07** Voice assets: wake-word KWS + STT (Moonshine/Whisper) + VAD — all three are code-complete, models missing.
- **GAP-08** Conversation mode (Gemini Live or open alternative) with `ask_aura` hand-off.
- **GAP-09** On-device vision model (`omniparser_icon_detect.onnx` or replacement).
- **GAP-10** Permission registry UX.

### Tier 3 — quality of life / parity polish

- **GAP-11** MCP pairing hardening (PIN-derived verification code, DTLS-style proof — can be simplified).
- **GAP-12** Banking/payment/authenticator hard block.
- **GAP-13** Research-first skill doctrine.
- **GAP-14** ScreenWakeActivity + OverlayFixActivity for OEM quirks.
- **GAP-15** Remote Config kill switch.
- **GAP-16** Screen state resume after process death.

### Tier 4 — ours-only moat (defend these)

- **M-01** B2 storage (upload_file/download_file + session backup + export bundle).
- **M-02** Offline-first, diagnostics opt-in.
- **M-03** Smaller APK (91 MB vs 155 MB).
- **M-04** CI-enforced release signing and verification.
- **M-05** Cleaner permission set (no camera/bluetooth/location).

---

## 4. Full implementation plan

### Sprint 1 — Legal, CI, protocol basics (Week 1, ~5–10 days)

- [ ] **S1.1** Write `LICENSE` (AGPL-3.0 full text) + `LICENSE-EXCEPTION.md` (Google Play services /
      Firebase / ML Kit linking exception — mirror theirs, independent wording).
- [ ] **S1.2** Write `COMMERCIAL-LICENSE.md`, `CLA.md`, `CLA.github/CLA.yml`, `CODE_OF_CONDUCT.md`.
- [ ] **S1.3** Write `CONTRIBUTING.md`, `SECURITY.md`, `SUPPORT.md` (or `.github/`), `CHANGELOG.md`.
- [ ] **S1.4** Add `AGENTS.md` with repo layout, build commands, quality bar (mirror theirs, shorter).
- [ ] **S1.5** In `.github/workflows/android.yml`: add `:app:testDebugUnitTest` and
      `:mcp-server:test` steps (keep existing secret-scan + assemble + verify).
- [ ] **S1.6** Write ≥40 unit tests: agent loop (`AgentRuntime`), `McpToolRegistry`, clipboard
      `SafeKeyStore`/`EncryptedSharedPreferences`, `S3v4` signer, skills runner, `LearningsStore`.
- [ ] **S1.7** MCP protocol: add `resources/list`, `resources/read`, `prompts/list`,
      `prompts/get` to `HttpMcpServer` + `InProcessMcpServer` and populate:
      - `resources`: `aura://safety/policy`, `aura://tools/guide`, `aura://device/status`,
        `aura://on-device-report`
      - `prompts`: `task_template/<name>` for each built-in skill
      - `instructions`: connect-time safety model, tool usage rules
- [ ] **S1.8** `ask_user` tool: overlay panel + notification action, suspends `AgentRuntime` job,
      resumes on reply. Wire into `AuraDeviceBridge`, `ToolCatalog`, `DestructiveGoal` flow.
- [ ] **S1.9** README overhaul: hero, feature matrix, badges (build | APK | license | stars).
- [ ] **S1.10** Rename decision recorded (see §7).

### Sprint 2 — Voice & skill depth (Week 2, ~7 days)

- [ ] **S2.1** Bundle sherpa-onnx KWS model (`kws/` assets, ~5 MB) and wire `WakeWordListeningService`.
- [ ] **S2.2** Bundle on-device STT (Moonshine or sherpa zipformer ~30–60 MB) — evaluate size;
      if too big, keep Groq Whisper-style fallback behind user's own key (competitor supports both).
- [ ] **S2.3** Bundle Silero VAD ONNX (`2–3 MB`) and wire into `LocalSttController` (create if missing).
- [ ] **S2.4** TTS model — bundle sherpa TTS (speaches/kokoro) **or** fall back to system `TextToSpeech`
      as the competitor does for non-bundled voices.
- [ ] **S2.5** Port the 3 skills missing from our set (`research-the-web`, `act-without-screen`,
      `aura-skill` doctrine → our JSON format).
- [ ] **S2.6** Enforce research-first for unknown apps: if a skill doesn't match, inject
      "web_search first" into the system prompt for Tier-A apps (social/banking/search).
- [ ] **S2.7** Keyboard dictation polish if not done.

### Sprint 3 — Perception & `ask_aura` (Week 3, ~7 days)

- [ ] **S3.1** Download/bundle `omniparser_icon_detect.onnx` (20 MB, relicensed AGPL by competitor —
      we **cannot** reuse their file; train our own or license from Microsoft/under their terms
      **or** use ML Kit + custom regex icons). Safest initial: **our own ONNX wrapper + ASL icon
      pack** OR rely on tree + OCR for v1 and mark perception as "tree-first" with confidence scoring.
- [ ] **S3.2** Optional: try `yolov8n`-style icon detector fine-tuned on Android widgets; ship as
      4–8 MB asset. If not ready, skip for v1 and keep the tree-first path.
- [ ] **S3.3** Conversation mode: implement Gemini Live client (`CompanionLiveController` shape):
      mic capture → WebSocket Live API → streaming text/audio → `ask_aura` tool handoff to `AgentRuntime`.
      Requires Google AI Studio key (see Requirements). If Gemini Live unavailable in user's region,
      fallback to push-to-talk Groq STT + LLM at similar latency.
- [ ] **S3.4** `OverlayFixActivity` + `ScreenWakeActivity` for OEM quirks (Xiaomi/OPPO/realme).
- [ ] **S3.5** Permission registry UI (`PermissionRegistry` + `PermissionSystemLauncher`) replacing
      the bundled `PermissionsHealthScreen`.
- [ ] **S3.6** `OnDeviceReport` screen showing run stats and app version bands (offline-only).

### Sprint 4 — Benchmark & launch (Week 4, ~7 days)

- [ ] **S4.1** Clone competitor's **approach** to AndroidWorld harness (not their code):
      Python runner + emulator + `task.is_successful(env)` — use Google Research's public AndroidWorld.
- [ ] **S4.2** Run two targets: (a) our on-device agent, (b) our MCP server + Claude Code on the PC.
- [ ] **S4.3** Publish results (videos + score) on website; add badges to README.
- [ ] **S4.4** Website v1: static HTML/Netlify/Vercel (account required, see Requirements);
      Download button, 5 clips, feature grid, privacy manifesto.
- [ ] **S4.5** Discord + `#announce` + install counter; tweet/Show HN/r/androidapps.
- [ ] **S4.6** Release APK `v1.1.x` with website URL and all Sprint 1–3 fixes.

### Sprint 5 — Parity polish & mozzarella (Weeks 5–8, ongoing)

- [ ] **S5.1** Banking/payment/authenticator hard-block list in code (cannot be opened).
- [ ] **S5.2** Pairing hardening: PIN-derived 6-digit verification code, HMAC proof on reconnect,
      bound to TLS/DTLS fingerprints.
- [ ] **S5.3** Remote Config `kill_switch_enabled` + `min_supported_version_code`.
- [ ] **S5.4** On-resume mid-task state restore (dump `AgentState` to SharedPrefs on every
      iteration, restore on app start).
- [ ] **S5.5** ToolBench in `:mcp-server/src/test/`.
- [ ] **S5.6** Fold `ask_aura` into MCP instructions; add `verify_action` hook in agent doctrine.
- [ ] **S5.7** Release: tag + report + artifacts only from green CI; document `release=true` flow.

### Sprint 6 — Harden & scale (Month 2–3, ongoing)

- [ ] **S6.1** AndroidWorld monthly re-run; publish trend.
- [ ] **S6.2** 100+ stars target; `good first issue` board; 1 maintainer/do-not-merge label.
- [ ] **S6.3** Play Console closed testing track ($25 one-time) — only if pursuing Play Store;
      recommend sideload-first just like competitor.
- [ ] **S6.4** Commercial license conversations with 2–3 pilot customers (dev shops, app testers).
- [ ] **S6.5** Folder READMEs, `docs/architecture.md`, `docs/decisions/` ADRs.

### Suggested release cadence

| Milestone | Tag | Contents |
|---|---|---|
| Sprint 1 | `v1.1.0` | Legal, CI tests, MCP protocol, `ask_user`, README/site |
| Sprint 3 | `v1.2.0` | Voice assets, vision model (if S3.1 shipped), conversation mode |
| Sprint 4 | `v1.2.1` | Benchmark + website + launch artifacts |
| Sprint 5 | `v1.3.x` | Parity polish (banking block, kill switch, resume) |
| Sprint 6 | `v1.4.x` | Hardening + monthly bench runs |

---

## 5. Requirements inventory (accounts, APIs, auth, infra)

### 5.1 Accounts & sign-ups

| # | Account / Service | Status | Purpose | Required for | Free? |
|---|---|---|---|---|---|
| R1 | **Google account** (existing) | ✅ exists (`nabinsharma816@gmail.com`) | Firebase project, Google Sign-In, Gemini API | C1, G8, V7 | free |
| R2 | **Google AI Studio account** (same Google account) | ✅ implicitly | Gemini API keys + Gemini Live | V7 (Sprint 3) | free tier with rate limit |
| R3 | **Firebase project** (`aura-android-use-nabin`) | ✅ exists (apps registered, Auth on, Firestore created) | Sync, FCM, Remote Config | C2, C4, C5, S5.3 | Spark free tier |
| R4 | **Firebase Cloud Messaging** | ✅ (FCM enabled via service) | Push delivery | C5 | free |
| R5 | **Firebase Remote Config** | ❌ not yet touched | Kill switch, version floor | S5.3 | free |
| R6 | **Backblaze B2 account** | ✅ exists (bucket `aura-lite-storage`, scoped key) | File backup/export | M-01 | 10 GB free tier; $0.005/GB/mo storage |
| R7 | **npm account** (`npmjs.com`) | ❌ not yet | Publish bridge | D5 | free |
| R8 | **Vercel account** (`vercel.com`) | ❌ not yet (competitor uses it) | Static site + install-stats serverless function | D1 | Hobby free tier |
| R9 | **Discord account** | ✅ implicit | Community server | D4 | free |
| R10 | **GitHub account** | ✅ exists | Repo, Releases, CI, CLA workflow | All | free |
| R11 | **Anthropic API key** (console.anthropic.com) | ❌ not yet | Claude Code MCP client target for benchmark | GAP-06 | paid (~$3–15 per run depending on size; Haiku is $1/M in+out) |
| R12 | **OpenAI / Groq / OpenRouter key** (yours for study) | ❌ not yet | Benchmark cross-provider comparison (optional) | Benchmark | Groq free tier; OpenRouter pay-as-you-go |
| R13 | Google Play Console **closed testing** app | ❌ not yet | Only if/when Play Store is a goal | S6.3 | one-time US$25 |
| R14 | Tavily account (app user provides) | existing/optional | Web search tool | Both | free tier 1k/mo |
| R15 | CLA assistant account (CLA assistant) | ❌ not yet | CLA enforcement on PRs | L6 | free |

### 5.2 APIs & keys (by service)

| # | API | Where it lives | Auth method | Used for | Cost | Notes |
|---|---|---|---|---|---|---|
| API1 | OpenAI-compatible streaming (`POST /v1/chat/completions`) | App ↔ provider | user's own bearer in `EncryptedSharedPreferences` | agent loop | varies | 14 built-in presets; user pays their provider |
| API2 | Gemini API (GenerateContent + **Live** `BidiGenerateContent`) | App ↔ Google | AI Studio key (user's own or yours for bench) | conversation mode | free tier rate limits (~15 req/min Flash) | V7 / S3.3 |
| API3 | Groq Whisper STT (`POST /v1/audio/transcriptions`) | App ↔ Groq | Groq key (user's own) | voice commands | free tier | alternative path when Moonshine not bundled |
| API4 | Groq LLM (`llama-…`, `gemma-…`) | App ↔ Groq | Groq key | benchmark + fast agent | free tier | S4.2 |
| API5 | Anthropic Claude API (`POST /v1/messages`) | Benchmark harness ↔ Claude Code | Anthropic API key | benchmark target B | paid | S4.2 |
| API6 | Firestore REST/SDK | App ↔ Firebase | user's Firebase identity (SHA-pinned OAuth client) | sync | free | C4 |
| API7 | Firebase Auth REST / Google Sign-In | App ↔ Firebase | OAuth clients (debug + release SHA registered) | sign-in | free | C2 |
| API8 | FCM message send | Your server → **or** Cloud Functions | server key / functions | notifications | free | C5 |
| API9 | Remote Config REST | App ↔ Firebase | implicit via SDK | kill switch | free | S5.3 |
| API10 | Backblaze B2 (S3-compatible SigV4) | App ↔ B2 directly | user's scoped app key (`aura-lite-app`, `namePrefix=users/`, `users/` only) | file backup/export | pennies | C9, C10 |
| API11 | Tavily Search (`POST https://api.tavily.com/search`) | App ↔ Tavily | user's Tavily key | `web_search` | free 1k/mo | optional |
| API12 | AndroidWorld harness | Your machine ↔ emulator | none (local) | benchmark | free | S4.2 |

### 5.3 Auth & identity flows

| # | Flow | Where | Type | Account needed | Notes |
|---|---|---|---|---|---|
| A-1 | Voice wake word ("hey aura") | App | on-device | none | offline, no key |
| A-2 | Voice command ("Book a cab …") | App | on-device STT **or** Groq Whisper | Groq key (optional) | user's own key |
| A-3 | Agent plan step → LLM | App | bearer | user's LLM provider key | user's own key |
| A-4 | MCP pairing (LAN) | Phone ↔ PC | PIN-derived verification + shared secret proof + TLS 1.3 | none | Not Firebase; fully local |
| A-5 | Firebase Auth — Google | App ↔ Firebase | OAuth 2.0, SHA-1/SHA-256 registered (release cert only) | Google account (user's own) | for sync/analytics |
| A-6 | Firebase Auth — Email/Password | App ↔ Firebase | Firebase SDK | user's own email | enabled in project |
| A-7 | Firestore per-user docs | App ↔ Firebase | request.auth.uid matches `users/{uid}/…` | user's own uid | enforced by our `firestore.rules` |
| A-8 | MCP client pairing → phone | PC ↔ Phone | one-time PIN + HMAC-SHA256 proof | none | local trust-on-first-use |
| A-9 | Release signing | GitHub Actions | `AURA Release` RSA-2048, PKCS12, secrets in repo | — | already provisioned |
| A-10 | Google Play Console (optional) | Play Store | developer account | you (US$25 one-time) | only if Play Store path chosen |

### 5.4 Tooling & local setup

| # | Tool | Purpose | Cost | Notes |
|---|---|---|---|---|
| T1 | Android Studio + SDK 36 + `adb` | Build, device install, screenrecord | free | you have this |
| T2 | JDK 17 (Temurin) | CI already uses it; local needs same | free | in CI image already |
| T3 | Node 18+ (for npm publish & benchmark runner) | Bridge publish + eval | free | you have node |
| T4 | Python 3.11+ (AndroidWorld) | Benchmark harness | free | install once |
| T5 | Android Emulator (or a real phone) | Benchmark runs; need 1 real phone | phone you own | ≥4 GB RAM, Android 12+ |
| T6 | Git LFS (if bundling 20 MB ONNX) | Asset management | free | only if shipping icon_detect |
| T7 | `b2` CLI (Python) | Backblaze verification | free | for smoke-test in `backblaze/README.md` |

### 5.5 Secrets & keys — where each must live

```
GitHub repo secrets (CI)          EncryptedSharedPreferences (device)      Local file (device/dev)
─────────────────────────────    ─────────────────────────────────       ──────────────────────────
AURA_KEYSTORE_BASE64            provider LLM keys (per provider)        AuraSettings flags (user)
AURA_KEYSTORE_PASSWORD          Tavily key (single)                      no keys in git
AURA_KEY_ALIAS                  B2 app key (if configured)
AURA_KEY_PASSWORD               Firebase auth token (SDK-managed)
GOOGLE_SERVICES_JSON_BASE64     MCP pairing bearer (after one-time PIN)
```
**Rule:** no key material in git, in commit messages, or in tagged releases. CI secret-scan
blocks patterns like `gsk_…`, `sk-or-v1-…`, `AIza…`, `nvapi-…`, `tvly-…`. Backblaze keys, when
entered in-app, live only in `aura_secure` (EncryptedSharedPreferences).

### 5.6 What the user must supply at runtime (per-user, never yours)

Every AI call is user-owned: their Gemini/OpenAI/Groq/etc. key. This is non-negotiable for them and
for us — the app never shares one master LLM key across users. Tavily, if used, is the user's key.

### 5.7 What you need to bring for the *competitor parity* work (one-time)

| # | Deliverable | Cost (approx) | Needed for |
|---|---|---|---|
| Q1 | **Anthropic API key** + modest Claude Code usage | ~US$10–30 per benchmark run; Haiku is cheap | S4.2 target B |
| Q2 | **Physical Android phone** (Android 12+, ≥4 GB RAM) for ~2 benchmark runs | sunk cost: a phone you own | S4.2 target A |
| Q3 | **An Amazon/flipkart/Rapido/Maps** test account for end-to-end demos | free accounts | demo clips S4.4 |
| Q4 | **Discord invite link + server** (free) | free | D4 |
| Q5 | **Vercel hobby account** for the static site + `/api/install-stats` | free | D1 |
| Q6 | **npmjs.com account** for `aura-mcp-connect-lite` | free | D5 |

---

## 6. Cost summary (12 months)

| Line item | Est. annual cost | Notes |
|---|---|---|
| Firebase (Spark) | $0 | Firestore, Auth, FCM, RC, Crashlytics all free at this scale |
| Backblaze B2 (10 GB free) | $0 – $2 | within free tier for personal/early app |
| AndroidWorld runs (Anthropic API) | ~$60 – $200 | 5–10 benchmark iterations during launch |
| Groq API | $0 | free tier generous |
| Google AI Studio | $0 | includes Gemini Live free tier |
| APK signing | $0 | already provisioned |
| Website (Vercel) | $0 | hobby tier |
| npm | $0 | |
| Domain (optional, `aura-lite.app` or similar) | ~$10–15/yr | polish only |
| Play Store (optional) | $25 one-time | skip unless Play track |
| **Total year-1** | **~$70 – $230** | mostly bench costs; grows with usage |

---

## 7. Decisions you must take now

1. **Name** — both products are AURA (including package `com.aura.aura_ui.feature`). Ship under a
   distinct brand, e.g. *AURA Lite*, *openAURA*, or a new name. Decide before Sprint 1.
2. **LICENSE model** — recommended: **AGPL-3.0 + linking exception + commercial license + CLA**,
   mirroring the competitor's model (they prove it works). Legal text we'll write.
3. **Do we want a FOSS-only (no Firebase) build?** Yes, and make it a tag like `v1.x-foss`.
4. **Target Android 12+ only for v1?** Competitor targets Android 8+. We target 26+; raising to
   28+ would trim 10–15% of code for ~5% more installs not served. Keep 26 for now.
5. **Conversation mode scope** — full Gemini Live (mic streaming, audio reply, `ask_aura`) vs
   push-to-talk-only for v1.2. Decide before Sprint 3.

---

## 8. Risks and anti-patterns

- **Risk**: AGPL contamination. Do not copy any competitor code (they are AGPL). Port behavior, not
  text. Re-implement from public protocol docs (MCP, SigV4, WebRTC, Gemini Live, Firestore rules).
- **Risk**: `qwen3-embedding`, `moonshine`, `omniparser_icon_detect` asset licensing — several are
  CC-BY-NC or proprietary. Verify license **before** bundling any model into an APK; otherwise the
  APK is unshippable outside our repo.
- **Risk**: Play Store rejection — accessibility + screenshot = sensitive permission. Stay sideload;
  later evaluate closed testing (US$25).
- **Risk**: Google sign-in in embedded browser is blocked by Google; our in-app `B2` browser avoids
  it by opening the *system* browser for auth flows.
- **Risk**: Diagnostic defaults — never switch ours on-by-default; keep opt-in/opt-out symmetric to
  competitor (off by default) as a differentiator.
- **Anti-pattern**: Copying competitor APIs' endpoint strings into code without understanding hot
  paths; that just burns rate limits. Use documented provider endpoints.
- **Anti-pattern**: Shipping before signing. Every public APK must be `AURA Release`-signed; CI
  enforces.

---

## 9. What to do first (this week)

1. Finalize the name (§7).
2. Write `LICENSE`, `LICENSE-EXCEPTION.md`, `COMMERCIAL-LICENSE.md`, `CLA.md` — half a day.
3. Sprint 1 tasks S1.5–S1.8 in order — another 3–5 days.
4. Draft the website skeleton in `/website/` — half a day, no build step.
5. Set `GOOGLE_SERVICES_JSON_BASE64` locally so forks build without real credentials.

Estimated lead time to a public, honest launch: **4–5 weeks** (Sprint 1–4), with benchmark
verification gated on your phone's availability.

---

## 10. Appendix: competitor files worth studying (not copying)

| Path (in `aura-android-use/`) | What it teaches |
|---|---|
| `.github/readme/…webp` / `architecture.png` | Visual communication of a complex tool |
| `aura-mcp-connect/` | How to make MCP painless for desktop users |
| `docs/agent-review/EVAL_TASKS.md` | Benchmark framing and scoring |
| `benchmark/` | How to run AndroidWorld against an on-device agent |
| `agent/conversation/CompanionLiveController.kt` | Gemini Live + `ask_aura` wiring pattern |
| `models/omniparser/icon_detect/` | AGPL relicensing of a detection model |
| `LICENSE-EXCEPTION.md` | Linking exception pattern for Google SDKs |
| `CLA.md` | Contributor agreement structure |
| `website/install-stats.json` | Public install counter pattern |
| `tools/bench/MockPhone` | Adversarial mock-phone testing philosophy |
