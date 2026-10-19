package com.nbansal.afterglow.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.takeover.DebloatItem
import com.nbansal.afterglow.takeover.SpeedUpStep
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.ListRow
import com.nbansal.afterglow.ui.focusSoon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val Intro = "Switches off TV-maker apps you may not need. Nothing is uninstalled, nothing is chosen for you, " +
    "and every change can be restored here. USB debugging (Developer options) must stay on so the TV can always be put back."

/** "Speed up my TV": tick packages, confirm, apply one by one, restore any time. [extraRows] are the onboarding Next/Back. */
@Composable
fun SpeedUpPage(model: AppModel, extraRows: List<SettingsRow> = emptyList()) {
    val s = model.speedUp
    val off = model.state.disabledExtras
    val items by produceState(emptyList<DebloatItem>(), off, s.step) {
        value = if (s.step == SpeedUpStep.List) withContext(Dispatchers.IO) { s.load(off) } else emptyList()
    }
    val offLabels = remember(off) { off.sorted() }

    val rows = buildList<SettingsRow> {
        when (s.step) {
            SpeedUpStep.List -> {
                if (items.isEmpty()) add(SettingsRow("Nothing to switch off on this TV") {})
                items.forEach { add(SettingsRow((if (it.pkg in s.ticked) "☑ " else "☐ ") + it.pkg.removePrefix("com."), it.reason) { s.toggle(it.pkg) }) }
                if (s.ticked.isNotEmpty()) add(SettingsRow("Apply (${s.ticked.size})") { s.begin() })
                offLabels.forEach { pkg -> add(SettingsRow("Restore ${pkg.removePrefix("com.")}") { s.restore(pkg) }) }
                if (off.isNotEmpty()) add(SettingsRow("Restore all") { s.restore() })
            }
            SpeedUpStep.Confirm -> {
                add(SettingsRow("Yes, switch off ${s.ticked.size} app(s)") { s.apply() })
                add(SettingsRow("Cancel") { s.cancel() })
            }
            SpeedUpStep.Working -> add(SettingsRow("Please wait…") {})
            SpeedUpStep.Result -> add(SettingsRow("OK") { s.cancel() })
        }
        addAll(extraRows)
    }
    val text = when (s.step) {
        SpeedUpStep.List -> Intro
        SpeedUpStep.Confirm -> "These will be switched off, one at a time:\n" + s.ticked.sorted().joinToString("\n") { it.removePrefix("com.") }
        else -> s.message
    }
    val first = remember(s.step) { FocusRequester() }
    LaunchedEffect(s.step) { first.focusSoon() }
    Box(Modifier.fillMaxSize()) {
        Text("SPEED UP MY TV", style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        Text(text, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 9, modifier = Modifier.padding(start = 100.dp, top = 120.dp, end = 100.dp))
        LazyColumn(
            Modifier.padding(top = 360.dp).width(1500.dp),
            contentPadding = PaddingValues(start = Ag.MarginX, top = 16.dp, bottom = 60.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(rows.size) { i ->
                val r = rows[i]
                ListRow(r.label, if (i == 0) Modifier.focusRequester(first) else Modifier, r.value, onClick = r.onClick)
            }
        }
    }
}
