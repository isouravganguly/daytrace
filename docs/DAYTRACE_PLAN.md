# Daytrace — product and architecture plan

Last updated: 2026-10-10. Single-user app. Status: phase 1 built; design v3 and nudges built (§8), §11 built (sleep rhythm, good night, phone-based sleep, bedtime lock, plant alarm); awaiting install on the phone.

Hold This Thought grows into **Daytrace**: one personal app that traces your day: thoughts,
desires, learnings, where your time went, what you ate, whether you moved, and how the day felt.
It collects with as little typing as possible. It turns the data into daily, weekly, monthly and
yearly reviews, so you can understand your life and optimise it later, without judging it.

---

## 1. Product principles

1. **Collect automatically first, ask second.** Every signal the phone can gather passively
   (screen time, steps, walks, sleep, later places) is gathered passively. Manual entry is reserved
   for what only you know: thoughts, mood, meals, the day's verdict.
2. **Ask only for the essential gap.** The app knows what is missing today, such as "no lunch
   logged" or "no verdict yet". It asks for one thing at a time, with one tap answers.
3. **Understand, don't judge.** Screen time is shown as "where your day went", never as a red
   warning. Streaks forgive: one missed day is fine. Language is curious, not scolding.
4. **Make it fun to feed.** Logging earns XP. Days have quests. Streaks and badges reward
   consistency. Celebrations are small, quick and never block you.
5. **Your data, one place, portable.** Everything lives in your one Supabase project with Row
   Level Security, and is exportable as Markdown or JSON and readable by Claude through MCP.
   Moving off Supabase means moving one Postgres database.
6. **Local-first, never blocked by the network.** The phone works offline. Sync and AI catch up
   later.
7. **Health-grade privacy.** No third-party analytics SDKs. Device tokens are hashed on the
   server and excluded from cloud backups on the phone. Health data is never shipped to Gemini
   without an explicit feature that needs it.

## 2. What we trace

| Stream | How it's collected | Effort for you | Phase |
| --- | --- | --- | --- |
| Thoughts, learnings, todos | Voice/text capture (exists) | One tap + speak | done |
| **Desires** (things you want: to do, have, become) | New kind; AI-classified from capture | none extra | 1 |
| **Wake-up** | Home-screen widget "Good morning" → `daytrace /wakeup` | One tap | 1 |
| **Screen time per app** | `UsageStatsManager` collector → `daytrace /ingest` | none | 1 |
| **Mood** | 5-face check-in, optional note | One tap | 1 |
| **Meals** | Breakfast / lunch / dinner / snack chips + optional words | One or two taps | 1 |
| **Walk / movement** | Manual tick now; Health Connect steps + walks later | one tap → none | 1 → 2 |
| **Day verdict** | Evening: rate the day 1–5 + one line | Two taps | 1 |
| Steps, exercise, sleep | Health Connect (replaces deprecated Google Fit APIs) | none | 2 |
| Mac activity | Existing Mac collector → `daytrace /ingest` | none | done (other chat) |
| Places / trips | Visit detection (significant places, not raw GPS trails) | none | 4 |
| Calendar | Read-only calendar events as context for reviews | none | 4 |

## 3. Experience design

### 3.1 Identity

- **Name: Daytrace.** The launcher label becomes "Daytrace". Capturing is still "hold this
  thought", the verb inside the app. (It's a single string to change if you'd prefer the old name.)
- **Logo:** a rising sun over a traced line (a sparkline of the day), with a small dot that marks
  "now". Coral-to-amber sunrise gradient on a cream tile, with a monochrome layer for Android
  themed icons.
- **Palette:** Sunrise coral (primary), Sky blue, Mint, Sun yellow, Grape. Warm cream background
  in light mode; deep ink-navy in dark mode. Each stream has its own colour: thought = sky,
  learning = sun, todo = mint, desire = grape, time = coral.
- **Type:** bold rounded-feeling sans for numbers and headings (playful, game-like). The serif
  stays on the capture canvas, where writing should feel personal.
- **Motion:** springy progress rings, a floating "+15 XP" after each log, a short confetti burst
  when a quest or the whole day completes, light haptics. Everything under 600 ms and skippable.

### 3.2 Navigation (phone: bottom bar · tablet ≥ 600 dp: navigation rail)

