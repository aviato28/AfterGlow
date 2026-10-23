package com.nbansal.afterglow.keys

import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.AdbWhitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    fun keyDowns(onLine: () -> Unit = {}): Flow<Int> = lines().onEach { onLine() }.drop(1).mapNotNull { KeyLog.parse(it) }
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

/** `logcat` run by Afterglow itself: only sees other apps' lines once READ_LOGS was granted over adb. */
class LocalLogcat : KeyLogSource {
    override val name = "Local"

    override fun lines(): Flow<String> = callbackFlow {
        val process = ProcessBuilder(AdbWhitelist.Logcat.split(' ')).redirectErrorStream(true).start()
        launch(Dispatchers.IO) {
            // When logcat exits (READ_LOGS gone, killed) the flow fails with its exit code so the reader falls back to Adb.
            val failure = runCatching { process.inputStream.bufferedReader().forEachLine { if ("WindowManager" in it) trySend(it) } }
                .exceptionOrNull() ?: IOException("logcat exited (${process.waitFor()})")
            channel.close(failure)
        }
        awaitClose { process.destroy() }
    }
}

/** The same command as the shell user over the TV's own debugging port: no extra permission. */
class AdbLogcat(context: Context) : KeyLogSource {
    private val adb = AdbShell(context)
    override val name = "Adb"

    override fun lines(): Flow<String> = adb.lines(AdbWhitelist.Logcat)
}

/**
 * Picks and runs the log reader. Local when READ_LOGS is granted and its logcat stays alive, else Adb (dadb, 127.0.0.1:5555).
 * No self-test: nobody presses a key while it would wait. After an exit or error the Adb reader is retried, at most once every 5 s.
 */
class KeyReader(
    private val hasReadLogs: () -> Boolean,
    private val local: () -> KeyLogSource,
    private val adb: () -> KeyLogSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Runs until cancelled; [onKey] gets each fresh press. */
    suspend fun run(onKey: (Int) -> Unit) {
        var source = if (hasReadLogs()) local() else adb()
        while (true) {
            val started = now()
            KeyWatch.source = source.name
            try {
                source.keyDowns { KeyWatch.linesSeen++ }.collect(onKey)
                KeyWatch.lastError = "${source.name} log ended"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KeyWatch.lastError = "${source.name}: ${e.message ?: e.javaClass.simpleName}"
            }
            KeyWatch.source = "none"
            source = adb()
            sleep((RetryMs - (now() - started)).coerceAtLeast(0))
        }
    }

    companion object {
        const val RetryMs = 5_000L

        fun forDevice(context: Context) = KeyReader(
            hasReadLogs = {
                context.checkSelfPermission(android.Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
            },
            local = { LocalLogcat() },
            adb = { AdbLogcat(context) },
        )
    }
}
