# Launch checklist

A launch is a distribution event, not a code event. Sequence matters.

## Pre-flight (1–2 days before)

- [ ] CI green on `main` for the last 3 consecutive pushes.
- [ ] `gradle :app:assembleRelease` and the verifier steps pass in the release workflow.
- [ ] README screenshots reflect the current build (Monadic visual language, capsule nav).
- [ ] `git clean -fdx` tree: no local `google-services.json`, no `.env`, no keystore material. Confirm with
      `git status --porcelain` and `git grep -iE '(key|secret|token)[-_]?=' | grep -v -- '--'` (empty).
- [ ] `google-services.json` on this machine is the only Firebase/derived artifact; it is gitignored.
- [ ] Version number bumped for the launch release (dispatch with `release_tag` empty → `v1.0.<code>`).

## Launch day

- [ ] Tag the release: `git tag v1.0.<code> && git push origin v1.0.<code>`, or dispatch the workflow
      with `release=true` (uses `v1.0.<code>` automatically).
- [ ] Verify the GitHub release: APKs attached, checksums listed, certificates reported.
- [ ] Publish the install page link everywhere: release body, website, README badge.
- [ ] Post to Show HN and r/androidapps with a 3-line description and one screenshot.
- [ ] Update the benchmark page or announcement thread (see `benchmark/README.md`).
- [ ] Tag: `v1.0.<code>-launch` — used to freeze marketing assets later.

## Post-launch (same day)

- [ ] Watch the first 30 minutes for install failures — most are SHA mismatch on stale debug installs.
      Call them out in the release body: uninstall before installing the new build.
- [ ] Bump `min_supported_version_code` (Firebase Remote Config) only **after** a critical fix ships.
- [ ] Log feedback as issues on the repo; label-good-first once triage starts.

## Don'ts

- Do not announce before the APK is verified on a physical device — emulators lie about permissions.
- Do not ship a release that skips CI; every public APK is signed by CI and carries the fingerprint in
  the release notes.
- Do not hand-build a release you will not be able to reproduce in 12 months.
