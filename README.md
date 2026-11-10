# AURA for Android — Rebuild Documentation

This folder contains everything needed to rebuild the AURA Android app from scratch with the
**same UI and the same features**.

Created 2026-11-10 · Based on the shipped build `com.aura.aura_ui.feature` v1.0.601.

---

## 📁 Documents

| # | File | What it answers |
|---|---|---|
| 1 | [`01-PRD.md`](01-PRD.md) | What the product is, who it's for, every functional requirement + acceptance criteria, scope and non-goals |
| 2 | [`02-TRD.md`](02-TRD.md) | How it's built: architecture, modules, exact dependency list, build config, agent runtime, tool surface, memory budgets |
| 3 | [`03-APP-FLOW.md`](03-APP-FLOW.md) | Every screen, every route, all state machines and navigation diagrams |
| 4 | [`04-DESIGN-UI-UX-BRIEF.md`](04-DESIGN-UI-UX-BRIEF.md) | The MONO design language: exact colours, type scale, spacing, components, screen layouts |
| 5 | [`05-BACKEND-SCHEMA.md`](05-BACKEND-SCHEMA.md) | On-device schema: Room tables, prefs keys, session log format, MCP API, provider registry |
| 6 | [`06-IMPLEMENTATION-PLAN.md`](06-IMPLEMENTATION-PLAN.md) | Phased build order with effort estimates, acceptance tests, and a do-not-repeat defect list |

---

## 🚀 The full build prompt

Copy the block below into a coding agent (or use it yourself as the build brief). It references
the six documents in this repo root, so keep them at the same level as this README.

```text
You are building AURA, a production Android app, from scratch. Full specifications are the
markdown files at the root of this repository:

  01-PRD.md                    product requirements and acceptance criteria
  02-TRD.md                    technical requirements and architecture
  03-APP-FLOW.md               navigation, screens and state machines
  04-DESIGN-UI-UX-BRIEF.md     exact design tokens and component specs
  05-BACKEND-SCHEMA.md         storage schemas and the MCP protocol
  06-IMPLEMENTATION-PLAN.md    build order, estimates, acceptance tests

Read all six before writing code. Then follow 06-IMPLEMENTATION-PLAN.md phase by phase.

== WHAT THE APP IS ==
AURA is an on-device AI agent for Android. The user supplies their own LLM API key. AURA reads
the screen through the Accessibility Service, then taps, types, scrolls, launches apps, sends
messages, controls media and system settings to complete multi-step tasks. It also runs an MCP
server so external MCP clients can call the same device tools over a paired local channel.

It is NOT a chatbot, NOT an account-based product, and NOT cloud-connected.

== HARD CONSTRAINTS (do not violate) ==
1. No accounts, no sign-in, no Firebase, no analytics, no crash reporting, no remote config,
   no cloud sync, no kill switch, no device registry. Network calls are allowed ONLY to the
   user's chosen LLM provider, the optional Tavily search API, and the local MCP client.
2. No API keys in source control. The user enters their key at runtime. Store it in
   EncryptedSharedPreferences only.
3. Memory is an acceptance criterion, not a later optimisation:
   idle PSS <= 250 MB, during-run PSS <= 400 MB, cold start <= 1.8 s.
   Never hold more than one full-resolution frame. Create ONNX sessions lazily and close them.
   Do not auto-start the wake-word service.
4. Every phase must end with a building, installable APK. Build after every screen.
5. Every screen uses the design tokens in 04-DESIGN-UI-UX-BRIEF.md. No raw hex outside the
   theme file. No drawer, no top app bar, no tab bar: the floating black capsule is the only
   navigation element.
6. One tool contract: the JSON-RPC MCP tool surface in 05-BACKEND-SCHEMA.md is used by both the
   in-app agent and remote clients.

== TECH STACK (locked) ==
Kotlin, Jetpack Compose, Material 3, Hilt + KSP, Room, DataStore,
Navigation-Compose, Coroutines/Flow, OkHttp + Retrofit, kotlinx.serialization,
Koog (agent framework), MCP Kotlin SDK, ONNX Runtime (perception), ML Kit (OCR),
sherpa-onnx (wake word), Silero VAD, EncryptedSharedPreferences, WorkManager.
applicationId com.aura.aura_ui.feature, namespace com.aura.aura_ui,
minSdk 26, compileSdk 36, targetSdk 36, JDK 17, modules :app and :mcp-server.
Release ABIs: arm64-v8a + armeabi-v7a only.

== BUILD ORDER ==
Phase 0  scaffold + CI (debug + release APK artifacts)
Phase 1  design system + app shell with the capsule nav
Phase 2  Room + preferences + secure key storage + session log
Phase 3  accessibility engine (tree extraction, gestures, screen capture)  <- highest risk
Phase 4  MCP tool surface in :mcp-server (45 tools, unified result envelope)
Phase 5  LLM provider catalogue + agent loop (plan -> act -> verify -> answer)
Phase 6  screens: Home, Agent hub, Brain, Tools, Hooks, Skills, Memory, Companion, Voice,
         Activity, Session detail, Settings and every sub-screen
Phase 7  onboarding (10 steps, persisted, auto-advancing permission steps)
Phase 8  voice (wake word, VAD, STT/TTS, overlay)
Phase 9  perception (optional vision model, lazy loaded)
Phase 10 MCP over LAN: TLS, QR pairing, trusted devices, audit
Phase 11 hardening: memory, battery, R8, secret scan, legal screens
Phase 12 parity verification against 01-PRD.md section 4

== DEFINITION OF DONE ==
- Every route in 03-APP-FLOW.md is reachable and every screen has loading, empty, error,
  permission-missing and offline states.
- A 20-task agent suite completes >= 14 tasks on a physical device.
- 30-minute continuous session on a 4 GB phone with zero low-memory-killer kills.
- CI builds debug and release APKs from a clean clone.
- `git log -S` finds no API keys anywhere in history.

Start with Phase 0, show me the file tree and the CI result, then continue phase by phase
without stopping for approval unless a spec is genuinely ambiguous.
```

---

## ⚡ Quick facts

| Item | Value |
|---|---|
| Package | `com.aura.aura_ui` / `com.aura.aura_ui.feature` |
| Modules | `:app`, `:mcp-server` |
| Min / target SDK | 26 / 36 |
| Original size | 912 Kotlin files, ~137,000 LOC |
| Tools exposed | 45 MCP tools |
| LLM providers | 14 built-in + custom OpenAI-compatible |
| Design language | MONO — white canvas, black ink, blood-red semantics |
| Navigation | single floating capsule (4 tabs ↔ back + title) |
| Persistence | Room v1 + SharedPreferences + EncryptedSharedPreferences + JSONL |
| Assets | 28 MB (20.6 MB vision model, 2.3 MB VAD, wake-word, skills) |

---

## ⚠️ Known trap

The shipped build measured **~505 MB PSS** and was repeatedly killed by Android's low-memory
killer (kernel log: *"device is in direct reclaim and thrashing"*) on a 3.6 GB phone where the
system displayed "1 GB available". `MemAvailable` is not free RAM. The rebuild must pass the
memory gates in `02-TRD.md §15` **before** feature work is considered complete — see the
"do-not-repeat list" in `06-IMPLEMENTATION-PLAN.md`.
