# Afterglow roadmap

Status: **0.10.1 released (Afterglow 2: Lite v2 home, Showcase, quick panel v2). Next: task list item 1 (crash-loop auto-restore).**

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
| Look | See Design below (Afterglow 2: `docs/design/v2/`). Clock + greeting. No weather. Lite (default) and Showcase (TMDB art, opt-in). |
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

## Task list (open, do in this order)
3. **Speed and memory pass** [baseline profile + `docs/SPEED.md` built in M10; measurements on the TV pending]. Baseline profile for Home, cold start under 500 ms on the TCL, memory with Showcase images + glass + key-watch service,
   no leaks opening/closing apps, a 2 GB TV check; record numbers in the README.
4. **Showcase v2** [built, awaiting test] (owner loved the mockups, 1 Nov): `docs/design/v2/showcase2-dune.png`, `showcase2-batman.png`, `showcase2-kenburns.gif` (+ HTML).
   a. **Title logos:** TMDB `/3/{movie|tv}/{id}/images?include_image_language=en,null`, best English PNG logo (`w500`), cached with the backdrop; no logo ->
      styled text title. Logo max ~860x260 dp, soft glow behind.
   b. **Ken Burns drift:** backdrop scales 1.04 -> 1.10 and pans ~40 dp over the 15 s, GPU transform only (graphicsLayer), stops when Home isn't resumed or focus leaves the hero.
   c. **Colour matched to the artwork:** one accent per backdrop (downscale to 16x9, most saturated mid-bright color) -> focus ring/glow, score ring, dots, shelf tint,
      bottom glow. Fallback amber.
   d. **Details:** score ring (vote_average x10), age certification for India (`/release_dates` movies, `/content_ratings` tv, region IN, fall back to US), runtime,
      up to 2 genres, "New on streaming" badge (released within 60 days). One details call per title, cached with the 6 h refresh. NO 4K/Dolby Vision badges.
5. **Showcase night mode** [built, awaiting test]: in the Night glow phase (21-05) the artwork dims ~40% and the drift slows; matches the Lite glow phases.
6. **Artwork screensaver** [built, awaiting test]: a second screensaver choice next to the sunset clock: cached Showcase backdrops (no extra network), slow crossfade + drift, small clock.
   Only offered when Showcase has a working key. Same "don't start while video plays" rule.
7. **Dead code clean-up** [built, M10]: remove what M4/M5/M7 left behind (old list panel, unused icons, Local logcat reader and `GrantLogs` remnants, old Learn paths). No behaviour change.
8. **README + website for the v2 look:** real screenshots (`adb exec-out screencap -p > home.png` from the laptop -> `docs/screenshots/`), and a download button on
   afterglow.nbansal28.com linking to the latest release APK.
