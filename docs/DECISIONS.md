# Decisions

A running log of architecturally significant decisions. See `AGENTS.md` for the shorter version.

## 2026-11-12 — Name remains "AURA Lite"; fork branding documented

The shipped product is `AURA` (`aura-android-use`), this repo is `AURA Lite`. The name is kept
because it is now public; the fork-check below is the standing answer:

- **This repo does not ship the competitor's logo, splash, launch identity, or marketing assets.**
  It is a reimplementation from the public specification documents, rebuilt from scratch.
- Any forks sold commercially must rename their product and MUST NOT use the AURA mark without
  written consent; they receive the linking-exception rights only as AGPL users.

## 2026-11-12 — Firebase stays strictly opt-in

- `google-services.json` is gitignored and only restored in CI from a repository secret.
- Release builds without Firebase stay intentionally offline-only (`firebase/…` documents this).
- Analytics and Crashlytics are compiled-in but **collection disabled by default**; the user
  enables them in Settings → Firebase. The diagnostics toggle never defaults on.

## 2026-11-12 — Remote kill switch exists, but only in the opt-in Firebase path

`RemoteGate` reads `kill_switch_enabled` and `min_supported_version_code` from Firebase Remote
Config only when a Firebase build is wired in. Offline builds no-op, by design. The switch is
reserved for critical security events (e.g., a compromised signing key); routine
`min_supported_version_code` updates require a documented reason in the release notes.

## 2026-11-12 — Banking / payment / authenticator refusal is hard-coded

`RestrictedAppsRepository.SYSTEM_BLOCKED_PACKAGES` lists banking apps, wallets and authenticators.
The agent refuses to act while one of these is foreground, even if the user asked; the refusal is a
normal tool error with the fix path, not a silent failure. Users can extend the list; they cannot
remove system entries.

## 2026-11-12 — Voice models are not committed

KWS/STT/VAD/TTS models are downloaded separately (`k2-fsa/sherpa-onnx`); without them voice
features degrade cleanly and `AndroidManifest` keeps the microphone permission for wake-word use
only after explicit onboarding. Forks that do not want voice can delete `voice/` and be smaller.

## 2026-11-12 — `omniparser_icon_detect.onnx` is not redistributed

The reference icon detector is treated as a vendor tree. This repository ships OCR (ML Kit) and
tree-first perception only; the detector plugs in if the file is present at build time.
