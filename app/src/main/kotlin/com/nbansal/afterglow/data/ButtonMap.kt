package com.nbansal.afterglow.data

import android.view.KeyEvent

/** Pure logic for "Remote buttons": the owner's own key code -> package mapping (unit-tested). */
object ButtonMap {
    /** Keys needed to move around Afterglow itself; they can never be learned. */
    private val Reserved = setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )

    fun isLearnable(keyCode: Int): Boolean = keyCode > KeyEvent.KEYCODE_UNKNOWN && keyCode !in Reserved

    fun set(map: Map<Int, String>, keyCode: Int, pkg: String): Map<Int, String> =
        if (isLearnable(keyCode) && pkg.isNotBlank()) map + (keyCode to pkg) else map

    fun remove(map: Map<Int, String>, keyCode: Int): Map<Int, String> = map - keyCode

    fun encode(map: Map<Int, String>): String = map.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" }

    fun decode(s: String): Map<Int, String> = s.split('\n').mapNotNull {
        val i = it.indexOf('\t')
        val code = if (i <= 0) null else it.substring(0, i).toIntOrNull()
        val pkg = if (i <= 0) "" else it.substring(i + 1).trim()
        if (code == null || pkg.isEmpty()) null else code to pkg
    }.toMap()
}
