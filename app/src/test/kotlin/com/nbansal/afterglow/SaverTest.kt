package com.nbansal.afterglow

import com.nbansal.afterglow.saver.NightStyle
import com.nbansal.afterglow.saver.Saver
import com.nbansal.afterglow.saver.SaverFlow
import com.nbansal.afterglow.saver.SaverResult
import com.nbansal.afterglow.saver.SaverStore
import com.nbansal.afterglow.saver.SaverStyle
import com.nbansal.afterglow.takeover.Adb
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.takeover.ShellResult
import com.nbansal.afterglow.ui.GlowPhase
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun greetingAndPhaseAtBoundaries() {
        assertNull(Saver.greeting(t(4, 59)))
        assertEquals("Good morning", Saver.greeting(t(5, 0)))
        assertEquals("Good evening", Saver.greeting(t(20, 59)))
        assertNull(Saver.greeting(t(21, 0)))
        assertTrue(Saver.isNight(t(4, 59)))
        assertFalse(Saver.isNight(t(5, 0)))
        assertFalse(Saver.isNight(t(20, 59)))
        assertTrue(Saver.isNight(t(21, 0)))
        assertEquals(GlowPhase.Night, GlowPhase.at(t(21, 0)))
    }

    @Test
    fun clockIs24HourAndDateIsPlain() {
        assertEquals("07:05", Saver.clockText(t(7, 5)))
        assertEquals("21:30", Saver.clockText(t(21, 30)))
        assertEquals("00:00", Saver.clockText(t(0, 0)))
        assertEquals("Monday, 19 October", Saver.dateText(LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun nightStyleIsDimmer() {
        assertTrue(NightStyle.ClockScale == 0.6f && NightStyle.ClockAlpha == 0.55f && NightStyle.HorizonAlpha == 0.4f)
        assertFalse(NightStyle.ShowGreeting)
    }

    @Test
    fun shiftStaysOnScreen() {
        val rnd = Random(1)
        repeat(2000) {
            val left = rnd.nextInt(0, 1000).toFloat()
            val top = rnd.nextInt(0, 500).toFloat()
            val right = (left + rnd.nextInt(100, 900)).coerceAtMost(SaverStyle.Width)
            val bottom = (top + rnd.nextInt(100, 500)).coerceAtMost(SaverStyle.Height)
            val (dx, dy) = Saver.shift(rnd, left, top, right, bottom)
            assertTrue(dx in -60f..60f && dy in -40f..40f)
            assertTrue(left + dx >= 0 && right + dx <= SaverStyle.Width)
            assertTrue(top + dy >= 0 && bottom + dy <= SaverStyle.Height)
        }
        // A group touching the left/top edge can only move away from it.
        val (dx, dy) = Saver.shift(Random(2), 0f, 0f, 1920f, 1080f)
        assertEquals(0f, dx); assertEquals(0f, dy)
    }

    @Test
    fun describesTheSetting() {
        assertEquals("off", Saver.describe(null))
        assertEquals("off", Saver.describe("null"))
        assertEquals("Afterglow clock", Saver.describe(Saver.Component))
        assertEquals("Google TV ambient mode", Saver.describe("com.google.android.apps.tv.dreamx/com.google.android.apps.tv.dreamx.service.Backdrop"))
        assertEquals("something else", Saver.describe("a.b/a.b.C"))
    }

    @Test
    fun whitelistAcceptsExactlyTheSettingsCommands() {
        listOf(
            "settings get secure screensaver_components", "settings get secure screensaver_enabled",
            "settings put secure screensaver_components " + Saver.Component, "settings put secure screensaver_components a.b/.C,d.e/d.e.F\$G",
            "settings put secure screensaver_components null", "settings put secure screensaver_enabled 0", "settings put secure screensaver_enabled 1",
        ).forEach { assertTrue(it, AdbWhitelist.allows(it)) }
        listOf(
            "settings get secure adb_enabled", "settings get global screensaver_enabled", "settings put secure screensaver_enabled 2",
            "settings put secure screensaver_enabled", "settings put secure screensaver_components", "settings put secure screensaver_components nope",
            "settings put secure screensaver_components a.b/c;reboot", "settings put secure screensaver_components a.b/c d", "settings put global adb_enabled 0",
            "settings put secure install_non_market_apps 1", "settings delete secure screensaver_components", "settings list secure",
            "settings get secure screensaver_components ", "settings put secure screensaver_enabled 1;id", "settings put secure screensaver_components a/b",
        ).forEach { assertFalse("'$it'", AdbWhitelist.allows(it)) }
    }

    private class Fake(var components: String = "null", var enabled: String = "0", val accept: Boolean = true) : Adb() {
        val log = mutableListOf<String>()
        override suspend fun exec(command: String): ShellResult {
            log += command
            val p = command.split(' ')
            return when {
                command == AdbWhitelist.GetScreensaver -> ShellResult(0, components + "\n")
                command == AdbWhitelist.GetScreensaverEnabled -> ShellResult(0, enabled + "\n")
                p[3] == "screensaver_components" -> { if (accept) components = p[4]; ShellResult(0, "") }
                else -> { enabled = p[4]; ShellResult(0, "") }
            }
        }
    }

    private class Store(var value: String? = null) : SaverStore {
        override suspend fun previous() = value
        override suspend fun setPrevious(value: String?) { this.value = value }
    }

    @Test
    fun savesThePreviousValueFirstAndRestoresIt() = runBlocking {
        val adb = Fake(components = "com.google.android.apps.tv.dreamx/x.Y")
        val store = Store()
        assertTrue(SaverFlow(adb, store).use() is SaverResult.Ok)
        assertEquals("com.google.android.apps.tv.dreamx/x.Y", store.value)
        assertEquals(Saver.Component, adb.components)
        assertEquals("1", adb.enabled)
        assertTrue(SaverFlow(adb, store).undo() is SaverResult.Ok)
        assertEquals("com.google.android.apps.tv.dreamx/x.Y", adb.components)
        assertNull(store.value)
    }

    @Test
    fun usingTwiceKeepsTheFirstPreviousValue() = runBlocking {
        val adb = Fake(components = "a.b/a.b.C")
        val store = Store()
        SaverFlow(adb, store).use()
        SaverFlow(adb, store).use()
        assertEquals("a.b/a.b.C", store.value)
    }

    @Test
    fun undoWithNothingSavedSaysSo() = runBlocking {
        val adb = Fake()
        val r = SaverFlow(adb, Store()).undo()
        assertTrue(r is SaverResult.Failed)
        assertTrue(adb.log.isEmpty())
    }

    @Test
    fun putsTheOldValueBackWhenItDidNotStick() = runBlocking {
        val adb = Fake(components = "a.b/a.b.C", accept = false)
        val store = Store()
        assertTrue(SaverFlow(adb, store).use() is SaverResult.Failed)
        assertEquals("a.b/a.b.C", adb.components)
        assertEquals("a.b/a.b.C", store.value)
    }
}
