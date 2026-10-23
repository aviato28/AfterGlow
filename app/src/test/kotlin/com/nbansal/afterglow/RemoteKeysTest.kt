package com.nbansal.afterglow

import android.view.KeyEvent
import com.nbansal.afterglow.home.KeyDedupe
import com.nbansal.afterglow.home.RemoteAction
import com.nbansal.afterglow.home.RemoteKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeysTest {
    @Test
    fun settingsKeysOpenThePanel() {
        assertEquals(RemoteAction.Panel, RemoteKeys.resolve(KeyEvent.KEYCODE_SETTINGS, emptyMap()))
        assertEquals(RemoteAction.Panel, RemoteKeys.resolve(KeyEvent.KEYCODE_NOTIFICATION, emptyMap())) // 83 on the TCL remote
    }

    @Test
    fun appButtonsLaunchTheirApp() {
        assertEquals("com.netflix.ninja", (RemoteKeys.resolve(4062, emptyMap()) as RemoteAction.Launch).pkg)
        assertEquals("com.amazon.amazonvideo.livingroom", (RemoteKeys.resolve(4099, emptyMap()) as RemoteAction.Launch).pkg)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertNull(RemoteKeys.resolve(KeyEvent.KEYCODE_DPAD_CENTER, emptyMap()))
        assertNull(RemoteKeys.resolve(KeyEvent.KEYCODE_BACK, emptyMap()))
        assertNull(RemoteKeys.resolve(4063, emptyMap()))
    }

    @Test
    fun userMappingBeatsBuiltIns() {
        assertEquals("my.app", (RemoteKeys.resolve(4062, mapOf(4062 to "my.app")) as RemoteAction.Launch).pkg)
        assertEquals("my.app", (RemoteKeys.resolve(83, mapOf(83 to "my.app")) as RemoteAction.Launch).pkg)
        assertEquals("other.app", (RemoteKeys.resolve(183, mapOf(183 to "other.app")) as RemoteAction.Launch).pkg)
    }

    @Test
    fun learnModeReturnsTheCodeOfABuiltInKey() {
        assertEquals(RemoteAction.Learned(4062), RemoteKeys.resolve(4062, emptyMap(), learning = true))
        assertEquals(RemoteAction.Learned(83), RemoteKeys.resolve(83, mapOf(83 to "my.app"), learning = true))
        assertNull(RemoteKeys.resolve(KeyEvent.KEYCODE_DPAD_UP, emptyMap(), learning = true))
    }

    @Test
    fun samePressSeenTwiceRunsOnce() {
        val d = KeyDedupe()
        assertTrue(d.isFirst(83, 1_000))
        assertFalse(d.isFirst(83, 1_300))
        assertTrue(d.isFirst(4062, 1_350))
        assertTrue(d.isFirst(83, 1_400))
        assertTrue(d.isFirst(83, 1_900))
    }
}
