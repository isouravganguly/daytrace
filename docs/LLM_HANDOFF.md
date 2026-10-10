# LLM handoff notes

Last updated: 2026-10-10

Read `PROJECT_CONTEXT.md`, then `DAYTRACE_PLAN.md` (the current product plan), then
`ARCHITECTURE.md`, before changing code.

## User preferences

- Wants proactive building, explained simply and concretely.
- Personal, single-user app. No sharing or multi-tenant features.
- The app is now **Daytrace**: one personal tracer for thoughts, learnings, desires, wake-up,
  screen time, meals, mood, walks and the day's verdict (health later; never finance).
- Wants it fun and gamified but **easy on the eyes**: soft, low-saturation palette with one colour
  per tab (Today sky, Capture lavender, Library sage, You apricot; gold only for XP). Blip is a cute,
  matte 2D character with arms and legs, playable (tap / double-tap / long-press) in the app and
  on the widget. See `DAYTRACE_PLAN.md` §8.
- Nudges must be ADHD-friendly: one-tap, varied, rewarding, capped and guilt-free (§8.4).
- Logo: a simple gold sun (circle and bold rounded rays, **no face**) on a blue gradient. The
  user rejected a swoosh and doesn't want eyes on the sun.
- Does not want a circular speak button. Voice is a wide pill ("Hold to talk", or tap for hands-free).
- After every Android rebuild, install the **release** build on the test phone over USB (Xiaomi
  phones need "Install via USB" turned on). It's R8-optimised and signed with the local debug key,
  so it installs over earlier builds and keeps data:
  `./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk &&
  adb shell cmd package compile -m speed-profile -f dev.holdthisthought`.
  Debug builds are several times slower and aren't representative. The release build isn't
  debuggable, so `run-as` database edits need a debug build.
- Performance budget and measurements: `docs/PERFORMANCE_PLAN.md`. Keep motion on `Motion` tokens
  (120–240 ms), and never run an always-on animation: Blip's idle clock sleeps between moments.
- Daytrace device tokens are entered by the user in You → Day tracing. Never hardcode, print or
  commit them.

## Commands

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p dev.holdthisthought 1
```

## Rules when changing code

- Changing the Room entity means bumping the DB version, adding a `Migration`, and committing the
  new `app/schemas/.../N.json`. Never use destructive migration; this is someone's memory.
- New user-editable fields go into `RemoteThoughtPush`. AI fields must not.
- Server schema changes go in a new file under `supabase/migrations/`.
- Never print or commit `local.properties` values or tokens.

## Known state (2026-10-10)

- v0.3.0: Room v3 (adds `entries`, `usage_intervals`, `wakeups`), DESIRE kind (server constraint
  widened, `thought-machine` v3 deployed). Installed on the phone; the 2 → 3 migration is
  additive and matches the exported schema.
- Daytrace backend (`daytrace` Edge Function + `daytrace` schema) was built in another chat. No
  device is registered yet; live upload/wake-up is unverified until the user enters a token.
- Check-ins (mood, meals, walks, verdicts) are local only. Syncing them is phase 2.
- v0.4.0: Health Connect (Room v4 `health_days`), `daytrace.health_daily` + `ingest_health()`,
  `daytrace` function v2 with `/health` (source now in `supabase/functions/daytrace/`). The phone
  has Google Fit (synced to Health Connect), Mi Health and MyFitnessPal. Usage access is granted.
  Health Connect is connected; it held only Google Fit steps on 2026-10-10.
- v0.4.x (§10): devices self-register via `POST /daytrace/register` after the owner signs in
  (`daytrace.owners` = the user's account), so there is no token UI. First-launch onboarding (goal,
  Health Connect, sign-in), fitness-first Today (hero with steps ring + quest dots, quick log,
  collapsed "More"), cleaned-up You (Advanced section), and a redesigned widget. The user still needs
  to sign in once on the phone (You → Account) for backup and device registration.
- Dark mode of design v2 has not been checked on a device.
- Cloud: 8 thoughts owned by 2 orphaned anonymous users; the user's email account exists. Ask
  before deleting anything.
- §11 (2026-10-10): logical day 04:00–04:00 (`DayClock`), "My day" schedule drives nudges (wake
  10:00 … bedtime 02:00), Good night entry (`EntryType.BEDTIME`), `SleepEstimator` (phone-use
  gaps) feeds `health_days` when Health Connect has no sleep, plant alarm (`alarm/`, ML Kit
  on-device labels, `TakePicturePreview` so no photo is stored), and bedtime lock (`bedtime/`,
  accessibility service, package names only, hold-10-s escape twice a night). Server: `/wakeup`
  accepts the tap time (`log_wakeup(token, at)`).
