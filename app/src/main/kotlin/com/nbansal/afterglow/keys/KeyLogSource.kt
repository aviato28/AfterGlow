package com.nbansal.afterglow.keys

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.AdbWhitelist
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Remote key presses seen in the system log (tag WindowManager), from any app. */
interface KeyLogSource {
    val name: String

    /** Raw log lines; the first one is the last line already in the log (`-T 1`). */
    fun lines(): Flow<String>

    /** Key codes of fresh presses. The backlog line is dropped so an old press never fires again. */
    fun keyDowns(onLine: (String) -> Unit = {}): Flow<Int> = lines().onEach { onLine(it) }.drop(1).mapNotNull { KeyLog.parse(it) }
}

/** Pure parser for `handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_NOTIFICATION, ... repeatCount=0, ...}`. */
object KeyLog {
    private val Down = Regex("""handleKeyBeforeQueueing event=KeyEvent \{ action=ACTION_DOWN, keyCode=(\S+?),""")
    private val Repeat = Regex("""repeatCount=(\d+)""")

    fun parse(line: String, nameToCode: (String) -> Int = { KeyEvent.keyCodeFromString(it) }): Int? {
        val raw = Down.find(line)?.groupValues?.get(1) ?: return null
        if (Repeat.find(line)?.groupValues?.get(1) != "0") return null
        val code = raw.toIntOrNull() ?: runCatching { nameToCode(raw) }.getOrNull() ?: return null
        return code.takeIf { it > KeyEvent.KEYCODE_UNKNOWN }
    }
}

/** The same command as the shell user over the TV's own debugging port: no extra permission. */
class AdbLogcat(context: Context) : KeyLogSource {
    private val adb = AdbShell(context)
    override val name = "Adb"

    override fun lines(): Flow<String> = adb.lines(AdbWhitelist.Logcat)
}

/**
 * Runs the Adb log reader (shell user over dadb, 127.0.0.1:5555: no permission popup). No self-test: nobody presses a key while it
 * would wait. A reader that exits, errors or stops seeing the service's heartbeat line is restarted, at most once every 5 s.
 */
class KeyReader(
    private val adb: () -> KeyLogSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Runs until cancelled; [onKey] gets each fresh press. */
    suspend fun run(onKey: (Int) -> Unit) {
        while (true) {
            val started = now()
            val source = adb()
            KeyWatch.source = source.name
            KeyWatch.readerSinceMs = started
            KeyWatch.lastHeartbeatMs = 0L
            try {
                coroutineScope {
                    val watchdog = launch {
                        while (true) {
                            delay(WatchdogCheckMs)
                            if (isStale(now(), started, KeyWatch.lastHeartbeatMs)) throw IOException("no heartbeat for ${StaleMs / 1000} s")
                        }
                    }
                    source.keyDowns { line ->
                        KeyWatch.linesSeen++
                        if (Heartbeat.TEXT in line) KeyWatch.lastHeartbeatMs = now()
                    }.collect(onKey)
                    watchdog.cancel()
                }
                KeyWatch.lastError = "${source.name} log ended"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KeyWatch.lastError = "${source.name}: ${e.message ?: e.javaClass.simpleName}"
            }
            KeyWatch.source = "none"
            sleep((RetryMs - (now() - started)).coerceAtLeast(0))
        }
    }

    companion object {
        const val RetryMs = 5_000L
        const val StaleMs = 75_000L
        private const val WatchdogCheckMs = 5_000L

        /** No heartbeat line for [StaleMs] since the later of the reader start and the last heartbeat: the log connection is dead. */
        fun isStale(nowMs: Long, startedMs: Long, lastHeartbeatMs: Long): Boolean =
            nowMs - maxOf(startedMs, lastHeartbeatMs) > StaleMs

        fun forDevice(context: Context) = KeyReader(adb = { AdbLogcat(context) })
    }
}

/** The service writes one line with this tag and text every 30 s while the screen is on; the reader's own filter lets it through. */
object Heartbeat {
    const val TAG = "WindowManager"
    const val TEXT = "afterglow-heartbeat"
    const val EveryMs = 30_000L

    fun beat() { Log.d(TAG, TEXT) }
}
