# AURA Lite — Competitive roadmap vs `Dinesh210805/aura-android-use`

> Analysis and plan produced 2026-11-12 from a direct clone of the competitor repo.
> **Do not commit this file to the public repo without a decision.** It is intentionally not
> tracked; it records internal strategy.

## Verdict up front

You can't beat them by out-coding them 1:1 — they ship weekly and their tool surface is at parity.
You beat them on three axes they can't copy without rebuilding:

1. **Engineering hygiene + release trust** — ours is already ahead (CI secret scan, signed APKs,
   `apksigner verify` enforced, production keystore, reports/attestation) and should be marketed.
2. **Privacy / offline-first positioning** — diagnostics off by default, no account, no sign-in,
   cloud strictly opt-in; they ship diagnostics opt-out and Google sign-in.
3. **Public presence + a measured benchmark** — website, npm bridge, demo clips, AndroidWorld
   number published. That is their moat, and it closes in weeks, not months.

Don't compete on feature count. Don't copy their code (AGPL — ours stays independent). Don't promise
Play Store yet (sideload/APK is the distribution path for both).

## Competitive analysis

### Who they are

Solo project, ~7 weeks old (created 2026-09-22, squashed public history, active — last push
2026-11-11). 24 stars, 8 forks, 1 open issue, ~850 APK downloads (155 MB, `v1.0.592`),
~96 tracked installs across ~33 countries, npm `aura-mcp-connect@0.11.0`, website
`aura-android-use.vercel.app`, launch film (4:25), Discord, M8ven badge.

Positioning: *"AURA — open-source hands for AI agents to control your Android."* On-device agent +
MCP server in the phone, driven by Claude Code/Cursor/any MCP client.

### Side-by-side

| | Them (`aura-android-use`) | Us (`aura-lite`) |
|---|---|---|
| Code size / maturity | 911 Kotlin files, 240 unit tests, 3 androidTest | 78 Kotlin files, **0 tests** |
| App quality | Launch icon, onboarding, voice wake word, in-app browser, deep-link shortcuts, **conversation mode (Gemini Live + `ask_aura`)**, vision model (YOLOv8 + OCR) | Comparable core (49 tools), **no conversation mode, no benchmark, no tests** |
| Distribution | Website, APK releases, npm bridge, Discord, launch film, M8ven, install counter | Production-signed GitHub releases, **no site/community/bridge/counter** |
| Engineering rigor | No build CI, local Windows builds, no artifact verification | Secret scan, sign + verify + checksum + report artifacts — **we win** |
| MCP surface | tools + **instructions** + **resources** (safety policy, guide, device status) + **prompts** (templates, skills) | tools only; `resources/list`, `prompts/list` return empty |
| Cloud posture | Firebase sign-in, diagnostics **on by default**, install record tied to Google sign-in, kill switch / version floor via Remote Config | Offline-first, diagnostics **off** by default, no account |
| License / governance | AGPL-3.0 + linking exception + **commercial license** + CLA | **None** — code is legally undistributable |
| Benchmarking | AndroidWorld (on-device + MCP+Claude), published on site | None |
| Marketing | Launch film, badges, install counter, honest "known rough edges" | README only |

### Where they beat us today

1. **Trust signals**: website, YouTube demo, download counter, Discord, published AndroidWorld
   results, ~850 downloads — outsiders can see this works.
2. **Ecosystem**: npm bridge → Claude Code/Cursor/VS Code pickup out of the box; MCP
   instructions/resources/prompts make their server dramatically more useful.
3. **Visible quality bar**: 240 unit tests, mock-phone ToolBench, public benchmark, launch film.
4. **Legal**: AGPL + CLA + commercial option; they can sell closed derivatives; we cannot ship.

### Where we already win

1. **Release hygiene**: production keystore, apksigner enforced, checksums, CI artifacts & secret
   scan; their builds are manual and local.
2. **Privacy**: offline-first, diagnostics opt-in, no sign-in, opt-in cloud — they cannot match
   this without a privacy fork.
3. **Footprint**: ~91 MB vs their ~155 MB APK.
4. **Architecture parity**: the full AURA spec implemented (agent loop, hooks, learnings, skills,
   sherpa voice, capsule nav, MONO design system).
5. **Bus factor equal**: both solo — whoever ships a credible public presence first wins.

## Roadmap

### Phase 0 — Respect the basics (Week 1–2, blocking)

- [ ] **License & governance** (independent writing): AGPL-3.0 + linking exception (Google Play
      services/Firebase/ML Kit) + optional commercial license + CLA. Add `LICENSE`,
      `LICENSE-EXCEPTION.md`, `COMMERCIAL-LICENSE.md`, `CLA.md`, `CONTRIBUTING.md`, `SECURITY.md`,
      `PRIVACY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`, `AGENTS.md`.
- [ ] **Name decision**: both products are "AURA" with identical package roots. Rename ours before
      any publicity (see Decisions).
