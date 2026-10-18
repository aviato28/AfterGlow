package com.nbansal.afterglow.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.ListRow
import com.nbansal.afterglow.ui.focusSoon

class SettingsRow(val label: String, val value: String? = null, val onClick: () -> Unit)

/** A titled, scrolling list of big rows; the first row takes focus when the page opens. */
@Composable
fun SettingsPage(title: String, rows: List<SettingsRow>, modifier: Modifier = Modifier, headerHeight: Dp = 170.dp, header: (@Composable () -> Unit)? = null) {
    val first = remember { FocusRequester() }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) first.focusSoon() }
    Box(modifier.fillMaxSize()) {
        Text(title.uppercase(), style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        header?.let { Box(Modifier.padding(start = 100.dp, top = 130.dp, end = 100.dp)) { it() } }
        LazyColumn(
            Modifier.padding(top = if (header != null) 130.dp + headerHeight else 120.dp).width(1100.dp),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(start = Ag.MarginX, top = 24.dp, bottom = 60.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(rows.size) { i ->
                val r = rows[i]
                ListRow(r.label, if (i == 0) Modifier.focusRequester(first) else Modifier, r.value, onClick = r.onClick)
            }
        }
    }
}
