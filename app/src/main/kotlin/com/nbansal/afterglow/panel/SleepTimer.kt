package com.nbansal.afterglow.panel

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nbansal.afterglow.core.CrashGuard
import com.nbansal.afterglow.keys.KeyWatch
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.densityOf
import com.nbansal.afterglow.ui.focusSoon
import com.nbansal.afterglow.ui.tvFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Pure rules of the sleep timer (unit-tested). The clock side runs in [com.nbansal.afterglow.keys.KeyWatchService]. */
object SleepTimer {
    /** OK on the tile cycles through these; 0 = off. */
    val Steps = listOf(0, 15, 30, 45, 60, 90)
    const val WarnMs = 60_000L
    private const val MinuteMs = 60_000L

    fun next(minutes: Int): Int = Steps[(Steps.indexOf(minutes).coerceAtLeast(0) + 1) % Steps.size]

    /** The step a running timer counts as (after a restart only the end time is known): the smallest one that still covers it. */
    fun stepFor(remainingMs: Long): Int =
        if (remainingMs <= 0) 0 else Steps.firstOrNull { it > 0 && it * MinuteMs >= remainingMs } ?: Steps.last()

    fun endFor(minutes: Int, nowMs: Long): Long = if (minutes <= 0) 0L else nowMs + minutes * MinuteMs

    /** New end time after one OK press. */
    fun cycle(endMs: Long, nowMs: Long): Long = endFor(next(stepFor(endMs - nowMs)), nowMs)

    fun subtitle(endMs: Long, nowMs: Long): String =
        if (endMs <= nowMs) "Off" else "Off in ${(endMs - nowMs + MinuteMs - 1) / MinuteMs} min"

    class Plan(val warnAfterMs: Long, val sleepAfterMs: Long)

    /** What to wait for, also after a service restart: null when off or already past (a late timer never turns the TV off on boot). */
    fun plan(endMs: Long, nowMs: Long): Plan? =
        if (endMs <= 0 || endMs <= nowMs) null else Plan((endMs - WarnMs - nowMs).coerceAtLeast(0), endMs - nowMs)
}

/** Turns the TV off (standby) with the sleep key, over the TV's own adb. */
suspend fun sleepTv(context: Context) {
    try {
        AdbShell(context.applicationContext).use { it.run(AdbWhitelist.Sleep) }
    } catch (e: Exception) {
        CrashGuard.record(context, e)
    }
}

fun sleepTvInBackground(context: Context) {
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { sleepTv(context) }
}

/** "TV turns off in 1 minute" with a focused Cancel button, as a small overlay at the bottom centre. Main thread only. */
object SleepWarning {
    private var view: ComposeView? = null
    private var owner: PanelOwner? = null

    fun show(context: Context) {
        if (view != null) return
        try {
            val wm = context.getSystemService(WindowManager::class.java)
            val o = PanelOwner().also { it.start() }
            val v = ComposeView(context).apply {
                setViewTreeLifecycleOwner(o)
                setViewTreeSavedStateRegistryOwner(o)
                setViewTreeViewModelStoreOwner(o)
                setContent {
                    CompositionLocalProvider(densityOf(LocalContext.current)) { WarningContent { KeyWatch.setSleepEnd(0L); hide() } }
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 80 }
            wm.addView(v, params)
            view = v
            owner = o
        } catch (e: Exception) {
            CrashGuard.record(context, e)
            hide()
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { v.context.getSystemService(WindowManager::class.java).removeViewImmediate(v) }
        owner?.stop()
        owner = null
    }
}

@Composable
private fun WarningContent(onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.focusSoon() }
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier.padding(24.dp).background(Color(0xE61C1614), shape).padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("TV turns off in 1 minute", style = Ag.Label.copy(color = Ag.Ink))
        Text(
            "Cancel",
            style = Ag.Label.copy(color = Color(0xFF1A1210)),
            modifier = Modifier
                .width(260.dp)
                .focusRequester(focus)
                .tvFocus(RoundedCornerShape(30.dp), onCancel)
                .background(Ag.Ring, RoundedCornerShape(30.dp))
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