1. **Today** (home): the game board for the day.
2. **Capture**: the existing voice/text canvas, now with a Desire kind.
3. **Library**: all thoughts, learnings, todos and desires; search by words or meaning.
4. **You**: level, badges, streak calendar, then settings (cloud account, daytrace device,
   permissions, export).

### 3.3 Today screen (top to bottom)

- **Header:** "Good morning", the date, and a streak flame with the day count.
- **Level bar:** level number and title (Spark → Noticer → Tracer → Pathfinder → Cartographer →
  Sage → …), with XP progress to the next level.
- **Today's quests:** five rings. Tap one to complete it in place:
  - **Rise:** wake-up logged (widget or here).
  - **Reflect:** at least one thought, learning or desire captured.
  - **Fuel:** at least two meals logged.
  - **Move:** walk logged (auto from Health Connect in phase 2).
  - **Verdict:** evening rating of the day (unlocks after 6 pm).
  - Completing all five gives a **Perfect day** bonus and a confetti burst.
- **Next best nudge:** one card with the single most useful missing thing right now, based on the
  time of day. For example: 1 pm with no lunch logged → "Lunch? 🍜" with chips.
- **Mood strip:** five faces, one tap.
- **Where your day went:** total screen time, a 24-hour timeline strip coloured by app, and the
  top five apps with minutes. Neutral colours, no judgement.
- **Today's captures:** a compact list of what you held today.

Tablet: two columns. Quests, nudge and mood sit on the left; time and captures sit on the right.

### 3.4 Gamification rules (computed, never stored)

XP and badges are **derived from your data every time**, by a pure function. They can't drift,
they survive reinstall and sync, and the rules can be tuned later without migrations.

| Action | XP |
| --- | --- |
| Wake-up logged | 20 (+10 if before your target, default 7:30) |
| Thought captured | 10 |
| Learning captured | 15 |
| Desire captured | 10 |
| Todo completed | 10 |
| Mood logged | 5 (once per 3 hours counts) |
| Meal logged | 5 (max 4/day) |
| Walk logged | 15 |
| Day verdict | 20 |
| Perfect day (all quests) | 50 |

- **Level curve:** level n needs `50·n·(n+1)` total XP (100, 300, 600, 1000, …).
- **Streak:** consecutive days with at least 3 of the 5 quests done. Today doesn't break the
  streak until it's over. One missed day per rolling 7 days is forgiven automatically (a
  "streak shield").
- **Badges (first set):**
  - First Light: first wake-up.
  - Early Bird: 7 wake-ups before target.
  - Curious Mind: 10 / 50 / 100 learnings.
  - Wishmaker: 10 desires.
  - Well Fed: meals logged 7 days in a row.
  - Wanderer: 10 walks.
  - Week Warrior: 7-day streak.
  - Monthly Marathon: 30-day streak.
  - Perfect Day.
  - Honest Mirror: 7 verdicts.

### 3.5 Gentle gate (phase 4, opt-in)

You suggested limiting the phone until essentials are entered. Android doesn't let a normal app
truly lock the phone, and a hard lock would become something you fight. The plan is an
**opt-in check-in gate**:
- At a chosen time (for example 9 pm), a full-screen reminder appears: "Close your day: mood ·
  meals · verdict".
- It takes about 15 seconds, and you can snooze it twice.
- It's configurable in settings and off by default.

## 4. Architecture

### 4.1 One app, local-first

```
Compose UI (Today · Capture · Library · You)   Glance widget
        │                                            │
   ViewModels ── TodayRepository / ThoughtRepository / CheckInRepository
        │                     │
      Room (source of truth): thoughts · entries · usage_intervals · wakeups
        │                     │
  SyncEngine (thoughts, JWT)  DaytraceUploader (device token)    UsageCollector
        │                     │                                   (UsageStatsManager)
  Supabase public.*           Edge fn `daytrace` → schema daytrace.*
```

- **Two auth paths, deliberately:** thoughts use your Supabase account (JWT + RLS). Daytrace
  streams use a **per-device token** (`x-device-token`), as the existing backend does, so
  headless collectors (Mac, phone workers) never need your password. Phase 3 links devices to
  your user id, so one review can join both.
- **GamificationEngine:** a pure Kotlin object. Its input is facts (wake-ups, thoughts, entries,
  date and zone). Its output is XP, level, quests, streak and badges. It's unit-tested.

