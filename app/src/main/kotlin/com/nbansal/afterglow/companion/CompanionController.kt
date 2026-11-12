package com.nbansal.afterglow.companion

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.takeover.AdbShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Settings > Phone companion: the switch, the pairing PIN and the paired phones. The web server itself lives in [CompanionService]. */
class CompanionController(private val app: Application, prefs: Prefs, apps: () -> List<AppEntry>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = CompanionStore(app)
    private val pairing = Pairing()
    private val adb = AdbShell(app)

    /** Pairing PIN while the pairing screen is open, else null. */
    var pin by mutableStateOf<String?>(null); private set

    /** Counts successful pairings, so the pairing screen can say "Phone paired". */
    var pairedEvents by mutableIntStateOf(0); private set
    var enabled by mutableStateOf(false); private set
    var phones by mutableIntStateOf(0); private set

    /** Why the server could not start (port in use), else empty. */
    var problem by mutableStateOf("")

    val api = CompanionApi(app, prefs, store, pairing, adb, apps) {
        pairedEvents++
        if (pin != null) pin = pairing.newPin() // one PIN per phone: show a fresh one for the next
    }

    init {
        scope.launch {
            store.state.collect {
                enabled = it.enabled
                phones = it.tokens.size
            }
        }
    }

    /** On stays on (also after a restart of the TV) until switched off here. */
    fun turn(on: Boolean) {
        scope.launch {
            store.setEnabled(on)
            if (on) CompanionService.start(app) else CompanionService.stop(app)
            if (!on) closePairing()
        }
    }

    /** Pairing screen opened: a new PIN, valid 10 minutes. Switches the companion on first if needed. */
    fun openPairing() {
        if (!enabled) turn(true)
        pin = pairing.newPin()
    }

    fun newPin() { pin = pairing.newPin() }

    fun closePairing() {
        pairing.clearPin()
        pin = null
    }

    fun unpairAll() {
        scope.launch { store.setTokens(emptyList()) }
    }
}
