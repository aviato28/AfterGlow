package com.nbansal.afterglow.takeover

import android.app.Application
import android.os.Build
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

enum class SpeedUpStep { List, Confirm, Working, Result }

/** Runs "Speed up my TV" in app-lifetime scope so leaving the page never cancels it halfway. */
class SpeedUpController(private val app: Application, prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adb = AdbShell(app)
    private val flow = SpeedUpFlow(adb, object : DisabledStore {
        override suspend fun get() = prefs.state.first().disabledExtras
        override suspend fun set(packages: Set<String>) = prefs.setDisabledExtras(packages)
    })

    var step by mutableStateOf(SpeedUpStep.List); private set
    var message by mutableStateOf(""); private set
    var ticked by mutableStateOf<Set<String>>(emptySet()); private set
    private var known: Set<String> = emptySet()

    fun toggle(pkg: String) { ticked = if (pkg in ticked) ticked - pkg else ticked + pkg }
    fun begin() { if (ticked.isNotEmpty() && step == SpeedUpStep.List) step = SpeedUpStep.Confirm }
    fun cancel() { if (step != SpeedUpStep.Working) step = SpeedUpStep.List }

    /** Items for this TV: the shipped list for [Build.MANUFACTURER], installed here only. Reads assets and the package list. */
    fun load(alreadyOff: Set<String>): List<DebloatItem> {
        val text = Debloat.assetFor(Build.MANUFACTURER)
            ?.let { runCatching { app.assets.open(it).bufferedReader().use { r -> r.readText() } }.getOrNull() }
            ?: return emptyList()
        val all = Debloat.parse(text)
        known = all.map { it.pkg }.toSet()
        val installed = runCatching { app.packageManager.getInstalledPackages(0).map { it.packageName }.toSet() }.getOrDefault(emptySet())
        return Debloat.offered(all, installed, Takeover.homePackages(app.packageManager), alreadyOff)
    }

    fun apply() {
        if (step != SpeedUpStep.Confirm) return
        val chosen = ticked.toList()
        run("Working… " + AdbShell.AllowHint) { flow.apply(chosen, Takeover.homePackages(app.packageManager)) }
    }

    fun restore(pkg: String? = null) {
        if (step == SpeedUpStep.Working) return
        run("Restoring… " + AdbShell.AllowHint) { flow.restore(pkg, known) }
    }

    private fun run(working: String, block: suspend () -> SpeedUpResult) {
        step = SpeedUpStep.Working
        message = working
        scope.launch {
            val text = withContext(NonCancellable + Dispatchers.IO) {
                try {
                    describe(block())
                } catch (e: AdbException) {
                    TakeoverController.explain(e)
                } finally {
                    adb.close()
                }
            }
            ticked = emptySet()
            message = text
            step = SpeedUpStep.Result
        }
    }

    private fun describe(r: SpeedUpResult) = buildString {
        if (r.switchedOff.isNotEmpty()) append("Done: ${r.switchedOff.size} app(s) changed.")
        if (r.switchedOff.isEmpty() && r.failed.isEmpty()) append("Nothing to change.")
        if (r.stoppedEarly) append("\n" + AdbShell.NotListeningHint)
        if (r.failed.isNotEmpty()) append("\nCould not change: ${r.failed.joinToString()}")
    }
}
