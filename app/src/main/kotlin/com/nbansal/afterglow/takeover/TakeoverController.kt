package com.nbansal.afterglow.takeover

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Home button page is showing. */
enum class TakeoverStep { Idle, Confirm1, Confirm2, Working, Result }

/**
 * Runs the takeover in app-lifetime scope and never cancels it halfway: leaving the page while it works
 * must not leave a half state.
 */
class TakeoverController(app: Application, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adb = AdbShell(app)
    private val flow = TakeoverFlow(adb, app.packageName, object : DisabledStore {
        override suspend fun get() = prefs.state.first().disabledHomes
        override suspend fun set(packages: Set<String>) = prefs.setDisabledHomes(packages)
    })

    var step by mutableStateOf(TakeoverStep.Idle); private set
    var message by mutableStateOf(""); private set
    var homeNow by mutableStateOf<String?>(null); private set

    fun begin() { if (step == TakeoverStep.Idle || step == TakeoverStep.Result) step = TakeoverStep.Confirm1 }
    fun confirm() { if (step == TakeoverStep.Confirm1) step = TakeoverStep.Confirm2 else if (step == TakeoverStep.Confirm2) start() }
    fun cancel() { if (step != TakeoverStep.Working) step = TakeoverStep.Idle }

    /** Set by [refreshRestoreProbe]: a known home app is switched off even though our list is empty. */
    var probeSaysRestore by mutableStateOf(false); private set

    fun refreshRestoreProbe() {
        // Only when this TV already accepted our key, so opening the page never pops the "Allow" question.
        if (step == TakeoverStep.Working || !adb.hasKey()) return
        scope.launch {
            probeSaysRestore = withContext(Dispatchers.IO) {
                try { flow.restoreNeeded() } catch (_: AdbException) { false } finally { adb.close() }
            }
        }
    }

    fun restore() {
        if (step == TakeoverStep.Working) return
        step = TakeoverStep.Working
        message = "Restoring… " + AdbShell.AllowHint
        scope.launch {
            val result = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    flow.restore()
                } catch (e: AdbException) {
                    FlowResult.Stopped(
                        if (e.kind == AdbException.Kind.NotListening || e.kind == AdbException.Kind.NotAllowed)
                            explain(e) + "\nManual route: " + ManualRoute
                        else "Something went wrong talking to the TV.\nManual route: $ManualRoute",
                    )
                } finally {
                    adb.close()
                }
            }
            finish(result)
        }
    }

    private fun finish(result: FlowResult) {
        when (result) {
            is FlowResult.Done -> { message = result.message; homeNow = result.homeNow; probeSaysRestore = false }
            is FlowResult.Stopped -> message = result.message
        }
        step = TakeoverStep.Result
    }

    private fun start() {
        step = TakeoverStep.Working
        message = "Working… " + AdbShell.AllowHint
        scope.launch {
            val result = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    flow.makeHome()
                } catch (e: AdbException) {
                    FlowResult.Stopped(explain(e))
                } finally {
                    adb.close()
                }
            }
            finish(result)
        }
    }

    companion object {
        const val ManualRoute =
            "Settings > Apps > See all apps > Show system apps, then open each switched-off Google TV app and press Enable."

        fun explain(e: AdbException) = when (e.kind) {
            AdbException.Kind.NotListening -> AdbShell.NotListeningHint
            AdbException.Kind.NotAllowed -> "The TV did not allow the connection in time. ${AdbShell.AllowHint} Then try again."
            else -> "Something went wrong talking to the TV. Nothing was changed."
        }
    }
}
