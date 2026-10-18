package com.nbansal.afterglow.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.focusSoon
import com.nbansal.afterglow.ui.tvFocus
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ALL_APPS = "pill-all-apps"
const val TAG_SETTINGS = "pill-settings"

private val PillShape = RoundedCornerShape(36.dp)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by rememberNow()
    val allApps = remember { FocusRequester() }
    LaunchedEffect(Unit) { allApps.focusSoon() }

    Box(modifier.fillMaxSize().padding(horizontal = Ag.MarginX, vertical = Ag.MarginY)) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            LogoMark(Modifier.padding(start = 6.dp, top = 16.dp))
            Text(formatClock(context, now), style = Ag.Clock, modifier = Modifier.padding(top = 4.dp))
        }

        Column(Modifier.align(Alignment.TopStart).padding(top = 166.dp)) {
            Text(Greeting.at(now.toLocalTime()).text, style = Ag.Greeting)
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = Ag.Date,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Pill(
                Modifier.width(220.dp).focusRequester(allApps).testTag(TAG_ALL_APPS),
                onClick = { /* M1: open the all-apps grid */ },
            ) { Text("All apps", style = Ag.Label.copy(fontSize = 26.sp)) }
            Pill(
                Modifier.width(100.dp).testTag(TAG_SETTINGS),
                onClick = { openSettings(context) },
            ) { GearIcon() }
        }
    }
}

@Composable
private fun Pill(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(72.dp)
            .tvFocus(PillShape, onClick)
            .clip(PillShape)
            .background(Ag.PillFill)
            .border(1.dp, Ag.PillLine, PillShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Half sun on a horizon line plus the lowercase wordmark, drawn from the same shapes as docs/design/logo.svg. */
@Composable
private fun LogoMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 64.dp, height = 42.dp)) {
            val sun = Path().apply {
                moveTo(6.dp.toPx(), 34.dp.toPx())
                arcTo(
                    Rect(6.dp.toPx(), 8.dp.toPx(), 58.dp.toPx(), 60.dp.toPx()),
                    180f, 180f, false,
                )
                close()
            }
            drawPath(
                sun,
                Brush.verticalGradient(
                    0f to Color(0xFFFFC27A), 0.6f to Color(0xFFF0707A), 1f to Color(0xFF8A4FA8),
                    startY = 8.dp.toPx(), endY = 34.dp.toPx(),
                ),
            )
            drawRoundRect(
                Ag.Ink.copy(alpha = 0.8f), Offset(0f, 38.dp.toPx()), Size(64.dp.toPx(), 3.dp.toPx()),
                CornerRadius(1.5.dp.toPx()),
            )
        }
        Text(
            "afterglow",
            style = Ag.Label.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun GearIcon() {
    Canvas(Modifier.size(30.dp)) {
        drawCircle(Ag.Ink, radius = 13.dp.toPx(), style = Stroke(3.dp.toPx()))
        drawCircle(Ag.Ink, radius = 4.dp.toPx())
    }
}

private fun formatClock(context: Context, now: java.time.LocalDateTime): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

private fun openSettings(context: Context) {
    val intent = Intent("android.settings.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No settings app on this device: nothing to open.
        }
    }
}
