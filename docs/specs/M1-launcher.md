# M1 — A launcher you can live with (Home + Takeover)

Goal: after this session the owner can use Afterglow every day on the TCL: real apps, favourites, and the Home
button opens Afterglow instead of Google TV. Five phases (0–4), in order. **Commit and push at the end of every phase**
(`git push`), so a stop mid-way loses nothing. Fix CI if the Build workflow goes red before starting the next phase.

Token rules for this session: read `CLAUDE.md`, `docs/ROADMAP.md` (Design section) and the existing code under
`app/src/main/kotlin/com/nbansal/afterglow/` only. Do not read Vanta / Penumbra. No emulator runs. Use
Robolectric tests for logic and keep command output filtered (`| tail -30`). Look: `docs/design/home.png`.

## Phase 0 — Polish from the first TV test (small)
- Font: replace Inter with **Outfit** (variable, OFL): download `Outfit[wght].ttf` from
  github.com/google/fonts (`ofl/outfit/`), save as `res/font/outfit.ttf`, add `assets/licenses/outfit-OFL.txt`, delete
  inter files. Rename `Ag.Inter` to `Ag.Font`. If the download fails, keep Inter and say so under "Not done".
- Focus glow: reduce intensity by about 25% (lower alpha of the stacked glow layers). Keep the ring as is.
- Date text 26 → 30 sp.
- Initial focus stays on the first favourite tile (after Phase 1), else "All apps".

## Phase 1 — Apps and Home
**App list** (`apps/AppRepository.kt`): query `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`; also include
`CATEGORY_LAUNCHER` apps that have no leanback entry. Exclude Afterglow itself. Fields: package, activity, label,
banner/icon loaded lazily. Refresh on `ACTION_PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` (dynamic receiver while
the activity is started, plus a refresh in `onStart`). Launch with `getLeanbackLaunchIntentForPackage` ?:
`getLaunchIntentForPackage`, `FLAG_ACTIVITY_NEW_TASK`; catch failures and do nothing.

**Banners** (`apps/Banners.kt`): `loadBanner()` (fallback `loadIcon()` drawn centred on a dark rounded tile with the label
under it). Render to a bitmap at the displayed pixel size (16:9 tile 320x180 dp, focused lift is scale only), cache in
an `LruCache` of about 10% of the app's max memory, clear on `onTrimMemory(RUNNING_LOW+)`. Load off the main thread.

**Preferences** (`data/Prefs.kt`, DataStore): `favourites: List<String>` (ordered packages), `hidden: Set<String>`,
`names: Map<String,String>` (renames). Default favourites on first run: installed apps from
[Netflix `com.netflix.ninja`, YouTube `com.google.android.youtube.tv`, Prime Video `com.amazon.amazonvideo.livingroom`,
Disney+ `com.disney.disneyplus`, Plex `com.plexapp.android`, Spotify `com.spotify.tv.android`, Apple TV
`com.apple.atve.androidtv.appletv`, Jellyfin `org.jellyfin.androidtv`, Kodi `org.xbmc.kodi`], in that order, max 8;
if none are installed, the first 6 apps alphabetically.

**Home** (`home/HomeScreen.kt`, as `docs/design/home.png`): keep the top bar, greeting and date. Add the
"FAVOURITES" caption and a horizontally scrolling row of 16:9 tiles (320x180 dp, 20 dp radius, 40 dp gap, 96 dp left
margin, label under the focused tile only). Row sits at y≈500 dp so the lifted tile and ring are never clipped.
Bottom-right pills stay: All apps, Settings. Focus is remembered when coming back from an app (use `rememberSaveable`
for the index; `focusSoon()` on start). Empty favourites: a quiet line "Press OK on All apps, then hold OK on an app
to add it here". Home key while Afterglow is already on top (`onNewIntent`): scroll to the first tile and focus it.

**All apps** (`home/AllAppsScreen.kt`): full-screen grid, same background, 5 columns of 16:9 tiles with the label
under each, sorted by name, hidden apps excluded. Back returns Home. Long-press OK (or Menu) on any tile opens the
**app options** popup (`TvDialog`, large rows, focus on the first row): Add to / Remove from favourites · Move
earlier / Move later (favourites only) · Hide · Rename (uses the system on-screen text field dialog; keep it simple) ·
App info (`ACTION_APPLICATION_DETAILS_SETTINGS`) · Uninstall (`ACTION_DELETE`). Long-press OK on a favourites tile
opens the same popup.

