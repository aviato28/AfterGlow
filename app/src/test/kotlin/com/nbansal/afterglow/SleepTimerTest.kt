package com.nbansal.afterglow

import com.nbansal.afterglow.keys.QuickPanelController
import com.nbansal.afterglow.panel.SleepTimer
import com.nbansal.afterglow.takeover.AdbWhitelist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private val minute = 60_000L

    @Test
    fun okCyclesOffThrough15To90AndBackToOff() {
        var end = 0L
        val seen = mutableListOf<Int>()
        repeat(7) {
            end = SleepTimer.cycle(end, nowMs = 1_000_000)
            seen += if (end == 0L) 0 else ((end - 1_000_000) / minute).toInt()
        }
        assertEquals(listOf(15, 30, 45, 60, 90, 0, 15), seen)
    }

    @Test
    fun aRunningTimerCountsAsTheSmallestStepThatCoversIt() {
        assertEquals(0, SleepTimer.stepFor(0))
        assertEquals(0, SleepTimer.stepFor(-5))
        assertEquals(15, SleepTimer.stepFor(1))
        assertEquals(15, SleepTimer.stepFor(15 * minute))
        assertEquals(30, SleepTimer.stepFor(15 * minute + 1))
        assertEquals(90, SleepTimer.stepFor(89 * minute))
        assertEquals(90, SleepTimer.stepFor(500 * minute))
        // 35 minutes left on a 45 minute timer: the next press goes to 60
        assertEquals(1_000_000 + 60 * minute, SleepTimer.cycle(1_000_000 + 35 * minute, nowMs = 1_000_000))
    }

    @Test
    fun subtitleShowsMinutesLeftRoundedUp() {
        assertEquals("Off", SleepTimer.subtitle(0, 5_000))
        assertEquals("Off", SleepTimer.subtitle(5_000, 5_000))
        assertEquals("Off in 45 min", SleepTimer.subtitle(5_000 + 45 * minute, 5_000))
        assertEquals("Off in 45 min", SleepTimer.subtitle(5_000 + 44 * minute + 1, 5_000))
        assertEquals("Off in 1 min", SleepTimer.subtitle(5_000 + 1, 5_000))
    }

    @Test
    fun theTimerIsReArmedAfterAServiceRestart() {
        val now = 10_000_000L
        val plan = SleepTimer.plan(now + 45 * minute, now)
        assertNotNull(plan)
        assertEquals(44 * minute, plan!!.warnAfterMs)
        assertEquals(45 * minute, plan.sleepAfterMs)
        // less than a minute left: warn at once, sleep when it ends
        val late = SleepTimer.plan(now + 30_000, now)!!
        assertEquals(0L, late.warnAfterMs)
        assertEquals(30_000L, late.sleepAfterMs)
    }

    @Test
    fun noTimerOrOnePastItsEndNeverTurnsTheTvOff() {
        assertNull(SleepTimer.plan(0, 1_000))
        assertNull(SleepTimer.plan(1_000, 1_000))
        assertNull(SleepTimer.plan(500, 1_000))
    }

    @Test
    fun tclChannelSwitchUsesTheWhitelistFormsAndRefusesProtectedPackages() {
        val off = QuickPanelController.channelCommand(true)
        val on = QuickPanelController.channelCommand(false)
        assertEquals("pm disable-user --user 0 com.tcl.channelplus", off)
        assertEquals("pm enable com.tcl.channelplus", on)
        assertTrue(AdbWhitelist.allows(off!!))
        assertTrue(AdbWhitelist.allows(on!!))
        assertNull(QuickPanelController.channelCommand(true, "com.android.settings"))
        assertNull(QuickPanelController.channelCommand(false, "com.android.vending"))
        assertFalse(AdbWhitelist.allows("pm disable-user --user 0 com.tcl.channelplus; reboot"))
    }
}
