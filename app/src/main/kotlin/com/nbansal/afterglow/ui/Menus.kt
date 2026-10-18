package com.nbansal.afterglow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp

class MenuRow(val label: String, val value: String? = null, val onClick: () -> Unit)

val RowShape = RoundedCornerShape(20.dp)

/** One large focusable row: label left, optional value right. */
@Composable
fun ListRow(label: String, modifier: Modifier = Modifier, value: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .tvFocus(RowShape, onClick, onLongClick)
            .clip(RowShape)
            .background(Ag.PillFill)
            .border(1.dp, Ag.PillLine, RowShape)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = Ag.Body, maxLines = 1, modifier = Modifier.fillMaxWidth(0.62f))
        if (value != null) Text(value, style = Ag.Body.copy(color = Ag.Muted), maxLines = 1, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** Centred popup menu through [TvDialog]; focus goes to the first row. */
@Composable
fun TvMenu(title: String, rows: List<MenuRow>, onDismiss: () -> Unit) {
    TvDialog(onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.focusSoon() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(760.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF17120F)).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = Ag.SectionCaps.copy(letterSpacing = 1.sp), maxLines = 1)
                rows.forEachIndexed { i, row ->
                    ListRow(
                        row.label,
                        if (i == 0) Modifier.focusRequester(first) else Modifier,
                        row.value,
                        onClick = { onDismiss(); row.onClick() },
                    )
                }
            }
        }
    }
}
