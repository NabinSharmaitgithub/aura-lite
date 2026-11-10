# PRD — AURA for Android (Product Requirements Document)

> **Purpose of this document:** enough detail to rebuild the app from scratch with identical UI and identical behaviour.
> **Source of truth:** the shipped codebase `aura-android` (v1.0.601, `com.aura.aura_ui.feature`).
> **Status:** reconstruction spec · Version 1.0 · 2026-11-10

---

## 1. Product summary

**AURA** is an on-device AI agent for Android. It does what a human does with a phone: reads the screen, taps, types, scrolls, launches apps, answers messages, places calls, controls media and system settings — driven by a large language model that the **user** supplies (bring-your-own-key).

It is **not** a chatbot wrapper. The LLM is the *planner*; the phone's Accessibility Service, MediaProjection, notification listener and system intents are the *hands*. The app is also an **MCP server** on the device: external MCP clients (desktop assistants, IDEs) can call the same device tools over a paired, local, encrypted channel.

### One-line positioning
> "AURA: an AI that uses your Android phone the way you do."

### Product pillars
| # | Pillar | Consequence for the build |
|---|---|---|
| 1 | **Acts, doesn't chat** | Accessibility automation is a first-class subsystem, not an add-on |
| 2 | **On-device first** | Agent loop, perception, tool execution, logs and memory live on the phone |
| 3 | **Bring your own LLM** | No vendor lock-in; provider catalogue + custom OpenAI-compatible endpoints |
| 4 | **Voice as a first-class input** | Wake word, push-to-talk, spoken replies, hands-free overlay |
| 5 | **Transparent & controllable** | Every action visible in a trace, pausable, restricted per-app, audited |
| 6 | **Local-first privacy** | No accounts, no cloud sync, no telemetry by default |

---

## 2. Target users & jobs to be done

| Persona | Job |
|---|---|
| **Power user / tinkerer** | "Automate my repetitive phone chores with my own API key." |
| **Accessibility user** | "Operate apps hands-free by voice." |
| **Developer** | "Expose my phone's capabilities to my desktop agent over MCP." |
| **Privacy-conscious user** | "An assistant that does not ship my screen to a vendor cloud account." |

### JTBD statements
- When I ask for something multi-step, I want AURA to plan and execute it and **show me what it did**, so I can trust and correct it.
- When I talk to my phone hands-free, I want it to wake, understand, act and speak back **without unlocking**.
- When I don't want automation in an app (banking), I want to **block AURA from that app** in one toggle.
- When a run goes wrong, I want to **pause it instantly** and see the exact step and screenshot.

---

## 3. Scope

### 3.1 In scope (v1 parity)
1. Onboarding wizard (10 steps) with consent, permissions, profile, provider key.
2. Home: chat/prompt surface, run cards, status chip, marquee suggestions.
3. Agent runtime: planner → tool calls → verification → summary, with live trace.
4. Device tool surface (~45 tools): UI automation, apps, files, contacts, media, notifications, device status, clipboard, browser, perception, web search, deep links, system intents.
5. Screen understanding: accessibility tree extraction + optional on-device vision model (OmniParser) + screenshots.
6. Voice: wake word (sherpa-onnx KWS), VAD (Silero), STT, TTS, spoken overlay assistant.
7. LLM provider catalogue (12 built-ins + custom endpoint), key storage, model catalogue fetch.
8. MCP server on device (LAN, TLS, pairing, audit) + MCP client to that server for the local agent.
9. Memory & learnings: conversation memory, user learnings, reminders.
10. Activity: session logs, traces, retention controls.
11. Skills / hooks / tools inventory screens.
12. Settings: brain, voice, permissions health, privacy, restricted apps, trusted devices, search key, legal.
13. Eval harness screen (internal quality tooling).
14. Overlay: floating pill/glow, pause control, voice assistant surface.

### 3.2 Out of scope (explicitly removed / not to rebuild)
- User accounts, sign-in (Google / email / phone), profile sync.
- Firebase Cloud Messaging, Remote Config, Crashlytics, Analytics, Firestore.
- Remote gate / kill switch / version gate / blocklist / device registry served from cloud.
- Cloud execution of the agent ("computer control" servers).
- Subscription/billing.

> **Rule:** if a feature needs a server we operate, it is out of scope. The only network calls are to the **user's chosen LLM provider**, the optional **Tavily** search API, and the local MCP client connection.

---

## 4. Functional requirements

### FR-1 Onboarding (10 steps)
| Step | Requirement | Acceptance |
|---|---|---|
| 0 | Landing: brand, promise, single CTA | Renders < 400 ms cold on mid-range device |
| 1 | How AURA works + policy consent gate | Cannot advance without consent |
| 2 | User profile (name) **and** provider key entry | Name persisted; key verified before advance |
| 3 | Restricted-settings guidance (Android 13+ sideload restriction) | Instructional; skippable |
| 4 | Accessibility service enable | Detects live state; auto-advances when enabled |
| 5 | Overlay permission | Detects `Settings.canDrawOverlays` |
| 6 | Notification listener | Detects enabled listeners |
| 7 | Microphone + notifications | Runtime grants |
| 8 | Provider key verification | Live "verify" call; blocks advance on failure |
| 9 | Ready / summary | Enters Home |

- Progress pager with back navigation; permission steps auto-advance on detection.
- Onboarding must be resumable after process death (persist step index).

