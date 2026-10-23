# Afterglow roadmap

Status: **M0, M1 and Chunk A (in-app Home takeover and Restore) done and tested on the TCL 65Q6C. Next: Chunk B (onboarding, speed-up, recents, polish).**

## What it is
A clean, fast home screen for Google TV / Android TV that replaces the stock Google TV launcher. Free, public, open
source, no ads, no tracking. Aimed especially at older and low-memory TVs where the stock home screen lags.
One look (no themes). Reference device: TCL 65Q6C.

## Decisions
| Topic | Decision |
|---|---|
| Distribution | GitHub Releases on this public repo (free CI minutes). Not Play Store (Play bans self-updating apps). Obtainium/F-Droid later maybe. |
| Updates of Afterglow | In-app: check GitHub Releases API (no token, public repo), download APK, install with PackageInstaller. Silent install when self-ADB is paired. |
| Updates of other apps | Left to the Play Store. Afterglow never installs other apps, so certified Netflix/Disney+/YouTube builds keep HDR, Dolby Vision, Atmos, 4K. Onboarding checks Play auto-update is on. |
| Replacing stock launcher | In-app wireless-debugging pairing ("self-ADB"), then `pm disable-user --user 0` the stock launcher. One-tap restore. Accessibility-service Home-button catcher as fallback for people who skip ADB. |
| Lost with stock launcher | Google's "Continue watching"/content rows and the Google TV notification panel. Owner OK with that. Settings and voice search stay; inputs via the remote's input key. |
| Settings | Stock Android TV settings (picture, sound, network). Afterglow only has its own small settings page (apps, takeover, updates, about). |
| Look | See Design below. Clock + time-of-day greeting. No weather. |
| Android version | 11+ only (minSdk 30): one pairing path, less code. |
| Licence | MIT. |

## Milestones
Each milestone gets a spec in `docs/specs/` written by the planning chat before building. Model per milestone in brackets.

- **Design, M0 Foundation, M1 Launcher** [done]: look, project and CI, home screen, apps, favourites, All apps, options popup,
  settings, updater, Outfit font. Takeover verified by hand on the TCL (`docs/TAKEOVER-TCL.md`).
- **Chunk A: in-app takeover and Restore** [Sonnet, one session]: `docs/specs/M2-takeover-in-app.md`. Done, tested on the TCL (released in 0.3.0).
- **Chunk B: onboarding, "Speed up my TV", recents in All apps, clock screensaver, polish** [Sonnet, one session]: `docs/specs/M3-onboarding-speedup.md`.
- **Chunk C: speed pass and 1.0** [Sonnet]: baseline profile, cold start under 500 ms, memory check on a 2 GB TV, README with screenshots
  and the TCL recipe, v1.0 release.
- **Clock screensaver:** designed with the owner (centered thin clock on the sunset glow, tiny half-sun horizon, dim and small at night) and built as Phase 5 of Chunk B (done, awaiting CI and a TV check).

Decided NOT to build: HDMI inputs row, weather, themes, settings backup for a second TV, an accessibility Home-key catcher as the main path.

