package com.nbansal.afterglow.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nbansal.afterglow.HomeActivity
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.AutoRestore
import com.nbansal.afterglow.takeover.DisabledStore
import com.nbansal.afterglow.takeover.TakeoverFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

/**
 * A launcher that crashes can strand the user, so: write the problem to `last-problem.txt`, restart Home once, and
 * if it crashes again within [LOOP_WINDOW_MS] hand over to the system's handler instead of looping.
 */
object CrashGuard {
    const val FILE_NAME = "last-problem.txt"
    const val LOOP_WINDOW_MS = 15_000L
    const val MAX_STACK_CHARS = 6000
    const val CRASHES_FILE = "crashes.txt"
    const val CRASH_LOOP_COUNT = 3
    const val CRASH_LOOP_WINDOW_MS = 2 * 60_000L

    /** 3 crashes (this one included) within 2 minutes. */
    fun isCrashLoop(crashTimes: List<Long>, nowMs: Long): Boolean =
        crashTimes.count { nowMs - it in 0..CRASH_LOOP_WINDOW_MS } >= CRASH_LOOP_COUNT

    /** Adds [nowMs], drops entries older than the window, keeps the file small. */
    fun recordCrash(file: File, nowMs: Long): List<Long> {
        val old = runCatching { file.readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
        val kept = (old + nowMs).filter { nowMs - it in 0..CRASH_LOOP_WINDOW_MS }.takeLast(10)
        runCatching { file.writeText(kept.joinToString("\n")) }
        return kept
    }

    /**
     * Crash loop: one bounded attempt (own thread, [AutoRestore.TIMEOUT_MS]) to give Home back to Google TV. True only when it worked.
     * Anything that goes wrong is logged and swallowed: this runs inside the crash handler and must never throw or retry.
     */
    private fun autoRestore(app: Application, nowMs: Long): Boolean {
        val out = AtomicReference<AutoRestore.Outcome?>(null)
        val worker = Thread {
            runCatching {
                val prefs = Prefs(app)
                val adb = AdbShell(app)
                val store = object : DisabledStore {
                    override suspend fun get() = prefs.state.first().disabledHomes
                    override suspend fun set(packages: Set<String>) = prefs.setDisabledHomes(packages)
                }
                val last = File(app.filesDir, AutoRestore.LAST_ATTEMPT_FILE)
                try {
                    out.set(
                        runBlocking {
                            AutoRestore.run(
                                TakeoverFlow(adb, app.packageName, store), store,
                                runCatching { last.readText().trim().toLong() }.getOrNull(), nowMs,
                            ) { runCatching { last.writeText(nowMs.toString()) } }
                        },
                    )
                } finally {
                    adb.close()
                }
            }.onFailure { Log.w("CrashGuard", "auto-restore failed", it) }
        }
        worker.isDaemon = true
        worker.start()
        runCatching { worker.join(AutoRestore.TIMEOUT_MS) }
        val result = out.get()
        if (result == null) {
            Log.w("CrashGuard", "auto-restore: nothing done or no answer in time")
            return false
        }
        if (!result.restored) {
            Log.w("CrashGuard", "auto-restore failed: ${result.text}")
            return false
        }
        runCatching { File(app.filesDir, AutoRestore.FILE_NAME).writeText("${Instant.ofEpochMilli(nowMs)}\n${result.text}") }
        return true
    }

    /** Settings > About note: (time, result) or null. */
    fun autoRestoreNote(context: Context): Pair<String, String>? = runCatching {
        File(context.filesDir, AutoRestore.FILE_NAME).takeIf { it.exists() }?.readLines()?.let { it.first() to it.drop(1).joinToString(" ") }
    }.getOrNull()

    fun dismissAutoRestoreNote(context: Context) {
        File(context.filesDir, AutoRestore.FILE_NAME).delete()
    }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val file = File(app.filesDir, FILE_NAME)
            val now = System.currentTimeMillis()
            val restart = shouldRestart(if (file.exists()) file.lastModified() else null, now)
            runCatching { file.writeText(report(thread, error, now)) }
            val loop = runCatching { isCrashLoop(recordCrash(File(app.filesDir, CRASHES_FILE), now), now) }.getOrDefault(false)
            val gaveHomeBack = loop && runCatching { autoRestore(app, now) }.getOrDefault(false)
            if (gaveHomeBack) {
                // Google TV has Home again: do not relaunch Afterglow.
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } else if (restart) {
                runCatching {
                    app.startActivity(
                        Intent(app, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /** Restart unless the previous crash was less than [LOOP_WINDOW_MS] ago. */
    fun shouldRestart(previousCrashAtMs: Long?, nowMs: Long): Boolean =
        previousCrashAtMs == null || nowMs - previousCrashAtMs >= LOOP_WINDOW_MS

    fun report(thread: Thread, error: Throwable, nowMs: Long): String =
        "${Instant.ofEpochMilli(nowMs)}\nthread: ${thread.name}\n${Log.getStackTraceString(error)}".take(MAX_STACK_CHARS)

    /** A caught problem (no crash, no restart): still shown in Settings > About. */
    fun record(context: Context, error: Throwable) {
        runCatching { File(context.filesDir, FILE_NAME).writeText(report(Thread.currentThread(), error, System.currentTimeMillis())) }
    }

    fun lastProblem(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clearProblem(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