- [ ] **CI unit tests**: `:app:testDebugUnitTest`, `:mcp-server:test` in workflow; ≥50 focused
      tests (agent loop, voice corrections, SecureKeyStore, SIGV4, tool registry).
- [ ] **MCP protocol completeness** (1–2 days each): `instructions` at initialize (safety policy +
      how to use); `resources/list` + `resources/read` (safety policy, tool-selection guide, live
      device status); `prompts/list` + `prompts/get` (task templates + user skills as prompts).
- [ ] **`ask_user` tool + gate**: agent pauses → overlay question → resumes on answer.
- [ ] **Release UX**: release naming `AURA v1.0.x — <sha>`, links to website/docs in notes.

### Phase 1 — Ship the public face (Week 2–4)

- [ ] **Static website** (GitHub Pages or Vercel): install guide, feature grid, privacy copy,
      benchmark badge placeholder, install counter, architecture page. Plain HTML, no build step.
- [ ] **Demo clips**: 4–5 real phone recordings ("book a ride", "compare prices", "message Mum",
      "summarize an article").
- [ ] **npm bridge** `aura-mcp-connect-lite` (MIT): stdio → HTTPS bridge to the LAN MCP server so
      Claude Code/Cursor/Windsurf/Zed work out of the box. First test whether
      `claude mcp add --transport http aura https://<phone>:47821/mcp` works with our bearer token;
      if so, ship wrapper as convenience.
- [ ] **Discord/community**, install counter badge in README, M8ven/directory listings.
- [ ] **FOSS build flavor**: build without `google-services.json` (we already build offline-only by
      default — document it as a feature).

### Phase 2 — Earn trust with measured quality (Week 3–6)

- [ ] **AndroidWorld benchmark**: run our on-device agent and our MCP server + Claude Code through
      AndroidWorld (116 tasks). Publish report + videos on the site. Single highest-ROI trust asset.
- [ ] **ToolBench**: adversarial mock-phone tests in `:mcp-server`, report artifact in CI.
- [ ] **Real-device gates**: idle ≤250 MB PSS, run ≤400 MB, cold start ≤1.8 s on a real 4 GB phone,
      published with device model.
- [ ] **Reproducible release pipeline**: nightly release-build job; tag releases after verification;
      consider sigstore/cosign attestation.
- [ ] **50+ green unit tests in CI**, benchmark badge once measured.

### Phase 3 — Take the privacy-first niche (Week 4–10)

- [ ] **Positioning**: "AURA that works with no account and no telemetry — by default." Repeated on
      every page and release. Diagnostics off by default, sign-in not required, cloud opt-in only.
- [ ] **Launch sequence**: r/androidapps, r/androiddev, X; 1× HackerNews "Show HN" after website
      polish; YouTube demo short(s).
- [ ] **Publish AndroidWorld number + videos**; submit to M8ven and agent-benchmark directories.
- [ ] **Docs**: Diátaxis (`getting-started`, `guides`, `reference`, `explanation`, `decisions`) —
      closes their docs gap first.
- [ ] **Landing-page counter** and weekly "verified on Android 14/15" attest cadence.

### Phase 4 — Hold & extend (Month 2–4)

- [ ] Automate gate: only verified devices/ROMs count as "supported" publicly.
- [ ] Public ROADMAP.md with dates; `good first issue` board; issue templates; weekly releases.
- [ ] Dual license/commercial option actively managed (their playbook, openly).
- [ ] Optional: Gemini Live conversation mode (`ask_aura` style) if Phases 1–3 land well.

## 90-day scoreboard targets

| | Today | Target (day 90) |
|---|---|---|
| GitHub stars | (this repo, new) | 150+ (vs their 24) |
| APK downloads | per release | 1,000+ total |
| Tracked installs | n/a | 100+ |
| Public benchmark | none | AndroidWorld number published |
| CI status | green, no tests | 50+ tests + benchmark artifact |
| Community | none | Discord + 2 launches + regular releases |

## Risks / decisions needed

1. **Name** — both products are AURA with the same package root. Rename before any publicity.
2. **License** — AGPL + linking exception + commercial (mirror their playbook; strong) vs
   MIT/Apache (max adoption, no commercial option). Recommend AGPL + exception + commercial.
3. **Time/budget** — website + clips + benchmark on a physical phone = 2–4 weekends.
4. **Conversation mode scope** — Gemini Live streaming is real work; defer or do in Phase 4.
5. **Legal gap today** — this repo currently has **no license**, so nobody can legally fork,
   redistribute or commercially use it. Highest priority of all items above.

## Sequencing notes

- Phase 0 items that are independent of the public launch (license, tests, MCP surface, `ask_user`)
  can start immediately — they don't depend on the name decision.
- Website/branding wait on the name decision.
- Benchmark needs: one physical phone + a Friday of time.
- Do **not** publish install-counter numbers from the competitor or anyone else's dashboard; only
  our own (they have one because they opted users in to a device hash record — we won't).
