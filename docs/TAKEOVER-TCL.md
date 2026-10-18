# Making Afterglow the Home button (TCL Q-series, Google TV, Android 14)

Verified on the owner's TCL 65Q6C. Setting Afterglow as the "chosen home" (`cmd package set-home-activity`) did NOT work:
Google TV still won. Disabling the Google TV home app did.

Set up once from a phone/tablet with Termux (`pkg install android-tools`), same Wi-Fi as the TV:
1. TV: Settings > System > About, press OK 7 times on "Android TV OS build". Developer options > Wireless debugging ON >
   "Pair device with pairing code".
2. `adb pair IP:PAIRING_PORT` (enter the 6-digit code), then `adb connect IP:CONNECT_PORT` (port from the main Wireless
   debugging screen; it changes).
3. List home apps (never disable the setup-recovery or Settings fallback entries):
   `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
   TCL shows: `com.google.android.apps.tv.launcherx` (Google TV), `com.google.android.tungsten.setupwraith` (setup recovery,
   leave alone), `com.nbansal.afterglow`, `com.android.tv.settings` FallbackHome (leave alone).
4. Disable Google TV home: `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`

Restore (nothing is deleted): `adb shell pm enable com.google.android.apps.tv.launcherx`
or on the TV: Settings > Apps > See all apps > Show system apps > Google TV home > Enable.

Not built: doing this from inside Afterglow (needs ADB pairing in-app). The Home-key accessibility catcher also did not work
on this TV (the service switched itself off right after enabling).
