# Afterglow roadmap

Status: **planning approved in principle → next: mockups.** Nothing is built yet.

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
| Lost with stock launcher | Google's "Continue watching"/content rows and the Google TV notification panel. Owner OK with that. Inputs, Settings, voice search stay. |
| Look | Dark, warm "afterglow" gradient that shifts slowly with time of day; big rounded app tiles; clock + optional weather. |
| Licence | GPL-3.0 (proposed; forks must stay open). |

## Milestones
Each milestone gets a spec in `docs/specs/` written by the planning chat before building. Model per milestone in brackets.

- **Mockups** [Opus plans, Sonnet draws]: clickable 1920x1080 HTML mockups of Home, All apps, App options, Onboarding
  (5 steps), Takeover, Updates, Settings. Approve before any code.
- **M0 Foundation** [Sonnet]: Gradle project, Compose, 1920 canvas, `tvFocus`, crash-restart, CI: build + unit tests
  on push, signed release on tag `v*` uploads the APK to GitHub Releases; one-time "create signing key" workflow.
- **M1 Home** [Sonnet]: HOME + LEANBACK_LAUNCHER activity; favourites row, all apps grid, Inputs (HDMI) row, Settings
  tile, clock/weather, ambient gradient; hide / reorder / rename apps; installed-apps watcher. Diagnostics page listing
  all installed packages (used to build the TCL debloat list).
- **M2 Onboarding** [Sonnet]: welcome → pick apps you use (installed ones ticked; popular missing ones link to Play) →
  order favourites → check Play auto-update → optional Takeover → done. Export/import layout (file + QR) to set up a
  second TV.
- **M3 Updater** [Sonnet]: Releases check on launch + daily (WorkManager-free: alarm/job), changelog card, download with
  progress, install permission step, never mid-app (only when Afterglow is in front).
- **M4 Takeover** [Opus spec, Sonnet build]: wireless-debugging pairing (pairing code + port) via an ADB client library
  (candidates: libadb-android, dadb); legacy "network debugging" port 5555 for Android 10 TVs; disable stock launcher;
  optional curated debloat list per brand with a protected list; restore all; re-check after OTA/reboot; accessibility
  fallback. Spike on the TCL first.
- **M5 Speed + 1.0** [Sonnet; Haiku for copy]: baseline profile, cold start target < 500 ms on TCL, memory target
  < 80 MB, 2 GB emulator test, README with screenshots and ADB-from-PC restore instructions, v1.0 release.

## Later (not v1)
Folders, remote-button remapping, screensaver, Obtainium listing.

## Open questions
- Weather: include at all? (needs a location + a free API; adds a network call.)
- Licence GPL-3.0 vs MIT.
