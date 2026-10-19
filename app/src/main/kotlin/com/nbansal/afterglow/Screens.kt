package com.nbansal.afterglow

/** Every screen of the app; Back goes to [parent] (Home has none and never exits). */
enum class Screen(val parent: Screen?) {
    Home(null),
    AllApps(Home),
    Settings(Home),
    Onboarding(Home),
    SpeedUp(Settings),
    HomeButton(Settings),
    Hidden(Settings),
    Packages(Settings),
    About(Settings),
}
