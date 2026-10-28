package com.nbansal.afterglow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.apps.rememberBanner

val TileShape = RoundedCornerShape(20.dp)
val LabelHeight = 40.dp

/**
 * A 16:9 app tile. [width] fixed (home row) or null to fill the cell (grid). The label sits under the tile,
 * always ([labelAlways]) or only while focused; its space is always reserved so nothing jumps.
 */
@Composable
fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    labelAlways: Boolean = false,
    shape: Shape = TileShape,
    lift: Float = 1.06f,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var px by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val banner by rememberBanner(app, px.width, px.height)

    Column(
        (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).onFocusChanged { hasFocus = it.hasFocus },
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onSizeChanged { px = it }
                .tvFocus(shape, onClick, onLongClick, lift)
                .clip(shape)
                .then(Modifier),
        ) {
            banner?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.FillBounds) }
        }
        if (showLabel) Text(
            app.label,
            style = Ag.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(LabelHeight).padding(top = 10.dp).alpha(if (labelAlways || hasFocus) 1f else 0f),
        )
    }
}
