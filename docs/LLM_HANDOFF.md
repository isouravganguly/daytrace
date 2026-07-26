# LLM handoff notes

Last updated: 2026-07-26

Read this before changing code.

## User preference/context

- The user wants proactive building, not just abstract planning.
- Keep communication simple and concrete.
- The product should feel modern, clean, calm, and personal.
- Personal and Work spaces should currently share the same clean visual system.
- The user dislikes the circular Speak CTA and wants better button design.
- After every Android rebuild, reinstall the APK into the emulator.

## Current working directory

`/path/to/HoldThisThought`

## Important local files

- Product docs:
  - `docs/PROJECT_CONTEXT.md`
  - `docs/ARCHITECTURE.md`
  - `docs/IMPLEMENTATION_PLAN.md`
  - `docs/ROADMAP.md`

- Android:
  - `app/src/main/java/dev/holdthisthought/MainActivity.kt`
  - `app/src/main/java/dev/holdthisthought/ui/AppTheme.kt`
  - `app/src/main/java/dev/holdthisthought/ui/ThoughtViewModel.kt`
  - `app/src/main/java/dev/holdthisthought/ThoughtApplication.kt`
  - `app/src/main/java/dev/holdthisthought/data/Thought.kt`
  - `app/src/main/java/dev/holdthisthought/data/ThoughtRepository.kt`
  - `app/src/main/java/dev/holdthisthought/data/ThoughtDao.kt`
  - `app/src/main/java/dev/holdthisthought/data/SupabaseRemoteDataSource.kt`
  - `app/src/main/java/dev/holdthisthought/sync/ThoughtSyncWorker.kt`

- Supabase:
  - `supabase/migrations/202607250001_create_thoughts.sql`
  - `supabase/functions/thought-machine/index.ts`

## Build/install commands

Build:

```bash
./gradlew :app:assembleDebug
```

Install in emulator after every rebuild:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch:

```bash
adb shell monkey -p dev.holdthisthought 1
```

## Secrets and config

- Android reads `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from `local.properties` or Gradle properties.
- Do not print or commit secret values.
- `GEMINI_API_KEY` belongs in Supabase Edge Function custom secrets, not in Android.

## Known current state

- Android app builds and has been installed on an emulator previously.
- Supabase anonymous auth is enabled.
- Edge Function `thought-machine` has been deployed previously.
- Gemini enrichment has previously returned data and embeddings for saved thoughts.
- UI was recently improved with a clean neutral theme and consistent shapes.
- The voice CTA still needs redesign because the user does not like the circular Speak button.

## Good next coding task

Implement the new voice CTA:

- Replace the circle with a wide, modern press-and-hold button.
- Preserve the existing `SpeechRecognizer` behavior.
- Keep the interaction simple: press starts listening, release stops.
- Make states clear without clutter.
- Build and reinstall into emulator after changes.

