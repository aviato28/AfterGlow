package com.nbansal.afterglow.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.ui.Ag

/** Settings > Look: Lite or Showcase, the TMDB key, the app that opens titles. [onEditKey] opens the key text box. */
@Composable
fun LookPage(model: AppModel, onEditKey: () -> Unit, onPickApp: () -> Unit) {
    val context = LocalContext.current
    val allowed = model.showcaseAllowed
    val openers = remember(model.state.openWith) { ShowcaseLogic.openers(context) }
    val chosen = openers.firstOrNull { it.first == model.state.openWith }?.second
    val rows = listOf(
        SettingsRow("Home style", if (model.showcaseOn) "Showcase" else "Lite") {
            if (allowed) model.setShowcase(!model.state.showcase)
        },
        SettingsRow("TMDB key", if (model.state.tmdbKey.isEmpty()) "Not set" else "Set", onEditKey),
        SettingsRow("Test key", model.showcase.testStatus.ifEmpty { null }) { model.showcase.testKey(model.state.tmdbKey) },
        SettingsRow("Open titles with", chosen ?: if (openers.isEmpty()) "No app" else "Not chosen") { if (openers.isNotEmpty()) onPickApp() },
    )
    val note = buildList {
        add(
            if (allowed) "Lite is the default. Showcase shows this week's trending TMDB artwork and needs your own free TMDB key (themoviedb.org > Settings > API)."
            else "Showcase needs a TV with 3 GB of RAM or more. This TV has less, so it keeps the Lite home.",
        )
        if (openers.isEmpty()) add("No app on this TV opens TMDB links.")
        if (model.showcase.error.isNotEmpty()) add(model.showcase.error)
    }.joinToString("\n")
    SettingsPage(
        "Look", rows, headerHeight = 210.dp,
        header = { Text(note, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 6) },
    )
}

/** Settings > Look > Open titles with: apps that resolve a TMDB link. */
@Composable
fun OpenWithPage(model: AppModel, onPicked: () -> Unit) {
    val context = LocalContext.current
    val openers = remember { ShowcaseLogic.openers(context) }
    SettingsPage(
        "Open titles with",
        if (openers.isEmpty()) listOf(SettingsRow("No app on this TV opens TMDB links", null) {})
        else openers.map { (pkg, label) ->
            SettingsRow(label, if (pkg == model.state.openWith) "Chosen" else null) { model.setOpenWith(pkg); onPicked() }
        },
    )
}
