package com.nbansal.afterglow

import android.view.KeyEvent
import com.nbansal.afterglow.home.RemoteAction
import com.nbansal.afterglow.home.RemoteKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeysTest {
    @Test
    fun settingsKeysOpenTvSettings() {
        assertTrue(RemoteKeys.actionFor(KeyEvent.KEYCODE_SETTINGS) is RemoteAction.TvSettings)
        assertTrue(RemoteKeys.actionFor(KeyEvent.KEYCODE_NOTIFICATION) is RemoteAction.TvSettings) // 83 on the TCL remote
    }

    @Test
    fun appButtonsLaunchTheirApp() {
        assertEquals("com.netflix.ninja", (RemoteKeys.actionFor(4062) as RemoteAction.Launch).pkg)
        assertEquals("com.amazon.amazonvideo.livingroom", (RemoteKeys.actionFor(4099) as RemoteAction.Launch).pkg)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertNull(RemoteKeys.actionFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(RemoteKeys.actionFor(KeyEvent.KEYCODE_BACK))
        assertNull(RemoteKeys.actionFor(4063))
    }
}
