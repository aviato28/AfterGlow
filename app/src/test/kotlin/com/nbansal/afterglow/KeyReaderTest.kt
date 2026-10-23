package com.nbansal.afterglow

import com.nbansal.afterglow.keys.KeyLogSource
import com.nbansal.afterglow.keys.KeyReader
import com.nbansal.afterglow.keys.KeyWatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private fun reader(readLogs: Boolean, local: Fake, adb: Fake, rounds: Int) = KeyReader(
        hasReadLogs = { readLogs },
        local = { started += "Local"; local },
        adb = { started += "Adb"; adb },
        now = { clock },
        sleep = { sleeps += it; if (sleeps.size >= rounds) throw Stop() },
    )

    private fun runUntilStop(r: KeyReader) = runBlocking { try { r.run { keys += it } } catch (_: Stop) {} }

    @Before
    fun reset() { KeyWatch.linesSeen = 0; KeyWatch.lastError = ""; KeyWatch.source = "none" }

    @Test
    fun localWhenReadLogsIsGrantedWithoutWaitingForAPress() {
        val local = Fake("Local", listOf("old", press(4062), press(83)))
        runUntilStop(reader(true, local, Fake("Adb", emptyList()), rounds = 1))
        assertEquals(listOf("Local", "Adb"), started)
        assertEquals(listOf(4062, 83), keys)
        assertEquals(3, KeyWatch.linesSeen)
        assertEquals("Local log ended", KeyWatch.lastError)
    }

    @Test
    fun adbWhenReadLogsIsMissing() {
        val local = Fake("Local", listOf("old", press(83)))
        val adb = Fake("Adb", listOf("old", press(4099)))
        runUntilStop(reader(false, local, adb, rounds = 1))
        assertEquals(0, local.runs)
        assertEquals(listOf(4099), keys)
    }

    @Test
    fun errorsRetryAdbAtMostEveryFiveSeconds() {
        val local = Fake("Local", emptyList(), error = "logcat exited (1)")
        val adb = Fake("Adb", emptyList(), error = "Connection refused")
        runUntilStop(reader(true, local, adb, rounds = 3))
        assertEquals(listOf("Local", "Adb", "Adb", "Adb"), started)
        assertEquals(2, adb.runs)
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
        val r = KeyReader({ false }, { error("unused") }, { adb }, { clock }, { sleeps += it; throw Stop() })
        runUntilStop(r)
        assertEquals(listOf(0L), sleeps)
    }
}
