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
1. **BUG: the Afterglow clock screensaver starts while a movie is playing** (owner, 0.5.0). Fix WRITTEN, not merged, not built, untested:
   `ClockDream.onDreamingStarted()` calls `wakeUp()` when `AudioManager.isMusicActive()`. It is in commit 1f54b3d on branch
   `claude/afterglow-tv-launcher-g6ow3l` (the build was cancelled at the owner's request). To test: play a movie, let the TV idle, the screensaver must
   not appear; pause, it may. Still to check: video from an HDMI input has no audio session on the TV.
2. **Remote buttons dead since the takeover: Settings key, Netflix, Prime Video, YouTube.** Findings from the TV's input log (TCL 65Q6C, owner's laptop):
   Settings = key code 83 (`KEYCODE_NOTIFICATION`), Netflix = 4062, Prime Video = 4099; all three reach Afterglow's window when it is in front.
   **YouTube sends nothing to the app** (the TV consumes it), so it cannot be mapped; what it does now is unknown. Fix WRITTEN, not merged, not built,
   untested: `home/RemoteKeys.kt` + `HomeActivity.onKeyDown` (Settings opens TV settings, Netflix and Prime launch their app) + `RemoteKeysTest`, in the same
   commit 1f54b3d. Limits: only works while Afterglow is on screen (inside another app these keys go to that app). Open: ask the owner what the YouTube button does.
   **Next step (only when the owner says go):** check CI on that branch, merge, Release 0.5.1, owner tests the buttons and the screensaver.
3. **Remote-button mapping: "TCL Channel" button should open Penumbra+ (owner request).** Do NOT hard-code it: Penumbra+ is a private app and
   its name must not appear in this public repo, and the key code of the Channel button is not known yet. Build it as a general feature instead:
   Settings > Afterglow settings > **Remote buttons**: "Learn a button" (press the remote button, Afterglow shows its key code), pick any installed app
   for it; the mapping (key code -> package) lives in DataStore and is checked in `HomeActivity.onKeyDown` after the built-in `RemoteKeys`
   (Settings 83, Netflix 4062, Prime 4099). Also lets the owner remap Netflix/Prime. Same limits as task 2: only while Afterglow is on screen, and
   a button the TV consumes (like YouTube) cannot be learned. Needs: a unit-tested mapping store and a plain "press a button" capture screen.
   Until then, the key code can be read with the adb log test (press the Channel button once, `findstr publishKeyEvent`).
4. **Auto-restore if Afterglow crash-loops** (safety). If Afterglow crashes 3 times within 2 minutes (`CrashGuard`), run the M2 Restore flow by
   itself over `127.0.0.1:5555` (re-enable the packages saved in `disabledHomes`) so Google TV comes back instead of leaving the TV stuck, then
   show a note in Settings > About. Reuse `TakeoverFlow.restore()`; no new adb commands. Needs: crash counter with timestamps, a guard against
   restoring when USB debugging is off (then just log), unit tests with a fake adb.
5. **Speed pass (Chunk C, part 1):** baseline profile for the Home screen, cold start under 500 ms on the TCL, memory check on a 2 GB TV
   (image cache, no leaks when opening apps and coming back), measure and record the numbers in the README.
6. **Real screenshots in the README + a download button on the owner's website** (afterglow.nbansal28.com should link to the latest GitHub release
   APK). Screenshots: `adb exec-out screencap -p > afterglow-home.png` from the laptop, then `docs/screenshots/`.
8. **Check the shipped "Speed up my TV" list against the owner's real TV** (all items are unticked and unverified). Needs the output of
   `adb shell pm list packages` (system and third-party) from the owner.
7. Release 1.0 after 4 to 6 (and the Speed up list check, task 8).
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
