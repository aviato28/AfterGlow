package com.nbansal.afterglow.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.ui.Ag

/** Screensaver page: shows the current one in plain words; also used as an onboarding step via [extraRows]. */
@Composable
fun ScreensaverPage(model: AppModel, extraRows: List<SettingsRow> = emptyList()) {
    val s = model.saver
    val context = LocalContext.current
    LaunchedEffect(Unit) { s.refresh() }
    val rows = buildList {
        add(SettingsRow("Use Afterglow clock") { s.use() })
        add(SettingsRow("Undo") { s.undo() })
        add(SettingsRow("Open screen saver settings") {
            runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        addAll(extraRows)
    }
    val text = "Now: ${s.current}\n" + s.message.ifEmpty {
        "The screensaver starts after the TV's own timeout, which is set in the TV's Settings > Device Preferences > Screen saver. " +
            "Changing it here needs USB debugging (Developer options) to be on."
    }
    SettingsPage("Screensaver", rows, headerHeight = 220.dp, header = {
        Text(text, style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 6)
    })
}