### 4.2 New local tables (Room v3, additive migration)

| Table | Columns | Notes |
| --- | --- | --- |
| `entries` | id (uuid), type (MOOD/MEAL/WALK/VERDICT), value (text), note, occurredAt, createdAt, updatedAt, deletedAt, syncState | Generic check-ins. New types later are additive (WATER, WEIGHT, …). Phase 2 syncs to `public.entries`. |
| `usage_intervals` | id, packageName, app (label), startedAt, endedAt, uploaded | Unique (app, startedAt). Feeds "where your day went" and the upload queue. Pruned after 35 days once uploaded. |
| `wakeups` | day (yyyy-MM-dd, PK), tappedAt, wokeAt, greeting, state (PENDING/LOGGED) | Drives the widget, the Rise quest and wake-up XP. |

`ThoughtKind` gains `DESIRE`. The server check constraint is widened and `thought-machine` learns
to classify desires: "I want / wish / would love to / someday…".

### 4.3 Usage collector (spec from the daytrace brief)

- `UsageStatsManager.queryEvents(cursor, now)` reads ACTIVITY_RESUMED / ACTIVITY_PAUSED,
  SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE and KEYGUARD_SHOWN.
- **IntervalBuilder** is a pure, unit-tested state machine:
  - RESUMED(p) opens p, closing any other open app at that moment.
  - PAUSED(p) marks p as "pausing". A RESUMED of the same app within 3 s continues the same
    interval, so activity switches inside one app don't split it.
  - Screen off or lock closes the open interval at that moment.
  - Intervals under 2 s are dropped. The launcher and System UI are ignored.
- **State persisted between runs:** the event cursor and the open interval. The first run
  backfills the last 7 days.
- **Labels:** `PackageManager` labels. Launcher-app visibility comes from a `<queries>`
  declaration, with no `QUERY_ALL_PACKAGES`.
- **Upload:** batches of up to 1000 to `/ingest`, which is idempotent server-side.
  - Rows are marked uploaded only after a 2xx.
  - A 401 stops retrying and shows "Token rejected" in settings.
- **Scheduling:** periodic WorkManager every 15 min (network needed only for the upload step),
  plus `BOOT_COMPLETED` and an app-open refresh.

### 4.4 Wake-up widget (Glance)

- Before tapping: a sun icon, a "Good morning" button and the date.
- Tap → save a local `wakeups` row (PENDING, `tappedAt`) → expedited WorkManager job with a
  network constraint → `POST /wakeup` → store `greeting` + `woke_at` → refresh the widget.
- The rest of the day shows the greeting, "Up at 6:42", streak 🔥n and today's quest count.
- Offline shows "Saved · will sync" with the tap time. It refreshes at midnight via the periodic
  worker.
- **Backend gap (proposed, not yet applied):** `/wakeup` uses server time, so an offline tap that
  syncs later logs a late time. The proposal: accept an optional body `{"at": ISO}`, trusted when
  it's within the last 12 h.

### 4.5 Permissions and onboarding

A "Set up tracing" checklist card appears on Today until everything is done:
- **Usage access:** `ACTION_USAGE_ACCESS_SETTINGS`, with a granted/not-granted state that
  re-checks on resume.
- **Device token and name:** entered in You → Daytrace device.
- **Add the widget:** `requestPinAppWidget`.
- **Notifications** (phase 2, for evening nudges).
- Phase 2 adds Health Connect permissions.

### 4.6 Security

- The device token lives in app-private prefs. A `data_extraction_rules.xml` file excludes it
  from cloud backup and device transfer. It's never logged and shown masked.
- Endpoint URL default is `https://<project>.supabase.co/functions/v1/daytrace`, editable.
- Cloud data in health-adjacent tables (`entries`, Health Connect data) gets RLS and per-user
  ownership. No service-role keys on devices.

## 5. Delivery phases

### Phase 1 — Merge + game board (this session)

1. Plan doc (this file).
2. Room v3: `entries`, `usage_intervals`, `wakeups`, and the `DESIRE` kind. Server: widen the
   kind constraint and teach `thought-machine` about desires.
3. Daytrace data layer: settings, HTTP client (`HttpURLConnection`), IntervalBuilder, usage
   collector, uploader, workers, boot receiver.
