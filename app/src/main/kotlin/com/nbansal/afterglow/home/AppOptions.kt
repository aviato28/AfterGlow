package com.nbansal.afterglow.home

import androidx.compose.runtime.Composable
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.ui.MenuRow
import com.nbansal.afterglow.ui.TvMenu

class AppActions(
    val isFavourite: Boolean,
    val onToggleFavourite: () -> Unit,
    val onMoveEarlier: () -> Unit,
    val onMoveLater: () -> Unit,
    val onHide: () -> Unit,
    val onRename: () -> Unit,
    val onInfo: () -> Unit,
    val onUninstall: () -> Unit,
)

/** Rows of the app options popup, in order (move rows only for favourites). */
fun appOptionRows(a: AppActions): List<MenuRow> = buildList {
    add(MenuRow(if (a.isFavourite) "Remove from favourites" else "Add to favourites", onClick = a.onToggleFavourite))
    if (a.isFavourite) {
        add(MenuRow("Move earlier", onClick = a.onMoveEarlier))
        add(MenuRow("Move later", onClick = a.onMoveLater))
    }
    add(MenuRow("Hide", onClick = a.onHide))
    add(MenuRow("Rename", onClick = a.onRename))
    add(MenuRow("App info", onClick = a.onInfo))
    add(MenuRow("Uninstall", onClick = a.onUninstall))
}

@Composable
fun AppOptions(app: AppEntry, actions: AppActions, onDismiss: () -> Unit) {
    TvMenu(app.label, appOptionRows(actions), onDismiss)
}
