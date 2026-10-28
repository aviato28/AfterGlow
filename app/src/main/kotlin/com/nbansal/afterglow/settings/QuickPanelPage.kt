package com.nbansal.afterglow.settings

import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.keys.KeyWatch
import com.nbansal.afterglow.takeover.AdbShell
import com.nbansal.afterglow.ui.Ag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val Intro = "Makes the remote's Settings key open a quick panel over any app (the video keeps playing), and the Netflix, " +
    "Prime Video and learned buttons work inside other apps too. Also adds a sleep timer, a Now playing card and quick links. Uses USB debugging (Developer options); everything is undone when switched off."

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** Settings > Quick panel: the switch and a diagnostics block the owner can check without a laptop. */
@Composable
fun QuickPanelPage(model: AppModel) {
    val c = model.quickPanel
    val on = model.state.quickPanel
    val keys = KeyWatch.recent.joinToString("   ") { "${buttonName(it.code)} ${TimeFormat.format(Instant.ofEpochMilli(it.atMs))}" }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val since = KeyWatch.readerSinceMs.takeIf { it > 0 && KeyWatch.source != "none" }?.let { TimeFormat.format(Instant.ofEpochMilli(it)) } ?: "not running"
    val beat = KeyWatch.lastHeartbeatMs.takeIf { it > 0 }?.let { "${(nowMs - it) / 1000} s ago" } ?: "none yet"
    val text = buildString {
        append(c.message.ifEmpty { Intro })
        append("\n\nSource: ${KeyWatch.source}   Service: ${if (KeyWatch.running) "running" else "stopped"}   Reader up since: $since")
        append("\nLast heartbeat: $beat   Log lines seen: ${KeyWatch.linesSeen}")
        append("\nSettings presses seen: ${KeyWatch.settingsPresses}   Panels shown: ${KeyWatch.panelsShown}")
        if (KeyWatch.lastPanelError.isNotEmpty()) append("\nLast panel error: ${KeyWatch.lastPanelError}")
        if (KeyWatch.lastError.isNotEmpty()) append("\nLast reader error: ${KeyWatch.lastError}")
        append("\nLast keys: ${keys.ifEmpty { "none yet (press Settings now to test)" }}")
        append("\nAndroid hides this panel on its own Settings screens and system dialogs; that is by design.")
        if (!on && c.message.isEmpty()) append("\nIf it fails: " + AdbShell.NotListeningHint)
    }
    val rows = listOf(
        SettingsRow("Quick panel and remote keys everywhere", if (c.busy) "…" else if (on) "On" else "Off") {
            if (!c.busy) { if (on) c.switchOff() else c.switchOn() }
        },
    )
    SettingsPage(
        "Quick panel", rows,
        headerHeight = 440.dp,
        header = { Text(text, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 14) },
    )
}
