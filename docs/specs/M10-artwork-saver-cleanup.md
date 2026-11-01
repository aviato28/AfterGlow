# M10 — Artwork screensaver + dead code clean-up + speed groundwork (ROADMAP tasks 6, 7, part of 3)

Read `CLAUDE.md`, ROADMAP tasks 3, 6, 7. Read only `saver/`, `showcase/` (cache access), `keys/`, `panel/`, `app/build.gradle.kts`, the screensaver settings page, tests.

## 1. Artwork screensaver (task 6)
- A second DreamService `saver/ArtDream.kt` (declare in the manifest like `ClockDream`), drawn with a plain View (like ClockView, no Compose): cached Showcase
  backdrops from disk only (no network), one at a time, 20 s each, 1.5 s crossfade, slow drift (scale 1.04 -> 1.10), small clock bottom-right (ClockView style),
  dim at Night phase. Same `isMusicActive` -> `wakeUp()` rule as ClockDream. Decode at screen size RGB_565, max 2 bitmaps in memory.
- Screensaver settings: choose **Sunset clock** or **Artwork** (Artwork only offered when the Showcase cache has at least 3 backdrops); "Set as screensaver"
  writes the chosen component through the existing whitelisted `settings put secure screensaver_components` path (extend the strict pattern to allow
  `com.nbansal.afterglow/.saver.ArtDream`, test it).
## 2. Dead code clean-up (task 7)
- Remove code no longer reachable after M4-M7.1: unused icons/constants, the Local logcat reader and any `GrantLogs`/READ_LOGS remnants (keep the revoke-on-upgrade),
  the old list-style panel code, old Learn paths. Grep for references before deleting. No behaviour change. Keep tests green (update or delete tests of removed code).
## 3. Speed groundwork (task 3, part)
- Add `app/src/main/baseline-prof.txt` with rules for Home startup: `HomeActivity`, `AppModel`, `HomeScreen`/`ShowcaseHome` composables, `Glow`, `tvFocus`,
  the app repository (use `HSPLcom/nbansal/afterglow/**` style rules for those classes; profileinstaller is already a dependency so it ships).
- Add a `docs/SPEED.md` with the owner's measuring commands: `adb shell am force-stop com.nbansal.afterglow; adb shell am start -W -n
  com.nbansal.afterglow/.HomeActivity` (TotalTime = cold start) and `adb shell dumpsys meminfo com.nbansal.afterglow | findstr TOTAL`, plus a table to fill in.

## 4. Speed up list (task 9)
- Update `app/src/main/assets/debloat/tcl.txt` exactly as ROADMAP task 9 says (remove, re-word, add, with short reasons). Keep the header comment, keep everything
  unticked; update `SpeedUpTest` if it counts entries. Don't add any package on the "Never listed"/"NOT added" lists.

## Acceptance
No SDK here: be compile-careful. Push once to `claude/afterglow-tv-launcher-g6ow3l`, no PR, no release. One ROADMAP edit (tasks 6, 7 built; task 3 groundwork
done, measurements pending on the TV).
