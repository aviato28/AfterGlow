package com.nbansal.afterglow.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nbansal.afterglow.takeover.Takeover
import com.nbansal.afterglow.ui.Ag

/** "Home button" settings: live status plus Method B (the Home key catcher). */
@Composable
fun HomeButtonPage() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val opensNow = remember(tick) { Takeover.homeLabel(context) }
    val catcherOn = remember(tick) { Takeover.accessibilityOn(context) }

    val status = buildString {
        append("Home button opens: ").append(opensNow).append('\n')
        if (catcherOn) {
            append("Home key catcher is on. If Home still lands in Google TV, this TV doesn't pass the Home key on.")
        } else {
            append("The Home key catcher only watches the Home key and reads nothing else. Turn it on in the next screen.")
        }
    }
    SettingsPage(
        "Home button",
        listOf(
            SettingsRow("Home key catcher", if (catcherOn) "On" else "Off") {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                }
            },
        ),
        headerHeight = 180.dp,
        header = { Text(status, style = Ag.Body.copy(color = Ag.Muted), maxLines = 5) },
    )
}
