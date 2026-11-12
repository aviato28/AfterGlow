package com.nbansal.afterglow.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.showcase.Picks
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.ui.Ag

private fun countText(n: Int, none: String = "Any"): String = if (n == 0) none else "$n chosen"

/** Settings > Look: Lite or Showcase, the TMDB key, the app that opens titles. [onEditKey] opens the key text box. */
@Composable
fun LookPage(model: AppModel, onEditKey: () -> Unit, onPickApp: () -> Unit) {
    val context = LocalContext.current
    val allowed = model.showcaseAllowed
    val openers = remember(model.state.openWith) { ShowcaseLogic.openers(context) }
    val chosen = openers.firstOrNull { it.first == model.state.openWith }?.second
    var confirmClear by remember { mutableStateOf(false) }
    // "" = the main page; "more", "less" or "lang" = a list of choices that switch on and off (Done goes back).
    var section by remember { mutableStateOf("") }
    val taste = model.showcase.taste
    if (section.isNotEmpty()) {
        val genres = model.showcase.genres.entries.sortedBy { it.value }
        val choices: List<SettingsRow> = when (section) {
            "lang" -> Picks.LANGUAGES.map { (code, name) ->
                SettingsRow(name, if (code in taste.languages) "On" else null) { model.showcase.setTaste(taste.toggleLanguage(code)) }
            }
            else -> genres.map { (id, name) ->
                val on = if (section == "more") id in taste.more else id in taste.less
                SettingsRow(name, if (on) "On" else null) {
                    model.showcase.setTaste(if (section == "more") taste.toggleMore(id) else taste.toggleLess(id))
                }
            }
        }
        val title = when (section) { "more" -> "More of"; "less" -> "Less of"; else -> "Languages" }
        val empty = if (choices.isEmpty()) listOf(SettingsRow("Genres load after the first Showcase refresh", null) {}) else emptyList()
        SettingsPage(title, listOf(SettingsRow("Done", null) { section = "" }) + choices + empty)
        return
    }
    val rows = listOf(
        SettingsRow("Home style", if (model.showcaseOn) "Showcase" else "Lite") {
            if (allowed) model.setShowcase(!model.state.showcase)
        },
        SettingsRow("TMDB key", ShowcaseLogic.maskKey(model.state.tmdbKey), onEditKey),
        SettingsRow("Test key", model.showcase.testStatus.ifEmpty { null }) { model.showcase.testKey(model.state.tmdbKey) },
        SettingsRow("Clear key", if (confirmClear) "Press again to remove the key" else null) {
            if (model.state.tmdbKey.isNotEmpty()) {
                if (confirmClear) { model.setTmdbKey(""); confirmClear = false } else confirmClear = true
            }
        },
        SettingsRow("Open titles with", chosen ?: if (openers.isEmpty()) "No app" else "Not chosen") { if (openers.isNotEmpty()) onPickApp() },
        SettingsRow("More of", countText(taste.more.size)) { section = "more" },
        SettingsRow("Less of", countText(taste.less.size)) { section = "less" },
        SettingsRow("Languages", countText(taste.languages.size, "All")) { section = "lang" },
    )
    val note = buildList {
        add(
            if (allowed) "Lite is the default. Showcase shows TMDB artwork picked from what is trending and what your streaming apps carry, and needs your own free TMDB key (themoviedb.org > Settings > API)."
            else "Showcase needs a TV with 3 GB of RAM. This TV has less, so it keeps the Lite home.",
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
