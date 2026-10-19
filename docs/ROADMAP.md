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
- **Clock screensaver:** designed with the owner (centered thin clock on the sunset glow, tiny half-sun horizon, dim and small at night) and built as Phase 5 of Chunk B.

Decided NOT to build: HDMI inputs row, weather, themes, settings backup for a second TV, an accessibility Home-key catcher as the main path.

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
