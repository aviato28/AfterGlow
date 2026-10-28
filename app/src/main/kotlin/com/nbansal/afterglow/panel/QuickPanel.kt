package com.nbansal.afterglow.panel

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.nbansal.afterglow.ui.TvCanvas
import com.nbansal.afterglow.ui.focusSoon
import com.nbansal.afterglow.ui.tvFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

private const val PanelWidthDp = 640f
private const val EdgeDp = 36f
private const val CanvasWidthDp = 1920f
private const val IdleCloseMs = 10_000L
private const val SessionPollMs = 1_500L

/**
 * The Settings-key side panel: an overlay window on the right edge, over whatever app is playing (the app keeps running behind).
 * Takes D-pad focus; Settings again, Back or 10 s without input closes it and focus goes back to the app. Main thread only.
 * Android hides overlays on its own Settings screens and system dialogs; that is by design and cannot be changed.
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
                (widthPx * (PanelWidthDp + EdgeDp) / CanvasWidthDp).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.END or Gravity.TOP }
            wm.addView(v, params)
            view = v
            owner = o
            KeyWatch.panelsShown++
        } catch (e: Exception) {
            KeyWatch.lastPanelError = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
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
internal class PanelOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
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


// Icons from docs/design/v2/panel.html (24 x 24 view box, 2 px round strokes).
private const val IconSleep = "M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"
private const val IconTimer = "M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 9v4l2 2M9 2h6"
private const val IconWifi = "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0M12 19.5h.01"
private const val IconBluetooth = "m7 7 10 10-5 5V2l5 5L7 17"
private const val IconSound = "M8 2h8a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3zM12 10a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 6h.01"
private const val IconPicture = "M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM8 21h8M12 17v4"
private const val IconAccess = "M12 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM4 8l8 2 8-2M12 10v5l-4 7M12 15l4 7"
private const val IconSettings = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"
private const val IconVolume = "M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7"
private const val IconPrev = "M19 5v14L8 12zM5 5v14"
private const val IconNext = "M5 5v14l11-7zM19 5v14"
private const val IconPlay = "M7 4v16l13-8z"
private const val IconPause = "M8 5v14M16 5v14"

private val PanelShape = RoundedCornerShape(44.dp)
private val TileShape = RoundedCornerShape(30.dp)
private val Cream = Color(0xFFFFD9B0)
private val Dark = Color(0xFF1A1210)

@Composable
private fun PathIcon(d: String, box: Dp, color: Color, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(box)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** One media session, for the Now playing card. The controller itself is kept apart so the card does not redraw on every poll. */
private data class Playing(val pkg: String, val title: String, val app: String, val playing: Boolean)

private fun readSession(context: Context): Pair<Playing, MediaController>? {
    try {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        // Only allowed once the NowPlayingListener was enabled over adb (switch-on); otherwise a SecurityException = no card.
        val list = manager.getActiveSessions(ComponentName(context, NowPlayingListener::class.java))
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull() ?: return null
        val pm = context.packageManager
        val app = runCatching { pm.getApplicationInfo(c.packageName, 0).loadLabel(pm).toString() }.getOrDefault(c.packageName)
        val title = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: app
        return Playing(c.packageName, title, app, c.playbackState?.state == PlaybackState.STATE_PLAYING) to c
    } catch (_: SecurityException) {
        return null
    } catch (e: Exception) {
        return null
    }
}

private fun loadArt(context: Context, controller: MediaController?): ImageBitmap? {
    val md = controller?.metadata
    val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    if (art != null) return runCatching { art.asImageBitmap() }.getOrNull()
    // No picture from the session: the app's own icon, drawn small.
    return runCatching {
        val d = context.packageManager.getApplicationIcon(controller?.packageName ?: return null)
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 96, 96)
        d.draw(android.graphics.Canvas(bmp))
        bmp.asImageBitmap()
    }.getOrNull()
}

private fun wifiConnected(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}.getOrDefault(false)

