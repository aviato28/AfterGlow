# M8 — Safety: crash-loop auto-restore + firmware update check (ROADMAP tasks 1, 2)

Read `CLAUDE.md`, ROADMAP tasks 1-2, `docs/TAKEOVER-TCL.md`. Read only `core/CrashGuard.kt`, `core/AfterglowApp.kt`, `core/AppModel.kt`, `takeover/` (Takeover,
TakeoverFlow, TakeoverController, AdbShell), `data/Prefs.kt`, the Home button and About settings pages, `AndroidManifest.xml`, tests.

## 1. Crash-loop auto-restore
- `CrashGuard` keeps the last crash timestamps (small file in `filesDir`, survives process death). 3 crashes within 2 minutes = crash loop.
- On a crash loop, and only if `disabledHomes` is not empty: run the existing restore flow (re-enable exactly the saved packages over `127.0.0.1:5555`,
  same code path as Settings > Restore; no new adb commands) from a short-lived worker that does not depend on the UI (e.g. in the next process start
  before any Compose, or a `JobIntentService`/plain thread in the crashing process with a 10 s timeout). Write `filesDir/auto-restore.txt` (time, result).
- If adb is unreachable: only log it (no loop, no retry storm).
- Never restore more than once per 10 minutes. After a successful auto-restore, the existing "restart Home once" logic must not relaunch Afterglow.
- Settings > About shows "Afterglow crashed repeatedly at <time> and gave Home back to Google TV" (+ result) when that file exists, with a "Dismiss" row.
- Unit tests: loop detection (2 crashes no, 3 in 2 min yes, 3 over 3 min no), once-per-10-min guard, restore called with the saved set (fake adb), no call
  when `disabledHomes` is empty.

## 2. Firmware update check
- Check on app start, on `ACTION_BOOT_COMPLETED`, on `ACTION_MY_PACKAGE_REPLACED`, and at most once a day: if any package in `disabledHomes` is enabled
  again (`PackageManager.getApplicationEnabledSetting`/`ApplicationInfo.enabled`), or the HOME intent no longer resolves to Afterglow, set a flag.
- When the flag is set and Afterglow is opened: a TvDialog "A TV update brought Google TV back. Re-apply takeover?" with **Re-apply** (runs the existing
  verified takeover flow with all its checks) and **Not now** (asks again next day). Never re-apply silently.
- Unit-test the detection logic with fakes.

## Acceptance
No SDK here: be compile-careful (imports, signatures; named args when composables gain params). Push once to `claude/afterglow-tv-launcher-g6ow3l`, no PR,
no release. One ROADMAP edit: move tasks 1-2 to "Built, awaiting a TV test".
