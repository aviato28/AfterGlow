package com.nbansal.afterglow.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.core.CrashGuard
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.update.Versions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
fun HiddenAppsPage(model: AppModel) {
    val hidden = model.hiddenApps
    SettingsPage(
        "Hidden apps",
        if (hidden.isEmpty()) listOf(SettingsRow("Nothing hidden", null) {})
        else hidden.map { app -> SettingsRow(app.label, "OK to unhide") { model.unhide(app.pkg) } },
    )
}

@Composable
fun PackagesPage() {
    val context = LocalContext.current
    val rows by produceState(listOf(SettingsRow("Loading…") {})) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledPackages(0)
                .map { (it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName) to it.packageName }
                .sortedBy { it.second }
                .map { (label, pkg) -> SettingsRow(label, pkg) {} }
        }
    }
    SettingsPage("Installed packages", rows)
}

@Composable
fun AboutPage(model: AppModel) {
    val context = LocalContext.current
    var problemVersion by remember { mutableIntStateOf(0) }
    val problem = remember(problemVersion) { CrashGuard.lastProblem(context) }
    val latest = model.latest
    val available = model.updateAvailable
    val rows = buildList {
        add(SettingsRow("Version", versionName(context)) {})
        add(
            SettingsRow(
                "Status",
                when {
                    available -> "Update available: ${latest?.tag?.removePrefix("v")}"
                    latest != null -> "Up to date"
                    else -> "Not checked yet"
                },
            ) {},
        )
        add(
            SettingsRow(
                if (available) "Download and install" else "Check for updates",
                model.updateStatus.ifEmpty { null },
            ) { if (available) model.downloadAndInstall() else model.checkForUpdates() },
        )
        add(SettingsRow(if (problem == null) "No problems so far" else "Clear last problem") {
            CrashGuard.clearProblem(context)
            problemVersion++
        })
    }
    val text = if (available) latest?.notes?.let { Versions.firstLines(it) } else problem?.lines()?.take(6)?.joinToString("\n")
    SettingsPage(
        "About", rows,
        headerHeight = if (available) 380.dp else 200.dp,
        header = text?.takeIf { it.isNotBlank() }?.let { t ->
            { Text(t, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 12) }
        },
    )
}

/** Readable name for a key code: "PROG_RED", or the bare number for vendor buttons. */
fun buttonName(keyCode: Int): String = android.view.KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

/**
 * Settings > Remote buttons. While [learning], the next press is captured before any mapping (HomeActivity and the key watcher both
 * read [com.nbansal.afterglow.keys.KeyWatch.learning]); [learned]: a code was captured, pick the app for it. Learned buttons beat the built-ins.
 */
@Composable
fun RemoteButtonsPage(model: AppModel, learning: Boolean, learned: Int?, onLearn: () -> Unit, onPicked: () -> Unit) {
    val note: String
    val rows: List<SettingsRow>
    when {
        learned != null -> {
            note = "Button ${buttonName(learned)} (code $learned): pick the app it opens."
            rows = model.visibleApps.map { app -> SettingsRow(app.label) { model.mapButton(learned, app.pkg); onPicked() } }
        }
        learning -> {
            note = "Press the button on your remote now. Back cancels.\nButtons the TV keeps for itself (Channel, YouTube) are caught too when Quick panel is on."
            rows = emptyList()
        }
        else -> {
            note = "Give a remote button an app. Works while Afterglow is on screen, or everywhere with Quick panel switched on."
            val names = model.apps.associate { it.pkg to com.nbansal.afterglow.data.Favourites.named(it, model.state.names).label }
            val qp = model.quickPanel
            val context = androidx.compose.ui.platform.LocalContext.current
            val channelOff = remember(qp.channelTick, qp.busy) { qp.channelOff(context) }
            val installed = remember(qp.channelTick) { qp.channelInstalled(context) }
            val channelRow = if (!installed) emptyList() else listOf(
                SettingsRow("Stop TCL Channel opening too", if (qp.busy) "…" else if (channelOff) "On" else "Off") {
                    if (!qp.busy) qp.setChannelOff(!channelOff)
                },
            )
            rows = listOf(SettingsRow("Learn a button", onClick = onLearn)) + channelRow +
                model.state.buttons.entries.sortedBy { it.key }.map { (code, pkg) ->
                    val builtIn = com.nbansal.afterglow.home.RemoteKeys.builtIn(code) != null
                    SettingsRow("${buttonName(code)} → ${names[pkg] ?: pkg}", if (builtIn) "OK: reset to default" else "OK to remove") {
                        model.unmapButton(code)
                    }
                }
        }
    }
    val extra = model.quickPanel.message.takeIf { it.isNotEmpty() && !learning && learned == null }
    SettingsPage("Remote buttons", rows, header = { Text(listOfNotNull(note, extra).joinToString("\n"), style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 4) })
}
