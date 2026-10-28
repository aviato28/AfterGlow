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

/**
 * Lite home background (docs/design/v2/lite.html): base fill, a soft glow rising from the bottom (ellipse 70% x 55% at 50% 112%:
 * amber .50, rose .28 at 40%, violet .14 at 70%, clear at 100%) and a faint violet glow top-right. The phase scales the strength
 * (Evening = the mockup) and tints the warm colours. Drawn once, no animation.
 */
@Composable
fun AfterglowBackground(phase: GlowPhase, modifier: Modifier = Modifier) {
    val bottom = remember(phase) {
        val k = phase.amber / GlowPhase.Evening.amber
        Brush.radialGradient(
            0f to phase.warm.copy(alpha = 0.50f * k),
            0.40f to phase.mid.copy(alpha = 0.28f * k),
            0.70f to Ag.Violet.copy(alpha = 0.14f * k),
            1f to Ag.Base.copy(alpha = 0f),
            center = Offset.Zero,
            radius = 1f,
        )
    }
    val corner = remember {
        Brush.radialGradient(
            0f to Ag.Violet.copy(alpha = 0.20f),
            0.70f to Ag.Violet.copy(alpha = 0f),
            1f to Ag.Violet.copy(alpha = 0f),
            center = Offset.Zero,
            radius = 1f,
        )
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Ag.Base)
        // CSS ellipses: draw a unit-radius gradient scaled to the ellipse's radii, centred where the CSS says.
        withTransform({
            translate(size.width * 0.5f, size.height * 1.12f)
            scale(size.width * 0.70f, size.height * 0.55f, pivot = Offset.Zero)
        }) {
            drawRect(bottom, topLeft = Offset(-1f, -1f), size = Size(2f, 2f))
        }
        withTransform({
            translate(size.width * 0.88f, size.height * -0.05f)
            scale(size.width * 0.40f, size.height * 0.35f, pivot = Offset.Zero)
        }) {
            drawRect(corner, topLeft = Offset(-1f, -1f), size = Size(2f, 2f))
        }
    }
}
