package com.nbansal.afterglow

import android.view.KeyEvent
import com.nbansal.afterglow.takeover.HomeKeyService
import com.nbansal.afterglow.takeover.Takeover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverTest {
    private val self = "com.nbansal.afterglow"

    @Test
    fun candidatesDropSelfProtectedAndOddNames() {
        val homes = listOf(
            "com.google.android.apps.tv.launcherx", self, "com.android.settings", "com.android.providers.tv",
            "com.google.android.tvlauncher", "com.google.android.tvlauncher", "bad name; rm -rf /",
        )
        assertEquals(
            listOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher"),
            Takeover.candidates(homes, self),
        )
    }

    @Test
    fun protectedList() {
        assertTrue(Takeover.isProtected("com.android.vending"))
        assertTrue(Takeover.isProtected("com.android.providers.media"))
        assertFalse(Takeover.isProtected("com.google.android.tvlauncher"))
    }

    @Test
    fun commandsAreBuiltAndValidated() {
        assertEquals("pm disable-user --user 0 a.b.c", Takeover.disableCommand("a.b.c"))
        assertEquals("pm enable a.b.c", Takeover.enableCommand("a.b.c"))
        try {
            Takeover.disableCommand("a.b; reboot")
            org.junit.Assert.fail("should reject")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(Takeover.disableSucceeded(0, "Package a.b.c new state: disabled-user"))
        assertFalse(Takeover.disableSucceeded(1, "Exception"))
        assertTrue(Takeover.enableSucceeded(0, "Package a.b.c new state: enabled"))
    }

    @Test
    fun serviceEnabledAndStaleNotice() {
        val c = "$self/com.nbansal.afterglow.takeover.HomeKeyService"
        assertTrue(Takeover.serviceEnabled("x/y:$c", c))
        assertFalse(Takeover.serviceEnabled(null, c))
        assertTrue(Takeover.staleNotice(false, setOf("a.b")))
        assertFalse(Takeover.staleNotice(true, setOf("a.b")))
        assertFalse(Takeover.staleNotice(false, emptySet()))
    }

    @Test
    fun homeKeyIsConsumedOthersUntouched() {
        var opened = 0
        assertTrue(HomeKeyService.handleKey(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_DOWN) { opened++ })
        assertEquals(1, opened)
        assertTrue(HomeKeyService.handleKey(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_UP) { opened++ })
        assertEquals(1, opened)
        assertFalse(HomeKeyService.handleKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN) { opened++ })
        assertEquals(1, opened)
    }
}
