package com.nbansal.afterglow.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.takeover.Takeover
import com.nbansal.afterglow.takeover.TakeoverStep
import com.nbansal.afterglow.ui.Ag

/** "Home button" settings: live status plus Method B (the Home key catcher). */
@Composable
fun HomeButtonPage(model: AppModel, extraRows: List<SettingsRow> = emptyList()) {
    val t = model.takeover
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val opensNow = remember(tick, t.step) { Takeover.homeLabel(context) }
    val catcherOn = remember(tick) { Takeover.accessibilityOn(context) }

    val catcherRow = SettingsRow("Home-key catcher (other TVs)", if (catcherOn) "On" else "Off") {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { t.refreshRestoreProbe() }
    val restoreRow = SettingsRow("Restore Google TV") { t.restore() }
    val showRestore = model.state.disabledHomes.isNotEmpty() || t.probeSaysRestore
    val cancel = SettingsRow("Cancel") { t.cancel() }

    val (text, rows) = when (t.step) {
        TakeoverStep.Idle -> {
            val catcher = if (catcherOn) "The Home-key catcher is on." else "The Home-key catcher is a secondary route for TVs that allow it."
            "Home button opens: $opensNow\n$catcher" to listOf(
                SettingsRow("Make Afterglow my Home") { t.begin() },
            ) + listOfNotNull(restoreRow.takeIf { showRestore }) + catcherRow + extraRows
        }
        TakeoverStep.Confirm1 -> (
            "Afterglow will switch off the TV's own home screen apps (not uninstall them) so the Home button opens Afterglow. " +
                "It checks the result and undoes everything by itself if it does not work. " +
                "Needs USB debugging on in Developer options."
            ) to listOf(SettingsRow("Continue") { t.confirm() }, cancel)
        TakeoverStep.Confirm2 -> (
            "Are you sure? You can go back any time with Restore Google TV on this page."
            ) to listOf(SettingsRow("Yes, make Afterglow my Home") { t.confirm() }, cancel)
        TakeoverStep.Working -> t.message to listOf(SettingsRow("Please wait…") {})
        TakeoverStep.Result -> "${t.message}\nHome button opens: $opensNow" to (listOf(SettingsRow("OK") { t.cancel() }) + extraRows)
    }
    key(t.step) {
        SettingsPage(
            "Home button", rows,
            headerHeight = 260.dp,
            header = { Text(text, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 8) },
        )
    }
}
