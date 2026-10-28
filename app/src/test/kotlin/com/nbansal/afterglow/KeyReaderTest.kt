package com.nbansal.afterglow

import com.nbansal.afterglow.keys.KeyLogSource
import com.nbansal.afterglow.keys.KeyReader
import com.nbansal.afterglow.keys.KeyWatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyReaderTest {
    private class Stop : RuntimeException()

    private fun press(code: Int) =
        "D/WindowManager( 1): handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN, keyCode=$code, scanCode=0, repeatCount=0, }"

    /** Emits [lines] then ends, or fails with [error]. */
    private class Fake(override val name: String, private val lines: List<String>, private val error: String? = null) : KeyLogSource {
        var runs = 0
        override fun lines(): Flow<String> = flow {
            runs++
            lines.forEach { emit(it) }
            error?.let { throw IllegalStateException(it) }
        }
    }

    private val started = mutableListOf<String>()
    private val sleeps = mutableListOf<Long>()
    private val keys = mutableListOf<Int>()
    private var clock = 0L

    private fun reader(adb: Fake, rounds: Int) = KeyReader(
        adb = { started += "Adb"; adb },
        now = { clock },
        sleep = { sleeps += it; if (sleeps.size >= rounds) throw Stop() },
    )

    private fun runUntilStop(r: KeyReader) = runBlocking { try { r.run { keys += it } } catch (_: Stop) {} }

    @Before
    fun reset() { KeyWatch.linesSeen = 0; KeyWatch.lastError = ""; KeyWatch.source = "none"; KeyWatch.lastHeartbeatMs = 0L }

    @Test
    fun alwaysTheAdbReaderAndTheBacklogLineIsDropped() {
        val adb = Fake("Adb", listOf("old", press(4099), press(83)))
        runUntilStop(reader(adb, rounds = 2))
        assertEquals(listOf("Adb", "Adb"), started)
        assertEquals(listOf(4099, 83, 4099, 83), keys)
        assertEquals(6, KeyWatch.linesSeen)
        assertEquals("Adb log ended", KeyWatch.lastError)
    }

    @Test
    fun errorsRetryAtMostEveryFiveSeconds() {
        val adb = Fake("Adb", emptyList(), error = "Connection refused")
        runUntilStop(reader(adb, rounds = 3))
        assertEquals(listOf("Adb", "Adb", "Adb"), started)
        assertEquals(3, adb.runs)
        assertEquals(listOf(5_000L, 5_000L, 5_000L), sleeps)
        assertEquals("Adb: Connection refused", KeyWatch.lastError)
        assertEquals("none", KeyWatch.source)
    }

    @Test
    fun noExtraWaitAfterALongRun() {
        val adb = object : KeyLogSource {
            override val name = "Adb"
            override fun lines() = flow { emit("old"); clock += 60_000 }
        }
        val r = KeyReader({ adb }, { clock }, { sleeps += it; throw Stop() })
        runUntilStop(r)
        assertEquals(listOf(0L), sleeps)
    }

    @Test
    fun aHeartbeatLineIsRecordedButNeverTreatedAsAKey() {
        clock = 123_000
        val adb = Fake("Adb", listOf("old", "D/WindowManager( 9): afterglow-heartbeat", press(4062)))
        runUntilStop(reader(adb, rounds = 1))
        assertEquals(123_000L, KeyWatch.lastHeartbeatMs)
        assertEquals(listOf(4062), keys)
    }

    @Test
    fun aReaderWithoutHeartbeatForSeventyFiveSecondsIsStale() {
        assertFalse(KeyReader.isStale(nowMs = 75_000, startedMs = 0, lastHeartbeatMs = 0))
        assertTrue(KeyReader.isStale(nowMs = 75_001, startedMs = 0, lastHeartbeatMs = 0))
        assertFalse("a fresh heartbeat resets the clock", KeyReader.isStale(nowMs = 100_000, startedMs = 0, lastHeartbeatMs = 40_000))
        assertTrue(KeyReader.isStale(nowMs = 120_000, startedMs = 0, lastHeartbeatMs = 40_000))
        assertTrue("a new reader counts from its own start", KeyReader.isStale(nowMs = 200_000, startedMs = 100_000, lastHeartbeatMs = 10_000))
        assertFalse(KeyReader.isStale(nowMs = 160_000, startedMs = 100_000, lastHeartbeatMs = 10_000))
    }
}
