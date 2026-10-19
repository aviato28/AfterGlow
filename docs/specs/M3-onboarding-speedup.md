# M3: Onboarding, Speed-up, screensaver and polish (Chunk B)

Do this AFTER `docs/specs/M2-takeover-in-app.md` is merged (it reuses its adb layer `takeover/AdbShell.kt`, the command whitelist,
`disabledHomes` handling and the "persist the plan first, verify, undo on failure" pattern). Read `CLAUDE.md`,
`docs/TAKEOVER-TCL.md` and the existing code first. Do not read Vanta or Penumbra. Commit and push after each phase. No emulator runs.

## Phase 1: First-run onboarding
Shown once (flag in DataStore, re-runnable from Afterglow settings > "Set up again"). Every step has Skip and Back. Same look as Home.
1. Welcome: what Afterglow is, in two lines.
2. **Your apps:** grid of installed apps with checkboxes, OK toggles. Chosen apps become favourites in the order they were ticked
   (reuse the existing favourites logic and default-favourites list). Then an "Order them" step (left/right to move).
3. **Keep apps updating:** plain text: Afterglow never installs or updates other apps; Netflix, YouTube and others update through the
   Play Store, which keeps HDR, Dolby Vision and 4K working. Button "Open Play Store" (leanback/market intent, ignore failure).
   Say "Turn on auto-update in Play Store settings". Do not claim to read the setting.
4. **Make Afterglow my Home:** runs the M2 takeover screen. Skippable. Explain in two lines that USB debugging must be on.
5. **Speed up my TV (optional):** opens Phase 2. Skippable.
6. **Screensaver (optional):** offers "Use Afterglow clock" (Phase 5). Skippable. 7. Done.

## Phase 2: "Speed up my TV" (safe, undoable, never automatic)
- Shipped list: `assets/debloat/tcl.txt` (one package per line with a short plain-language reason after `#`; comments start with `##`).
  Start from these TCL packages, all UNVERIFIED on the owner's Q6C so every item is unticked by default: `com.tcl.tv.plus`,
  `com.tcl.channelplus`, `com.tcl.tv.tclhome_passive`, `com.tcl.partnercustomizer`, `com.tcl.interactive`, `com.tcl.smartlink.core`,
  `com.tcl.exhibit`, `com.tcl.esticker`, `com.tcl.ocean.instructions`, `com.tcl.repairguide`, `com.google.android.feedback`,
  `com.google.android.marvin.talkback`, `com.google.android.apps.tv.dreamx`, `com.google.android.tv.remote.service`.
  Show only packages that are actually installed on this TV (`pm list packages`); add `Build.MANUFACTURER` matching
  (`tcl.txt` now; other brands can be added as files later).
- **Protected, never offered** (hard-coded, with a test): everything on the M2 protected list plus `com.tcl.tv`, `com.tcl.tvinput`,
  `com.tcl.dashboard`, `com.tcl.suspension`, `com.tcl.autopair`, `com.tcl.systemserver`, `com.android.tv.settings`, Play Store, GMS,
  GSF, anything with a HOME or LAUNCHER role, Netflix, YouTube, Prime Video, Disney+ (apps the user watches are never in the list).
- UI: list with checkboxes and one-line reasons; "Apply" shows a confirm screen. Before applying: save the chosen packages in
  DataStore (`disabledExtras`), then disable one package per command (M2 whitelist), then report which succeeded. Per-item
  "Restore" and "Restore all" always available. If adb (127.0.0.1:5555) is not reachable, explain and stop. Extend the command
  whitelist only with `pm disable-user --user 0 <pkg>` and `pm enable <pkg>` for these packages (already in the whitelist from M2).
- Precondition: the TV must still be reachable after a restart. Say plainly that Developer options > USB debugging must stay on.

## Phase 3: Recents (in All apps, not on Home)
- Afterglow records its own launches (package + time, last 8, in DataStore); no usage-access permission. Show a "RECENT" row of
  small tiles at the top of the All apps screen (hidden apps excluded, favourites still shown). Home stays favourites-only.

## Phase 4: Polish
- **Home button page: the "Restore Google TV" row is always visible** (not only after a first adb connection). Pressing it connects (the TV may
  ask "Allow USB debugging?") and, if nothing was switched off, says "Nothing to restore: Google TV's home screen is already on."
- Home button page and About page wording in plain language; show the version and "up to date" state clearly.
- Favourites empty state text; focus is never lost when a popup closes (return to the item that opened it).
- The remote's Settings key opens TV settings from any Afterglow screen (already on Home).
- Keep the Back rule: Back never leaves Afterglow's Home.

## Phase 5: Clock screensaver ("Afterglow clock")
Design approved by the owner (mockup: a huge thin clock on the sunset glow, greeting and date small underneath, a tiny half-sun on a hairline
horizon). Replaces Google TV's blurry-photo-and-ugly-clock ambient mode. Keep it light: **plain Android Views and Canvas, no Compose, no
network, no libraries**.
- `saver/ClockDream.kt`: a `DreamService` (label "Afterglow clock", `BIND_DREAM_SERVICE`, intent filter `android.service.dreams.DreamService`,
  meta-data `android.service.dream` with an empty xml, no settings activity), `isInteractive = false`, `isFullscreen = true`. Content is one custom
  `View` (`saver/ClockView.kt`) that draws everything itself on a 1920x1080 layout scaled to the screen width (same idea as `TvCanvas`).
