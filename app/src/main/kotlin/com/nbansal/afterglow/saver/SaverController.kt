package com.nbansal.afterglow.saver

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.takeover.AdbException
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.TakeoverController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Use Afterglow clock" / "Undo" in app-lifetime scope, so leaving the page never cancels it halfway. */
class SaverController(private val app: Application, prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adb = AdbShell(app)
    private val flow = SaverFlow(adb, object : SaverStore {
        override suspend fun previous() = prefs.state.first().saverPrevious
        override suspend fun setPrevious(value: String?) = prefs.setSaverPrevious(value)
    })

    var current by mutableStateOf(read()); private set
    var message by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    /** The setting can be read without adb. */
    private fun read() = Saver.describe(Settings.Secure.getString(app.contentResolver, "screensaver_components"))

    fun refresh() { current = read() }

    fun use() = run { flow.use() }
    fun undo() = run { flow.undo() }

    private fun run(block: suspend () -> SaverResult) {
        if (busy) return
        busy = true
        message = "Working… " + AdbShell.AllowHint
        scope.launch {
            val text = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    block().message
                } catch (e: AdbException) {
                    TakeoverController.explain(e) + "\nManual route: Settings > Device Preferences > Screen saver."
                } finally {
                    adb.close()
                }
            }
            message = text
            current = read()
            busy = false
        }
    }
}
