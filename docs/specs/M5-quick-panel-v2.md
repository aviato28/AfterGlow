# M5 — Quick panel v2 (reliability + new design + sleep + now playing) and Learn a button from the log

Read `CLAUDE.md`, `docs/ROADMAP.md` (task list), `docs/specs/M4-quick-panel.md`, `docs/specs/M4.2-quick-panel-reliability.md`. Visual reference:
`docs/design/v2/quick-panel.png` and its source `docs/design/v2/panel.html` + `base.css` (take sizes, colors, radii from there; 1 CSS px = 1 dp on our 1920 canvas).
Read only the code you need: `keys/`, `panel/`, `takeover/AdbShell.kt`, `takeover/Takeover.kt` (protected list), the Remote buttons + Learn screen, `HomeActivity.kt`, tests.

## 1. Everything in M4.2
No log-access popup (Adb reader only, revoke READ_LOGS), heartbeat watchdog, START_STICKY + restart from `HomeActivity.onResume`, panel-open try/catch, diagnostics.

## 2. New panel design (replace the list)
- Right-side glass panel 640 dp wide, 36 dp from the edges, radius 44, dark translucent fill (no real blur: low-end GPUs), subtle amber glow at its bottom.
- Top: clock (56 sp light) + "Quick settings". Then **Now playing** card (only when a media session exists), **volume** bar (left/right = 1 step, shows the
  number), then a 2-column grid of tiles (radius 30, icon, title, subtitle; "on" tiles amber-tinted; focused tile cream `#FFD9B0` with dark text and glow),
  footer "Afterglow settings" (focusable) and "Back to close". Simple vector icons as in the mockup.
- Tiles: **Sleep** (turn off now), **Sleep timer**, **Wi-Fi** (subtitle Connected / Off from ConnectivityManager), **Bluetooth**, **Sound output**, **Picture**,
  **Accessibility**, **All settings**. Settings tiles open the standard `Settings.ACTION_*` page (Wi-Fi, Bluetooth, Sound, Display, Accessibility, Settings;
  fall back to `ACTION_SETTINGS` if one doesn't resolve) and close the panel.
- **Sleep:** over adb `input keyevent KEYCODE_SLEEP` (add this exact string to `AdbWhitelist` + test).
- **Sleep timer:** OK cycles Off -> 15 -> 30 -> 45 -> 60 -> 90 -> Off; subtitle "Off in N min". Runs in `KeyWatchService` (end time saved in DataStore, re-armed
  after a service restart). 60 s before: a small overlay "TV turns off in 1 minute" with a focused **Cancel** button; then Sleep. Unit-test the cycle and re-arm.
- **Now playing:** a `NotificationListenerService` (`panel/NowPlayingListener.kt`, does nothing with notifications) only so `MediaSessionManager.getActiveSessions`
  is allowed. Grant on switch-on over adb: `cmd notification allow_listener com.nbansal.afterglow/com.nbansal.afterglow.panel.NowPlayingListener`; undo on
  switch-off with `disallow_listener` (both exact strings in the whitelist + tests). Card: art (session art or app icon), title, "app label", prev / play-pause / next
  via `TransportControls`. No session -> no card.

## 3. Learn a button from the log
- The Learn screen also listens to the key-watch flow (keys TCL handles itself, like Channel or YouTube, never reach the app). First non-navigation key from
  either path wins; ignore DPAD_*, DPAD_CENTER, ENTER, BACK from the log. Learned key code -> chosen app, as today.
- The service launches the mapped app for a learned code from any app (already does for built-ins). Diagnostics already show codes.
- Remote buttons page: if `com.tcl.channelplus` is installed, a switch **"Stop TCL Channel opening too"** (off by default): `pm disable-user --user 0
  com.tcl.channelplus` / `pm enable com.tcl.channelplus` through the existing whitelist forms; refuse if it's on the protected list. Undo is the same switch.

## Acceptance
`./gradlew testDebugUnitTest` once at the end (if no SDK locally, say so; CI runs on push). Push once to `claude/afterglow-tv-launcher-g6ow3l`. No PR, no release.
One ROADMAP edit: mark M4.2, the YouTube/Channel item and Afterglow 2 part (a) done, awaiting a TV test. List anything skipped under "Not done" below.

## Not done
- Not compiled or run: this container has no Android SDK, so `./gradlew testDebugUnitTest` was not run; CI builds on push. Riskiest APIs to check first:
  `androidx.compose.ui.graphics.vector.PathParser` (panel icons), `MediaSessionManager.getActiveSessions`, `NotificationListenerService` manifest entry.
- M4.2 was not in the code yet, so it was built here as part 1 (Adb reader only, `GrantLogs` and the Local reader removed, heartbeat + 75 s watchdog,
  restart on screen on / user present, START_STICKY, `HomeActivity.onResume` restart, diagnostics). READ_LOGS stays declared in the manifest only so it can be revoked.
- The built-in YouTube key mapping (ROADMAP item part 2) needs the key code, which is still unknown: Learn a button now catches it from the log instead.
- Bluetooth, Sound output and Picture subtitles are fixed text ("Open settings"): reading Bluetooth state needs a runtime permission, so it is left out.
- No unit tests for the panel UI, the now-playing poll or the overlay windows (need a device). Tested as pure logic: sleep cycle/re-arm, whitelist strings,
  reader source and heartbeat staleness, learn-from-log, the TCL Channel commands.
- Sleep timer: a timer whose end passed while the service was down is dropped (never turns the TV off at boot). The warning overlay has no Back handling; only Cancel.
