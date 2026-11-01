package com.nbansal.afterglow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "A TV update brought Google TV back": Back counts as Not now. */
@Composable
fun FirmwareDialog(onReapply: () -> Unit, onNotNow: () -> Unit) {
    TvDialog(onNotNow) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.focusSoon() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(760.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF17120F)).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("A TV update brought Google TV back. Re-apply takeover?", style = Ag.Body, maxLines = 3)
                ListRow("Re-apply", Modifier.focusRequester(first), onClick = onReapply)
                ListRow("Not now", onClick = onNotNow)
            }
        }
    }
}