4. Glance wake-up widget.
5. GamificationEngine plus tests.
6. New theme, logo, adaptive navigation (bottom bar / rail).
7. Screens:
   - Today (quests, nudge, mood, meals, time breakdown).
   - Check-in sheets.
   - You (level, badges, settings incl. daytrace device + usage access).
   - Capture/Library touched up for the new look plus desires.
8. Build, unit tests, install on phone and emulator, verify against the live endpoint with your
   token.

### Phase 2 — Health and sync

- Health Connect: steps, walking sessions and sleep, mapped to the Move quest automatically.
- `public.entries` table with RLS, plus generalising SyncEngine to sync check-ins.
- Evening notification nudges (one per gap, max two a day).
- Proposed `/wakeup` `at` field (§4.4).

### Phase 3 — Reviews and insight

- In-app **Day / Week / Month / Year** review screens:
  - time by app/category
  - wake-up trend
  - mood vs. sleep/steps/screen time
  - learnings list
  - desires that keep recurring
- Server **digest** job (Edge Function + Gemini, scheduled): writes a Markdown review per period
  to a `reviews` table, readable in the app and through MCP.
- **Data-quality report:** what was gathered, which gaps there are ("no meals Tue–Thu", "phone
  collector silent 2 days") and one suggestion to improve collection.
- MCP tools: `day_summary`, `time_breakdown`, `checkins`, `review`.
- Link daytrace devices to the user id.

### Phase 4 — Patterns and context

- Places (visit-level), calendar context, app categories.
- Pattern detection, for example a repeated trip or a recurring late night before a low-mood day.
  Suggestions are phrased as questions, never as rules.
- The opt-in gentle gate (§3.5).

## 6. Acceptance checks for phase 1

- [ ] Existing thoughts survive the upgrade (Room 2 → 3 migration test on device).
- [ ] With usage access granted and a valid token, the 15-min worker uploads intervals, and the
      rows appear in `daytrace.app_intervals` with app labels, none under 2 s.
- [ ] A bad token shows "Token rejected" and doesn't retry endlessly.
- [ ] Widget: a tap shows "Good morning" and the time, a second tap says "Good morning again", the
      greeting is still shown hours later, and the airplane-mode tap shows pending and then logs.
- [ ] Quests, XP, level and streak update instantly after each log, and each completion
      celebrates once.
- [ ] Today and You lay out in two columns on a tablet-width screen. Nothing is clipped at
      phone width.
- [ ] Unit tests pass for IntervalBuilder and GamificationEngine.

---

## 7. Design v2 — "Electric sky" (supersedes the palette and logo in §3.1)

Feedback on v1: it looks good but should be more fun, modern and striking, with more
gamification, characters (3D if possible), a cohesive palette led by sky blue and electric blue,
and a minimal Nike/Apple-style logo in those blues.

### 7.1 Palette (one family, one warm pop)

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| Electric blue (primary) | `#2F5BFF` | `#5B7CFF` | buttons, selected states, hero gradients |
| Sky blue (secondary) | `#4FC3FF` | `#6FD0FF` | highlights, progress, the mascot's glow |
| Deep ink | `#0B1026` | `#070B1F` (background) | text; dark mode is a deep navy "night sky" |
| Cloud | `#F4F8FF` (background) | — | light background, cool white |
| Gold (XP, the only warm pop) | `#FFC53D` | `#FFD166` | XP, level-ups, perfect days |
| Flame | `#FF7A1A` | `#FF9447` | streaks |

Quest accents stay in the blue family, with one warm exception for the sun:
- Rise: gold `#FFB020`
- Reflect: electric `#2F5BFF`
- Fuel: sky `#00B8FF`
- Move: aqua `#14D9A0`
- Verdict: violet `#8B5CFF`

Thought kinds re-map to the same family.

Hero surfaces use an **electric → sky gradient** with a soft glow. Dark mode is the showcase:
neon-blue glows on night navy.

### 7.2 Logo

A cute, minimal sun: a gold (`#FFD23F`) circle with eight bold, rounded rays, and no face. It
sits on an electric-to-sky blue gradient tile. The monochrome layer is the same sun, for
Android themed icons. (It replaced a "leaping swoosh" draft, which was rejected.)

### 7.3 Characters: "Blip"

**Blip** is a glossy, clay-style 3D-looking orb mascot who lives on Today and reacts to your day.

- **Look:** it's rendered in Compose with layered gradients (core, rim light, specular highlight,
  contact shadow), so it looks 3D without a 3D engine or downloaded assets. It has big eyes,
  blinks and gently bobs.
