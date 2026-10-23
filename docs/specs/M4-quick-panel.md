# M4 — Quick panel + remote keys everywhere + Learn-a-button fix

Why: after the Home takeover the stock Google TV launcher is disabled, so the remote's Settings key (and Netflix/Prime/YouTube) do nothing inside other
apps, and the TCL side bar (launched by the launcher) is gone (`docs/ROADMAP.md` task 0). Idea: watch the system log for the key press, then show our
own panel over the video. Read `CLAUDE.md` and `docs/ROADMAP.md` (task list) first. Everything must be opt-in and undoable from the app.

## Facts from the owner's TCL 65Q6C log (Android 14)
- Every press is logged by tag `WindowManager`, e.g.
  `handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_NOTIFICATION, scanCode=240, ... repeatCount=0, ...}`
  (Settings key = code 83 = `KEYCODE_NOTIFICATION`; Netflix 4062, Prime 4099; vendor codes may print as a number). The foreground app also still receives the key.
- A normal app cannot read other apps' log lines unless `READ_LOGS` is granted over adb; the shell user can always read them (Afterglow already talks to
  `127.0.0.1:5555` with dadb).

## 1. Key source (`keys/KeyLogSource.kt`)
- `interface KeyLogSource { fun keyDowns(): Flow<Int> }` emits key codes of lines matching
  `handleKeyBeforeQueueing event=KeyEvent \{ action=ACTION_DOWN, keyCode=(\S+?),` with `repeatCount=0`. Accept a number or a `KEYCODE_...` name
  (`KeyEvent.keyCodeFromString`). Unit-test the parser with the real lines above.
- Two implementations, same parser: `LocalLogcat` (`Runtime.exec("logcat -v brief -T 1 WindowManager:D *:S")`, needs `READ_LOGS`) and `AdbLogcat` (same
  command over dadb as shell, no extra permission). Use Local if it yields a line within 3 s of a self-test, else Adb. Show which is active in the diagnostics.
- Cheap: one process, only the WindowManager tag, drop everything else early, stop on `ACTION_SCREEN_OFF`, restart on `ACTION_SCREEN_ON`, no wake locks, no
  polling. Never loop-restart faster than every 5 s.

## 2. Service (`keys/KeyWatchService.kt`)
- Foreground service (type `specialUse`, one low-importance silent notification "Afterglow remote keys"), started from `HomeActivity` and a
  `BOOT_COMPLETED` receiver, only when the feature is switched on. Catch all errors, log to last-problem, never crash.
- Dispatch each key through one function `RemoteKeys.resolve(code, userMappings)`: user mapping first, then built-ins (Settings -> panel, Netflix, Prime).
  Launch other apps with `startActivity(NEW_TASK)`; this works from the background because of the overlay permission (section 4).
- When Afterglow itself is in front it also gets the key in `onKeyDown`: dedupe (ignore a repeat of the same code within 400 ms) so nothing fires twice.

## 3. The panel (`panel/QuickPanel.kt`)
- A `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`), right edge, 520 dp wide on the 1920 canvas, full height, dark glass `#0B0908` @ 92% with the amber glow
  at the bottom, tvFocus items, text 24 sp+. It must take D-pad focus (not `FLAG_NOT_FOCUSABLE`) but let the video keep playing behind it.
  Compose in a service window needs `LifecycleOwner`, `SavedStateRegistryOwner`, `ViewModelStoreOwner` on the ComposeView; re-apply the 1920 density.
- Settings key again, Back, or 10 s without input closes it and focus returns to the app. First item focused on open.
- v1 rows: **Volume** (left/right changes `AudioManager` STREAM_MUSIC, shows the level, steps of 1), **Mute**, **Picture** / **Sound** / **Network** / **Display**
  (open the matching stock Settings screen with the standard `Settings.ACTION_*` intents, then close the panel), **All settings**, **Afterglow settings**.
  No fake picture-mode controls: those are TCL private (list in "Not done").
- Panel also opens from Afterglow's own Settings key (replaces opening full-screen Settings there).

## 4. Setup page (Settings > Afterglow settings > Quick panel)
- Switch "Quick panel and remote keys everywhere". Turning it on runs, over the existing adb: `appops set com.nbansal.afterglow SYSTEM_ALERT_WINDOW allow`
  and `pm grant com.nbansal.afterglow android.permission.READ_LOGS` (add these two exact strings to `AdbWhitelist` with strict patterns + unit tests; package
  name is Afterglow's own, nothing else). Turning it off stops the service and runs `appops set ... SYSTEM_ALERT_WINDOW default` and `pm revoke ...READ_LOGS`.
  Granting READ_LOGS may kill the app: re-open the page and continue.
- Diagnostics block: active source (Local/Adb/none), service running, last 5 detected key codes with time ("press Settings now to test"). This is how the owner
  checks it without a laptop.
- If adb is off: show the same "turn on USB debugging" help the takeover page uses. Feature stays off, nothing breaks.

## 5. Learn-a-button fix (roadmap task 0b, from the 0.6.0 test)
- While the Learn screen is open, capture the key code BEFORE any mapping (a `learning` flag read in `HomeActivity.onKeyDown` and by the service), show the code.
- User mappings are checked before the built-ins; per-button "Reset to default" row. Unit-test: learn mode returns a built-in key's code; user mapping beats `RemoteKeys`.

## Acceptance
- `./gradlew testDebugUnitTest` once at the end; push once to `claude/afterglow-tv-launcher-g6ow3l`; no PR, no release.
- No new dependencies. No network. Update `docs/ROADMAP.md` (mark task 0 done, awaiting a TV test) in one edit.
- Owner test (put in the ROADMAP note): switch on, play YouTube, press Settings -> panel over the video, video keeps playing; Netflix/Prime keys launch the app from inside YouTube; Learn a button captures the Netflix key.

## Not done (expected; list honestly in the session's final reply)
Picture mode / sound mode / sleep timer controls (TCL private), HDMI-input check, YouTube key (the TV swallows it), other TV brands.

Build session notes: Picture and Display both open `Settings.ACTION_DISPLAY_SETTINGS` (there is no standard picture-settings action); Network tries
Wi-Fi, then wireless settings. The first log line (`-T 1` backlog) is dropped so an old press never fires. If granting READ_LOGS fails after the overlay
was allowed, the switch stays on and the Adb source is used. Not tested on a TV (no device in the build session).
