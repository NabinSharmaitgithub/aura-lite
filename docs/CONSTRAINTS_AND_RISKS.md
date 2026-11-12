# Known Constraints and Risks

## Device / OEM constraints
- **Accessibility reliability varies by OEM** (MIUI/ColorOS/One UI etc.). Services may be killed, events throttled, or trees sparse.
- **Background execution limits** can stop wake word/agent services; foreground service types and battery-optimization exemptions are required.
- **Microphone contention** between wake word, STT, and live conversation requires strict single-owner arbitration.
- **Screen capture consent** is per-session (MediaProjection); vision may be unavailable.
- **Package visibility** rules can hide installed apps on some OS versions/OEMs; alias/fallback resolution mitigates but cannot guarantee all apps.

## Model / provider constraints
- **BYOK LLM availability/latency/cost** vary; providers may rate-limit or change APIs.
- **Vision/token budgets** limit how much screen history can be kept; compaction can lose nuance.
- **Local voice models** (KWS/STT/VAD) add app size and require compatible assets; accuracy varies by microphone/noise/accent.
- **Gemini Live / realtime APIs** require network, valid keys/models, and protocol compatibility; audio orchestration is device-sensitive.

## Security / privacy risks
- **Prompt injection via on-screen/remote content** — mitigated by treating content as data and enforcing policy in code, but residual risk remains.
- **Sensitive data leakage** through screenshots, traces, or logs — mitigated by redaction/opt-in sync, but must be continuously tested.
- **Remote MCP exposure** on LAN/WebRTC — requires pairing, trust revocation, least-privilege toolsets, and audit; misconfiguration is a real risk.
- **Desktop bridge trust material** (`~/.aura/webrtc.json`) must be permission-restricted and rotatable.
- **Debug tools (`aura-adb`, debug receivers/hooks)** bypass some phone-side checks and must be developer-opt-in only.

## Reliability / correctness risks
- **Stale perception** can cause wrong taps — mitigated by generation/signature checks and re-observation, but async UI changes can still race.
- **Looping/false completion** — mitigated by loop detection, budgets, evidence/proof checks; complex flows may still need HITL.
- **Resume after environment change** (different screen/app/permissions) — must re-verify and refuse unsafe resume.
- **Remote gating** can brick users if misconfigured — enforce cached/offline-safe defaults and staged rollout.

## Compatibility / operational risks
- **Desktop `aura-mcp-connect` protocol drift** vs phone transport can break clients; keep shared protocol tests and versioning.
- **Android version policy changes** (permissions, foreground services, notification/listener rules) may require ongoing updates.
- **Firebase/B2 optionality** means features must degrade cleanly when unconfigured.
- **Eval benchmarks** are environment-sensitive; verdicts need normalization and human review for disputed cases.

## Mitigations summary
- Enforce invariants in code (`docs/INVARIANTS.md`), test adversarially (tool bench), redact logs, keep offline-safe defaults, require explicit confirmation for sensitive/destructive actions, and validate on real devices across OEMs.
