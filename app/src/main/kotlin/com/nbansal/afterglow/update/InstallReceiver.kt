package com.nbansal.afterglow.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

/** Result of the PackageInstaller session: show the system confirm screen, or record what went wrong. */
class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater(context).clean()
            else -> {
                Updater(context).clean()
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                runCatching {
                    File(context.filesDir, "last-problem.txt").writeText("Update install failed: status $status $message")
                }
            }
        }
    }
}
