package com.nbansal.afterglow.keys

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.panel.QuickPanel
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
        // Granting READ_LOGS can kill Afterglow: the marker re-opens this page after the restart, and the switch is already saved.
        reopenMarker(app).createNewFile()
        adb.run(AdbWhitelist.GrantLogs)
        reopenMarker(app).delete()
        KeyWatchService.start(app)
        "On. Press Settings on the remote to test."
    }

    fun switchOff() = work("Switching off…") {
        prefs.setQuickPanel(false)
        withContext(Dispatchers.Main) {
            QuickPanel.hide()
            KeyWatchService.stop(app)
        }
        adb.run(AdbWhitelist.OverlayDefault)
        reopenMarker(app).createNewFile()
        adb.run(AdbWhitelist.RevokeLogs)
        reopenMarker(app).delete()
        "Off. Afterglow's extra permissions were removed."
    }

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
        /** Present when the app was restarted in the middle of a switch: HomeActivity opens the Quick panel page again. */
        fun reopenMarker(app: android.content.Context) = File(app.filesDir, "reopen-quick-panel")
    }
}
