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
        add(SettingsRow("Latest release", latest?.tag?.removePrefix("v") ?: "–") {})
        add(
            SettingsRow(
                if (available) "Download and install" else "Check for updates",
                model.updateStatus.ifEmpty { null },
            ) { if (available) model.downloadAndInstall() else model.checkForUpdates() },
        )
        add(SettingsRow(if (problem == null) "No problems recorded" else "Clear last problem") {
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
