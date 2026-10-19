# M2: Home button takeover and Restore inside Afterglow

Goal: Settings > Afterglow settings > Home button gets two real buttons: **Make Afterglow my Home** and **Restore Google TV**,
plus a live status line. Everything the owner did by hand from a tablet (see `docs/TAKEOVER-TCL.md`, verified on a TCL 65Q6C)
happens from the TV, with an automatic undo if the result is wrong. Read `CLAUDE.md`, `docs/TAKEOVER-TCL.md` and the existing
`takeover/` package (`Takeover` rules, protected list, tests, `PrefState.disabledHomes`) first. Do not read Vanta or Penumbra.
Commit and push after each phase. No emulator runs. Keep command output short.

## Phase 1: ADB client for the TV's own debugging port
- Dependency: `dev.mobile:dadb` (check Maven Central for the current version; an earlier session found 2.0.0, ~132 KB, TCP only).
  Add R8 keep rules. Nothing else new.
- `takeover/AdbShell.kt`: `suspend fun run(command: String): Result` over `Dadb.create("127.0.0.1", 5555, keyPair)`. RSA key pair
  generated once with dadb's `AdbKeyPair`, stored in `filesDir/adb/` (never logged, never exported). 15 s timeouts, off the main thread.
- **Command whitelist in code** (reject anything else, with a test): `pm disable-user --user 0 <pkg>`, `pm enable <pkg>`,
  `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`,
  `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`, `pm list packages -d`.
- First connection: the TV shows "Allow USB debugging?". Show a plain message ("On the TV press Allow and tick Always allow") and wait up to 30 s.
- If nothing listens on 127.0.0.1:5555: explain in two sentences that Developer options > USB debugging must be on, and stop.

## Phase 2: Takeover with automatic undo
Screen "Make Afterglow my Home": explains what happens in plain words, then asks for confirmation (OK twice).
1. Check the adb connection works (Phase 1). Check Afterglow itself is a HOME handler.
2. Query the HOME handlers; the packages to disable are every HOME handler except Afterglow and the protected list (existing
   `Takeover` rules). On the TCL that gives `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith`
   (the second must go too: its RecoveryActivity otherwise takes Home and the TV shows black after a restart). Never disable
   `com.android.tv.settings` (FallbackHome), Play, GMS, or the other protected entries.
3. Save the planned list in DataStore (`disabledHomes`) BEFORE disabling anything. Disable one package per command.
4. Verify: `resolve-activity` must end with `com.nbansal.afterglow/.HomeActivity`. If not, re-enable everything in the list and
   show "This TV did not accept Afterglow as Home. Nothing was changed." Never leave a half state.
5. Success screen: "Press Home to try it." The status line shows who owns Home.

## Phase 3: Restore Google TV
- Button "Restore Google TV" (always visible when `disabledHomes` is not empty, or when `pm list packages -d` shows a HOME handler
  that Afterglow disabled): `pm enable` every package in `disabledHomes` (and, if the list is lost, the two TCL packages when they
  are disabled), then verify with `resolve-activity`, clear `disabledHomes`, and say what Home opens now.
- If adb is not reachable: show the manual route (Settings > Apps > See all apps > Show system apps > enable them) as text.
- Status line on the Home button page: "Home button opens: Afterglow / Google TV / something else".
- The accessibility catcher stays as a secondary row "Home-key catcher (other TVs)". Do not make it the main path.

## Tests (JVM, fake adb)
Whitelist (rejects everything else), HOME-handler parsing from `query-activities` output, protected-list filtering,
"verify fails, so undo everything" path, restore with a lost list, never more than one package per command.

## Wrap-up
Update `docs/TAKEOVER-TCL.md` (add "now also available in the app") and `docs/ROADMAP.md`; fill "Not done" below. Build workflow
green. Do not run Release (the owner does). No pull request.

## Not done
(build session fills this in)
