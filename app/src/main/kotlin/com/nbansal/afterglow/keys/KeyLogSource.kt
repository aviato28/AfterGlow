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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Remote key presses seen in the system log (tag WindowManager), from any app. */
interface KeyLogSource {
    val name: String

    /** Raw log lines; the first one is the last line already in the log (`-T 1`). */
    fun lines(): Flow<String>

    /** Key codes of fresh presses. The backlog line is dropped so an old press never fires again. */
    fun keyDowns(): Flow<Int> = lines().drop(1).mapNotNull { KeyLog.parse(it) }
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
            runCatching { process.inputStream.bufferedReader().forEachLine { if ("WindowManager" in it) trySend(it) } }
            channel.close()
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

object KeySources {
    private const val SelfTestMs = 3_000L

    /** Local if it gives a line within 3 s, else adb; null when neither works (adb off). */
    suspend fun pick(context: Context): KeyLogSource? {
        if (context.checkSelfPermission(android.Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED) {
            val local = LocalLogcat()
            if (works(local)) return local
        }
        val adb = AdbLogcat(context)
        return adb.takeIf { works(it) }
    }

    private suspend fun works(source: KeyLogSource) =
        runCatching { withTimeoutOrNull(SelfTestMs) { source.lines().first() } != null }.getOrDefault(false)
}
