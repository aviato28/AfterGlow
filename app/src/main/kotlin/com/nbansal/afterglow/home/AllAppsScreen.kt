package com.nbansal.afterglow.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Recents
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.AppTile
import com.nbansal.afterglow.ui.focusSoon

@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    recents: List<AppEntry> = emptyList(),
    refocus: Int = 0,
    focusIndex: MutableIntState = remember { mutableIntStateOf(0) },
    onOpen: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    val requesters = remember(apps.size) { List(apps.size) { FocusRequester() } }
    LaunchedEffect(apps.isNotEmpty(), refocus) {
        if (apps.isNotEmpty()) {
            val i = focusIndex.intValue.coerceIn(apps.indices)
            state.scrollToItem(i + if (recents.isNotEmpty()) 1 else 0)
            requesters[i].focusSoon()
        }
    }
    Box(modifier.fillMaxSize()) {
        Text("ALL APPS", style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = state,
            modifier = Modifier.padding(top = 120.dp),
            contentPadding = PaddingValues(start = Ag.MarginX, end = Ag.MarginX, top = 36.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            if (recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    Column {
                        Text("RECENT", style = Ag.SectionCaps, modifier = Modifier.padding(bottom = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            for (slot in 0 until Recents.MAX) {
                                Box(Modifier.weight(1f)) {
                                    recents.getOrNull(slot)?.let { app ->
                                        AppTile(app, onClick = { onOpen(app) }, onLongClick = { onOptions(app) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(apps, key = { _, app -> app.pkg }) { i, app ->
                AppTile(
                    app,
                    Modifier.focusRequester(requesters[i]).onFocusChanged { if (it.isFocused) focusIndex.intValue = i },
                    labelAlways = true,
                    onClick = { onOpen(app) },
                    onLongClick = { onOptions(app) },
                )
            }
        }
    }
}