**Settings button** → small `TvDialog` with two rows: "TV settings" (`android.settings.SETTINGS`, as now) and
"Afterglow settings".

**Afterglow settings** (`settings/SettingsScreen.kt`): rows: Home button (Phase 2), Hidden apps (list with OK to
unhide), Installed packages (a plain scrolling list of every installed package name + label, for the owner to
photograph; this feeds the debloat list later), About (version, last crash text from `CrashGuard.lastProblem()` with a
Clear row).

Tests: favourites ordering/move logic, default-favourites selection, hidden filter, rename. Plain unit tests.

## Phase 2 — Making Afterglow the Home button (Takeover)
Context from the TCL test: Home currently returns to Google TV and Android never asks for a launcher. There are two
methods; build both, let the owner pick on one screen ("Home button" in Afterglow settings), show live status
("Home button opens: Google TV / Afterglow") by resolving `ACTION_MAIN + CATEGORY_HOME` with `resolveActivity`.

**Method B — Home key catcher (no ADB; build this first, it is the safe one).** An `AccessibilityService`
(`takeover/HomeKeyService.kt`) with `FLAG_REQUEST_FILTER_KEY_EVENTS`, `canRequestFilterKeyEvents=true`,
`canRetrieveWindowContent=false`, no event types, and a plain-words description. In `onKeyEvent`: on `KEYCODE_HOME`
key down, start `HomeActivity` (`FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP`) and return true (consumed); everything else
returns false. Screen: explains in two sentences that the service only watches the Home key and reads nothing else,
then opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`; status line shows whether the service is enabled. If it turns
out Home still goes to Google TV, say so honestly in the status text ("this TV doesn't pass the Home key on; use the
other method") rather than pretending.

**Method A — disable the stock launcher (fast, via the TV's own debugging).** Goal: run
`pm disable-user --user 0 <pkg>` for every package that handles `CATEGORY_HOME` other than Afterglow (discovered
with `queryIntentActivities`, so no hard-coded names; on the TCL expect `com.google.android.apps.tv.launcherx` and/or
`com.google.android.tvlauncher`), and `pm enable <pkg>` to restore. Needs an ADB client inside the app:
- First check Maven Central for the current versions of `io.github.muntashirakon:libadb-android` (supports Android 11
  wireless pairing, needs conscrypt) and `dev.mobile:dadb`; pick the one that actually supports what you implement.
  APK size limit is raised to 10 MB for this; keep R8 on and add keep rules.
- Key: generate an RSA key pair once, store in app-private storage (never log or export it).
- Path 1 (try first): connect to the TV's own IP on port 5555 ("Network debugging" in Developer options, where the
  TV has it). The TV shows "Allow USB debugging?"; the owner presses Allow.
- Path 2: Android 11+ Wireless debugging: discover `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp` with
  `NsdManager`, ask for the 6-digit code with an on-screen number pad (remote-friendly), pair, connect. Known issue:
  the system's pairing dialog closes when the user leaves Settings, which can stop the pairing service; handle by
  reading the pairing port as soon as it is advertised and showing the code field in Afterglow right away. If this
  cannot be made to work on a TV, ship Path 1 only and write exactly what failed under "Not done".
- Walk-through screen in plain steps with the TV's menu names (enable Developer options by pressing OK 7 times on
  Build number; turn on USB/ADB debugging and Wireless/Network debugging; return to Afterglow). Detect which paths
  are available and show only those.
- Safety (all required): never disable a package on the PROTECTED list: `com.android.settings`,
  `com.android.tv.settings`, `com.google.android.tv.settings`, `com.android.vending`, `com.google.android.gms`,
  `com.google.android.gsf`, `com.google.android.tvsetup`, `com.android.providers.*`, `com.android.systemui`,
  `com.android.tvprovider`, anything not returned by the HOME query. Before disabling, check Afterglow itself resolves
  as a HOME handler and show a warning screen ("If you uninstall Afterglow later, first press Restore here"). Store the
  list of disabled packages in DataStore. **Restore** row on the same screen (re-enables everything it disabled).
  If ADB is not reachable when restoring, show the manual route: Settings → Apps → See all apps → Show system apps →
  the package → Enable.
- After a TV reboot or system update the disabled state normally persists; on start, if Afterglow is not the HOME
  handler but the list says it should be, show a one-line notice on the settings screen.

Tests: protected-list filter, HOME-handler selection (fake `PackageManager` data), command building, and the key
handler (HOME consumed, other keys untouched).

## Phase 3 — Updater (so 0.2.0 is the last APK the owner moves by hand)
Small and boring on purpose (`update/Updater.kt`, `update/InstallReceiver.kt`; no WorkManager, no background jobs):
- Check: `GET https://api.github.com/repos/aviato28/AfterGlow/releases/latest` (public, no token). Compare `tag_name`
  (`v0.3.0`) with `BuildConfig.VERSION_NAME` using a numeric semver compare. Check on app start at most once per 12 h
  (store the time in DataStore), plus a manual "Check for updates" row in Settings → About. Network use is limited to
  `api.github.com`, `github.com` and `objects.githubusercontent.com`.
