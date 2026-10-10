# Daytrace design system

One calm theme for the whole app, with **forest green** as the only primary colour. No per-tab colours.
Code lives in `app/src/main/java/dev/holdthisthought/ui/theme/`.

## Layers

| Layer | File | What it holds | Who uses it |
|---|---|---|---|
| 1. Tokens | `Tokens.kt` | Raw colours (`Forest`, `Stone`, `Earth`) and spacing (`Space`) | The theme, plus always-dark surfaces (hero, alarm, wind-down, widget) |
| 2. Roles | `Theme.kt` | Material colour scheme (light and dark), `KindPalette`, `GameColors`, typography, shapes | All screens, via `MaterialTheme.colorScheme`, `LocalKindPalette` and `LocalGameColors` |
| 3. Motion | `Motion.kt` | Durations, easings, springs, `Modifier.pressSquish()`, `Modifier.popIn()` | Every animation |

Screens use roles, not tokens. Reach for a token only on a surface that never follows light or dark mode, such as the forest hero.

## Colour

- **Primary is forest.** `Forest.F700` (#2C5546) in light mode and `Forest.F300` in dark mode. Primary containers are `F100` and `F700`. The hero gradient runs from `F700` to `F600`.
- **Neutrals are green-tinted stone.** The background is `Stone.S50` (#F4F6F2) in light mode and `S950` (#111814) in dark mode. Text is `S800`, with `S600` for secondary text. No pure white and no pure black, so it's easy on the eyes.
- **Supporting accents are quiet and earthy.** Each quest or thought kind gets one: Moss (fuel), Sky (thoughts and verdict), Ochre (learnings, rise and XP), Heather (wishes and reflect), and Forest (todo and move). No orange and no saturated colours.
- **Cards are flat.** They use `surfaceContainerLowest` with a 1dp `outlineVariant` hairline and no shadow.

## Spacing and shape

- Spacing uses a 4-point scale: `Space.xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 20, `xxl` 28. The screen gutter is `Space.gutter` (20).
- Shapes: small 14, medium 22, large 28, extraLarge 36. Cards use large.

## Motion: the 12 principles

| # | Principle | Where |
|---|---|---|
| 1 | Squash & stretch | `pressSquish()` on buttons, rings and emoji. Blip hops and pats keep their volume (wider when shorter). |
| 2 | Anticipation | Blip crouches before a hop and leans back before a spin. `Easing.anticipate`. |
| 3 | Staging | One hero moment at a time: celebrations overlay, and Blip is centred in the hero. |
| 4 | Straight ahead / pose to pose | Blip reactions are keyed poses with eased in-betweens (`pose()` in `Blip.kt`). |
| 5 | Follow-through & overlap | Blip's leaf sprout lags and settles with a damped wobble. Arms trail the body. Springs overshoot. |
| 6 | Slow in / slow out | `Easing.standard` by default. Only loops are linear. |
| 7 | Arcs | Hops, hearts and confetti travel on curves. |
| 8 | Secondary action | Blip blinks (sometimes twice), glances left and right, and breathes. |
| 9 | Timing | `Duration.short` 160 for small things up to `Duration.hero` 700 for celebrations. |
| 10 | Exaggeration | Landings squash to 0.8, so they read at widget size. |
| 11 | Solid drawing | One cel-shade and consistent proportions in every pose. |
| 12 | Appeal | A big round head, low wide eyes with two catch-lights, blush, a "w" smile and a leaf sprout. |

## Blip

Blip is drawn in code (`ui/components/Blip.kt`). The four widget poses (`res/drawable/blip_*.xml`) are generated from the same geometry, so keep the two in sync when Blip's proportions change.
