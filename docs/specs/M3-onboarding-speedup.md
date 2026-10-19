# M3: Onboarding, Speed-up and polish (Chunk B)

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
5. **Speed up my TV (optional):** opens Phase 2. Skippable. 6. Done.

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
- Home button page and About page wording in plain language; show the version and "up to date" state clearly.
- Favourites empty state text; focus is never lost when a popup closes (return to the item that opened it).
- The remote's Settings key opens TV settings from any Afterglow screen (already on Home).
- Keep the Back rule: Back never leaves Afterglow's Home.

## Tests (JVM)
Onboarding step order and skip logic, favourites ordering from ticked apps, debloat list parsing and "installed only" filtering,
protected-list filtering (including every name above), recents ordering and cap, one-package-per-command, restore with a lost list.

## Wrap-up
Update `docs/ROADMAP.md`, fill "Not done" below, Build workflow green. Do not run Release and do not open a pull request.

## Not done
(build session fills this in)