@Composable
private fun PanelContent(context: Context, close: () -> Unit) {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var muted by remember { mutableStateOf(audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
    var lastInput by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var session by remember { mutableStateOf<Playing?>(null) }
    val controller = remember { arrayOfNulls<MediaController>(1) }
    val first = remember { FocusRequester() }
    val wifi = remember { wifiConnected(context) }

    LaunchedEffect(Unit) { first.focusSoon() }
    LaunchedEffect(lastInput) {
        delay(IdleCloseMs)
        close()
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val found = withContext(Dispatchers.Default) { readSession(context) }
            controller[0] = found?.second
            session = found?.first
            delay(SessionPollMs)
        }
    }

    fun setVolume(step: Int) {
        val v = (volume + step).coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun open(vararg actions: String) {
        close()
        for (a in actions.toList() + Settings.ACTION_SETTINGS) {
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

    Box(
        Modifier
            .fillMaxSize()
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
        // The glass: translucent fill, hairline, and a soft amber glow at the bottom (no real blur: low-end GPUs).
        Box(
            Modifier
                .padding(top = EdgeDp.dp, bottom = EdgeDp.dp, end = EdgeDp.dp)
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PanelWidthDp.dp)
                .clip(PanelShape)
                .background(Brush.verticalGradient(listOf(Color(0xC71C1614), Color(0xDB120E0D))))
                .background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Ag.Amber.copy(alpha = 0.22f)))
                .border(1.dp, Ag.Ink.copy(alpha = 0.12f), PanelShape),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 40.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(DateFormat.getTimeFormat(context).format(Date(nowMs)), style = Ag.Clock.copy(fontSize = 56.sp), maxLines = 1)
                    Text("Quick settings", style = Ag.Body.copy(color = Ag.Muted), modifier = Modifier.padding(bottom = 8.dp))
                }

                session?.let { s ->
                    val art = remember(s.pkg, s.title) { loadArt(context, controller[0]) }
                    NowPlayingCard(s, art, controller)
                }

                Spacer(Modifier.height(26.dp))
                VolumeRow(volume, max, muted, onStep = ::setVolume) {
                    runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
                    muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                }

                Spacer(Modifier.height(24.dp))
                val timerOn = KeyWatch.sleepEndMs > nowMs
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tile("Sleep", "Turn off now", IconSleep, false, Modifier.weight(1f).focusRequester(first)) {
                            close()
                            sleepTvInBackground(context)
                        }
                        Tile("Sleep timer", SleepTimer.subtitle(KeyWatch.sleepEndMs, nowMs), IconTimer, timerOn, Modifier.weight(1f)) {
                            val now = System.currentTimeMillis()
                            nowMs = now
                            KeyWatch.setSleepEnd(SleepTimer.cycle(KeyWatch.sleepEndMs, now))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tile("Wi-Fi", if (wifi) "Connected" else "Off", IconWifi, wifi, Modifier.weight(1f)) {
                            open(Settings.ACTION_WIFI_SETTINGS)
                        }
                        Tile("Bluetooth", "Open settings", IconBluetooth, false, Modifier.weight(1f)) {
                            open(Settings.ACTION_BLUETOOTH_SETTINGS)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tile("Sound output", "Open settings", IconSound, false, Modifier.weight(1f)) {
                            open(Settings.ACTION_SOUND_SETTINGS)
                        }
                        Tile("Picture", "Open settings", IconPicture, false, Modifier.weight(1f)) {
                            open(Settings.ACTION_DISPLAY_SETTINGS)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tile("Accessibility", "Captions, text", IconAccess, false, Modifier.weight(1f)) {
                            open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        }
                        Tile("All settings", "TV settings", IconSettings, false, Modifier.weight(1f)) {
                            open(Settings.ACTION_SETTINGS)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Afterglow settings",
                        style = Ag.Body.copy(fontSize = 22.sp, color = Ag.Muted),
                        maxLines = 1,
                        modifier = Modifier
                            .tvFocus(RoundedCornerShape(24.dp), onClick = {
                                close()
                                runCatching {
                                    context.startActivity(
                                        Intent(context, HomeActivity::class.java)
                                            .putExtra(HomeActivity.ExtraOpen, HomeActivity.OpenSettings)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text("Back to close", style = Ag.Body.copy(fontSize = 22.sp, color = Ag.Muted), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, icon: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> Cream
        on -> Ag.Amber.copy(alpha = 0.18f)
        else -> Ag.Ink.copy(alpha = 0.07f)
    }
    val line = when {
        focused -> Cream
        on -> Ag.Amber.copy(alpha = 0.35f)
        else -> Ag.Ink.copy(alpha = 0.09f)
    }
    val iconColor = if (focused) Dark else if (on) Ag.Amber else Ag.Ink
    Column(
        modifier
            .height(116.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(TileShape, onClick)
            .clip(TileShape)
            .background(fill)
            .border(1.dp, line, TileShape)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PathIcon(icon, 30.dp, iconColor)
        Column {
            Text(
                title,
                style = Ag.Body.copy(fontWeight = FontWeight.Medium, lineHeight = 26.sp, color = if (focused) Dark else Ag.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = Ag.Body.copy(fontSize = 20.sp, lineHeight = 22.sp, color = if (focused) Dark else Ag.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VolumeRow(volume: Int, max: Int, muted: Boolean, onStep: (Int) -> Unit, onToggleMute: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val fraction = if (max > 0) (volume.toFloat() / max).coerceIn(0f, 1f) else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onKeyEvent { e ->
                val step = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (step != 0 && e.type == KeyEventType.KeyDown) onStep(step)
                step != 0
            }
            .tvFocus(shape, onToggleMute)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PathIcon(IconVolume, 34.dp, if (muted) Ag.Muted else Ag.Ink)
        BoxWithConstraints(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Ag.Ink.copy(alpha = 0.14f))) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Ag.Amber, Ag.Rose))),
                )
            }
            Box(Modifier.offset(x = maxWidth * fraction - 15.dp).size(30.dp).clip(CircleShape).background(Ag.Ink))
        }
        Text(if (muted) "Mute" else volume.toString(), style = Ag.Body, maxLines = 1, modifier = Modifier.widthIn(min = 48.dp))
    }
}

@Composable
private fun NowPlayingCard(s: Playing, art: ImageBitmap?, controller: Array<MediaController?>) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .padding(top = 26.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Ag.Ink.copy(alpha = 0.07f))
            .border(1.dp, Ag.Ink.copy(alpha = 0.10f), shape)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(20.dp)).background(Ag.Violet.copy(alpha = 0.6f))) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(s.title, style = Ag.Body.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.app, style = Ag.Body.copy(fontSize = 22.sp, color = Ag.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportButton(IconPrev, false) { runCatching { controller[0]?.transportControls?.skipToPrevious() } }
            TransportButton(if (s.playing) IconPause else IconPlay, true) {
                runCatching { if (s.playing) controller[0]?.transportControls?.pause() else controller[0]?.transportControls?.play() }
            }
            TransportButton(IconNext, false) { runCatching { controller[0]?.transportControls?.skipToNext() } }
        }
    }
}

@Composable
private fun TransportButton(icon: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .tvFocus(CircleShape, onClick)
            .clip(CircleShape)
            .background(if (primary) Ag.Ink else Ag.Ink.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        PathIcon(icon, 24.dp, if (primary) Ag.Base else Ag.Ink)
    }
}
