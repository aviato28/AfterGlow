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
import com.nbansal.afterglow.panel.SleepTimer
import com.nbansal.afterglow.panel.SleepWarning
import com.nbansal.afterglow.panel.sleepTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches the system log for remote presses while any app is in front (Settings key -> quick panel, Netflix/Prime/learned
 * buttons -> their app). One logcat process, only while the screen is on; never restarted faster than every 5 s.
 * Writes a heartbeat line every 30 s so the reader can tell a dead connection from a quiet remote. Also runs the sleep timer.
 */
class KeyWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var beatJob: Job? = null
    private var sleepJob: Job? = null
    private var startedAtMs = 0L

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopWatching()
                else -> restartWatching() // screen on, or the lock screen was passed
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            goForeground()
            registerReceiver(
                screen,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
                },
            )
            KeyWatch.running = true
            if (getSystemService(PowerManager::class.java)?.isInteractive != false) startWatching()
            armSleepTimer()
        } catch (e: Exception) {
            CrashGuard.record(this, e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        stopWatching()
        SleepWarning.hide()
        scope.cancel()
        KeyWatch.running = false
        super.onDestroy()
    }

    /** Screen on / unlocked: a reader that is already running stays, unless it has been up for 5 s or more (never faster than that). */
    private fun restartWatching() {
        if (job?.isActive == true) {
            if (System.currentTimeMillis() - startedAtMs < KeyReader.RetryMs) return
            stopWatching()
        }
        startWatching()
    }

    private fun startWatching() {
        if (job?.isActive == true) return
        startedAtMs = System.currentTimeMillis()
        beatJob = scope.launch {
            delay(2_000)
            while (true) {
                Heartbeat.beat()
                delay(Heartbeat.EveryMs)
            }
        }
        job = scope.launch {
            try {
                KeyReader.forDevice(this@KeyWatchService).run { code ->
                    val now = System.currentTimeMillis()
                    KeyWatch.seen(code, now)
                    if (KeyWatch.learnFromLog(code, now)) return@run
                    if (!KeyWatch.serviceIgnores(code, now)) {
                        runCatching { KeyWatch.handle(this@KeyWatchService, code, now) }.onFailure { CrashGuard.record(this@KeyWatchService, it) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashGuard.record(this@KeyWatchService, e)
            }
        }
    }

    private fun stopWatching() {
        job?.cancel()
        job = null
        beatJob?.cancel()
        beatJob = null
        KeyWatch.source = "none"
    }

    /** The end time lives in DataStore, so a restarted service picks the timer up again ([SleepTimer.plan] drops one that has passed). */
    private fun armSleepTimer() {
        sleepJob = scope.launch {
            val prefs = (application as AfterglowApp).model.prefs
            KeyWatch.setSleepEnd(runCatching { prefs.state.first().sleepEndMs }.getOrDefault(0L))
            KeyWatch.sleepFlow.collectLatest { end ->
                runCatching { prefs.setSleepEnd(end) }
                SleepWarning.hide()
                val plan = SleepTimer.plan(end, System.currentTimeMillis())
                if (plan == null) {
                    if (end != 0L) KeyWatch.setSleepEnd(0L)
                } else {
                    delay(plan.warnAfterMs)
                    SleepWarning.show(this@KeyWatchService)
                    delay(plan.sleepAfterMs - plan.warnAfterMs)
                    SleepWarning.hide()
                    sleepTv(this@KeyWatchService) // before clearing: the new value would cancel this block
                    KeyWatch.setSleepEnd(0L)
                }
            }
        }
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
                runCatching {
                    if (app.model.prefs.state.first().quickPanel) {
                        start(app)
                        QuickPanelController.revokeLogsIfGranted(app)
                    }
                }
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