## Task list (open items, newest first)
0. **Two bugs from the 0.6.0 TV test (owner). Not started.**
   a. **Settings key opens full-screen Settings; TCL's normal behaviour is a narrow side bar on the right**, so you can change picture/sound while the
      video keeps playing. Goal: same side bar. Unknown: which component draws it (likely part of the Google TV launcher we disabled, or a TCL/`com.android.tv.settings`
      panel activity). Step 1 (discovery, owner's laptop, no code): find the component. (i) `adb shell pm list packages | findstr -i "tcl tv.settings"`;
      (ii) with Google TV restored once, press Settings and read `adb shell dumpsys window | findstr mCurrentFocus` while the bar is open. Step 2: launch that
      component from `RemoteAction.TvSettings` with a plain `startActivity` (no new adb commands). If it lives in a package we disable (launcherx) it cannot
      be started: fall back to Android TV Settings panel activities if any, else keep full-screen Settings and say so on the Remote buttons page.
      **Discovery result (owner's laptop, 23 Oct):** NOT the side bar: `com.tcl.suspension/.ui.main.MainActivity` (input sources menu), `com.tcl.dashboard/.MainActivity`
      (TCL home page, opens at the right edge), `com.google.android.apps.tv.dreamx/.settingsv2.TwoPanelActivity` (screensaver settings). Broadcast
      `com.tcl.quickpanel.show.window` has no receiver. Most likely the bar is inside the Google TV launcher (`launcherx`) we disable. Also found: **with launcherx
      disabled, the remote's Settings and YouTube keys do nothing inside other apps** (only Afterglow sees keys, and only while on screen): a limit of the takeover itself.
      Also ruled out: `com.tcl.suspension` (MainActivity, InputActivity and the `com.tcl.quickpanel.show.window` service action all open the INPUT menu), launcherx
      (no panel activity, only a slice provider), `TOGGLE_NOTIFICATION_HANDLER_PANEL`. Log: in another app the Settings key (83, `KEYCODE_NOTIFICATION`) goes straight to
      the app and no system component reacts (`handleKeyBeforeDispatching keyCode=83 disable`). **PARKED: keep full-screen Settings. `tclhome_passive` is an empty stub (activity class missing). Untried: `com.tcl.tv`, `com.tcl.systemui.plugin`.**
      **ROOT CAUSE FOUND (restored-state log, 23 Oct):** the Settings key makes TCL's `TclShortCutKeyManager` start `launcherx/.dashboard.DashboardHandler`
      (action `android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL`) in the Google TV launcher package. That package is disabled by the takeover, so the call fails.
      Home candidates: launcherx HomeActivity priority 2, setupwraith RecoveryActivity 1, Afterglow 0, FallbackHome -1; highest wins, no chooser, and a normal app cannot
      raise its priority. So: **Afterglow as Home and the stock side bar cannot coexist on this TV.** Left: TV setting `tcl_app_settings_key_customization=true` (look for
      a "customize buttons" menu in TV Settings); a custom panel inside Afterglow (only on Afterglow's own screen, no video behind). Decision: keep full-screen Settings.
      Next (only if the owner wants it): Restore once, press Settings during a video with `adb logcat -c` then `adb logcat -d | findstr "START cmp="`, then
      re-apply the takeover. Otherwise accept full-screen Settings, or try `com.android.tv.settings` side-panel activities. Not worth more guessing.
   b. **"Learn a button" launches the app instead of learning** when the pressed button is already mapped (Netflix, Prime, Settings). Fix: while the Learn
      screen is open, capture the key code BEFORE any mapping (a `learning` flag read in `HomeActivity.onKeyDown`, or consume in the Learn screen's key handler)
      and show the code. User mappings are checked before the built-ins so any button can be re-assigned; each remapped button gets "Reset to default".
      Unit-test: learn mode returns the code for a built-in key; a user mapping wins over `RemoteKeys`.
1. **DONE, awaiting a TV test: screensaver no longer starts while a movie is playing** (commit 1f54b3d). `ClockDream.onDreamingStarted()`
   calls `wakeUp()` when `AudioManager.isMusicActive()`. To test: play a movie, let the TV idle, no screensaver; pause, it may. Still to
   check: video from an HDMI input has no audio session on the TV.
2. **DONE, awaiting a TV test: remote Settings (83), Netflix (4062) and Prime Video (4099) buttons** (commit 1f54b3d, `home/RemoteKeys.kt`
   + `HomeActivity.onKeyDown` + `RemoteKeysTest`). Only while Afterglow is on screen. YouTube sends nothing to the app (the TV consumes it);
   open: ask the owner what it does now.
3. **DONE, awaiting a TV test: Settings > Afterglow settings > Remote buttons.** "Learn a button" waits for a press (`HomeActivity.dispatchKeyEvent`;
   Back, D-pad, OK, Home, Menu, power and volume can't be learned), then you pick any installed app. Stored as key code -> package in DataStore
   (`data/ButtonMap.kt`, `ButtonMapTest`), checked in `HomeActivity.onKeyDown` after the built-in `RemoteKeys` (so Netflix/Prime can't be
   remapped yet). Rows on the page remove a mapping. Same limits as task 2. To test: learn the TCL Channel button, pick an app, press it on Home.
4. **Auto-restore if Afterglow crash-loops** (safety). If Afterglow crashes 3 times within 2 minutes (`CrashGuard`), run the M2 Restore flow by
   itself over `127.0.0.1:5555` (re-enable the packages saved in `disabledHomes`) so Google TV comes back instead of leaving the TV stuck, then
   show a note in Settings > About. Reuse `TakeoverFlow.restore()`; no new adb commands. Needs: crash counter with timestamps, a guard against
   restoring when USB debugging is off (then just log), unit tests with a fake adb.
5. **Speed pass (Chunk C, part 1):** baseline profile for the Home screen, cold start under 500 ms on the TCL, memory check on a 2 GB TV
   (image cache, no leaks when opening apps and coming back), measure and record the numbers in the README.
6. **Real screenshots in the README + a download button on the owner's website** (afterglow.nbansal28.com should link to the latest GitHub release
   APK). Screenshots: `adb exec-out screencap -p > afterglow-home.png` from the laptop, then `docs/screenshots/`.
7. Release 1.0 after 4 to 6 (and the Speed up list check, task 8).
8. **Check the shipped "Speed up my TV" list against the owner's real TV** (all items are unticked and unverified). Needs the output of
   `adb shell pm list packages` (system and third-party) from the owner.
9. Tidy: the screensaver and takeover pages have only been tested on the TCL 65Q6C; other TVs untested.

## Later (not v1)
Folders, remote-button remapping, Obtainium listing.

## Design
- Mood: the sky just after sunset. Near-black base `#0B0908`; a soft glow rises from the bottom edge
  (amber `#FFB070` → rose `#E8607A` → violet `#5B3F8C`). Text warm white `#F3E9DF`, secondary `#B8ADA3`.
- Glow follows time of day, changed at most every 15 min (no animation loop): morning peach, day faint, evening full
  sunset (as mockup), night dim ember.
- Logo: half sun resting on a horizon line with fading reflections; wordmark "afterglow" in lowercase.
- Home: logo top-left, clock top-right, big light-weight greeting ("Good morning/afternoon/evening/night") + date,
  Favourites row of the apps' own 16:9 banners, bottom-right pills: All apps + Settings. No inputs row (the remote has an input key).
- Focus: tile lifts ~6%, warm cream ring `#FFD9B0` + amber glow behind it. Unfocused tiles flat, no shadows.
- Type: one font (Outfit, light 300 for clock/greeting, 400–600 elsewhere). Rounded 20–22 dp tiles, 36 dp pills.
- Motion: only focus lift and short fades (<200 ms). Nothing moves on its own.