- Asset: `afterglow-<version>.apk` and `afterglow-<version>.apk.sha256` (what `release.yml` produces). Download to
  `cacheDir/updates/` with a progress bar, verify SHA-256 against the `.sha256` file, delete the file after install or
  on failure, and keep only the latest.
- Install: `PackageInstaller` session, committed with a `PendingIntent` to `InstallReceiver`; on
  `STATUS_PENDING_USER_ACTION` start the system confirm screen from the extra intent; log other statuses to the
  last-problem file. Needs `REQUEST_INSTALL_PACKAGES`; if `canRequestPackageInstalls()` is false, show a plain step
  ("Allow Afterglow to install apps") that opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package, then continues.
- UI: Settings → About shows "Version x.y.z", the latest release version and notes (the release body, first 12 lines),
  and a single button: "Download and install" / "Check for updates". On Home, keep it clean: only a small amber dot on the
  Settings pill when an update is available. Never interrupt anything (Afterglow only runs when it is on screen).
- Tests: version compare (`0.9.0` < `0.10.0`, `v` prefix, pre-release tags ignored), checksum verify, 12 h throttle.

## Phase 4 — Wrap up
- Update `docs/ROADMAP.md`: mark M1 done (and the old M4's basics), list gaps. Update `docs/RELEASING.md` only if
  something changed.
- Fill the "Not done" section below.
- Build workflow green; do NOT run Release (the owner does that from the Actions tab).

## Acceptance (what the owner checks on the TCL)
Apps appear with banners; OK opens an app; long-press OK adds/removes favourites; Back from an app returns to
Afterglow's home with focus where it was; Settings → About shows the version and "Check for updates" works against the latest GitHub release; Settings → TV settings opens the stock settings; Home button opens
Afterglow (Method B, then Method A); Restore brings Google TV back; no lag moving around.

## Not done
- Not run on a device or emulator: only CI (Robolectric + release build) checks it. Look, focus and Home-key behaviour are unchecked on the TCL.
- **Outfit font**: the download from github.com/google/fonts was blocked in the build sandbox, so Inter is kept (`Ag.Font` rename done). Drop `Outfit[wght].ttf` in `res/font/` and swap the file reference in `ui/Theme.kt`.
- **Phase 2 Method A (disable the stock launcher through in-app ADB) is NOT built.** The sandbox's safety classifier refused the ADB controller file, so it was left for the owner to decide. Research done: `dev.mobile:dadb:2.0.0` is on Maven Central (132 KB, TCP only: Path 1, 127.0.0.1:5555 with RSA key in `filesDir/adb`); it has no TLS/pairing, so Path 2 (Wireless debugging pairing) needs `libadb-android` (JitPack, not Maven Central) plus conscrypt. Already in place for it: `Takeover` rules (protected list, HOME candidates, commands, stale notice) with tests, and `PrefState.disabledHomes`.
- Method B (Home key catcher) is built; whether the TCL passes the Home key to it is untested.
- Updater: untested against a real release; the install permission step opens the system screen and you press the button again afterwards.
- Release-notes box shows plain text (no markdown rendering).
