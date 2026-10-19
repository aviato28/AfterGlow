# Making Afterglow the Home button on a TCL Google TV (TCL 65Q6C, Android 14): VERIFIED RECIPE

Works on the owner's TCL 65Q6C (Google TV, Android 14): Home opens Afterglow, Back from apps returns to it, the mic button
(Assistant) works, and it survives a restart. Needs a phone/tablet with Termux (`pkg install android-tools nmap`).
Everything is DISABLED, not uninstalled, and can be undone with two commands.

## Why the usual tricks do not work on this TV
- `cmd package set-home-activity` and `cmd role add-role-holder android.app.role.HOME` succeed but Home still opens Google TV.
- The Home-key accessibility service is blocked by TCL's device policy
  (`Skipping enabling service disallowed by device admin policy`).
- Disabling only `com.google.android.apps.tv.launcherx` makes Home land on `com.google.android.tungsten.setupwraith/.RecoveryActivity`
  (a HOME handler with higher priority than any app), an invisible screen that eats the remote's keys; after a restart the TV
  shows black. Shell cannot disable that single component ("Shell cannot change component state"), but it CAN disable the
  whole package.
- So both packages must be disabled: `launcherx` and `setupwraith`.

## The safety net you must have FIRST: adb that survives a restart
Wireless debugging does NOT come back after a reboot on this TV (it says "IP address and port unavailable"). Port 5555 does
(it is open when the "USB debugging" toggle is on). Test before touching anything:
1. Developer options: turn on USB debugging (and Wireless debugging). `nmap -p 5555 <tv ip>` must say open.
2. `adb connect <tv ip>:5555`, accept "Allow USB debugging?" on the TV with "Always allow".
3. `adb reboot`, wait 3 minutes, `adb connect <tv ip>:5555` must give `device` with nothing touched on the TV.
If this fails, STOP: a bad state could not be undone without the TV's screen. On a phone hotspot the TV's address can change;
read it from the TV's Wireless debugging screen.

## Steps (set `export ANDROID_SERIAL=<tv ip>:5555` first; run commands one at a time)
1. Undo timer on the TV (10 min), check it runs (`sleep 600` line):
   `adb shell "nohup sh -c 'sleep 600; pm enable com.google.android.apps.tv.launcherx; pm enable com.google.android.tungsten.setupwraith' >/dev/null 2>&1 &"`
   `adb shell "ps -A | grep sleep"`
2. `adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith`
3. `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`
4. Self-checking line (undoes both if Home does not resolve to Afterglow):
   `R=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r'); echo "Home is now: $R"; case "$R" in *afterglow*) echo GOOD;; *) echo BAD; adb shell pm enable com.google.android.apps.tv.launcherx; adb shell pm enable com.google.android.tungsten.setupwraith;; esac`
5. If GOOD: press Home on the remote and test. Cancel the timer: `adb shell pkill -f "sleep 600"`.
6. `adb reboot`; Afterglow must come up by itself.

## Undo
`adb shell pm enable com.google.android.apps.tv.launcherx` and `adb shell pm enable com.google.android.tungsten.setupwraith`,
or on the TV: Settings > Apps > See all apps > Show system apps > enable them. A factory reset also restores both.

## Known side effects
- The remote's Settings (gear) key was handled by Google TV home; Afterglow now catches it itself while it is on screen.
- Google TV's rows, notifications panel and recommendations are gone. First-time setup cannot be re-run while `setupwraith` is off.
- Never disable anything else here without the same adb-after-restart test.

## History (what went wrong on the way, so nobody repeats it)
A first attempt with only `launcherx` disabled and no way back over adb after a restart ended in a black screen and a factory
reset. The cause was the invisible `RecoveryActivity` taking Home, not an extra command. Wireless debugging cannot be relied on
after a restart; port 5555 can.

Reference: https://github.com/Gathin23/tv-vibehack does the same thing (debug connection, `pm disable-user` on the stock
launcher, `cmd package set-home-activity`) with a restore script, on TVs where network adb stays available.
