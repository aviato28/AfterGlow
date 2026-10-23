package com.nbansal.afterglow.keys

import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.core.AfterglowApp
import com.nbansal.afterglow.data.ButtonMap
import com.nbansal.afterglow.home.KeyDedupe
import com.nbansal.afterglow.home.RemoteAction
import com.nbansal.afterglow.home.RemoteKeys
import com.nbansal.afterglow.home.openTvSettings
import com.nbansal.afterglow.panel.QuickPanel

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

    private val dedupe = KeyDedupe()
    private var swallowUp = KeyEvent.KEYCODE_UNKNOWN
    private var learnedAtMs = Long.MIN_VALUE / 2

    /**
     * HomeActivity hands every key here first. While learning it takes them all: the first press of a learnable button is the
     * result (even Netflix, Prime, Settings), Back cancels. The matching key-up is swallowed too so it can't navigate or launch.
     */
    fun learnerTakes(keyCode: Int, action: Int, repeatCount: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!learning) {
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

    fun seen(code: Int, nowMs: Long) { recent = (listOf(SeenKey(code, nowMs)) + recent).take(5) }

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
