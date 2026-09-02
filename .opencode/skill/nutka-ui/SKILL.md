---
description: Design rules for every Nutka UI change - flat 2D vector look, warm paper palette, Calistoga/Figtree type, tooltips on hover, and clean data visualizations. Use when editing anything in app/src/main/java/com/nutka/app/ui/.
---

# Nutka UI — design language

Every screen, component, and theme change MUST follow these rules. The app is a
warm-paper, flat 2D vector design — never glossy, never pseudo-3D.

## 1. Flat 2D vector graphics only

- Solid color fills + 1 dp hairline borders (`NutkaColors.divider` or a step
  from the same ramp as the fill). No gradients (`Brush.*`), no `shadow()`
  elevation glows, no radial "orbs", no specular highlights.
- Depth is expressed ONLY by: fill steps (bg < surface < neutral100), borders,
  and outline rings. If something looks like it needs a shadow, use a border
  instead.
- Icons: Material vector icons (`Icons.Default.*`) — never bitmaps.
- Decorative shapes (record disc, pulse rings) are plain circles/rounded rects:
  solid fill + darker outline from the same color family
  (e.g. accent fill + accent700 border).
- Animation is allowed for motion (springs, pulses), but colors stay solid.

## 2. Color system (`ui/theme/Color.kt`, single source: `NutkaColors`)

- Background `bg` (#F5EAD8 warm paper); cards `surface`; raised elements
  `neutral100`.
- Primary terracotta `accent` (+100..900 ramps), secondary sage `accent2`.
- Text always `text` with `.copy(alpha = …)` for hierarchy (1.0 titles,
  ~0.6 meta, ~0.45 hints). Never introduce new hue families.
- Status tags use the `Tag` component styles only (ACCENT / ACCENT2 / NEUTRAL /
  OUTLINE).

## 3. Typography (`ui/theme/Type.kt`)

- Headings/titles: Calistoga (`NutkaHeadingFont`) via MaterialTheme title/headline
  styles — chunky, friendly, full Polish diacritics.
- Body/labels/buttons: Figtree (`NutkaBodyFont`). Timer uses tabular digits
  (`NutkaTimerStyle`).
- Always go through `MaterialTheme.typography`; raw `fontSize` only for small
  one-off labels (10–14 sp).

## 4. Copy: short and simple

- Polish UI copy, max ~2–4 words per button ("Nagrywaj", "Zatrzymaj",
  "Wyślij do Notion").
- Long explanations NEVER live on the label — they live in the tooltip.

## 5. Tooltips explain on hover

- Wrap any non-obvious control in `HintTooltip("…")`
  (`ui/components/Tooltip.kt`). It shows on pointer hover (mouse/stylus) and
  while long-pressing on touch; taps pass through untouched.
- Tooltip text: one short sentence answering "what does this do / what happens
  if I tap".
- Do NOT wrap components that already consume long-press (delete-on-long-press
  rows, hold-to-2x play) — the gestures would fight.

## 6. Visualizations

- Data visuals (waveform bars, speaker timeline, speaker stats split, pulse
  rings) are drawn from real state (mic level, segment times, playhead) —
  never random.
- Style: rounded solid blocks/bars/strokes in the two speaker colors
  (accent = speaker A, accent2 = speaker B), neutral track behind
  (`neutral300`), thin dark playhead.
- Keep them calm: bounded amplitudes, spring smoothing, dimmed when paused.

## 7. Layout & navigation

- Edge-to-edge; anything anchored to the bottom (nav bar, toasts) must apply
  `navigationBarsPadding()` / `windowInsetsPadding(WindowInsets.navigationBars)`
  so it can never sit under the gesture bar.
- Nav items keep ≥ 48 dp touch targets; selected item gets the flat accent-tinted
  pill marker, not color-only changes.
- Corner radii come from `MaterialTheme.shapes` (10–30 dp) or explicit pills
  (999.dp). Cards: surface fill + divider border, radius ~20–24 dp.
