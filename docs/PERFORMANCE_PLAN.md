# Performance plan

Goal: Daytrace should feel instant on a 2 GB RAM phone. That means a fast cold start, sharp
animations that never make you wait, and no work burning CPU when nothing is changing.

Each step is small, measured on the device and shipped separately. Cold start is measured with
`adb shell am start -W -S dev.holdthisthought/.MainActivity`, reading `TotalTime`, median of 5 runs.

## Findings

| # | Area | Finding | Impact |
|---|---|---|---|
| F1 | Build | The phone runs the **debug** build: no R8, no resource shrinking, debuggable. Compose in a debug build is several times slower, and `material-icons-extended` ships whole. | **High:** launch, scrolling, APK size |
| F2 | Build | No app-specific baseline profile, and a sideloaded APK isn't AOT-compiled until the phone is idle, so the first launches run interpreted. | High: launch, first scroll |
| F3 | Blip | Three infinite animations (`bob`, `blink`, `idle`) are read during composition, so Blip **recomposes every frame, forever**, wherever it's visible: the Today hero and the You hero. Each frame also allocates around 10 new `Path` objects. | **High:** CPU, battery, jank |
| F4 | Navigation | Tab switches use `Crossfade` (300 ms by default), which composes **both** screens for the whole fade, including two Blips. | Medium: tab switches feel slow |
| F5 | Motion | `popIn` waits 45 ms × order and then takes 450 ms with overshoot, replaying on every tab visit. The XP and goal bars take 900 ms and the Settings slide around 400 ms. | Medium: the UI feels laggy |
| F6 | Startup | `Application.onCreate` runs on the main thread. It builds the Supabase/Ktor client, initialises WorkManager to schedule two periodic jobs, schedules the alarm, and fires a sync and a collect. It does this on **every process start**, including background workers and widget updates. | Medium: launch time, background CPU |
| F7 | Data | `observeAllVisible()` is collected twice by Today (two Room queries plus two mappings per change). The game is recomputed from all history on every emission. | Low now; grows with data |
| F8 | APK | ML Kit's bundled model and native libraries for every ABI make the APK large; a bigger APK means slower install and page-ins. | Low to medium |
| F9 | Today first frame | Today renders nothing until the game state is computed, then pops in. | Low: perceived launch |

## Plan, in order

1. **Release build for daily use (F1, F2).**
   - Turn on R8 (`isMinifyEnabled`, `isShrinkResources`) with keep rules for Supabase/Ktor/serialization.
   - Sign with the local debug key, so it installs over the current app without losing data and no key is committed.
   - After each install, run `cmd package compile -m speed-profile` so the phone AOT-compiles right away.
2. **Sharp motion (F4, F5).** Speed up the motion tokens, with nothing over 250 ms in normal UI:
   - Tabs switch instantly, keeping their state via `SaveableStateHolder`, with no crossfade.
   - `popIn` takes 180 ms, staggers by 20 ms and moves 8 dp, and plays once per launch rather than on every tab visit.
   - Progress bars take 350 ms, the Settings slide 180 ms, and springs get stiffer.
   - Blip's reactions get about 25% shorter while keeping their poses.
3. **Blip without recomposition (F3).**
   - Read the animation values only in the draw phase, so Blip redraws without recomposing.
   - Reuse `Path` objects between frames.
   - Run the idle loops only while Blip is visible and the app is in the foreground.
4. **Lean startup (F6, F9).**
   - Create the Supabase client lazily, off the main thread.
   - Move WorkManager and alarm scheduling to a background coroutine, once per app version or boot.
   - Request sync and collect when the app opens, not on every process start.
   - Show Today's layout immediately and fill the numbers in.
5. **Data flow (F7).** Share one thoughts flow in Today and skip game recomputation when its inputs are unchanged.
6. **APK size (F8, done for ABIs).** Ship only `arm64-v8a` and `armeabi-v7a`, and consider unbundled ML Kit, where Play Services downloads the model.
7. **Later.**
   - A macrobenchmark module to generate a real baseline profile.
   - JankStats or `FrameMetrics` logging in debug builds to catch slow frames.

## Results

These are measured on the test phone: a release build, AOT-compiled, on a 120 Hz screen.

| Metric | Before | After |
|---|---|---|
| Cold start (`TotalTime`, ms) | 1,008–1,688 (debug) | **130–150** (steps 1 and 4) |
| APK size | 76 MB (debug) | **22.8 MB** (release, R8, ARM-only native libs) |
| CPU while Today sits idle | ~70% of a core | **~13%** average, 0% between Blip's moments |
| Frames drawn while idle on Today | ~240/s (every vsync) | ~23/s average, only during blinks, glances and waves |
| Frames with Blip scrolled off screen | ~60/s | **0** |
| Janky frames (idle) | 0% | <1% |
| Tab switch | 300 ms crossfade composing two screens | Instant, with state kept |
| Entrance animation | 450 ms + 45 ms stagger, every tab visit | 180 ms + 20 ms stagger, once per launch |

Notes:
- Blip had three effects stacked. It recomposed every frame (now it reads values in the draw
  phase), it re-recorded the whole screen every frame (now it has its own layer), and it animated
  continuously (now it's event-driven and pauses off screen or in the background).
- Sync and usage collection are now requested when the app opens, not on every process start, so
  the 15-minute worker no longer queues a second collection each time it runs.
- ML Kit no longer initialises at app start. It starts on the first plant photo.
