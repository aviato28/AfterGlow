package com.nbansal.afterglow.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nbansal.afterglow.ui.PathIcon
import com.nbansal.afterglow.ui.accentColor
import com.nbansal.afterglow.ui.tvFocus
import kotlinx.coroutines.launch
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
        Text(title, style = Ag.Title, maxLines = 1, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 10.dp))
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

class SettingsSection(val title: String, val subtitle: String, val icon: String, val rows: List<SettingsRow>)

/** Which rail section is open and whether focus is in the content panel; kept by the activity so Back from a page lands where you left. */
class SettingsHubState {
    var section by mutableIntStateOf(0)
    var inContent by mutableStateOf(false)
}

const val TAG_RAIL_PREFIX = "settingsRail"

private val PanelShape = RoundedCornerShape(44.dp)
private val RailShape = RoundedCornerShape(24.dp)

/**
 * Settings v2 (docs/design/v2/settings-v2.png): a left rail of icon sections and a glass panel of rows. Focus starts on the rail;
 * Right or OK moves into the panel, Left or Back returns to the rail. Rail focus shows that section in the panel.
 */
@Composable
fun SettingsHub(sections: List<SettingsSection>, state: SettingsHubState, modifier: Modifier = Modifier) {
    val rails = remember(sections.size) { List(sections.size) { FocusRequester() } }
    val firsts = remember(sections.size) { List(sections.size) { FocusRequester() } }
    val section = state.section.coerceIn(sections.indices)
    val scope = rememberCoroutineScope()

    fun toRail() { state.inContent = false; scope.launch { rails[section].focusSoon() } }
    fun toContent() {
        if (sections[section].rows.isEmpty()) return
        state.inContent = true
        scope.launch { firsts[section].focusSoon() }
    }

    LaunchedEffect(Unit) { if (state.inContent) firsts[section].focusSoon() else rails[section].focusSoon() }
    BackHandler(enabled = state.inContent) { toRail() }

    Row(modifier.fillMaxSize().padding(start = Ag.MarginX, end = Ag.MarginX, top = Ag.MarginY + 20.dp, bottom = Ag.MarginY)) {
        Column(
            Modifier.width(440.dp).fillMaxHeight().onKeyEvent { e ->
                if (e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown) { toContent(); true } else false
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("SETTINGS", style = Ag.SectionCaps, modifier = Modifier.padding(start = 24.dp, bottom = 14.dp))
            sections.forEachIndexed { i, sec ->
                RailItem(sec, active = i == section, Modifier.focusRequester(rails[i]).testTag(TAG_RAIL_PREFIX + i),
                    onFocus = { state.section = i }) { toContent() }
            }
        }
        Spacer(Modifier.width(40.dp))
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(PanelShape)
                .background(Brush.verticalGradient(listOf(Color(0xC71C1614), Color(0xDB120E0D))))
                .border(1.dp, Ag.Ink.copy(alpha = 0.12f), PanelShape)
                .onKeyEvent { e ->
                    if (e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) { toRail(); true } else false
                },
        ) {
            AnimatedContent(
                targetState = section,
                transitionSpec = { fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 14 } togetherWith fadeOut(tween(90)) },
                label = "settingsSection",
            ) { i ->
                val sec = sections[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp)) {
                    Text(sec.title, style = Ag.Title, maxLines = 1)
                    Text(sec.subtitle, style = Ag.Body.copy(color = Ag.Muted), maxLines = 1, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(sec.rows.size) { r ->
                            val row = sec.rows[r]
                            ListRow(
                                row.label, if (r == 0) Modifier.focusRequester(firsts[i]) else Modifier, row.value,
                                onClick = { state.inContent = true; row.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(sec: SettingsSection, active: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val accent = accentColor()
    Row(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tvFocus(RailShape, onClick)
            .clip(RailShape)
            .background(if (active) accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathIcon(sec.icon, 32.dp, if (active) accent else Ag.Ink)
        Spacer(Modifier.width(20.dp))
        Text(sec.title, style = Ag.Body.copy(color = if (active) accent else Ag.Ink, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
