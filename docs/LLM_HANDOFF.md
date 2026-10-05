# LLM handoff notes

Last updated: 2026-10-04

Read `PROJECT_CONTEXT.md`, then `ARCHITECTURE.md`, before changing code.

## User preferences

- Wants proactive building, explained simply and concretely.
- Personal, single-user app. No sharing or multi-tenant features.
- Calm, modern, warm UI. Work and Personal share one visual system.
- Does not want a circular speak button. Voice is a wide pill ("Hold to talk", or tap for hands-free).
- After every Android rebuild, reinstall the APK on the emulator (`Pixel_9_Pro_XL` AVD).

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

## Known state (2026-10-04)

- The v0.1 → v0.2 migration was tested on the emulator with real v1 data (8 rows preserved; NOTE→LEARNING).
- The Supabase project hostname did not resolve (most likely paused). Cloud features are
  untested against the new migration and function until it is restored and they are deployed.
- The emulator holds an anonymous session from v0.1. Use "Secure my account" in Settings to keep it.