- **Moods (derived, never stored):**
  - *sleepy*: morning, and no wake-up logged yet.
  - *cheerful*: the default.
  - *curious*: there's a nudge pending.
  - *excited*: right after a log.
  - *proud*: a perfect day, or a streak of 7 or more.
- **Evolution by level:**
  - L1: a plain orb.
  - L2: grows a glowing antenna.
  - L4: gets little feet.
  - L6: wings.
  - L8: a gold halo.

  Levelling up is something you can see happen to Blip.
- **Talks:** a speech bubble carries the next nudge in Blip's voice ("Morning! Tap me when you're
  up ☀️"). Tapping Blip runs the nudge's action, with a squash-and-stretch response.
- **Celebrates:** Blip jumps into the confetti on quest completions, and level-ups get a
  full-screen "Blip evolved!" moment.
- **Also on the widget:** a static Blip face, sleepy before your tap and smiling after.

True 3D option (needs your OK to download assets): Microsoft's open-source *Fluent Emoji 3D*
(MIT licence) could replace the emoji used for moods, meals and badges with 3D-rendered PNGs.

### 7.4 More game

- **Daily XP goal:** 50 XP a day, shown as a ring around Blip. Hitting it gives a gold glow.
- **Weekly challenges (Mon–Sun):** three per week, chosen from your weakest stream. For example:
  "Walk on 4 days", "Log 10 meals", "Capture 5 learnings", "Wake before target 3 times".
  - +100 XP each.
  - Derived from data, like everything else.
- **Badge unlocks** celebrate in the toast ("Badge unlocked: First Light 🌅").
- **Level-up moment:** a full-screen card with the evolved Blip, its new title and confetti.

### 7.5 Build order

1. Palette and theme tokens (light and dark).
2. Logo and adaptive icon.
3. Blip renderer, moods and evolution.
4. Hero card (Blip + XP ring + speech bubble), replacing the level card.
5. Weekly challenges and daily goal in the GameEngine, with tests.
6. Level-up dialog and badge toasts.
7. Widget restyle with Blip.
8. Install on the phone and check light and dark mode.

---

## 8. Design v3 — softer, cuter, and nudges that keep you coming back

Feedback on v2:
- The colours strain the eyes.
- Blip should be cuter, with hands and legs, and a matte finish rather than gloss.
- The user wants to interact with Blip, including on the widget.
- It's easy to lose interest or forget to log, so notifications should keep people engaged and
  excited.

### 8.1 UI review: what strains the eyes in v2

| Problem | Fix |
| --- | --- |
| Large saturated electric-blue areas (hero, buttons) | Softer, lower-saturation accents; hero becomes a pale tinted card with a coloured character |
| Pure white cards on a blue-white background, near-black text | Tinted off-white cards, softer ink `#2A3042`, and less contrast on large surfaces |
| Black/ExtraBold headings everywhere | Bold headings only; regular weight elsewhere |
| One blue for everything, so tabs blur together | **One soft colour per tab** (below) |
| Deep navy dark mode with neon glows | Soft charcoal dark mode `#16181F` with muted accents |

### 8.2 Tab colours (soft, distinct, same lightness)

| Tab | Colour | Hex (light / dark) | Why |
| --- | --- | --- | --- |
| Today | Soft sky | `#4F8FD8` / `#8FBDEB` | Morning, the day ahead |
| Capture | Lavender | `#8270D6` / `#B6A9EE` | Thinking, ideas |
| Library | Sage | `#3E9A80` / `#86CDB6` | Calm, growth of knowledge |
| You | Apricot | `#D9825B` / `#F0B394` | Warmth, the person |

Each tab's screen gets its colour as the Material `primary` (buttons, chips, headers). The
bottom bar tints the selected tab in that colour too. Gold stays reserved for XP.

### 8.3 Blip v3: cute, matte, with arms and legs

- **Style:** flat 2D with soft cel shading, in the spirit of Google's and Microsoft's modest
  illustration styles. No specular gloss. A rounded bean body in soft sky blue, with a slightly
  darker shade on one side for volume, little arms and stubby legs, dot eyes, blush cheeks and
  a tiny smile.
