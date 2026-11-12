# Support

## Where to get help

- **GitHub issues** — bug reports, feature requests, and questions. Search first; someone may have
  already hit it.
- **Discussions** — open-ended questions and design conversations (coming once the repo has a
  community; meanwhile use issues).
- **Security issues** — see `SECURITY.md`; do **not** file a public issue.

## What to include in a bug report

1. Android version, device manufacturer and model (e.g. Pixel 7, Android 15).
2. What you expected, what happened, and the exact steps to reproduce.
3. The app version from **Settings → About** (or the release tag from the GitHub Releases page).
4. Relevant logcat output — redact any personal data, screen contents, and API keys before pasting.
5. If the agent did something surprising, the trace from **Activity → the run → Export**. Review it
   before attaching; traces contain what the agent saw.

## What not to report

- Requests that the agent disobey the built-in safety rules (banking apps, entering credentials,
  destructive actions without confirmation). Those are refusals by design.
- "It didn't guess an ambiguous goal — it asked me instead." That's `ask_user` working.
- Issues with a third-party provider whose API key you own; check their status and quota first.

## Response expectations

This is a small, unfunded project. Maintainer response time is not guaranteed; good reproduction
info meaningfully increases the odds of a fast response. Merged fixes are released on the next tag.
