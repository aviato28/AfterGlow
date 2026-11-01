package com.nbansal.afterglow

import com.nbansal.afterglow.core.CrashGuard
import com.nbansal.afterglow.takeover.AutoRestore
import com.nbansal.afterglow.takeover.FirmwareCheck
import com.nbansal.afterglow.takeover.TakeoverFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Me = "com.nbansal.afterglow"
private const val Gtv = "com.google.android.apps.tv.launcherx"
private const val Wr = "com.google.android.tungsten.setupwraith"

class SafetyTest {
    @Test fun twoCrashesAreNotALoop() = assertFalse(CrashGuard.isCrashLoop(listOf(0L, 10_000L), 10_000L))

    @Test fun threeInTwoMinutesIsALoop() = assertTrue(CrashGuard.isCrashLoop(listOf(0L, 50_000L, 100_000L), 100_000L))

    @Test fun threeOverThreeMinutesIsNot() = assertFalse(CrashGuard.isCrashLoop(listOf(0L, 90_000L, 180_000L), 180_000L))

    @Test fun onceInTenMinutes() {
        assertTrue(AutoRestore.allowed(null, 1_000L))
        assertFalse(AutoRestore.allowed(1_000L, 1_000L + 9 * 60_000L))
        assertTrue(AutoRestore.allowed(1_000L, 1_000L + 10 * 60_000L))
    }

    @Test fun restoresTheSavedSet() = runBlocking {
        val tv = FakeTv(disabled = mutableSetOf(Gtv, Wr))
        val store = MemoryStore(setOf(Gtv, Wr))
        var marked = false
        val out = AutoRestore.run(TakeoverFlow(tv, Me, store), store, null, 5L) { marked = true }
        assertNotNull(out)
        assertTrue(out!!.restored)
        assertTrue(marked)
        assertTrue(tv.disabled.isEmpty())
        assertTrue(tv.log.contains("pm enable $Gtv") && tv.log.contains("pm enable $Wr"))
    }

    @Test fun nothingWhenNoSavedPackages() = runBlocking {
        val tv = FakeTv(disabled = mutableSetOf(Gtv))
        val store = MemoryStore(emptySet())
        val out = AutoRestore.run(TakeoverFlow(tv, Me, store), store, null, 5L) {}
        assertNull(out)
        assertTrue(tv.log.isEmpty())
    }

    @Test fun nothingWithinTenMinutes() = runBlocking {
        val tv = FakeTv(disabled = mutableSetOf(Gtv))
        val store = MemoryStore(setOf(Gtv))
        assertNull(AutoRestore.run(TakeoverFlow(tv, Me, store), store, 1_000L, 2_000L) {})
        assertTrue(tv.log.isEmpty())
    }

    @Test fun firmwareDetection() {
        val saved = setOf(Gtv)
        assertFalse(FirmwareCheck.cameBack(emptySet(), { true }, "other", Me))
        assertFalse(FirmwareCheck.cameBack(saved, { false }, Me, Me))
        assertTrue(FirmwareCheck.cameBack(saved, { it == Gtv }, Me, Me))
        assertTrue(FirmwareCheck.cameBack(saved, { false }, Gtv, Me))
        assertTrue(FirmwareCheck.cameBack(saved, { false }, null, Me))
    }

    @Test fun firmwareTiming() {
        assertTrue(FirmwareCheck.checkDue(0L, 1L))
        assertFalse(FirmwareCheck.checkDue(1_000L, 1_000L + FirmwareCheck.DAY_MS - 1))
        assertTrue(FirmwareCheck.checkDue(1_000L, 1_000L + FirmwareCheck.DAY_MS))
        assertFalse(FirmwareCheck.promptDue(false, 0L, FirmwareCheck.DAY_MS * 5))
        assertTrue(FirmwareCheck.promptDue(true, 0L, FirmwareCheck.DAY_MS * 5))
        assertFalse(FirmwareCheck.promptDue(true, 1_000L, 2_000L))
    }
}
