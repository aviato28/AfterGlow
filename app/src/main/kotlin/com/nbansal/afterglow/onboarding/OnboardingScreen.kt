package com.nbansal.afterglow.onboarding

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.settings.HomeButtonPage
import com.nbansal.afterglow.settings.SettingsRow
import com.nbansal.afterglow.settings.SpeedUpPage
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.AppTile
import com.nbansal.afterglow.ui.ListRow
import com.nbansal.afterglow.ui.focusSoon

/** First-run walk-through. Every step has Back and Skip; the Home and Speed-up steps reuse the settings pages. */
@Composable
fun OnboardingScreen(model: AppModel, onFinish: () -> Unit) {
    val o = model.onboarding
    val context = LocalContext.current
    LaunchedEffect(model.state.favourites) {
        if (o.step == OnboardingStep.Welcome && o.ticked.isEmpty()) o.restart(model.state.favourites.orEmpty())
    }
    val skipRow = SettingsRow("Skip") { o.skip() }
    val nextRow = SettingsRow("Next") { o.next() }
    val backRow = SettingsRow("Back") { o.back() }

    when (o.step) {
        OnboardingStep.Home -> HomeButtonPage(model, extraRows = listOf(nextRow, backRow))
        OnboardingStep.SpeedUp -> SpeedUpPage(model, extraRows = listOf(SettingsRow("Next") { o.next() }, backRow))
        OnboardingStep.Apps -> AppsStep(model.visibleApps, o.ticked, { o.toggle(it) }, onNext = { o.next() }, onSkip = { o.skip() }, onBack = { o.back() })
        OnboardingStep.Order -> OrderStep(model.visibleApps, o.ticked, { p, d -> o.move(p, d) }, onNext = { o.next() }, onBack = { o.back() })
        OnboardingStep.Welcome -> TextStep(
            "Welcome to Afterglow",
            "A free, fast, ad-free home screen for your TV.\nNothing is sent anywhere, and nothing changes without your OK.",
            listOf(SettingsRow("Get started") { o.next() }, SettingsRow("Skip set-up") { onFinish() }),
        )
        OnboardingStep.Updates -> TextStep(
            "Keep apps updating",
            "Afterglow never installs or updates other apps. Netflix, YouTube and the rest update through the Play Store, " +
                "which keeps HDR, Dolby Vision and 4K working.\nTurn on auto-update in Play Store settings.",
            listOf(
                SettingsRow("Open Play Store") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.android.vending")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                nextRow, skipRow, backRow,
            ),
        )
        OnboardingStep.Done -> TextStep(
            "All set",
            "Your favourites are on Home. Press and hold OK on any app for more options.\nYou can run this set-up again from Afterglow settings.",
            listOf(SettingsRow("Go to Home") { onFinish() }, backRow),
        )
    }
}

@Composable
private fun TextStep(title: String, text: String, rows: List<SettingsRow>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.focusSoon() }
    Box(Modifier.fillMaxSize()) {
        Text(title.uppercase(), style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        Text(text, style = Ag.Body.copy(color = Ag.Muted, fontSize = 26.sp), modifier = Modifier.padding(start = 100.dp, top = 140.dp, end = 300.dp))
        Column(Modifier.padding(start = Ag.MarginX + 4.dp, top = 440.dp).width(800.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            rows.forEachIndexed { i, r -> ListRow(r.label, if (i == 0) Modifier.focusRequester(first) else Modifier, r.value, onClick = r.onClick) }
        }
    }
}

@Composable
private fun AppsStep(apps: List<AppEntry>, ticked: List<String>, onToggle: (String) -> Unit, onNext: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) first.focusSoon() }
    Box(Modifier.fillMaxSize()) {
        Text("YOUR APPS", style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        Text("Press OK on the apps you want on Home. A tick means it is chosen.", style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), modifier = Modifier.padding(start = 100.dp, top = 100.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.padding(top = 150.dp, bottom = 130.dp),
            contentPadding = PaddingValues(start = Ag.MarginX, end = Ag.MarginX, top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            items(apps, key = { it.pkg }) { app ->
                val on = app.pkg in ticked
                AppTile(
                    if (on) app.copy(label = "✓ ${app.label}") else app,
                    if (app == apps.first()) Modifier.focusRequester(first) else Modifier,
                    labelAlways = true,
                    onClick = { onToggle(app.pkg) },
                    onLongClick = { onToggle(app.pkg) },
                )
            }
        }
        NavBar(Modifier.padding(start = Ag.MarginX + 4.dp, top = 960.dp), listOf(SettingsRow("Next") { onNext() }, SettingsRow("Skip") { onSkip() }, SettingsRow("Back") { onBack() }))
    }
}

@Composable
private fun OrderStep(apps: List<AppEntry>, ticked: List<String>, onMove: (String, Int) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val byPkg = apps.associateBy { it.pkg }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.focusSoon() }
    Box(Modifier.fillMaxSize()) {
        Text("ORDER THEM", style = Ag.SectionCaps, modifier = Modifier.padding(start = 100.dp, top = Ag.MarginY + 20.dp))
        Text("Press left or right on an app to move it earlier or later.", style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), modifier = Modifier.padding(start = 100.dp, top = 100.dp))
        LazyColumn(
            Modifier.padding(top = 150.dp, bottom = 130.dp).width(1100.dp),
            contentPadding = PaddingValues(start = Ag.MarginX, end = 24.dp, top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(ticked, key = { it }) { pkg ->
                val label = byPkg[pkg]?.label ?: pkg
                ListRow(
                    label,
                    Modifier.then(if (pkg == ticked.first()) Modifier.focusRequester(first) else Modifier).onPreviewKeyEvent {
                        if (it.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) false
                        else when (it.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { onMove(pkg, -1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { onMove(pkg, +1); true }
                            else -> false
                        }
                    },
                    "◀ ▶ ${ticked.indexOf(pkg) + 1}",
                    onClick = {},
                )
            }
        }
        NavBar(Modifier.padding(start = Ag.MarginX + 4.dp, top = 960.dp), listOf(SettingsRow("Next") { onNext() }, SettingsRow("Back") { onBack() }))
    }
}

@Composable
private fun NavBar(modifier: Modifier, rows: List<SettingsRow>) {
    Row(modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        rows.forEach { ListRow(it.label, Modifier.width(280.dp), onClick = it.onClick) }
    }
}