- Look (sizes in layout px; ONE constants object `SaverStyle` so the owner can tune them): background `#0B0908`; glow = the same radial
  gradient and time-of-day phases as `ui/Glow.kt` (move the phase logic to a shared pure function; do not duplicate it); clock `HH:mm`, always
  24-hour, Outfit weight 200 (`Typeface.Builder` with `'wght' 200`, fall back to the regular weight if variations fail), `ClockSize = 330`,
  letter spacing -6; one line under it "Good evening  ·  Monday, 19 October" (38 px, secondary colour, greeting in warm white); a hairline
  horizon 520 px wide (fading at both ends) with a 64x32 half-sun in the logo gradient resting on it, centred, 62 px below the text.
- **Night** (21:00 to 05:00, same boundaries as Glow): clock at 60% size and 55% opacity, glow reduced to a faint amber ember, greeting hidden
  (date stays), horizon and sun at 40% opacity. The night style is one constant (`NightStyle`) so it can later become "black with a tiny clock".
- **No burn-in:** once a minute, at the minute change, move the whole group by a random offset (at most 60 px sideways, 40 px up or down, never
  off screen) with a 1.5 s cross-fade. Nothing else moves. Redraw only on the minute and during that fade; stop all callbacks in
  `onDreamingStopped`; no wake locks; no animations loops.
- **Set as screensaver (in Afterglow, Settings > Afterglow settings > Screensaver):** shows the current screensaver (read with
  `settings get secure screensaver_components`) in plain words ("Afterglow clock", "Google TV ambient mode" for `...dreamx`, "something else",
  "off") and two buttons: **Use Afterglow clock** and **Undo**.
  - Use: save the previous value of `screensaver_components` in DataStore FIRST, then
    `settings put secure screensaver_components com.nbansal.afterglow/com.nbansal.afterglow.saver.ClockDream` and
    `settings put secure screensaver_enabled 1`; read the setting back to verify, and put the old value back if it did not stick.
  - Undo: put the saved previous value back (if there was none, say so and point to the TV's own Screen saver page).
  - **Extend the adb whitelist (M2 `AdbWhitelist`, with tests) with exactly:** `settings get secure screensaver_components`,
    `settings get secure screensaver_enabled`, `settings put secure screensaver_components <pkg>/<class>` (component syntax check),
    `settings put secure screensaver_enabled 0|1`. Nothing else.
  - If adb is not reachable (USB debugging off), show the manual route: the TV's Settings > Device Preferences > Screen saver, and the
    screensaver timeout is also set there. Add a "Open screen saver settings" row using `Settings.ACTION_DREAM_SETTINGS` (ignore failure).
    Do not invent a "preview now" adb command; just say the screensaver starts after the TV's own timeout.
- Note from the TCL example: disabling `com.google.android.apps.tv.dreamx` leaves `screensaver_components` pointing at it, which is one more
  reason to point the setting at the Afterglow clock.

## Tests (JVM)
Onboarding step order and skip logic, favourites ordering from ticked apps, debloat list parsing and "installed only" filtering,
protected-list filtering (including every name above), recents ordering and cap, one-package-per-command, restore with a lost list, screensaver: greeting and phase per hour (incl. boundaries 04:59, 05:00, 20:59, 21:00), 24-hour text, night style values, offsets always inside the screen, whitelist accepts exactly the four settings commands and rejects variants, previous-value save and restore.

## Wrap-up
Update `docs/ROADMAP.md`, fill "Not done" below, Build workflow green. Do not run Release and do not open a pull request.

## Not done
- Nothing was compiled or run in the build session (no Android SDK there); the Build workflow on the branch is the first compile. Nothing was tested on the TCL.
- "Installed only" uses `PackageManager` (QUERY_ALL_PACKAGES is already held) instead of adb `pm list packages`, so the whitelist did not grow.
- Onboarding shows once for everyone who has not seen it, including existing installs. Ticked apps start as today's favourites.
- Onboarding Home / Speed-up steps reuse the settings pages with extra Next/Back rows; "Next" there is the skip.
- Recents row has up to 8 small tiles at the top of All apps; it is a grid header, so up/down moves into it by normal focus search.
- Popup-close refocus re-runs the screens' saved-index focus (no new focus system). Not checked on a real remote.
- Restore with a lost list only re-enables shipped-list packages that are currently disabled.

Phase 5 (clock screensaver): built, not yet run on a TV or by CI at the time of writing.
- Weight 200 is set with `Paint.setFontVariationSettings` on the `res/font` Outfit (a `Typeface.Builder` cannot open a packed resource font); falls back to the regular weight.
- The current screensaver is displayed by reading `Settings.Secure` directly (no adb needed to look); the `settings get` commands are whitelisted and used to verify after a change.
- Component syntax for `settings put` also accepts comma-separated components and the word `null`, so any earlier value can be restored.
- Added an onboarding step "Screensaver" (between Speed up and Done); Next skips it.
- Not tested on the TV: dream selection, night look, minute shift/fade.
