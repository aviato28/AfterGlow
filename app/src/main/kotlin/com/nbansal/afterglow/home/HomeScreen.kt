package com.nbansal.afterglow.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.AppTile
import com.nbansal.afterglow.ui.focusSoon
import com.nbansal.afterglow.ui.tvFocus
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TAG_ALL_APPS = "pill-all-apps"
const val TAG_SETTINGS = "pill-settings"
const val PEEK_COUNT = 9

private val PillShape = RoundedCornerShape(36.dp)
private val ShelfShape = RoundedCornerShape(40.dp)
private val CardShape = RoundedCornerShape(24.dp)
private val PeekShape = RoundedCornerShape(22.dp)

/**
 * Lite home (docs/design/v2/home-lite.png): logo and a glass pill (clock, All apps, Settings) on top, greeting, the Favourites
 * on a frosted shelf, and the first row of All apps peeking in below. [peekApps] is that first row; Down from the shelf moves into it.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    favourites: List<AppEntry> = emptyList(),
    peekApps: List<AppEntry> = emptyList(),
    userName: String = "",
    focusIndex: MutableIntState = remember { mutableIntStateOf(0) },
    homeSignal: Int = 0,
    updateAvailable: Boolean = false,
    onOpen: (AppEntry) -> Unit = {},
    onOptions: (AppEntry) -> Unit = {},
    onAllApps: () -> Unit = {},
    onSettings: () -> Unit = { },
) {
    val now by rememberNow()
    val allApps = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val requesters = remember(favourites.size) { List(favourites.size) { FocusRequester() } }
    val hasFavourites = favourites.isNotEmpty()
    var shelfFocused by remember { mutableStateOf(false) }

    // First favourite (or the saved one) gets focus; no favourites: "All apps". Home key again: back to the first tile.
    LaunchedEffect(hasFavourites, homeSignal) {
        if (hasFavourites) {
            val i = focusIndex.intValue.coerceIn(favourites.indices)
            listState.scrollToItem(i)
            requesters[i].focusSoon()
        } else {
            allApps.focusSoon()
        }
    }

    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = Ag.MarginX, end = Ag.MarginX, top = Ag.MarginY),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoMark(Modifier.padding(start = 6.dp))
            Row(
                Modifier.height(72.dp).background(Ag.PillFill, PillShape).border(1.dp, Ag.PillLine, PillShape).padding(start = 32.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatClock(now), style = Ag.Clock.copy(fontSize = 30.sp), maxLines = 1)
                Spacer(Modifier.width(24.dp))
                RoundButton(Modifier.focusRequester(allApps).testTag(TAG_ALL_APPS), onAllApps) { GridIcon() }
                Spacer(Modifier.width(8.dp))
                RoundButton(Modifier.testTag(TAG_SETTINGS), onSettings) {
                    GearIcon()
                    if (updateAvailable) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(14.dp)
                                .background(Ag.Amber, CircleShape),
                        )
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.TopStart).padding(start = Ag.MarginX, top = 240.dp, end = Ag.MarginX)) {
            Text(
                Greeting.at(now.toLocalTime()).line(userName),
                style = Ag.Greeting.copy(letterSpacing = (-1).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = Ag.Date,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }

        // Favourites shelf: frosted glass without blur (translucent layers only).
        Box(
            Modifier.padding(start = 72.dp, top = 520.dp).width(1776.dp).height(330.dp)
                .onFocusChanged { shelfFocused = it.hasFocus }
                .glass(ShelfShape),
        ) {
            Text("FAVOURITES", style = Ag.SectionCaps, modifier = Modifier.padding(start = 40.dp, top = 34.dp))
            if (hasFavourites) {
                LazyRow(
                    Modifier.padding(top = 76.dp).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    itemsIndexed(favourites, key = { _, app -> app.pkg }) { i, app ->
                        AppTile(
                            app,
                            Modifier.focusRequester(requesters[i]).onFocusChanged { if (it.isFocused) focusIndex.intValue = i },
                            width = 261.dp,
                            shape = CardShape,
                            lift = 1.08f,
                            showLabel = false,
                            onClick = { onOpen(app) },
                            onLongClick = { onOptions(app) },
                        )
                    }
                }
            } else {
                Text(
                    "No favourites yet. Open All apps, hold OK on an app and choose Add to favourites.",
                    style = Ag.Body.copy(color = Ag.Muted),
                    modifier = Modifier.padding(start = 40.dp, top = 120.dp),
                )
            }
        }
        val focusedLabel = if (shelfFocused) favourites.getOrNull(focusIndex.intValue)?.label else null
        if (focusedLabel != null) {
            Text(
                focusedLabel,
                style = Ag.Label.copy(fontSize = 26.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 112.dp, top = 866.dp, end = 112.dp),
            )
        }

        // The first row of All apps, fading out downwards.
        if (peekApps.isNotEmpty()) {
            Box(
                Modifier.padding(top = 894.dp).fillMaxWidth().height(186.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 30.dp.toPx(), endY = 150.dp.toPx()),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Ag.MarginX, vertical = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    for (slot in 0 until PEEK_COUNT) {
                        Box(Modifier.weight(1f)) {
                            peekApps.getOrNull(slot)?.let { app ->
                                AppTile(
                                    app,
                                    shape = PeekShape,
                                    showLabel = false,
                                    onClick = { onOpen(app) },
                                    onLongClick = { onOptions(app) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Frosted glass: soft shadow outside, translucent gradient fill, 1 dp light border, inset top highlight. No blur. */
private fun Modifier.glass(shape: Shape): Modifier = this
    .drawBehind {
        val r = 40.dp.toPx()
        val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
        clipPath(hole, ClipOp.Difference) {
            for (i in 5 downTo 1) {
                val g = i * 12.dp.toPx()
                drawRoundRect(
                    Color.Black.copy(alpha = 0.06f),
                    topLeft = Offset(-g, -g + 28.dp.toPx()),
                    size = Size(size.width + 2 * g, size.height + 2 * g),
                    cornerRadius = CornerRadius(r + g),
                )
            }
        }
    }
    .background(Brush.verticalGradient(listOf(Ag.Ink.copy(alpha = 0.13f), Ag.Ink.copy(alpha = 0.05f))), shape)
    .border(1.dp, Ag.Ink.copy(alpha = 0.14f), shape)
    .drawBehind {
        drawLine(
            Ag.Ink.copy(alpha = 0.22f),
            Offset(40.dp.toPx(), 1.dp.toPx()), Offset(size.width - 40.dp.toPx(), 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }

@Composable
private fun RoundButton(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(56.dp).tvFocus(CircleShape, onClick).background(Ag.Ink.copy(alpha = 0.08f), CircleShape),
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
private fun GridIcon() {
    Canvas(Modifier.size(28.dp)) {
        val cell = 11.dp.toPx()
        val step = 17.dp.toPx()
        for (row in 0..1) for (col in 0..1) {
            drawRoundRect(
                Ag.Ink, Offset(col * step, row * step), Size(cell, cell), CornerRadius(3.dp.toPx()), style = Stroke(2.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun GearIcon() {
    Canvas(Modifier.size(28.dp)) {
        drawCircle(Ag.Ink, radius = 12.dp.toPx(), style = Stroke(3.dp.toPx()))
        drawCircle(Ag.Ink, radius = 4.dp.toPx())
    }
}

/** Always 24-hour, whatever the TV's own setting says. */
private fun formatClock(now: java.time.LocalDateTime): String = now.format(DateTimeFormatter.ofPattern("HH:mm"))

fun openTvSettings(context: Context) {
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
