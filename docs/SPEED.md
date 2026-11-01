# Measuring speed and memory

Run these from a computer with adb connected to the TV (Windows `findstr`; on Linux/macOS use `grep TOTAL`).

## Cold start

```
adb shell am force-stop com.nbansal.afterglow
adb shell am start -W -n com.nbansal.afterglow/.HomeActivity
```

Read **TotalTime** (milliseconds). Target: under 500 ms on the TCL 65Q6C. Do it 5 times and note the median.

## Memory

With Home open, then again after Showcase, All apps and the quick panel have been used:

```
adb shell dumpsys meminfo com.nbansal.afterglow | findstr TOTAL
```

Note the TOTAL PSS (KB). Target: no growth after opening and closing apps several times.

## Results (fill in)

| Date | Build | Setup | Cold start TotalTime (ms) | TOTAL PSS (KB) | Notes |
|---|---|---|---|---|---|
| | | Lite home, no baseline profile | | | |
| | | Lite home, baseline profile | | | |
| | | Showcase on | | | |
| | | After Showcase + quick panel used | | | |
| | | 2 GB TV check | | | |

The baseline profile (`app/src/main/baseline-prof.txt`) is compiled on the TV the first time the app is installed; the profile is applied in the background, so measure
after the app has been used once and the TV has idled for a few minutes (or run `adb shell cmd package compile -m speed-profile -f com.nbansal.afterglow`).
