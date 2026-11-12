package com.nbansal.afterglow.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.nbansal.afterglow.R
import com.nbansal.afterglow.core.AfterglowApp
import com.nbansal.afterglow.core.CrashGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Foreground service that keeps the phone companion's web server up while the switch in Settings is on. */
class CompanionService : Service() {
    private var server: CompanionServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            goForeground()
            val c = (application as AfterglowApp).model.companion
            val s = CompanionServer(c.api)
            if (s.start()) {
                server = s
                running = true
                c.problem = ""
            } else {
                c.problem = "Port ${CompanionServer.Port} is busy"
                stopSelf()
            }
        } catch (e: Exception) {
            CrashGuard.record(this, e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        server?.stop()
        server = null
        running = false
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Channel, "Afterglow phone companion", NotificationManager.IMPORTANCE_MIN).apply { setSound(null, null) },
        )
        val n = Notification.Builder(this, Channel)
            .setContentTitle("Afterglow phone companion")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NotificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NotificationId, n)
    }

    companion object {
        private const val Channel = "phone_companion"
        private const val NotificationId = 8

        @Volatile var running = false
            private set

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, CompanionService::class.java)) }
                .onFailure { CrashGuard.record(context, it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompanionService::class.java))
        }

        /** Starts the service when the switch is on (at boot and when Home opens). */
        fun startIfOn(context: Context) {
            val app = context.applicationContext as AfterglowApp
            if (running) return
            CoroutineScope(Dispatchers.Main).launch {
                runCatching { if (app.model.companion.store.state.first().enabled) start(app) }
            }
        }
    }
}
