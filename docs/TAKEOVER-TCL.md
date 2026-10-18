# DO NOT FOLLOW: disabling Google TV home on the TCL Q-series (Android 14) led to a factory reset

What happened on the owner's TCL 65Q6C (Google TV, Android 14), in order:
1. Setting Afterglow as the "chosen home" (`cmd package set-home-activity`) printed Success but Home still went to Google TV.
2. `pm disable-user --user 0 com.google.android.apps.tv.launcherx` made Home stay on Afterglow, BUT the app looked frozen:
   `dumpsys` showed `com.google.android.tungsten.setupwraith/.RecoveryActivity` (a HOME handler with priority 1, above
   Afterglow's 0) as the top resumed activity, invisibly eating the remote's keys. This step alone was recoverable
   (`pm enable com.google.android.apps.tv.launcherx` over adb).
3. Disabling that `RecoveryActivity` component as well (advice that was wrong to give) left the TV on a black screen after
   boot. The Gemini overlay still appeared, but could not answer. Wireless debugging was not reachable after the reboot
   (`adb connect` failed on every open port), so it could not be re-enabled over adb, and the TV needed a factory reset.

Lessons:
- Never disable anything outside the HOME-handler packages you can restore, and never disable setup components.
- A priority above 0 cannot be claimed by a normal app, so Afterglow cannot outrank `RecoveryActivity` by itself.
- Wireless debugging cannot be relied on after a reboot; if you disable anything, first make sure there is a way to undo it
  that does not need the TV's screen (and test that way before relying on it).
- Do not build or document a takeover feature until it is proven safe on a spare device or an emulator.

Home-key accessibility catcher: DEAD on this TV. logcat shows
`AccessibilityManagerService: Skipping enabling service disallowed by device admin policy: com.nbansal.afterglow/...HomeKeyService`.
TCL's firmware only permits its own accessibility service (`com.tcl.tv/com.tcl.tv.service.TvAccessibilityService`), so a
third-party accessibility service switches itself off right after the user confirms. The "Allow restricted settings" option
is not involved (it is not even shown on this TV). It may still work on TVs without such a policy.
