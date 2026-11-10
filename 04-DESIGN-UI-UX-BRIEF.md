# Design UI/UX Brief — AURA for Android

> Design language: **MONO — "Monochrome plus a drop of blood."**
> White canvas, black ink for content/action, **blood red strictly for semantic attention**.
> Implementing this exactly is what makes the rebuild "look identical".

---

## 1. Philosophy

| Principle | Expression |
|---|---|
| **One canvas, one action colour** | Warm white background; black pills/cards/buttons; nothing else competes |
| **Red is an alarm, not an accent** | Only for live/recording, destructive, broken permission, active-run dot |
| **One navigation element** | The floating black capsule. No drawer, no tab bar, no top app bar |
| **Calm density** | Generous 24 dp screen padding, 24 dp cards, large tap targets |
| **Craft in the micro** | Press scale, haptic on commit, 220 ms transitions, no bounce |
| **Technical honesty** | Monospace for ids, counts, durations, tool names |

Dark mode is a first-class twin: charcoal cards on near-black canvas, identical semantic red.

---

## 2. Colour tokens (exact)

### 2.1 Mono palette
| Token | Hex | Use |
|---|---|---|
| `Canvas` | `#FAFAF8` | app background (light) |
| `CanvasHigh` | `#FFFFFF` | elevated surfaces, sheets |
| `Outline` | `#E8E8E4` | hairline borders on white |
| `TextPrimary` | `#111111` | text on canvas |
| `TextSecondary` | `#6B6B68` | supporting text on canvas |
| `Ink` | `#0A0A0A` | black cards, pills, primary buttons |
| `InkSoft` | `#171717` | nested dark surface |
| `InkPressed` | `#262626` | pressed ink |
| `TextOnInk` | `#F5F5F2` | primary text on ink |
| `TextOnInkDim` | `#9C9C98` | secondary text on ink |
| `OutlineOnInk` | `#2E2E2B` | hairlines inside ink |
| `Blood` | `#A31621` | live dot, destructive, broken permission |
| `BloodPressed` | `#7A1019` | pressed red |
| `BloodContainer` | `#FBEBEC` | warning banner field |
| `ToggleGreen` | `#34C759` | **only** switch ON track |
| `ToggleTrackOff` | `#E4E4E1` | switch OFF (light) |
| `ToggleTrackOffDark` | `#3A3A3A` | switch OFF (dark) |

### 2.2 Dark scheme (grouped surfaces)
`canvas` near-black · `card` charcoal · `chip` slightly lighter charcoal · `divider`/`outline` subtle greys · `bloodContainer` deep red-black. `Mono.Blood` is **identical** in both modes.

### 2.3 Legacy palettes (present in code, not used by Mono screens)
`AppleColors.kt` (iOS-like greys, now desaturated to near-monochrome), `AuraWarm.kt` (warm accent scheme used by onboarding/landing), `BrandColors.kt`. If rebuilding, keep only Mono + the warm onboarding canvas to reduce surface area.

---

## 3. Typography

**Families** (Google Fonts provider):
- Display & body: **Plus Jakarta Sans**
- Technical/mono: **JetBrains Mono**

**Scale** (sp / lineHeight / tracking)

| Token | Weight | Size | Tracking |
|---|---|---|---|
| displayLarge | ExtraBold | 54 | −1.4 |
| displayMedium | Bold | 42 | −1.0 |
| displaySmall | Bold | 34 | −0.8 |
| headlineLarge | Bold | 30 | −0.6 |
| headlineMedium | SemiBold | 26 | −0.4 |
| headlineSmall | SemiBold | 22 | −0.2 |
| titleLarge | SemiBold | 20 | −0.2 |
| titleMedium | SemiBold | 16 | −0.1 |
| titleSmall | SemiBold | 14 | 0 |
| bodyLarge | Normal | 16 | 0 |
| bodyMedium | Normal | 14 | 0 |
| bodySmall | Normal | 12 | +0.1 |
| labelLarge | SemiBold | 14 | −0.1 |
| labelMedium | SemiBold | 12 | +0.1 |
| labelSmall | SemiBold | 11 | +0.2 |

Rules: negative tracking only ≥ 20 sp; never letterspace body text; numbers in mono when they change (timers, step counts, token usage).

---

## 4. Spacing, shape, motion

**Spacing** — `xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32` dp. Screen horizontal padding = **24 dp** (`Mono.ScreenH`).

**Shapes**
| Token | Radius |
|---|---|
| `ShapeCard` | 24 dp |
| `ShapeCardSmall` | 16 dp |
| `ShapeChip` | 12 dp |
| `ShapePill` | 50 % |

**Motion (ms)**
| Token | Value | Use |
|---|---|---|
| `MotionFast` | 150 | taps, colour changes |
| `MotionNormal` | 220 | screen transitions, sheet |
| `MotionSlow` | 400 | onboarding droplets, hero reveals |

Easing: standard `FastOutSlowIn`. Press feedback = scale 0.97 + `InkPressed`, never a ripple on ink surfaces.

---

## 5. Core components

### 5.1 The Capsule (signature component)
- Floating black strip, `ShapePill`, height ≈ 64 dp, horizontal inset 24 dp, bottom inset 16 dp + nav-bar inset.
- **Top-level mode:** 4 icons evenly spaced; active tab has a white 10 % highlight pill behind it; icons `TextOnInkDim` → `TextOnInk`.
- **Subpage mode:** `‹` back + current title (from `routeTitles`), left-aligned.
- Shadow: soft, 16 dp blur, 8 % black. No border.
- Icons: Material Symbols outlined, 24 dp. Active = filled variant.

