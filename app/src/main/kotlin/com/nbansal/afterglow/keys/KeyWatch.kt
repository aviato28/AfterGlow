package com.nbansal.afterglow.keys

import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.core.AfterglowApp
import com.nbansal.afterglow.data.ButtonMap
import com.nbansal.afterglow.home.KeyDedupe
import com.nbansal.afterglow.home.RemoteAction
import com.nbansal.afterglow.home.RemoteKeys
import com.nbansal.afterglow.home.openTvSettings
import com.nbansal.afterglow.panel.QuickPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One detected press, for the diagnostics block. */
class SeenKey(val code: Int, val atMs: Long)

/**
 * Shared by [KeyWatchService], HomeActivity and the panel (same process, main thread): every remote key goes through [handle],
 * and the setup page reads the diagnostics from here.
 */
object KeyWatch {
    /** Settings > Remote buttons is waiting for a press. */
    var learning by mutableStateOf(false)
    var learned by mutableStateOf<Int?>(null)

    var source by mutableStateOf("none")
    var running by mutableStateOf(false)
    var linesSeen by mutableIntStateOf(0)
    var lastError by mutableStateOf("")
    var recent by mutableStateOf<List<SeenKey>>(emptyList()); private set

    // Diagnostics (Settings > Quick panel).
    var readerSinceMs by mutableLongStateOf(0L)
    var lastHeartbeatMs by mutableLongStateOf(0L)
    var settingsPresses by mutableIntStateOf(0)
    var panelsShown by mutableIntStateOf(0)
    var lastPanelError by mutableStateOf("")

    /** Sleep timer end (epoch ms), 0 = off. The panel sets it; [KeyWatchService] persists and arms it. */
    var sleepEndMs by mutableLongStateOf(0L); private set
    private val sleepRequests = MutableStateFlow(0L)
    /** Plain flow for the service (a snapshot flow needs a running Compose to notice changes). */
    val sleepFlow: StateFlow<Long> get() = sleepRequests

    fun setSleepEnd(ms: Long) { sleepEndMs = ms; sleepRequests.value = ms }

    private val dedupe = KeyDedupe()
    private var swallowUp = KeyEvent.KEYCODE_UNKNOWN
    private var learnedAtMs = Long.MIN_VALUE / 2

    /**
     * HomeActivity hands every key here first. While learning it takes them all: the first press of a learnable button is the
     * result (even Netflix, Prime, Settings), Back cancels. The matching key-up is swallowed too so it can't navigate or launch.
     */
    fun learnerTakes(keyCode: Int, action: Int, repeatCount: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!learning) {
            // The log got this press first and learned it: the app's own copy must not also run the button.
            if (action == KeyEvent.ACTION_DOWN && keyCode == learned && nowMs - learnedAtMs < LogEchoMs) {
                swallowUp = keyCode
                return true
            }
            if (action != KeyEvent.ACTION_UP || keyCode != swallowUp) return false
            swallowUp = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                learning = false
                swallowUp = keyCode
            } else if (ButtonMap.isLearnable(keyCode)) {
                learned = keyCode
                learning = false
                swallowUp = keyCode
                learnedAtMs = nowMs
            }
        }
        return true
    }

    /** The service skips log keys while learning, and the late log copy of the press that was just learned. */
    fun serviceIgnores(keyCode: Int, nowMs: Long): Boolean =
        learning || (keyCode == learned && nowMs - learnedAtMs < LogEchoMs)

    private const val LogEchoMs = 2_000L

    /** Keys from the log that never count as "the button" while learning (moving around, OK, Back). */
    private val LogNavigation = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
    )

    /**
     * Learn a button also listens to the log: keys the TV handles itself (Channel, YouTube) never reach the app. The first
     * non-navigation key from either path wins; true when this press was the one learned.
     */
    fun learnFromLog(keyCode: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!learning || keyCode in LogNavigation || !ButtonMap.isLearnable(keyCode)) return false
        learned = keyCode
        learning = false
        learnedAtMs = nowMs
        return true
    }

    fun seen(code: Int, nowMs: Long) {
        recent = (listOf(SeenKey(code, nowMs)) + recent).take(5)
        if (code == KeyEvent.KEYCODE_NOTIFICATION || code == KeyEvent.KEYCODE_SETTINGS) settingsPresses++
    }

    /** Runs the action for [keyCode]; false when the key means nothing to Afterglow (the caller lets it through). */
    fun handle(context: Context, keyCode: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        val model = (context.applicationContext as AfterglowApp).model
        val action = RemoteKeys.resolve(keyCode, model.state.buttons, learning) ?: return false
        if (!dedupe.isFirst(keyCode, nowMs)) return true
        when (action) {
            is RemoteAction.Learned -> { learned = action.keyCode; learning = false }
            is RemoteAction.Launch -> { QuickPanel.hide(); model.launch(action.pkg) }
            RemoteAction.Panel ->
                if (Settings.canDrawOverlays(context)) QuickPanel.toggle(context.applicationContext) else openTvSettings(context)
        }
        return true
    }
}
