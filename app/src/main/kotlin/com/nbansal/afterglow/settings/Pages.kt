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
fun AboutPage(onClearProblem: () -> Unit, extraRows: List<SettingsRow> = emptyList()) {
    val context = LocalContext.current
    var problemVersion by remember { mutableIntStateOf(0) }
    val problem = remember(problemVersion) { CrashGuard.lastProblem(context) }
    val rows = buildList {
        add(SettingsRow("Version", versionName(context)) {})
        addAll(extraRows)
        add(SettingsRow(if (problem == null) "No problems recorded" else "Clear last problem") {
            CrashGuard.clearProblem(context)
            problemVersion++
            onClearProblem()
        })
    }
    SettingsPage("About", rows, header = problem?.let { text ->
        { Text(text.lines().take(6).joinToString("\n"), style = Ag.Body.copy(color = Ag.Muted), maxLines = 6) }
    })
}