9. **"Speed up my TV" list update** [built, M10; `youtube.tvmusic` taken off the never-offered list] (owner's package lists, 1 Nov; folded into M10). Verified safe (already off on the owner's TV, TV fine): tclhome_passive,
   channelplus, partnercustomizer, interactive, exhibit, esticker, ocean.instructions, repairguide. REMOVE `com.google.android.tv.remote.service` (owner uses the
   phone remote). `dreamx`: reason says "only if you use Afterglow's screensaver (also holds the TV's screensaver settings)". ADD (owner doesn't use them):
   `com.tcl.smartalexa` (Alexa), `com.tcl.magiconnectfree` (TCL phone app), `com.google.android.youtube.tvmusic` (YouTube Music), `com.google.android.play.games`,
   `com.tcl.usercenter` (TCL account), `com.tcl.hotelmenu` (hotel mode), `com.tcl.logkit` (TCL log collector), `tv.mopa.ginga` (Brazil broadcast extras).
   NOT added: AirPlay (`com.tcl.airplay2`, `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon`) and `com.tcl.miracast` (owner uses them sometimes).
   Never listed: mediashell (Chromecast), katniss (voice), netflix.tokenmanager, ui_mediaCenter, gamebar, factory.view. All stay unticked by default.
10. Release 1.0 after 1-3 and 9.
11. **Phone companion** (owner loves it, 12 Nov). **BUILT (M11), awaiting test on the TV.** Afterglow shows a QR code; the phone opens a small page served by the TV on the home Wi-Fi (no internet,
    no account, no app install). Opt-in once, then always on; a one-time PIN in the QR pairs a phone.
    a. **Phone as a keyboard for ANY app** (e.g. typing in a movie app's search): text typed on the phone goes to whatever field is focused on the TV, via the
       existing loopback adb (`input text`, sent in small throttled chunks because the TV keyboard drops fast input, special characters escaped) — no IME switch.
       Also Enter, Backspace, D-pad, Back, Home, OK from the page.
    b. Paste the TMDB key straight into Afterglow; reorder favourites; learn/assign remote buttons.
    c. **Backup and restore** of all Afterglow settings (favourites, names, mappings, look, panel, screensaver; TMDB key only if the owner ticks it) to a file
       on the phone, restore after a factory reset.
    **Decision (12 Nov): web page only, no phone APK.** Make it an installable web app (manifest + icon, "Add to Home screen", remembers the TV with a paired
    token so the QR is scanned once). Owner wants zero hassle: once switched on, the companion STAYS ON and paired phones stay paired (no auto-off); a new phone needs a fresh PIN shown on the TV; Settings has "Unpair all phones" and an off switch. An APK would only add a share-sheet
    "Send to TV", volume-button control, notification remote, widgets: revisit only if wanted later.
    **Rule change needed when built:** local network server allowed only while the companion is on (add to CLAUDE.md "Never" line).
12. **Feel and polish pass:** spring-physics focus movement, screens slide instead of cutting, shared-element transitions (tile/poster grows into its page),
    all under 200 ms; one accent everywhere (Lite time-of-day glow / Showcase artwork colour tints Settings and the panel too); a consistent icon set;
    v2 typography and glass on every Settings page (they still look v1). Mockups first.
13. **Beyond the TCL:** takeover recipes + checks for Chromecast with Google TV, Sony, Xiaomi; Fire TV "watcher" mode (cover Amazon's home, which can't be
    replaced); testers with other TVs; 1.0 launch (website, screenshots, Obtainium/F-Droid listing).
14. **Showcase fixes + better picks** (owner, 12 Nov; how it works today: TMDB trending/all/week, top 10, 6 h refresh -> same titles for days; it only
    rotates while focus is in the hero, so it sticks while you're on Favourites).
    a. **Logo box:** some TMDB logos have a solid background. Reject a logo whose border pixels are mostly opaque (sample the edges after decode); try the next
       logo, else the styled text title.
    b. **Smoother rotation:** rotate every 10 s always while Home is in front (also when focus is on Favourites); preload the next backdrop + logo before the switch;
       700 ms crossfade; drift continues across the switch.
    c. **Bigger, fresher pool (~50):** trending day + week, popular movies/tv, now playing in India, top rated; shuffle; don't repeat titles shown in the last 3 days.
    d. **Only what you can watch:** read the installed streaming apps (Netflix, Prime Video, Hotstar, SonyLIV, Zee5, ...) and use TMDB `discover` with
       `watch_region=IN&with_watch_providers=<ids>` for most of the pool; small "On Netflix" badge.
    e. **Your taste (optional):** genres more/less, languages (English, Hindi, Korean...) in Settings > Look; long-press OK on a title = "Not interested" (never shown again).
Mockups 12 Nov (owner asked for basic ones): `docs/design/v2/companion-tv.png`, `companion-phone.png` (task 11), `settings-v2.png` (task 12: Settings with a left
rail of icon sections and glass rows).
Decided NOT to add (owner, 12 Nov): universal search, "Jump back in"/where to watch/watchlist (the owner's movie app does that), kids/guest mode, bedtime,
movie-night, inputs tile.

## Waiting for the owner
- **Brightness bar** instead of volume in the quick panel: TCL keeps Backlight outside `settings` (discovery 1 Nov). Next if resumed: `adb logcat` while changing Backlight.
- **QR code key entry** (phone page on the local network to paste the TMDB key): owner to decide.
- **YouTube remote button:** learn it with Learn a button; a built-in mapping needs its key code from the Quick panel diagnostics.
- **Penumbra+ accepts TMDB links** (change in that app; note given to the owner 1 Nov) -> then pick it in Look > Open titles with.

## Built, awaiting a TV test
- Safety (M8): auto-restore of Google TV after 3 crashes in 2 min (note in Settings > About), and the firmware check ("A TV update brought Google TV back. Re-apply takeover?").
- Quick panel v2 (M4, M4.2, M5, M7.1): overlay over any app via the Adb log reader, heartbeat, no boot popup, Now playing, Sleep, Sleep timer, 6 tiles
  (Bluetooth/Accessibility removed). Owner confirmed the panel works in YouTube and Penumbra+.
- Remote buttons: Settings/Netflix/Prime built-ins, Learn a button (also from the log), "Stop TCL Channel opening too".
- Lite home v2 (M6) and Showcase (M7, M7.1; TMDB key works, 1 Nov).
- Screensaver yields while video plays (HDMI input not checked).

## Parked / decided
- **TCL Settings side bar:** lives in the Google TV launcher (`launcherx/.dashboard.DashboardHandler`), which the takeover disables, and Afterglow can't win Home
  any other way (priority 0 vs 2). Can't coexist; the Afterglow quick panel replaces it.
- Only the TCL 65Q6C has been tested; other brands and Fire TV untested.

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
