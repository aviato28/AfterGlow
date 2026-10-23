package com.nbansal.afterglow.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nbansal.afterglow.HomeActivity
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

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val file = File(app.filesDir, FILE_NAME)
            val now = System.currentTimeMillis()
            val restart = shouldRestart(if (file.exists()) file.lastModified() else null, now)
            runCatching { file.writeText(report(thread, error, now)) }
            if (restart) {
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
