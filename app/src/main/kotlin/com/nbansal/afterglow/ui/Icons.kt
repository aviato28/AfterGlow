package com.nbansal.afterglow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

// The one icon set (docs/design/v2/panel.html): outline paths in a 24 x 24 view box, drawn with a 2 unit round stroke.
const val IconSleep = "M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"
const val IconTimer = "M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM12 9v4l2 2M9 2h6"
const val IconWifi = "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0M12 19.5h.01"
const val IconSound = "M8 2h8a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3zM12 10a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 6h.01"
const val IconPicture = "M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM8 21h8M12 17v4"
const val IconSettings = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"
const val IconVolume = "M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7"
const val IconPrev = "M19 5v14L8 12zM5 5v14"
const val IconNext = "M5 5v14l11-7zM19 5v14"
const val IconPlay = "M7 4v16l13-8z"
const val IconPause = "M8 5v14M16 5v14"

// Settings rail.
const val IconHome = "M3 11l9-8 9 8M5 10v10h14V10M10 20v-6h4v6"
const val IconRemote = "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM12 7h.01M9 12h6M9 16h6"
const val IconPhone = "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2"
const val IconBolt = "M13 2 4 14h7l-1 8 9-12h-7z"
const val IconInfo = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5M12 8h.01"

@Composable
fun PathIcon(d: String, box: Dp, color: Color, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(box)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
