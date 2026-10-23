package com.nbansal.afterglow.keys

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.nbansal.afterglow.R
import com.nbansal.afterglow.core.AfterglowApp
import com.nbansal.afterglow.core.CrashGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the system log for remote presses while any app is in front (Settings key -> quick panel, Netflix/Prime/learned
 * buttons -> their app). One logcat process, only while the screen is on; never restarted faster than every 5 s.
 */
class KeyWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) startWatching() else stopWatching()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            goForeground()
            registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
            KeyWatch.running = true
            if (getSystemService(PowerManager::class.java)?.isInteractive != false) startWatching()
        } catch (e: Exception) {
            CrashGuard.record(this, e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        stopWatching()
        scope.cancel()
        KeyWatch.running = false
        super.onDestroy()
    }

    private fun startWatching() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val started = System.currentTimeMillis()
                var found = false
                try {
                    val source = KeySources.pick(this@KeyWatchService)
                    KeyWatch.source = source?.name ?: "none"
                    found = source != null
                    source?.keyDowns()?.collect { code ->
                        val now = System.currentTimeMillis()
                        KeyWatch.seen(code, now)
                        runCatching { KeyWatch.handle(this@KeyWatchService, code, now) }.onFailure { CrashGuard.record(this@KeyWatchService, it) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashGuard.record(this@KeyWatchService, e)
                }
                KeyWatch.source = "none"
                // The log ended (or nothing works yet): wait before trying again, longer when no source works at all.
                val wait = if (found) MinRestartMs - (System.currentTimeMillis() - started) else NoSourceRetryMs
                delay(wait.coerceAtLeast(0))
            }
        }
    }

    private fun stopWatching() {
        job?.cancel()
        job = null
        KeyWatch.source = "none"
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Channel, "Afterglow remote keys", NotificationManager.IMPORTANCE_MIN).apply { setSound(null, null) },
        )
        val n = Notification.Builder(this, Channel)
            .setContentTitle("Afterglow remote keys")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NotificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NotificationId, n)
    }

    companion object {
        private const val Channel = "remote_keys"
        private const val NotificationId = 7
        private const val MinRestartMs = 5_000L
        private const val NoSourceRetryMs = 60_000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, KeyWatchService::class.java)) }
                .onFailure { CrashGuard.record(context, it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeyWatchService::class.java))
        }

        /** Starts the service when the owner switched the feature on. */
        fun startIfOn(context: Context, done: () -> Unit = {}) {
            val app = context.applicationContext as AfterglowApp
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { if (app.model.prefs.state.first().quickPanel) start(app) }
                done()
            }
        }
    }
}

/** After a restart of the TV: back on if the switch is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            KeyWatchService.startIfOn(context) { pending.finish() }
        }
    }
}
