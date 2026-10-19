# Afterglow roadmap

Status: **M0 and M1 (home, apps, favourites, updater) done. Home-button takeover WORKS on the TCL 65Q6C with the recipe in docs/TAKEOVER-TCL.md (done by hand over adb; an in-app version is not built). Next: polish, onboarding (M2).**

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

- **Design** [done]: `docs/design/home.svg` (home screen) and `docs/design/logo.svg`. Other screens reuse the same
  parts; no further mockups.
- **M0 Foundation** [Sonnet, done]: Gradle project, Compose, 1920 canvas, theme tokens, app icon + TV banner from logo.svg, `tvFocus`, crash-restart, CI: build + unit tests
  on push, signed release on tag `v*` uploads the APK to GitHub Releases; one-time "create signing key" workflow.
  *Not done:* see "Not done" in `docs/specs/M0-foundation.md` (unverified on a real TV, focus glow approximated, release workflows not yet run).
- **M1 Launcher** [Sonnet, one session, 4 phases]: see `docs/specs/M1-launcher.md`. Polish (Outfit font, softer glow), apps + favourites + All apps + options popup + settings, then the Home-button takeover (accessibility catcher + disable stock launcher through in-app ADB, with Restore).
- **M2 Onboarding** [Sonnet]: welcome → pick apps you use (installed ones ticked; popular missing ones link to Play) →
  order favourites → check Play auto-update → optional Takeover → done. Export/import layout (file + QR) to set up a
  second TV.
- **M3 Updater** merged into M1 Phase 3 (check + download + checksum + install from GitHub Releases). Silent install via ADB later.
- **M4** merged into M1 Phase 2 (fallbacks and per-brand debloat list stay for later, after the Installed packages list from the TCL is photographed).
- **M5 Speed + 1.0** [Sonnet; Haiku for copy]: baseline profile, cold start target < 500 ms on TCL, memory target
  < 80 MB, 2 GB emulator test, README with screenshots and ADB-from-PC restore instructions, v1.0 release.

## Later (not v1)
Folders, remote-button remapping, screensaver, Obtainium listing.

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
