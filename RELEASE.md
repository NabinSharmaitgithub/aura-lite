# Release process

AURA Lite releases are produced by CI only. Releases are never signed or published locally.

## Trigger a release

Either tag:

```sh
git tag v1.0.<code>
git push origin v1.0.<code>
```

or dispatch:

```sh
gh workflow run android.yml --ref main -f release=true
```

Both publish a signed APK with the build report, checksums and certificate fingerprints. The tag
form is preferred for reproducibility and because it lets downstream pin to a known-good version.

## Versioning

`versionCode = 534 + git rev-list --count HEAD` and `versionName = 1.0.<versionCode>`. The SHA is
written into `BuildConfig` for support diagnostics. Debug builds carry `-<shortSha>` suffix and a
`.debug` applicationId.

## What the APK contains

- arm64-v8a + armeabi-v7a (release) or x86_64 (debug).
- The production signing key is in the `AURA_KEYSTORE_BASE64` secret; the release report is
  `build-report/REPORT.md`.
- `GOOGLE_SERVICES_JSON_BASE64` restores Firebase for builds with Firebase wired in;
  without it, builds are intentionally offline-only.

## Verifying a release

1. Download both APKs from the release page.
2. Check the SHA-256 in `REPORT.md` (right panel of the run summary).
3. Install on a physical device: Android 12+, clear unknown-sources warning, run the app once,
   confirm Gatekeeper badge in **Settings → About**.
4. The first reply should be a friendly greeting; voice models are optional and the app works without them.
