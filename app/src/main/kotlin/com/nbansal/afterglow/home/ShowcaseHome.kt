package com.nbansal.afterglow.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.showcase.ShowImage
import com.nbansal.afterglow.showcase.ShowTitle
import com.nbansal.afterglow.showcase.ShowcaseImages
import com.nbansal.afterglow.showcase.Tmdb
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.tvFocus
import kotlinx.coroutines.delay

const val ROTATE_MS = 15_000L
const val FADE_MS = 400

/** What the Showcase hero needs. [resumed]: Home is in front (rotation stops otherwise). [onWatch] returns a message, or null when sent. */
class ShowcaseUi(
    val titles: List<ShowTitle>,
    val images: ShowcaseImages,
    val trim: Int,
    val resumed: Boolean,
    val onWatch: (ShowTitle) -> String?,
)

private val ButtonShape = RoundedCornerShape(36.dp)

/**
 * Backdrop, shades, title, meta, overview, Watch / Next, dots and credit (docs/design/v2/home-showcase.png). Rotates every 15 s
 * with a 400 ms crossfade, only while Home is resumed and focus is in the hero. Only the current and next bitmap are held.
 */
@Composable
fun ShowcaseHero(ui: ShowcaseUi, watch: FocusRequester) {
    val titles = ui.titles
    var index by remember(titles) { mutableIntStateOf(0) }
    val safe = index.coerceIn(titles.indices)
    val title = titles[safe]
    var heroFocused by remember { mutableStateOf(false) }
    var notice by remember(safe) { mutableStateOf("") }
    var current by remember { mutableStateOf<ShowImage?>(null) }
    var previous by remember { mutableStateOf<ShowImage?>(null) }
    var seenTrim by remember { mutableIntStateOf(ui.trim) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(safe, titles, ui.trim) {
        if (seenTrim != ui.trim) {
            seenTrim = ui.trim
            current = null
            previous = null
        }
        val next = titles[(safe + 1) % titles.size]
        val keep = setOf(title.id, next.id)
        val loaded = ui.images.load(title, keep)
        if (loaded != null && loaded !== current) {
            previous = current
            current = loaded
            if (previous != null) {
                fade.snapTo(0f)
                fade.animateTo(1f, tween(FADE_MS))
                previous = null
            } else {
                fade.snapTo(1f)
            }
        }
        if (next.id != title.id) ui.images.load(next, keep)
    }

    LaunchedEffect(ui.resumed, heroFocused, safe, titles.size) {
        if (ui.resumed && heroFocused && titles.size > 1) {
            delay(ROTATE_MS)
            index = (safe + 1) % titles.size
        }
    }

    Box(Modifier.fillMaxSize()) {
        val now = current
        if (now != null) {
            previous?.let { p ->
                val bmp = remember(p) { p.bitmap.asImageBitmap() }
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            val bmp = remember(now) { now.bitmap.asImageBitmap() }
            Image(
                bmp, null,
                Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop,
            )
            // Shades from showcase.html: dark on the left and at the bottom, then a glow tinted toward the art.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Ag.Base.copy(alpha = 0.92f), 0.38f to Ag.Base.copy(alpha = 0.55f), 0.70f to Ag.Base.copy(alpha = 0f),
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Ag.Base.copy(alpha = 0f), 0.45f to Ag.Base.copy(alpha = 0f),
                        0.78f to Ag.Base.copy(alpha = 0.85f), 1f to Ag.Base,
                    ),
                ),
            )
            val tint = Color(now.tint)
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0f)),
                            center = Offset(size.width / 2f, size.height * 1.05f),
                            radius = size.width * 0.5f,
                        ),
                    )
                },
            )
        }

        Column(Modifier.padding(start = 96.dp, top = 230.dp).width(1000.dp)) {
            Text(
                title.title,
                style = Ag.Greeting.copy(fontSize = Tmdb.titleSizeSp(title.title).sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = Tmdb.metaLine(title)
            if (meta.isNotEmpty()) Text(meta, style = Ag.Body.copy(fontSize = 26.sp), maxLines = 1, modifier = Modifier.padding(top = 28.dp))
            if (title.overview.isNotEmpty()) {
                Text(
                    title.overview,
                    style = Ag.Body.copy(fontSize = 26.sp, color = Ag.Muted, lineHeight = 38.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 20.dp).width(820.dp),
                )
            }
            Row(
                Modifier.padding(top = 34.dp).onFocusChanged { heroFocused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                ShowButton("▶  Watch", primary = true, modifier = Modifier.focusRequester(watch)) {
                    notice = ui.onWatch(title).orEmpty()
                }
                ShowButton("Next", primary = false, modifier = Modifier) { index = (safe + 1) % titles.size }
            }
        }

        Row(Modifier.padding(start = 96.dp, top = 700.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in titles.indices) {
                Box(
                    Modifier.height(12.dp).width(if (i == safe) 40.dp else 12.dp)
                        .background(if (i == safe) Ag.Ring else Ag.Ink.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                )
            }
        }
        Text(
            "Trending this week · TMDB",
            style = Ag.Body.copy(fontSize = 20.sp, color = Ag.Muted),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 694.dp),
        )
        if (notice.isNotEmpty()) {
            Text(notice, style = Ag.Body.copy(fontSize = 22.sp, color = Ag.Amber), maxLines = 1, modifier = Modifier.padding(start = 96.dp, top = 726.dp))
        }
    }
}

@Composable
private fun ShowButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(72.dp)
            .tvFocus(ButtonShape, onClick)
            .clip(ButtonShape)
            .background(if (primary) Ag.Ink else Ag.Ink.copy(alpha = 0.12f))
            .border(1.dp, if (primary) Color.Transparent else Ag.Ink.copy(alpha = 0.2f), ButtonShape)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Ag.Label.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, color = if (primary) Ag.Base else Ag.Ink), maxLines = 1)
    }
}