- **Alive:**
  - idle bob and blink
  - an occasional wave
  - arms swing when it hops
  - leans toward where you touched it
- **Interactions:**
  - **Tap:** a random reaction (wave, hop, spin, giggle) with a short line ("Hehe!", "Hi you!").
  - **Long press:** a pet, with happy closed eyes, blush and floating hearts.
  - **Double tap:** a little dance.
  - Taps are for play. The nudge's actions are the buttons under the speech bubble, so a
    playful tap never logs anything by accident.
- **Evolutions:**
  - L2: a sun antenna.
  - L4: a scarf.
  - L6: a crown.
  - L8: sparkles.
- **Widget:** Blip appears in each pose (sleepy, waving, happy, cheering). Tapping Blip on the
  widget makes it react: it cycles its pose and says a new line. The widget shows the one
  right-now action and logs it in one tap without opening the app:
  - Good morning
  - Lunch? Yes
  - Mood faces

### 8.4 Notifications that work with short attention spans

The principles are in this order:
1. **One tap from the notification.** Action buttons log directly, so there's no app to open
   and nothing to forget halfway.
2. **Novelty.** Blip's voice, with many rotating lines per nudge, so they don't fade into
   background noise.
3. **Instant reward.** After a log, the notification changes to "+5 XP 🎉 Blip is dancing", then
   disappears on its own.
4. **Only when needed.** No nudge for something already logged. A daily cap, quiet hours, and
   never two within 45 minutes.
