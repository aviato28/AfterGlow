package com.nbansal.afterglow.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.LocalTime

enum class Greeting(val text: String) {
    Morning("Good morning"),
    Afternoon("Good afternoon"),
    Evening("Good evening"),
    Night("Good night");

    companion object {
        /** Morning 5-12, Afternoon 12-17, Evening 17-21, Night 21-5. */
        fun at(time: LocalTime): Greeting = when (time.hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..20 -> Evening
            else -> Night
        }
    }
}

/** Current time, refreshed on the minute (and on time/zone changes) by the system's own tick: no polling. */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val context = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                now.value = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        now.value = LocalDateTime.now() // anything missed between composition and registration
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}
