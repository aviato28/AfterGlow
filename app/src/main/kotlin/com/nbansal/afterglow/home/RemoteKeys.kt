package com.nbansal.afterglow.home

import android.view.KeyEvent

/** What a remote-control button does when Afterglow is on screen. */
sealed interface RemoteAction {
    data object TvSettings : RemoteAction
    class Launch(val pkg: String) : RemoteAction
}

/**
 * Buttons that Google TV home used to handle and that now reach Afterglow instead. The vendor codes were read from a TCL 65Q6C's input log
 * (Netflix 4062, Prime Video 4099, Settings 83). The YouTube button sends nothing to the app on that remote, so it is not listed.
 */
object RemoteKeys {
    private const val Netflix = "com.netflix.ninja"
    private const val PrimeVideo = "com.amazon.amazonvideo.livingroom"

    fun actionFor(keyCode: Int): RemoteAction? = when (keyCode) {
        KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_NOTIFICATION -> RemoteAction.TvSettings
        4062 -> RemoteAction.Launch(Netflix)
        4099 -> RemoteAction.Launch(PrimeVideo)
        else -> null
    }
}