5. **No guilt.** Streak-at-risk messages are framed as an opportunity ("2 quick taps keep your
   🔥5 alive"), never as failure.

| Nudge | When (if not done yet) | One-tap actions |
| --- | --- | --- |
| Good morning | 07:30–10:30 | "I'm up ☀️" |
| Breakfast / lunch / dinner | ~09:30 / ~13:30 / ~20:00 | "Yes 🍽️" · "Skipped" |
| Mood check | once mid-afternoon, at a slightly random time | 🙁 · 😐 · 🙂 |
| Walk | ~17:30 | "Walked ✅" |
| Close the day | ~21:30 | "Rate my day" (opens the verdict sheet) |
| Streak at risk | ~20:30, if fewer than 3 quests done and the streak is ≥ 2 | opens Today |
| Almost there | a weekly challenge is 1 step from done | opens Today |

- **Intensity setting:**
  - Gentle: up to 3 nudges a day.
  - Balanced (default): up to 5.
  - Hype: up to 8, including "almost there" and streak boosts.
- **Settings:** quiet hours default to 22:30–07:00. All of this is in You → Nudges, with a
  master switch.
- **Engine:** a pure `NudgeScheduler` decides what's due from the time, today's data and what was
  already sent today. It's unit-tested. The existing 15-minute background worker asks it each
  run, so there are no exact alarms and no battery-heavy services. Android 13+ asks for
  notification permission from the setup card.

### 8.5 Build order

1. Soft palette and per-tab colours, including dark mode.
2. Blip v3 renderer and interactions.
3. Widget poses and one-tap widget actions.
4. Notification scheduler, actions and settings, with tests.
5. Install on the phone, then check light and dark.

### 8.6 Revision: one earthy theme

The user's feedback on the §8.2 tab colours: keep the theme the same across tabs, softer, with
dull, earthy accents (forest green, sky blue) and no orange.
- The whole app now shares one stone/sage-neutral theme: background `#F1F2EE`, ink `#2E332F`,
  dark mode `#171A18`.
- Tabs only swap a dull accent: Today sky `#5F88AE`, Capture moss `#6F8455`, Library forest
  `#4C7259`, You slate `#6E7C8A`.
- Game accents are ochre, olive, forest, sky and dusty periwinkle. XP is muted ochre-gold. Blip
  is a dusty sky blue.

---

## 9. Phase 2a — Health Connect (steps, walks, sleep, meals)

The Google Fit APIs are being shut down. **Health Connect** is Android's built-in health data
store. Apps like Google Fit, Samsung Health, Mi Health and MyFitnessPal can all write to it.

### 9.1 What we read (read-only, the last 7 days, every run)

| Health Connect record | Becomes | Effect |
| --- | --- | --- |
| `StepsRecord` (aggregate `COUNT_TOTAL`, deduplicated across apps) | `steps` per day | Shown on Today. **Move** is done at 7,000 steps |
| `ExerciseSessionRecord` (walking, hiking, running) | `walkMinutes` per day | **Move** is done at 10+ minutes; it counts as a walk for XP and badges |
| `ExerciseSessionRecord` (any type) | `exerciseMinutes` | Reviews later |
| `SleepSessionRecord` ending that day | `sleepMinutes`, `sleepStart`, `sleepEnd` | "Last night: 7 h 20 m" on Today; wake-up trends later |
| `NutritionRecord` (meal type) | `meals` (slots), `calories` | **Fuel** fills from MyFitnessPal with no tapping; the meal nudges skip slots already logged |

Manual check-ins still work and merge with these: a meal slot counts once, whichever source it
came from.

### 9.2 Architecture

- `health/HealthConnectSource` wraps `HealthConnectClient`: availability, permissions, and a
  per-day read.
- `health_days` is a new Room table (v4 migration, additive). `HealthRepository.sync()` refreshes
  the last 7 days on app open and from the 15-minute worker. Background reads use Android 14+'s
  "read in background" permission; without it, data refreshes when the app opens.
- GameEngine and NudgeScheduler take health days as input, so quests auto-complete and nudges
  never ask about what Health Connect already knows.
- **Backup:** a new `daytrace.health_daily` table (one row per device per day, upserted),
  `daytrace.ingest_health(token, days)`, and a `POST /health` route on the `daytrace` function.
  It uses the same device token as screen time and is idempotent.
- **Privacy:**
  - Read-only permissions, nothing written back.
  - Only daily aggregates leave the phone: no raw heart rate, no locations.
  - Health Connect's required "why we need this" screen explains each permission.

### 9.3 UX

- Setup card: "Connect Health Connect". One system sheet with the toggles.
- Today: a **Body** card with a steps ring (goal 7,000), walk minutes, last night's sleep and
  calories when present.
- You → Health: connection status, last sync, "Sync now", "Manage permissions".
- Blip reacts when steps finish the Move quest on their own (celebration as usual).

---

## 10. Fitness first, zero setup (feedback round 6)

The user's feedback, in their words:
- "My most important goal right now is to get fit."
- The app still shows plumbing (setup banner, device name, endpoint URL, cloud login, sync
  buttons). Make it user-focused.
- Don't overwhelm me on first launch; I can explore the extra data later.
- The widget should be modern and clean: how many items are left and what they are, with
  progress bars.
- Blip's hands should sit behind the body.

### 10.1 No more device tokens for the user

The phone gets its own token automatically:
1. The user signs in once with their email account.
2. The app calls `POST /daytrace/register` with the session.
3. The function checks the account is the owner (`daytrace.owners`), creates or rotates this
   install's device row (`devices.install_id`, `devices.user_id`), and returns the token.
4. The token is stored silently. The endpoint, device name and token fields move into a
   collapsed **Advanced** section.

### 10.2 First launch: three short steps, then a calm Today

1. **Hi, I'm Blip.** Pick a daily step goal: 5k / 7k / 10k (default 7k).
2. **Let me count your steps.** Connect Health Connect (one system sheet). Can be skipped.
3. **Keep your progress safe.** Sign in once, for backup and syncing your phone's data. Can be
   skipped.

Notifications are asked in context the first time Blip has a nudge. The widget is offered with
a one-line card on Today the first time. Usage access (screen time) is not part of onboarding: it
is an optional extra under **You → Screen time**.

### 10.3 Today, reorganised around fitness

- **Hero:** Blip plus a speech bubble with the one next thing to do, a big **steps ring** toward
  the goal, and "2 of 5 done" with five quest dots.
- **Quests:** the five rings, tap to log.
- **Quick log:** mood faces and meal chips.
- **"More about your day"** is collapsed by default: weekly challenges, body details (walks,
  sleep, calories), screen time, today's captures.
- Removed from Today: the setup card and the separate wake-up card (the wake time moves into
  the hero subtitle).

### 10.4 You, reorganised

1. Profile: level, stats, calendar, badges.
2. **Goal:** the steps goal chips.
3. **Health:** status plus "Manage in Health Connect". No "Sync now": reads are automatic on
   open and every 15 minutes.
4. **Nudges:** switch and intensity. No test button.
5. **Account:** "Backed up as …" or Sign in.
6. **Advanced** (collapsed):
   - device and endpoint
   - screen time access
   - cloud sync details
   - export (kept until the server-side export is in place)
   - about

### 10.5 Fitness mechanics

- **Step goal:** the user's daily target (default 7,000) completes **Move**, as does a 10+
  minute walk.
- **Walk nudges** quote what's left ("1,800 steps to go: a 15-minute walk does it").
- **New weekly challenges:** "Hit your step goal on 4 days" and "Walk 3 times".
- **Widget:** a steps progress bar plus five quest dots and the single next action.

---

## 11. Sleep: your rhythm, good night, bedtime lock, plant alarm (round 7)

A typical late-night rhythm: Good morning around 10:00, breakfast 11:00, sleep around 02:00.
People often drift off with the phone still in hand, so sleep has to be inferred. Asks:
- nudges at their own times;
- a "Good night" action, or automatic sleep detection;
- a phone lock after 02:00 with only essentials;
- a 9:30 alarm that only stops for a photo of a plant.

### 11.1 Built

- **Logical day 04:00 → 04:00** (`DayClock`). A 1 AM verdict or 2 AM good night counts for the
  day being finished.
- **My day** (You → Nudges): wake 10:00, breakfast 11:00, lunch 14:00, walk 18:00, dinner 21:00,
  bedtime 02:00, all editable.
  - Every nudge window opens at the user's time.
  - Quiet hours run from an hour after bedtime until wake-up.
  - The early-bird bonus uses the wake time.
- **Good night:** a nudge at bedtime, a hero button and a widget button, all one tap (+10 XP).
  After it, Blip stops suggesting things for the day.
- **Sleep from phone use** (`SleepEstimator`): the longest gap of 3 hours or more with no app in
  the foreground, from 20:00 to 15:00. Glances under 3 minutes are ignored, and falling asleep on
  a call with the screen off counts as asleep. It's used when Health Connect has no sleep data,
  shown on the hero ("😴 Slept 7 h 30 m") and the Body card, and backed up with the daily health
  totals.

### 11.2 Bedtime lock (built; the user chose the suggested escape hatch)

From bedtime (02:00) until wake-up, opening anything other than allowed apps shows Daytrace's
**Wind-down** screen instead:
- **Allowed by default:** Phone, Messages, Clock, Daytrace, Settings and emergency apps; the list
  is editable.
- **What it shows:** sleepy Blip; ideas such as "read a physical book", "4-7-8 breathing",
  "write down what's on your mind" (which opens Capture) and "listen to a sleep story"; and a big
  **Good night** button.
- **Escape hatch:** hold a button for 10 seconds to unlock for 10 minutes, at most twice a night.
  Every unlock is logged so you can see it in reviews.

How it works: an Android Accessibility service notices when another app comes to the front and
brings up the Wind-down screen. This is how Digital Wellbeing-style blockers work.
- It needs one sensitive permission: Settings → Accessibility → Daytrace. Android warns that the
  app can see what's on screen.
- Daytrace would read only which app was opened, never screen content, and nothing leaves the
  phone.

The alternative with no code is the phone's own **Digital Wellbeing → Bedtime mode / Focus mode**
on a schedule. It's less flexible and has no Blip screen.

### 11.3 Plant alarm (built: 1 minute on, 1 minute off, 30-minute cap; default 9:30)

1. **Rings** at the wake time (default 9:30), even on silent or Do Not Disturb, at alarm volume
   that ramps to maximum. The full-screen alarm shows over the lock screen.
2. **Stops only with a plant photo.** Recognition runs on the phone (ML Kit image labelling looks
   for labels like plant, flowerpot, houseplant, leaf), and the photo is never stored or
   uploaded.
3. **Rings in bursts:** 1 minute of ringing, a 1-minute pause, and again, until a plant is
   confirmed. As a safety cap it gives up after 30 minutes, and a notification says it gave up.
4. **The plant photo logs Good morning** (with the real time) and celebrates the Rise quest. The
   alarm then becomes your wake-up log.

It needs exact alarms (Android allows this for alarm-clock apps), full-screen alerts and camera
permission.
