package com.nbansal.afterglow.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.companion.CompanionNet
import com.nbansal.afterglow.companion.CompanionServer
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.ListRow
import com.nbansal.afterglow.ui.focusSoon
import io.nayuki.qrcodegen.QrCode

private const val Intro = "Use your phone as a keyboard and remote, reorder your favourites and keep a backup of your settings. " +
    "Your phone opens a page served by this TV on your home Wi-Fi; nothing goes to the internet. Once on, it stays on until you switch it off here."

/** Settings > Phone companion: the switch, pairing, and forgetting all phones. */
@Composable
fun CompanionPage(model: AppModel, onPair: () -> Unit) {
    val c = model.companion
    var confirm by remember { mutableStateOf(false) }
    val rows = listOf(
        SettingsRow("Phone companion", if (c.enabled) "On" else "Off") { confirm = false; c.turn(!c.enabled) },
        SettingsRow("Pair a phone", "Shows a QR code") { confirm = false; onPair() },
        SettingsRow(
            "Unpair all phones",
            if (confirm) "Press again to unpair" else "${c.phones} paired",
        ) {
            if (c.phones > 0) { if (confirm) { c.unpairAll(); confirm = false } else confirm = true }
        },
    )
    SettingsPage(
        "Phone companion", rows,
        headerHeight = 200.dp,
        header = {
            Text(
                c.problem.ifEmpty { Intro },
                style = Ag.Body.copy(color = Ag.Muted, fontSize = 22.sp), maxLines = 5,
            )
        },
    )
}

/** The pairing screen: QR of the page address plus the 4-digit PIN (a new PIN each time this screen opens, valid 10 minutes). */
@Composable
fun PairPhonePage(model: AppModel, onDone: () -> Unit) {
    val c = model.companion
    val context = LocalContext.current
    val ip = remember { CompanionNet.ipv4(context) }
    DisposableEffect(Unit) {
        c.openPairing()
        onDispose { c.closePairing() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.focusSoon() }
    val pairedAtOpen = remember { c.pairedEvents }
    val justPaired = c.pairedEvents > pairedAtOpen
    val pin = c.pin
    val url = if (ip != null && pin != null) CompanionNet.url(ip, pin) else null
    val qr = remember(url) { url?.let { QrCode.encodeText(it, QrCode.Ecc.MEDIUM) } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(1180.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(Color(0xFF17120F))
                .border(1.dp, Ag.PillLine, RoundedCornerShape(44.dp))
                .padding(56.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            Box(Modifier.size(380.dp).clip(RoundedCornerShape(28.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (qr != null) QrView(qr, 360.dp) else Text("No QR yet", style = Ag.Body.copy(color = Color.Black))
            }
            Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Use your phone", style = Ag.Greeting.copy(fontSize = 56.sp), maxLines = 1)
                Text(
                    if (ip == null) "The TV has no home Wi-Fi address. Connect it to your network, then open this screen again."
                    else "Scan with your phone's camera. It opens a page served by this TV on your home Wi-Fi. Nothing goes to the internet.",
                    style = Ag.Body.copy(color = Ag.Muted), maxLines = 4,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("PIN", style = Ag.SectionCaps)
                    Text(
                        pin?.toCharArray()?.joinToString(" ") ?: "- - - -",
                        style = Ag.Greeting.copy(fontSize = 56.sp, fontWeight = FontWeight.Medium, color = Ag.Ring, letterSpacing = 4.sp),
                        maxLines = 1,
                    )
                }
                Text(
                    when {
                        justPaired -> "Phone paired. A new PIN is ready for the next phone."
                        ip != null -> "Waiting for your phone. Or open http://$ip:${CompanionServer.Port} and type the PIN."
                        else -> ""
                    },
                    style = Ag.Body.copy(color = if (justPaired) Ag.Ring else Ag.Ink, fontSize = 22.sp), maxLines = 3,
                )
                ListRow("New PIN", Modifier.focusRequester(first), onClick = { c.newPin() })
                ListRow("Done", onClick = onDone)
            }
        }
    }
}

@Composable
private fun QrView(qr: QrCode, box: Dp) {
    Canvas(Modifier.size(box)) {
        val quiet = 4
        val cell = size.minDimension / (qr.size + quiet * 2)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}
