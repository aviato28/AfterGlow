package com.nbansal.afterglow

import com.nbansal.afterglow.core.CrashGuard
import com.nbansal.afterglow.home.Greeting
import com.nbansal.afterglow.ui.GlowPhase
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOfDayTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun greetingAtBoundaries() {
        assertEquals(Greeting.Night, Greeting.at(t(4, 59)))
        assertEquals(Greeting.Morning, Greeting.at(t(5, 0)))
        assertEquals(Greeting.Morning, Greeting.at(t(11, 59)))
        assertEquals(Greeting.Afternoon, Greeting.at(t(12, 0)))
        assertEquals(Greeting.Afternoon, Greeting.at(t(16, 59)))
        assertEquals(Greeting.Evening, Greeting.at(t(17, 0)))
        assertEquals(Greeting.Evening, Greeting.at(t(20, 59)))
        assertEquals(Greeting.Night, Greeting.at(t(21, 0)))
    }

    @Test
    fun greetingText() {
        assertEquals("Good morning", Greeting.at(t(8, 0)).text)
        assertEquals("Good afternoon", Greeting.at(t(13, 0)).text)
        assertEquals("Good evening", Greeting.at(t(19, 0)).text)
        assertEquals("Good night", Greeting.at(t(23, 0)).text)
    }

    @Test
    fun glowPhaseAtBoundaries() {
        assertEquals(GlowPhase.Night, GlowPhase.at(t(4, 59)))
        assertEquals(GlowPhase.Morning, GlowPhase.at(t(5, 0)))
        assertEquals(GlowPhase.Morning, GlowPhase.at(t(10, 59)))
        assertEquals(GlowPhase.Day, GlowPhase.at(t(11, 0)))
        assertEquals(GlowPhase.Day, GlowPhase.at(t(11, 59)))
        assertEquals(GlowPhase.Day, GlowPhase.at(t(12, 0)))
        assertEquals(GlowPhase.Day, GlowPhase.at(t(16, 59)))
        assertEquals(GlowPhase.Evening, GlowPhase.at(t(17, 0)))
        assertEquals(GlowPhase.Evening, GlowPhase.at(t(20, 59)))
        assertEquals(GlowPhase.Night, GlowPhase.at(t(21, 0)))
    }

    @Test
    fun crashGuardDoesNotLoop() {
        assertTrue(CrashGuard.shouldRestart(null, 1_000_000))
        assertFalse(CrashGuard.shouldRestart(1_000_000 - 14_999, 1_000_000))
        assertTrue(CrashGuard.shouldRestart(1_000_000 - 15_000, 1_000_000))
    }
}
