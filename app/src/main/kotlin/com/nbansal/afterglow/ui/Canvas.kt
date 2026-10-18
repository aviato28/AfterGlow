package com.nbansal.afterglow.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val CANVAS_WIDTH_DP = 1920f

/** Every TV lays out on a fixed 1920x1080 dp canvas: 1 dp = widthPx / 1920 px, whatever the panel density says. */
private fun canvasDensity(context: Context): Density =
    Density(context.resources.displayMetrics.widthPixels / CANVAS_WIDTH_DP, fontScale = 1f)

/** Root of every screen. */
@Composable
fun TvCanvas(content: @Composable () -> Unit) {
    CompositionLocalProvider(densityOf(LocalContext.current)) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** The only allowed popup: a plain Dialog has its own window and ignores the root density, so re-apply it. */
@Composable
fun TvDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(densityOf(LocalContext.current)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

private fun densityOf(context: Context) = LocalDensity provides canvasDensity(context)
