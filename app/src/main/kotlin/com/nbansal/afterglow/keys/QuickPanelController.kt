package com.nbansal.afterglow.keys

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.panel.QuickPanel
import com.nbansal.afterglow.panel.SleepWarning
import com.nbansal.afterglow.takeover.Takeover
import com.nbansal.afterglow.takeover.AdbException
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.takeover.TakeoverController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings > Quick panel: switches the feature on and off over the TV's own adb (Afterglow's two permissions only). */
class QuickPanelController(private val app: Application, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adb = AdbShell(app)

    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set

    fun switchOn() = work("Switching on… " + AdbShell.AllowHint) {
        adb.run(AdbWhitelist.OverlayAllow)
        prefs.setQuickPanel(true)
        // Revoking READ_LOGS (so the "access all device logs" popup can never come back) kills Afterglow if it was granted: the marker
        // re-opens this page after the restart, and the switch is already saved.
        reopenMarker(app).createNewFile()
        adb.run(AdbWhitelist.RevokeLogs)
        reopenMarker(app).delete()
        adb.run(AdbWhitelist.AllowListener)
        KeyWatchService.start(app)
        "On. Press Settings on the remote to test."
    }

    fun switchOff() = work("Switching off…") {
        prefs.setQuickPanel(false)
        withContext(Dispatchers.Main) {
            QuickPanel.hide()
            SleepWarning.hide()
            KeyWatch.setSleepEnd(0L)
            KeyWatchService.stop(app)
        }
        adb.run(AdbWhitelist.DisallowListener)
        adb.run(AdbWhitelist.OverlayDefault)
        reopenMarker(app).createNewFile()
        adb.run(AdbWhitelist.RevokeLogs)
        reopenMarker(app).delete()
        "Off. Afterglow's extra permissions were removed."
    }

    /** Remote buttons > "Stop TCL Channel opening too": true while the TCL Channel app is switched off by us. */
    fun channelOff(context: Context): Boolean = runCatching {
        !context.packageManager.getApplicationInfo(ChannelPackage, 0).enabled
    }.getOrDefault(false)

    fun channelInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getApplicationInfo(ChannelPackage, 0); true }.getOrDefault(false)

    fun setChannelOff(off: Boolean) {
        val command = channelCommand(off)
        if (command == null) { message = "That app is on the protected list and is never switched off."; return }
        work(if (off) "Switching TCL Channel off…" else "Switching TCL Channel back on…") {
            val r = adb.run(command)
            val ok = if (off) Takeover.disableSucceeded(r.exitCode, r.output) else Takeover.enableSucceeded(r.exitCode, r.output)
            if (ok) { channelTick++; if (off) "TCL Channel is off. Switch this off to bring it back." else "TCL Channel is back on." }
            else "The TV did not accept that: ${r.output.trim().take(120)}"
        }
    }

    /** Bumped after a change so pages re-read [channelOff]. */
    var channelTick by mutableIntStateOf(0); private set

    private fun work(working: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        message = working
        scope.launch {
            message = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    block()
                } catch (e: AdbException) {
                    TakeoverController.explain(e)
                } catch (e: Exception) {
                    "Something went wrong: ${e.message}"
                } finally {
                    adb.close()
                }
            }
            busy = false
        }
    }

    companion object {
        const val ChannelPackage = "com.tcl.channelplus"

        /** The existing whitelist forms, or null when the package is on the protected list. */
        fun channelCommand(off: Boolean, pkg: String = ChannelPackage): String? = when {
            Takeover.isProtected(pkg) -> null
            off -> Takeover.disableCommand(pkg)
            else -> Takeover.enableCommand(pkg)
        }

        /**
         * Older installs were granted READ_LOGS, which makes Android ask "access all device logs?" at every boot. Once the service
         * starts with the grant still present, remove it (this restarts Afterglow; the service then comes back by itself).
         */
        fun revokeLogsIfGranted(app: Application) {
            if (app.checkSelfPermission(android.Manifest.permission.READ_LOGS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                AdbShell(app).use { runCatching { it.run(AdbWhitelist.RevokeLogs) } }
            }
        }

        /** Present when the app was restarted in the middle of a switch: HomeActivity opens the Quick panel page again. */
        fun reopenMarker(app: android.content.Context) = File(app.filesDir, "reopen-quick-panel")
    }
}