### 5.2 Cards
- Light: white card, `#E8E8E4` 1 px hairline, 24 dp radius, 20 dp padding.
- Ink card: `#0A0A0A`, no border, text `#F5F5F2`, 24 dp radius.
- Never nest more than one level (card in card ⇒ use `InkSoft`).

### 5.3 Buttons
| Type | Style |
|---|---|
| Primary | Ink fill, `TextOnInk` label, pill, 52 dp height |
| Secondary | White fill, 1 px `Outline`, ink label |
| Destructive | Blood fill, white label; or blood text on white |
| Ghost | Text only, `TextSecondary` |

Disabled = 40 % opacity, never grey-on-grey below 3:1 contrast.

### 5.4 Switches
Track ON `ToggleGreen`, OFF `ToggleTrackOff`; 48×28 dp; haptic on change. **The only green in the product.**

### 5.5 Status chip
Pill, 32 dp tall, `InkSoft` background; a 6 dp dot: green-ish→ actually **blood when broken**, `TextOnInkDim` when idle. Label in `labelMedium`.

### 5.6 Trace atoms
- Step row: mono index `01`, tool name in `titleSmall`, duration in mono `bodySmall` dim.
- Collapsible args/result in a `ShapeCardSmall` ink block.
- Error rows: blood left border 3 dp + `BloodContainer` background.
- Screenshot thumb: 16 dp radius, max 120 dp tall, tap to expand.

### 5.7 Overlay (floating assistant)
- Pill 160×56 dp, ink, 50 % radius, subtle 1 px light border for edge definition.
- States: idle (aura dot), listening (animated waveform in `TextOnInk`), thinking (rotating arc), speaking (level meter).
- Pause state expands to a labelled pill: `Pause` → `Paused · tap to resume`, blood accent.

---

## 6. Screen specs (layout intents)

### Home
1. Top: small brand wordmark + greeting (`headlineMedium`).
2. Status chip row (wrap).
3. Prompt composer: ink card, multiline, placeholder `Ask AURA to do something…`, mic + send on the right.
4. Suggestion marquee: horizontally scrolling ghost chips.
5. Active run card (if any) with live trace preview + pause.
6. "Recent" list: 3 rows, each with title, relative time, outcome dot.

Vertical rhythm: 24 dp between blocks; composer pinned above capsule.

### Agent hub
Grouped list: 7 rows, each = 40 dp rounded-square chip icon + title + one-line subtitle + chevron. Group headers in `labelSmall` uppercase, `TextSecondary`.

### Settings
Same grouped-list component. Section header + white card containing rows separated by 1 px `Outline` indented 56 dp. Destructive rows in `Blood`.

### Provider / Brain
- Provider picker (sheet, searchable, 14 built-ins + custom).
- Key field (secure, paste, "Get key ↗" link, verify button).
- Model picker (fetched list, search, capability badges: vision / reasoning / cheap).
- Advanced: temperature, max tokens, system prompt override.

### Onboarding
- Full-bleed warm canvas; hero type; a single CTA at the bottom.
- Steps 4–7: permission card + live status pill that flips from `Not enabled` (blood) to `Enabled` (ink) and auto-advances.
- Droplet ripple motion (`MotionSlow`) on step change; no confetti, no gradients.

### Activity / Session
- List: day groups, each row = goal text, step count, duration, outcome.
- Detail: timeline of trace atoms, filter chips (all / tools / errors), footer actions (re-run, delete).

### Voice overlay
- Centered bottom, ink pill; transcript in `bodyLarge`; cancel `×` left; send when STT final.

---

## 7. Iconography & imagery
- Material Symbols **Outlined**, 24 dp, stroke weight default, never coloured except semantic red.
- App icon: monochrome mark on ink. Splash: ink → canvas.
- Empty states: one line of copy + one ghost button. No illustrations (keeps APK small and tone dry).

---

## 8. Copy & tone
- Short, literal, first person from the assistant: "I'll open WhatsApp and send it." Not "Sure! 😊".
- Errors say what to do: "Accessibility is off. Turn it on to let AURA tap for you." + button.
- Never blame the user. Never expose stack traces; use a copy-details affordance.
- Buttons are verbs: `Turn on`, `Verify key`, `Pause run`.
- Money/irreversible actions name the consequence: `Send message`, `Delete 3 sessions`.

---

## 9. Accessibility
- Minimum touch target 48 dp; capsule icons 44 dp.
- Contrast: body ≥ 4.5:1; large text ≥ 3:1; never rely on red alone (pair with icon/text).
- `contentDescription` on every icon-only control; live regions for run status.
- Dynamic type: layouts must reflow to 1.3× without clipping (use `sp`, avoid fixed heights on text rows).
- TalkBack order: header → status → composer → list.
- Reduced motion: honour `Settings.Global.ANIMATOR_DURATION_SCALE == 0` → skip droplet/ripple.

---

## 10. Do / Don't

| ✅ Do | ❌ Don't |
|---|---|
| Ink for actions, white for canvas | Colourful gradients or brand accents |
| Red only for "look now" | Red decorative dots, red headings |
| 24 dp radius everywhere | Mixed 4/8/12 radii in one screen |
| One primary action per screen | Two filled buttons side by side |
| Mono for numbers | Proportional digits in timers |
| Empty space | Filling space with borders |

---

## 11. Screen checklist before "done"
- [ ] Capsule present, correct mode (tabs vs back+title)
- [ ] 24 dp horizontal padding
- [ ] Only ink/white/blood/green-switch colours used
- [ ] Every state designed: loading, empty, error, permission-missing, offline
- [ ] Dark mode checked
- [ ] 1.3× font scale checked
- [ ] No raw hex outside the theme file
