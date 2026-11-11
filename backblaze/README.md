# Backblaze B2 (optional, opt-in) for AURA — file storage

AURA is **offline-first**. The stock app has no account requirement, no cloud dependency and no
analytics. Backblaze B2 is the slot for **file-backed** opt-in storage (screenshots, session
artifacts, anything too big or too binary for the Firestore JSON snapshots). It replaces the old
pre-scoped Firebase Cloud Storage area, which has been removed (`firebase/storage.rules` is gone
and `firebase deploy` no longer references storage).

## What the app does today (implemented)

- **Agent tools**: `upload_file` and `download_file` upload/download arbitrary local files and
  are confined to the bucket's `users/` prefix. Uploads are marked `risky` and need confirmation.
- **Session screenshot backup** (Settings → Backblaze → "Back up session screenshots"):
  when a run ends, every PNG in the session's `screens/` directory is uploaded to
  `users/sessions/<sessionId>/screens/<name>.png`. Off by default.
- **Session export** (Session detail → "Export to Backblaze B2"): the whole session directory
  (trace + screenshots) is zipped and uploaded as `users/exports/<sessionId>-<timestamp>.zip`.
- **Hard cap**: the client refuses objects larger than **10 MiB** — B2 does not enforce a cap.
- **Layout**: everything the app writes lives under `users/` — `users/uploads/…`,
  `users/sessions/<id>/screens/…` and `users/exports/…`. This matches the app-key `namePrefix`.
- **Signing**: hand-rolled AWS SigV4 (path-style, `us-east-005`, region from the endpoint) —
  no AWS SDK, no new dependency. Verified end-to-end from this machine: authorize → PUT → GET →
  HEAD → DELETE → empty listing.
- **Keys**: the bucket name and endpoint come from Settings → Backblaze (SharedPreferences); the
  application key id and key are stored in **EncryptedSharedPreferences** (`aura_secure`) and never
  appear in git, in any APK, or in logs. The user enters them on-device and can clear them there.

## What was provisioned (2026-11-11)

| Item | Value |
|---|---|
| Account / master key id | `8a0a9dd0927b` |
| Bucket | `aura-lite-storage` — **allPrivate**, SSE-B2 (AES256) server-side encryption, S3 API enabled |
| Bucket id | `a8ea907ac95dcda0a912071b` |
| Native API endpoint | `https://api005.backblazeb2.com` |
| Download URL | `https://f005.backblazeb2.com` |
| S3-compatible endpoint | `https://s3.us-east-005.backblazeb2.com` (region `us-east-005`) |
| Layout | everything lives under `users/…` |
| App key | `aura-lite-app` — `applicationKeyId` `0058a0a9dd0927b0000000001` |
| App key capabilities | `listFiles`, `readFiles`, `writeFiles`, `deleteFiles` |
| App key restriction | this bucket only + `namePrefix` = `users/` (analogue of the old `users/{uid}/` Firebase rules) |
| App key secret | **not committed.** Held at `~/.config/aura-lite/backblaze.env` (mode 600); shown once at creation. |

The master key was used once to create the bucket and app key. **Rotate it** — it was handled in
plaintext. Create replacements at backblaze.com → *My Account → Application Keys*.

## Differences from Firebase Storage worth knowing

- Firebase Storage rules enforced per-user isolation server-side. B2 has no per-end-user ACLs:
  isolation comes from the app-key restriction above (this app may only touch `users/…`) and from
  what the client uploads. Per-user cryptographic isolation would need server-side key issuance;
  if AURA ever ships multi-user file sync to production, that is the gap to close.
- No 10 MiB per-object cap is enforced by B2 itself — AURA enforces it in the client.
- The bucket is `allPrivate`: downloads need an authorized key or a short-lived download
  authorization (`b2_get_download_authorization`, capability `readFiles` on `users/…`).
- Only keys with the `users/` prefix work; a client that tries any other key gets
  `AccessDenied / not entitled`.

## How the client talks to B2

The app talks to `s3.us-east-005.backblazeb2.com` with path-style addressing, region `us-east-005`,
and the app key as access key/secret, using ordinary SigV4 (see `S3v4.kt`, ~120 lines, covered by
a real bucket smoke test: put → get → head → delete all 200/204). The native API also works:
`b2_authorize_account` → `b2_get_upload_url` → `b2_upload_file`.

## Verify

```sh
# b2 CLI (https://github.com/Backblaze/B2_Command_Line_Tool)
pip install b2
b2 account authorize-account 0058a0a9dd0927b0000000001 "$B2_APP_KEY"
b2 ls b2://aura-lite-storage
printf 'hello' > /tmp/b2-hello.txt
b2 upload-file --remoteName users/smoke-test.txt aura-lite-storage /tmp/b2-hello.txt
b2 download-file aura-lite-storage users/smoke-test.txt /tmp/b2-hello-dl.txt && cat /tmp/b2-hello-dl.txt
b2 delete-file-version --bucketName aura-lite-storage users/smoke-test.txt
```

Verified working 2026-11-11 from this machine with the scoped key: authorize → upload
(`users/smoke-test.txt`) → download → list → delete (bucket left empty).

`b2 app-key info` should show `bucketId` and `namePrefix: users/` if you want to confirm the
restriction after any rotation.

## Checklist (one-time)

- [ ] Master key rotated; old master key deleted.
- [ ] Scoped app key entered in-app at **Settings → Backblaze** (stored in `aura_secure`); never in
      the repo, never baked into any APK, never in logs.
- [x] Client uploads only under `users/`, with a 10 MiB per-object cap.
- [x] `firebase deploy` no longer references `storage:rules`; the `storage` section is gone from
      `firebase/firebase.json`.
