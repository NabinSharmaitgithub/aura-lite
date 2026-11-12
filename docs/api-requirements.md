# AURA — API & Service Requirements (What You Need and Where to Get Them)

**Project:** `aura-android-use`  
**Generated:** 2026-11-12  
**Purpose:** Lists the APIs/services AURA uses, what each is for, whether it is required, and where to obtain/configure it.

> **Important:** Requirements depend on which features you enable (voice, vision, browser, remote MCP, Firebase-backed app features, etc.). This file distinguishes **core**, **feature-dependent**, and **optional** items.

---

## Table of Contents

1. [Quick Start (Minimum Required)](#1-quick-start-minimum-required)
2. [LLM / AI Provider APIs (BYOK)](#2-llm--ai-provider-apis-byok)
3. [Voice, Wake Word, and Conversation APIs/Services](#3-voice-wake-word-and-conversation-apisservices)
4. [Perception, OCR, and On-Device ML Dependencies](#4-perception-ocr-and-on-device-ml-dependencies)
5. [Search / Web APIs](#5-search--web-apis)
6. [Firebase & Google Mobile Services](#6-firebase--google-mobile-services)
7. [Android System APIs, Permissions, and Grants](#7-android-system-apis-permissions-and-grants)
8. [Remote MCP / Networking / Bridge Services](#8-remote-mcp--networking--bridge-services)
9. [Developer, Distribution, and Optional Services](#9-developer-distribution-and-optional-services)
10. [Configuration Checklist (Where to Put Keys/Settings)](#10-configuration-checklist-where-to-put-keyssettings)

---

## 1. Quick Start (Minimum Required)

To run a basic local AURA automation flow on-device, you typically need:

| Requirement | Why | Where to get/configure |
|---|---|---|
| **Android device/emulator (Android 8.0+/API 26+)** with required system grants | Runs the app and automation services | Device settings + AURA onboarding/permissions screens |
| **Accessibility Service enabled for AURA** | Read UI and perform actions | Android Settings → Accessibility → AURA (path varies by OEM) |
| **Overlay permission (“Display over other apps”)** | Assistant overlay/status/pause/report UI | Android Settings → Apps → AURA → Display over other apps |
| **At least one LLM provider API key (BYOK) or compatible endpoint** | Agent reasoning and tool-calling | Provider console (see [Section 2](#2-llm--ai-provider-apis-byok)); configure in AURA **Settings → Agent Brain** |
| **JDK 17 + Android SDK (API 36) for building from source** | Build the Android app | JDK: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/ ; Android Studio/SDK: https://developer.android.com/studio |

> For full stock features (sign-in, remote config/gating, registry, diagnostics), you also need Firebase setup (see [Section 6](#6-firebase--google-mobile-services)).

---

## 2. LLM / AI Provider APIs (BYOK)

AURA supports multiple providers/models (capabilities vary: tool calling, vision, context window, streaming/live). You need **at least one** working provider for the agent.

| Provider / API | Used for | Required? | Where to get API key / access | Where to configure in AURA | Notes |
|---|---|---|---|---|---|
| **Google Gemini API (AI Studio / Google AI)** | On-device agent LLM (if selected), and Gemini-based features; **Gemini Live** for conversation mode (feature-dependent) | **One provider required** (Gemini optional) | API key: https://aistudio.google.com/apikey ; Docs: https://ai.google.dev/ | App → **Settings → Agent Brain** (provider/model/key) and Companion/Live settings if applicable | Model availability/capabilities differ; live conversation requires a Gemini model/config that supports the live flow used by AURA. |
| **Anthropic (Claude) API** | Agent LLM | **One provider required** (optional choice) | Console/API keys: https://console.anthropic.com/ ; Docs: https://docs.anthropic.com/ | App → **Settings → Agent Brain** | Ensure selected model supports tool use if you rely on tool-heavy automation. |
| **OpenAI API** | Agent LLM | **One provider required** (optional choice) | API keys: https://platform.openai.com/api-keys ; Docs: https://platform.openai.com/docs | App → **Settings → Agent Brain** | Vision/tool support depends on chosen model. |
| **Groq API** | Agent LLM and/or voice STT path (feature-dependent per app config) | **One provider required** (optional choice) | Console/API keys: https://console.groq.com/keys ; Docs: https://console.groq.com/docs | App → **Settings → Agent Brain** (and voice-related settings if used) | Fast inference; check model tool/vision support before selecting. |
| **OpenRouter** | Agent LLM routing to many models | **One provider required** (optional choice) | API keys: https://openrouter.ai/keys ; Docs: https://openrouter.ai/docs | App → **Settings → Agent Brain** | Model capabilities/pricing/limits depend on upstream model. |
| **Mistral AI API** | Agent LLM | **One provider required** (optional choice) | Console: https://console.mistral.ai/ ; Docs: https://docs.mistral.ai/ | App → **Settings → Agent Brain** | Verify tool-calling/vision support per model. |
| **DeepSeek API** | Agent LLM | **One provider required** (optional choice) | Platform: https://platform.deepseek.com/ ; Docs: https://api-docs.deepseek.com/ | App → **Settings → Agent Brain** | Capabilities vary by model/version. |
| **xAI (Grok) API** | Agent LLM | **One provider required** (optional choice) | Console/docs: https://console.x.ai/ ; Docs: https://docs.x.ai/ | App → **Settings → Agent Brain** | Check tool/vision support for selected model. |
| **Alibaba Cloud Model Studio (Qwen)** or OpenAI-compatible Qwen endpoint | Agent LLM | **One provider required** (optional choice) | Model Studio: https://www.alibabacloud.com/help/en/model-studio/ ; Console: https://modelstudio.console.alibabacloud.com/ | App → **Settings → Agent Brain** | Endpoint/auth format may differ by region/product; use compatible endpoint settings if required. |
| **Moonshot AI (Kimi) API** | Agent LLM | **One provider required** (optional choice) | Platform: https://platform.moonshot.ai/ ; Docs: https://platform.moonshot.ai/docs | App → **Settings → Agent Brain** | Confirm model/tool support for automation use. |
| **Z.ai / GLM API** | Agent LLM | **One provider required** (optional choice) | Platform: https://open.bigmodel.cn/ ; Docs: https://open.bigmodel.cn/dev/api | App → **Settings → Agent Brain** | Regional availability and API compatibility may vary. |
| **Cerebras Inference API** | Agent LLM | **One provider required** (optional choice) | Cloud/API: https://cloud.cerebras.ai/ ; Docs: https://inference-docs.cerebras.ai/ | App → **Settings → Agent Brain** | Check supported models and tool-calling behavior. |
| **Together AI API** | Agent LLM | **One provider required** (optional choice) | API keys: https://api.together.ai/settings/api-keys ; Docs: https://docs.together.ai/ | App → **Settings → Agent Brain** | Model-dependent capabilities/limits. |
| **Any OpenAI-compatible endpoint (self-hosted or third-party)** | Agent LLM | **One provider required** (optional choice) | Depends on provider/self-host (e.g. vLLM, Ollama-compatible gateways, hosted proxies) | App → **Settings → Agent Brain** (custom endpoint/provider settings if supported) | Must be reachable from device and compatible with the client/protocol expected by AURA’s provider integration. |

### LLM selection recommendations (for reliability)
- Prefer models with **strong tool-calling/function-calling** support.
- Enable/use **vision-capable models** if you rely heavily on `perceive_screen` image reasoning.
- Ensure sufficient **context window** and provider rate limits for multi-step tasks.

---

## 3. Voice, Wake Word, and Conversation APIs/Services

| API / Service | Used for | Required? | Where to get it | Where to configure / enable | Notes |
|---|---|---|---|---|---|
| **Android Microphone API / RECORD_AUDIO permission** | Wake word, voice commands, live conversation audio input | **Yes for voice features** | Built into Android; grant permission on device | Android permission prompt / Settings → Apps → AURA → Permissions → Microphone | Mic access is mandatory for all voice input modes. |
| **sherpa-onnx (offline keyword spotting, vendored in project)** | Offline wake word detection (“Wake up AURA”) | **Yes for offline wake word** | Project fetches/uses vendored AAR/assets (see `app/build.gradle.kts` and `app/libs`/assets logic) | Enable wake word in AURA settings (if exposed) and grant mic + service permissions | No cloud API key required; model/assets must be present in build. |
| **Speech-to-Text (STT) service (cloud or local, mode-dependent)** | Voice command transcription | **Feature-dependent** | Cloud STT: provider account/API key (if using cloud STT path supported by your build/config); Local STT: bundled/local model components | AURA voice/provider settings (as available) | Exact cloud STT provider requirement depends on enabled voice pipeline; local components may reduce external dependency. |
| **Text-to-Speech (TTS) engine/service (local or system/provider-dependent)** | Spoken responses, narration/status | **Feature-dependent** | Local TTS components/models bundled/configured in app, or Android/system TTS depending on implementation | AURA voice settings | Can be local-only in some configurations. |
| **Gemini Live (Google AI realtime conversation API/model)** | Real-time conversation mode (voice back-and-forth) with tool handoff | **Optional** (required only for Conversation/Live mode) | Gemini API key: https://aistudio.google.com/apikey ; Gemini docs: https://ai.google.dev/ | AURA Companion/Conversation settings + Gemini key/model selection | Requires stable network and compatible Gemini model/access. |
| **VAD (voice activity detection) runtime/components** (e.g. Silero VAD integration in project) | Speech segmentation and capture quality | **Feature-dependent** | Included via project dependencies/models where configured | No separate user signup (typically) | Improves reliability of voice capture; implementation-specific. |

---

## 4. Perception, OCR, and On-Device ML Dependencies

| Dependency / API | Used for | Required? | Where to get it | Setup notes |
|---|---|---|---|---|
| **Android Accessibility APIs** | UI tree extraction, element discovery, semantics (`read_screen`, grounding) | **Yes (core perception/action)** | Built into Android SDK/framework | Enable Accessibility Service for AURA. |
| **Android screen capture / MediaProjection APIs** | Screenshots and visual perception inputs | **Yes for screenshot/vision** | Built into Android; user grants capture consent | User must approve capture prompt; FLAG_SECURE apps may block capture. |
| **ONNX Runtime for Android** | Running on-device icon detection model (OmniParser-based flow) for `perceive_screen` escalation/annotation | **Yes for CV-based perception** | Maven dependency in Android build (already declared in project) + model assets | No external account needed; device CPU/NPU/GPU support affects performance. |
| **Google ML Kit Text Recognition** | OCR for text in screenshots/UI perception pipeline | **Yes for OCR-assisted perception** | Maven dependency (already declared); docs: https://developers.google.com/ml-kit/vision/text-recognition | No API key required for on-device text recognition module used by the app. |
| **OmniParser icon detection model/config (project assets/metadata)** | Visual UI element detection | **Yes for vision detection path** | Bundled in app assets / repo model metadata (`models/omniparser/icon_detect/` and app assets) | Ensure model files/assets are present and compatible with runtime/build. |

---

## 5. Search / Web APIs

| API / Service | Used for | Required? | Where to get it | Where to configure in AURA | Notes |
|---|---|---|---|---|---|
| **Tavily Search API** | `web_search` tool for web grounding and unfamiliar-task guidance | **Optional** (required if using `web_search`) | Sign up/API key: https://tavily.com/ ; Docs: https://docs.tavily.com/ | App → **Settings → Tavily** (or equivalent key screen) | Requires internet access; subject to Tavily plan/rate limits. |
| **Target websites (HTTPS access)** | `browser_*` tools (open/read/find/act/extract in AURA browser) | **Optional** (required for web tasks) | Public web / target site accounts where applicable | No AURA API key; user may need to complete handoff for logins/CAPTCHAs | Site availability, anti-bot protections, and login walls affect automation. |

---

## 6. Firebase & Google Mobile Services

The app integrates Firebase services for identity, remote control, registry, and diagnostics in standard builds.

| Service / API | Used for | Required? | Where to get it | Setup / configuration | Notes |
|---|---|---|---|---|---|
| **Firebase project** | Container for Auth, Firestore, Remote Config, Crashlytics, Analytics | **Yes for full Firebase-backed features** | Firebase Console: https://console.firebase.google.com/ ; Docs: https://firebase.google.com/docs | Create project, add Android app(s), download `google-services.json` | Place `google-services.json` in `aura-android/app/` (or follow project structure). |
| **Firebase Authentication** | Sign-in (Google/Anonymous as configured), user/device identity linkage | **Yes for full auth/identity flows** | Enable in Firebase Console → Authentication | Enable providers (e.g. Google, Anonymous), configure OAuth consent/SHA as needed | Debug builds may support limited skip depending on config; full flow needs proper setup. |
| **Cloud Firestore** | Install/device registry, blocklist/suspension checks, remote control data | **Yes for registry/remote-gating features** | Enable in Firebase Console → Firestore Database | Create database, configure security rules and indexes as required by app behavior | Security rules are critical (not fully shipped in repo). |
| **Firebase Remote Config** | Kill switch, min supported version, update prompts/messages | **Yes for remote config/gating features** | Enable/use in Firebase Console → Remote Config | Define parameters/values used by app (keys must match app expectations) | App caches/hydrates values; behavior depends on implementation. |
| **Firebase Crashlytics** | Crash reporting and diagnostics (including native symbols upload support in build config) | **Optional but integrated** | Enable in Firebase Console → Crashlytics ; Docs: https://firebase.google.com/docs/crashlytics | Enable in project/build, run app to verify ingestion | Respect user privacy/diagnostics settings. |
| **Firebase Analytics (Google Analytics for Firebase)** | Usage/diagnostics analytics (feature/settings dependent) | **Optional but integrated** | Firebase Console → Analytics ; Docs: https://firebase.google.com/docs/analytics | Configure data collection and privacy settings | May be controlled by in-app diagnostics/privacy toggles. |
| **Google Play services** | Underlying support for Google/Firebase SDKs and sign-in flows on many devices | **Yes on most Android devices using Google features** | Preinstalled on Google-certified devices; updates via Play Store | Keep device Play services up to date | Emulators/images without Play services may break some flows. |
| **Google Sign-In / Credential Manager / Google Identity services** | Google account sign-in UX and credentials | **Yes for Google sign-in flow** | Google Cloud/Firebase OAuth setup: https://console.cloud.google.com/apis/credentials ; Firebase Auth Google provider docs: https://firebase.google.com/docs/auth/android/google-signin | Configure OAuth client IDs, SHA-1/SHA-256 fingerprints, consent screen/provider settings | Required only if using Google sign-in provider. |

---

## 7. Android System APIs, Permissions, and Grants

These are not “API keys” but are required platform services/grants.

| System API / Permission / Grant | Used for | Required? | Where to get / enable | Notes |
|---|---|---|---|---|
| **Accessibility Service** (`BIND_ACCESSIBILITY_SERVICE`, user enablement) | UI tree reading, gestures, app control | **Yes (core)** | Android Settings → Accessibility → AURA | Most critical dependency for automation. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Overlay UI, status strip, pause/report cards | **Yes (overlay UX)** | Android Settings → Apps → AURA → Display over other apps | Some OEMs require extra vendor-specific overlay toggles. |
| **Microphone** (`RECORD_AUDIO`) | Wake word, STT, live conversation | **Yes for voice** | Permission prompt / App permissions | Only one component should hold mic at a time. |
| **Screen capture consent (MediaProjection)** | Screenshots, visual perception | **Yes for vision/screenshots** | Runtime system consent dialog triggered by app | Can be denied/revoked; protected content may be unavailable. |
| **Notification Listener access** | Read/reply/dismiss notifications, media session integrations | **Yes for notification/media assistant features** | Android Settings → Notification access | User must explicitly grant. |
| **Input method enablement (AURA Keyboard IME)** | Typing fallback in apps without accessible editable nodes | **Feature-dependent (recommended)** | Android Settings → Keyboards / Input methods | Improves reliability in RN/Flutter/canvas fields. |
| **Contacts permission** (`READ_CONTACTS`) | Contact name resolution | **Yes for `resolve_contact`** | Permission prompt / App permissions | Sensitive data; handle/privacy controls apply. |
| **Media/files read permissions** (e.g. `READ_MEDIA_*`, legacy storage perms by API level) | File/media search/open/upload flows | **Yes for file features** | Permission prompt / App permissions | Scoped storage behavior varies by Android version. |
| **Alarm permission** (`SET_ALARM`) | Alarm/timer intents | **Yes for alarm/timer intents** | Manifest/declared permission + clock app handling | Final behavior depends on clock/handler app. |
| **Network state / internet access** (`INTERNET`, `ACCESS_NETWORK_STATE`, etc.) | LLM APIs, web search, browser, Firebase, LAN signaling | **Yes for networked features** | System/network connectivity | LAN remote control requires local network reachability. |
| **Foreground service / notification permissions** (`FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`, etc.) | Persistent services and user-visible service state | **Yes for background/ongoing features** | Runtime permissions and system allowances (OEM-dependent) | Required for stable wake-word/MCP/overlay operation. |
| **Query all packages / package visibility (as configured)** | App lookup, deep link discovery, launcher resolution | **Feature-dependent (often required in this app)** | Manifest declaration and platform policy | Needed for broad app discovery/automation. |

---

## 8. Remote MCP / Networking / Bridge Services

| Service / API / Component | Used for | Required? | Where to get it | Setup notes |
|---|---|---|---|---|
| **MCP (Model Context Protocol) SDKs** | Tool/resource/prompt communication (agent↔server, client↔server) | **Yes** | Kotlin SDK: https://github.com/modelcontextprotocol/kotlin-sdk ; Spec/docs: https://modelcontextprotocol.io/ | Used in Android app/module and desktop bridge. |
| **WebRTC (Android + Node)** | Encrypted MCP data channel over LAN (DTLS) | **Yes for remote MCP from computer** | Android dependency in project; Node: `@roamhq/wrtc` (installed via npm). Docs: https://webrtc.org/ | AURA uses LAN-only signaling/connection model (no STUN/TURN relay in this design). |
| **AURA on-device LAN signaling service** (`/aura/info`, `/aura/connect`) | Phone discovery and pairing/signaling | **Yes for remote MCP from computer** | Implemented in `:mcp-server` (no external signup) | Runs locally on phone; requires same/private network reachability. |
| **`aura-mcp-connect` (Node bridge/daemon)** | Local daemon (`127.0.0.1:4816/mcp`) + stdio shim + pairing CLI | **Yes for computer-side MCP clients** | npm package: https://www.npmjs.com/package/aura-mcp-connect ; Source in repo `aura-mcp-connect/` | Install: `npm install -g aura-mcp-connect` (Node.js >= 18). Pair phone with PIN from AURA app. |
| **Node.js runtime** | Runs `aura-mcp-connect` daemon/shim | **Yes for desktop bridge** | https://nodejs.org/ (LTS recommended, >=18 required) | Required on the computer running Claude Code/Cursor/etc. |
| **Local network (Wi-Fi/hotspot/private VPN)** | Phone↔computer connectivity for signaling/WebRTC | **Yes for remote MCP** | Your router/hotspot/Tailscale network setup | Phone and computer must be able to reach each other directly (LAN/private network). |
| **mDNS / local discovery (network-dependent)** | Automatic phone discovery by bridge | **Optional** | OS/network support (Bonjour/Avahi/router-dependent) | Bridge may also use saved host, subnet scan, or manual `--host`. |
| **Tailscale (private VPN)** | Remote/private-network connectivity when not on same Wi-Fi | **Optional** | https://tailscale.com/ ; Docs: https://tailscale.com/kb/ | Useful for pairing/connecting via phone’s Tailscale IP; not a required AURA cloud service. |

---

## 9. Developer, Distribution, and Optional Services

| Service / Tool | Used for | Required? | Where to get it | Notes |
|---|---|---|---|---|
| **Android Studio / Android SDK / Platform Tools** | Build, debug, emulator/device tooling | **Yes for development/building** | https://developer.android.com/studio | Includes SDK Manager, adb, emulator tools. |
| **ADB (Android Debug Bridge)** | Host-side device debugging and optional `aura-adb` MCP tool | **Optional** (dev/power-user) | Included with Android Platform Tools: https://developer.android.com/tools/adb | `aura-adb` in bridge must be explicitly armed and bypasses phone-side MCP policy checks. |
| **Git / GitHub** | Source control, releases, collaboration | **Optional** (dev/distribution) | Git: https://git-scm.com/ ; GitHub: https://github.com/ | Releases/workflows in repo are project ops, not runtime requirements. |
| **Vercel** | Website deployment for `aura-android-use-website` | **Optional** | https://vercel.com/ | Not required for app/MCP functionality. |
| **Python / PowerShell / Node (scripts)** | Eval, benchmarking, conversion, reporting utilities | **Optional** (dev/eval workflows) | Python: https://www.python.org/downloads/ ; Node: https://nodejs.org/ ; PowerShell: https://learn.microsoft.com/powershell/ | Required only for specific scripts in `scripts/` and `benchmark/`. |

---

## 10. Configuration Checklist (Where to Put Keys/Settings)

### In-app configuration (runtime)
| Item | Where to configure | Source |
|---|---|---|
| LLM provider, model, API key | AURA app → **Settings → Agent Brain** | Provider console (Section 2) |
| Gemini Live / conversation settings (if used) | AURA app Companion/Conversation settings (UI naming may vary) | Gemini API key/model access |
| Tavily API key (`web_search`) | AURA app → **Settings → Tavily** | https://tavily.com/ |
| Permissions (Accessibility, Overlay, Mic, Notifications, Contacts, Files, etc.) | Android system permission/settings screens + AURA onboarding | Device OS |
| Trusted devices / MCP pairing approval / toolsets | AURA app → MCP/Trusted Devices settings | Local pairing flow with `aura-mcp-connect` |

### Local build/project configuration (source build)
| Item | Where to configure | Source |
|---|---|---|
| Android SDK path, signing config, local defaults | `aura-android/local.properties` | Local dev machine / keystore |
| Firebase app config | `aura-android/app/google-services.json` | Firebase Console project settings |
| Custom endpoint/provider settings (if supported) | In-app settings and/or project-specific provider config stores | Your provider/self-hosted endpoint |

### Desktop bridge configuration
| Item | Where to configure | Source |
|---|---|---|
| `aura-mcp-connect` install | Computer terminal: `npm install -g aura-mcp-connect` | https://www.npmjs.com/package/aura-mcp-connect |
| Pairing and trust state | `aura-mcp pair <PIN>` (stores trust locally, e.g. `~/.aura/webrtc.json`) | PIN shown in AURA app MCP screen |
| Daemon port / optional ADB tool | CLI args or env vars (`AURA_MCP_PORT`, `AURA_MCP_ENABLE_ADB`, etc.) | Bridge README / local runtime config |

---

## Notes & Caveats
- **"Required" depends on feature scope:** AURA can run in reduced modes, but automation quality/features drop if key permissions/services are missing.
- **Provider capabilities vary:** not all LLM models support tool calling, vision, or long contexts equally well.
- **No AURA cloud relay for device control:** remote control is designed for local/private network paths (WebRTC + LAN signaling) via the bridge.
- **Privacy/security:** use least-privilege permissions, protect API keys, and avoid enabling privileged debug tools (e.g. `aura-adb`) outside trusted development scenarios.