package com.nbansal.afterglow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private const val GLOW_LAYERS = 6

/**
 * The one focus look: when focused, lift 6%, draw a cream ring and an amber glow behind. Nothing is drawn (and no
 * animation runs) when unfocused. Ring and glow are not clipped, so leave room around the item.
 *
 * OK/Enter = [onClick] on key up. OK held = [onLongClick] once, and the key up then does not click.
 * Menu key = [onLongClick] (many TCL remotes never send it, so long-press OK is the real path).
 */
fun Modifier.tvFocus(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    // A release only counts as a click if this item also saw the press: a popup that opens during a long press must not
    // receive the release of the key that opened it.
    var downSeen by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(durationMillis = 120), label = "tvFocusScale")

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (focused) {
                val corner = shape.createOutline(size, layoutDirection, this).cornerRadiusX()
                // Soft glow without blur: a few expanding translucent layers.
                for (i in GLOW_LAYERS downTo 1) {
                    val grow = i * 5.dp.toPx()
                    drawRoundRect(
                        color = Ag.Amber.copy(alpha = 0.05f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(corner + grow),
                    )
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val width = 4.dp.toPx()
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> drawRoundRect(
                        color = Ag.Ring,
                        cornerRadius = outline.roundRect.topLeftCornerRadius,
                        style = Stroke(width),
                    )
                    is Outline.Rectangle -> drawRect(Ag.Ring, style = Stroke(width))
                    is Outline.Generic -> drawPath(outline.path, Ag.Ring, style = Stroke(width))
                }
            }
        }
        .onKeyEvent { event ->
            val isOk = event.key in OkKeys
            when {
                isOk && event.type == KeyEventType.KeyDown -> {
                    // Only a fresh press counts. The auto-repeats of a held key also reach a popup that opened under the long
                    // press, and must not make its first row think it was pressed.
                    if (event.nativeKeyEvent.repeatCount == 0) downSeen = true
                    if (event.nativeKeyEvent.repeatCount >= 1 && !longFired && onLongClick != null) {
                        longFired = true
                        onLongClick()
                    }
                    true
                }
                isOk && event.type == KeyEventType.KeyUp -> {
                    val pressedHere = downSeen
                    downSeen = false
                    if (longFired) longFired = false else if (pressedHere) onClick()
                    true
                }
                event.key == Key.Menu && onLongClick != null -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onLongClick()
                    true
                }
                else -> false
            }
        }
        .onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) { longFired = false; downSeen = false }
        }
        .focusable()
}

private fun Outline.cornerRadiusX(): Float = when (this) {
    is Outline.Rounded -> roundRect.topLeftCornerRadius.x
    else -> 0f
}

/** Screens call this when they open: the view may not be attached yet, so retry for about a second. */
suspend fun FocusRequester.focusSoon() {
    repeat(20) {
        if (runCatching { requestFocus() }.getOrDefault(false)) return
        delay(50)
    }
}
