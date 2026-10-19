package com.nbansal.afterglow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Sky colour by time of day. Morning 5-11, Day 11-17, Evening 17-21, Night 21-5. */
enum class GlowPhase(val amber: Float, val rose: Float, val violet: Float, val warm: Color, val mid: Color) {
    Morning(0.38f, 0.26f, 0.14f, Color(0xFFFFB88A), Color(0xFFF08A8A)),
    Day(0.16f, 0.10f, 0.06f, Ag.Amber, Ag.Rose),
    Evening(0.55f, 0.38f, 0.22f, Ag.Amber, Ag.Rose),
    Night(0.24f, 0.12f, 0.05f, Color(0xFFFF9E5E), Color(0xFFC8503C));

    /** Gradient stops of the radial glow, shared by Home and the clock screensaver. [scale] dims the whole glow. */
    fun stops(scale: Float = 1f): List<Pair<Float, Color>> = listOf(
        0f to warm.copy(alpha = amber * scale),
        0.28f to mid.copy(alpha = rose * scale),
        0.55f to Ag.Violet.copy(alpha = violet * scale),
        1f to Ag.Base.copy(alpha = 0f),
    )

    companion object {
        fun at(time: LocalTime): GlowPhase = when (time.hour) {
            in 5..10 -> Morning
            in 11..16 -> Day
            in 17..20 -> Evening
            else -> Night
        }
    }
}

private const val PHASE_REFRESH_MS = 15 * 60 * 1000L

/** The current phase, re-evaluated every 15 minutes. No animation loop. */
@Composable
fun rememberGlowPhase(): GlowPhase {
    var phase by remember { mutableStateOf(GlowPhase.at(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PHASE_REFRESH_MS)
            phase = GlowPhase.at(LocalTime.now())
        }
    }
    return phase
}

/** Base fill plus one radial glow centred below the bottom edge (as docs/design/home.svg). */
@Composable
fun AfterglowBackground(phase: GlowPhase, modifier: Modifier = Modifier) {
    val brush = remember(phase) {
        Brush.radialGradient(
            *phase.stops().toTypedArray(),
            center = Offset.Zero,
            radius = 0.85f,
        )
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Ag.Base)
        // The SVG gradient is an ellipse in bounding-box units: draw a unit circle scaled to the screen.
        withTransform({
            translate(size.width / 2f, size.height * 1.18f)
            scale(size.width, size.height, pivot = Offset.Zero)
        }) {
            drawRect(brush, topLeft = Offset(-0.5f, -1.18f), size = Size(1f, 1f))
        }
    }
}
