package com.nbansal.afterglow.panel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nbansal.afterglow.HomeActivity
import com.nbansal.afterglow.core.CrashGuard
import com.nbansal.afterglow.keys.KeyWatch
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.ListRow
import com.nbansal.afterglow.ui.TvCanvas
import com.nbansal.afterglow.ui.focusSoon
import kotlinx.coroutines.delay

private const val PanelWidthDp = 520f
private const val CanvasWidthDp = 1920f
private const val IdleCloseMs = 10_000L

/**
 * The Settings-key side panel: an overlay window on the right edge, over whatever app is playing (the app keeps running behind).
 * Takes D-pad focus; Settings again, Back or 10 s without input closes it and focus goes back to the app. Main thread only.
 */
object QuickPanel {
    private var view: ComposeView? = null
    private var owner: PanelOwner? = null

    val isOpen get() = view != null

    fun toggle(context: Context) = if (isOpen) hide() else show(context)

    fun show(context: Context) {
        if (isOpen) return
        try {
            val wm = context.getSystemService(WindowManager::class.java)
            val widthPx = context.resources.displayMetrics.widthPixels
            val o = PanelOwner().also { it.start() }
            val v = ComposeView(context).apply {
                setViewTreeLifecycleOwner(o)
                setViewTreeSavedStateRegistryOwner(o)
                setViewTreeViewModelStoreOwner(o)
                setContent { TvCanvas { PanelContent(context, ::hide) } }
            }
            val params = WindowManager.LayoutParams(
                (widthPx * PanelWidthDp / CanvasWidthDp).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.END or Gravity.TOP }
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

/** A Compose window outside an activity needs its own lifecycle, saved state and view-model store. */
private class PanelOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

private class PanelRow(val label: String, val value: String? = null, val onKey: ((Int) -> Unit)? = null, val onClick: () -> Unit)

@Composable
private fun PanelContent(context: Context, close: () -> Unit) {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var muted by remember { mutableStateOf(audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
    var lastInput by remember { mutableLongStateOf(0L) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { first.focusSoon() }
    LaunchedEffect(lastInput) {
        delay(IdleCloseMs)
        close()
    }

    fun setVolume(step: Int) {
        val v = (volume + step).coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun open(vararg actions: String) {
        close()
        for (a in actions) {
            try {
                context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (e: Exception) {
                CrashGuard.record(context, e)
                return
            }
        }
    }

    val rows = listOf(
        PanelRow("Volume  $volume / $max", "◀ ▶", onKey = { setVolume(it) }) {},
        PanelRow(if (muted) "Unmute" else "Mute") {
            runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        },
        PanelRow("Picture") { open(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS) },
        PanelRow("Sound") { open(Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_SETTINGS) },
        PanelRow("Network") { open(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS) },
        PanelRow("Display") { open(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS) },
        PanelRow("All settings") { open(Settings.ACTION_SETTINGS) },
        PanelRow("Afterglow settings") {
            close()
            runCatching {
                context.startActivity(
                    Intent(context, HomeActivity::class.java)
                        .putExtra(HomeActivity.ExtraOpen, HomeActivity.OpenSettings)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Ag.Base.copy(alpha = 0.92f))
            .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Ag.Amber.copy(alpha = 0.22f)))
            .onPreviewKeyEvent { e ->
                lastInput = System.nanoTime()
                val code = e.nativeKeyEvent.keyCode
                when {
                    code == KeyEvent.KEYCODE_BACK -> { if (e.type == KeyEventType.KeyUp) close(); true }
                    // Settings again: same gate as the log watcher, so a press seen twice toggles only once.
                    (code == KeyEvent.KEYCODE_NOTIFICATION || code == KeyEvent.KEYCODE_SETTINGS) -> {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) KeyWatch.handle(context, code)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxHeight().padding(top = Ag.MarginY)) {
            Text("QUICK SETTINGS", style = Ag.SectionCaps, modifier = Modifier.padding(start = 48.dp, top = 20.dp))
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(rows) { i, r ->
                    var m: Modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
                    r.onKey?.let { onKey ->
                        m = m.onKeyEvent { e ->
                            val step = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                            if (step != 0 && e.type == KeyEventType.KeyDown) onKey(step)
                            step != 0
                        }
                    }
                    ListRow(r.label, m, r.value, onClick = r.onClick)
                }
            }
        }
    }
}