### FR-2 Home
- Prompt input (multiline, send, voice button).
- Suggested-prompt marquee.
- Latest run card + streaming trace preview.
- Status chip reflecting service health (accessibility, capture, voice, MCP).
- Tapping a run opens the session/trace screen.

### FR-3 Agent run lifecycle
1. User submits a goal (text or voice).
2. Runtime assembles context: device facts, foreground app, tool inventory, memory, learned preferences, skills, recent failures.
3. LLM plan → tool calls (`run_steps` batch or individual).
4. Each action executed via MCP tool surface against the device.
5. Verification step after each mutating action (read screen diff / expected element).
6. Events streamed to UI (thinking, tool call, result, error, screenshot).
7. Final answer + persistent session record.
8. Interruptible at any time; pause is immediate (no further taps).

**Acceptance:** a 5-step task on a real device completes without manual intervention ≥ 70 % of the time across a 20-task suite (`docs/agent-review`).

### FR-4 Tool surface
Must include the following tools with JSON schemas (see TRD §7):

`tap, swipe, scroll, set_value, type_text, press_key, press_enter, wait_for, verify_action, run_steps, read_screen, perceive_screen, get_screenshot, request_screen_capture_permission, launch_app, lookup_app, open_deeplink, resolve_deeplink, list_app_deeplinks, find_files, open_file, clipboard, resolve_contact, dial, compose_sms, media_control, get_media_sessions, read_notifications, notification_action, dismiss_notification, get_device_status, volume, system_intent, watch_device_events, web_search, browser_open, browser_read, browser_find, browser_act, browser_extract, browser_tabs, browser_wait, browser_screenshot, browser_upload, browser_handoff, browser_close, end_session`

- Every mutating tool returns a structured result: `ok`, `observed` (post-state), `changed`, `error`.
- Tools must be *idempotent-safe*: `verify_action` never mutates.
- Destructive goals require a confirmation card (HITL).

### FR-5 Voice
- Wake word ("hey aura") detection offline, foreground service, low battery impact.
- VAD → STT → goal submission → TTS reply.
- Overlay shows live transcript and allows cancel.
- Voice can be disabled entirely; no service start when disabled.

### FR-6 Providers
- Built-ins: Groq, OpenRouter, Gemini, OpenAI, Anthropic, Mistral, Qwen/DashScope, Cerebras, Z.ai (GLM), xAI, Together, Moonshot, DeepSeek, **NVIDIA (NIM)**.
- Custom endpoint: base URL + path + auth scheme + model id.
- Per-provider: key hint, key URL, default model, vision support, reasoning support.
- Keys stored in EncryptedSharedPreferences; never logged; never in APK.

### FR-7 MCP server
- Local HTTPS server on LAN, mDNS/QR pairing, bearer token, per-client audit.
- Tool list = same device tool surface.
- Toggle on/off; health indicator; approval notification for risky calls.

### FR-8 Safety & control
- Pause pill always available during a run.
- Per-app restriction list (AURA refuses to act in those packages).
- Trusted devices list (MCP clients).
- Destructive actions (payments, deletes, sends) require explicit confirmation.
- Full trace of every action retained per user retention setting.

---

## 5. Non-functional requirements

| Area | Target |
|---|---|
| Cold start | ≤ 1.8 s to interactive on 4 GB device |
| **Memory (PSS)** | **≤ 250 MB idle, ≤ 400 MB during a run** (current build breaches this: ~505 MB → LMK kills) |
| APK size | ≤ 120 MB (arm64+armv7), no x86 in release |
| Battery | Wake word ≤ 3 %/h idle |
| Crash-free sessions | ≥ 99.5 % |
| Offline | All non-LLM features work with no network |
| Min Android | 8.0 (API 26); target 36 |
| Locality | No analytics/telemetry endpoints in release build |

---

## 6. Privacy & compliance
- No account, no identifier collection, no advertising ID (explicit meta-data flags).
- Screen content leaves the device only as part of an LLM request the user initiated, to the provider they configured.
- Diagnostics are opt-in and local.
- Legal screens: Privacy, Terms, Licences (third-party notices).

---

## 7. Success metrics
- Task completion rate on the 20-task eval suite.
- Median steps per task (fewer = better planning).
- User interrupt rate (proxy for wrong actions).
- Memory kill rate (target: 0 in a 30-minute session on 4 GB).
- Time-to-first-action after prompt.

---

## 8. Risks
| Risk | Impact | Mitigation |
|---|---|---|
| OEM kills foreground service | Agent stops mid-task | Sticky service, wake lock, battery-optimisation guidance |
| Accessibility tree is incomplete (Flutter/WebView) | Cannot act | Fall back to screenshot + vision model |
| LLM hallucinated coordinates | Wrong taps | Always verify; prefer element-index taps over raw coords |
| Memory blow-up | App closed by LMK | Hard budgets, lazy model loading, no full-frame retention |
| User's key leaks | Cost/abuse | Keys in EncryptedSharedPreferences; no logging; docs on restrictions |

---

## 9. Release definition of done
- All FR-1…FR-8 verified on a physical device.
- Memory budget met for 30 min continuous run.
- Zero secrets in repo history (verified with `git log -S`).
- Signed release APK + debug APK produced by CI.
- Legal screens reachable and accurate.
