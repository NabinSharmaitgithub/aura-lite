# Firebase (optional, opt-in) for AURA

AURA is **offline-first**. The stock app has no account requirement, no cloud dependency and no
analytics. Firebase is compiled in as an **opt-in layer**: it activates only when a build carries
`app/google-services.json` *and* the user links an account in **Settings → Firebase**. Without the
file, the app is exactly the same offline product; every Firebase call site no-ops.

This directory holds everything needed to stand up your own Firebase backend and deploy the rules
and the optional function. Nothing here is committed into the app binary.

## What the integration does

| Layer | Wired in app | Needs user action | Needs Firebase project |
|---|---|---|---|
| Firebase Auth (email/password, Google) | `FirebaseAuthManager` + Settings → Firebase | enable providers | yes |
| Cloud Firestore sync (facts / plans / skills two-way, last write wins; sessions push-only opt-in) | `FirebaseSync` | tap "Sync now" | yes + deploy rules |
| FCM push notifications | `AuraFirebaseMessagingService` | device token shown in Settings | yes + server to send |
| Remote Config (`aura_sync_paused`) | `FirebaseRemoteConfigManager` | none | yes + set key |
| Crashlytics | `FirebaseDiagnostics` | enable toggle | yes |
| Analytics (coarse sync counts) | `FirebaseDiagnostics` | enable toggle | yes |
| App Check (Play Integrity) | enabled for all Firebase SDK calls once bundled | register app in App Check console | yes |
| Cloud Functions | `firebase/functions/` (optional) | none in app | yes, Blaze plan |

Unauthenticated users, offline-only builds and users who never open Settings → Firebase send
**nothing** anywhere.

## Data model and conflict policy

One document per kind under `users/{uid}/snapshots/{kind}`:

- `kind` ∈ `facts` (learned facts + voice corrections), `plans` (reminders), `skills` (skill packs),
  `sessions` (push-only opt-in: last 25 session summaries + the hash-only MCP audit log)
- fields: `payload` (string, JSON document, ≤ 1 MiB), `updatedAt` (int), `deviceId` (string), `version` (int, always 1)

Resolution is **last write wins** on `updatedAt`; `sessions` never comes back down. Imports
re-create reminders/skills wholesale (Room tables are cleared and re-upserted from the snapshot);
facts are imported only when the document holds a valid learnings object. Nothing else exists in
the database, so the rules have no other surface to protect.

`sessions` payloads deliberately contain only summaries — `id, goal, startedAt, endedAt, outcome,
steps, provider, model` — never the full trace, never screenshots, never prompt text (session
traces live in the local JSONL log and are user-deletable under Settings → Developer mode → "Log retention").

## Security rules

- `firestore.rules` — owner-only read/write, snapshot shape and size validation, anything else denied.
- `storage.rules` — no uploads today; pre-scoped per-user area with a 10 MiB cap for future use.
- `firestore.indexes.json` — empty; no queries, single-document gets only.

Test them (optional) with the emulator suite before deploying:

```sh
npm install --save-dev firebase @firebase/rules-unit-testing
npx firebase emulators:exec --only firestore "npm test"
```

## Deploy checklist (one-time, per Firebase project)

1. Create a project at console.firebase.com (no billing needed until Functions/Blaze).
2. Build → Add Android app with package name `com.aura.aura_ui.feature` (debug suffix: `com.aura.aura_ui.feature.debug`).
   Download `google-services.json` and place it at `app/google-services.json` (git-ignored). For CI,
   base64 it into the repository secret `GOOGLE_SERVICES_JSON_BASE64` instead.
3. Authentication → enable **Email/Password** and **Google** (Google needs the SHA-1 of your signing
   certificate — debug: `./gradlew signingReport`, release: see your release key or the CI report).
4. Firestore Database → create in production mode. Then:
   ```sh
   npm install -g firebase-tools
   firebase login
   cd firebase
   # Set your project id in .firebaserc (or run: firebase use --add)
   firebase deploy --only firestore:rules,firestore:indexes,storage:rules
   ```
5. App Check → register the Android app with **Play Integrity**; add the SHA-256 from the release
   signing certificate (CI's "Certificate fingerprints" step prints it) to both Firebase and Play.
6. Remote Config → add key `aura_sync_paused` (boolean, default `false`) to emergency-pause sync.
7. FCM → no server ships with the app. Publish from anywhere that can call the Admin API; data-only
   messages with `title`/`body` are displayed by `AuraFirebaseMessagingService`.
8. (Optional) Functions → `cd functions && npm install && firebase deploy --only functions`.
   Plan: **Blaze** required. Nothing in the app depends on it.
9. Release checklist for app stores: enable Crashlytics and Play Integrity only for release builds,
   and confirm the privacy label matches the toggles (both off by default).

## What the release pipeline does with this

CI reads `GOOGLE_SERVICES_JSON_BASE64`; if set, it restores `app/google-services.json` and applies
the Google Services/Crashlytics Gradle plugins. Otherwise it builds the same offline product
intentionally, and the Settings → Firebase screen reports "Not configured on this build".

## Privacy summary

- No account: no data ever leaves the device for AURA features.
- Linked account, sync not tapped: still nothing leaves (auth token exchange only).
- Linked account + sync tapped: only the selections above, to your own Firebase project.
- App Check and Crashlytics are byte-level products of Google; review their data policy before
  enabling the toggles.
