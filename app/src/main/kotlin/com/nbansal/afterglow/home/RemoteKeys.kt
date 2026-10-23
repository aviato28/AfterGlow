package com.nbansal.afterglow.home

import android.view.KeyEvent
import com.nbansal.afterglow.data.ButtonMap

/** What a remote-control button does. */
sealed interface RemoteAction {
    /** Settings key: the quick panel (full-screen TV settings when the panel is not allowed). */
    data object Panel : RemoteAction
    class Launch(val pkg: String) : RemoteAction
    /** Settings > Remote buttons is waiting for a press: this code was captured, nothing else happens. */
    data class Learned(val keyCode: Int) : RemoteAction
}

/**
 * Buttons that Google TV home used to handle. The vendor codes were read from a TCL 65Q6C's input log
 * (Netflix 4062, Prime Video 4099, Settings 83). The YouTube button sends nothing to the app on that remote, so it is not listed.
 */
object RemoteKeys {
    private const val Netflix = "com.netflix.ninja"
    private const val PrimeVideo = "com.amazon.amazonvideo.livingroom"

    fun builtIn(keyCode: Int): RemoteAction? = when (keyCode) {
        KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_NOTIFICATION -> RemoteAction.Panel
        4062 -> RemoteAction.Launch(Netflix)
        4099 -> RemoteAction.Launch(PrimeVideo)
        else -> null
    }

    /** The one dispatch rule: while learning, capture the code before any mapping; else the owner's mapping first, then the built-ins. */
    fun resolve(keyCode: Int, userMappings: Map<Int, String>, learning: Boolean = false): RemoteAction? = when {
        learning -> if (ButtonMap.isLearnable(keyCode)) RemoteAction.Learned(keyCode) else null
        else -> userMappings[keyCode]?.let { RemoteAction.Launch(it) } ?: builtIn(keyCode)
    }
}

/** The same press can arrive twice (Afterglow's own window and the system log): ignore a repeat of one code within [windowMs]. */
class KeyDedupe(private val windowMs: Long = 400) {
    private var lastCode = 0
    private var lastAt = Long.MIN_VALUE / 2

    fun isFirst(keyCode: Int, nowMs: Long): Boolean {
        val repeat = keyCode == lastCode && nowMs - lastAt < windowMs
        if (!repeat) { lastCode = keyCode; lastAt = nowMs }
        return !repeat
    }
}
