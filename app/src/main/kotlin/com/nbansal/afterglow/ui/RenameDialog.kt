package com.nbansal.afterglow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Plain text box; the TV's own on-screen keyboard appears. Done confirms; blank restores the original name. */
@Composable
fun RenameDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    TvDialog(onDismiss) {
        val focus = remember { FocusRequester() }
        var text by remember { mutableStateOf(current) }
        LaunchedEffect(Unit) { focus.focusSoon() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(900.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF17120F)).padding(40.dp)) {
                Text("Rename (leave empty for the original name)", style = Ag.SectionCaps.copy(letterSpacing = 0.sp), maxLines = 1)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = Ag.Body.copy(fontSize = 30.sp),
                    cursorBrush = SolidColor(Ag.Amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone(text); onDismiss() }),
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .clip(RowShape)
                        .background(Ag.PillFill)
                        .border(2.dp, Ag.Ring, RowShape)
                        .padding(28.dp)
                        .focusRequester(focus),
                )
            }
        }
    }
}
