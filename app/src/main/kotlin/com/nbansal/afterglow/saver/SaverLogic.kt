package com.nbansal.afterglow.saver

import com.nbansal.afterglow.home.Greeting
import com.nbansal.afterglow.takeover.Adb
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.ui.GlowPhase
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Every number the owner may want to tune, in layout px on a 1920x1080 canvas. */
object SaverStyle {
    const val Width = 1920f
    const val Height = 1080f
    const val Background = 0xFF0B0908.toInt()
    const val Warm = 0xFFF3E9DF.toInt()
    const val Secondary = 0xFFB8ADA3.toInt()
    const val ClockSize = 330f
    const val ClockWeight = 200
    const val ClockSpacing = -6f
    const val TextSize = 38f
    const val TextGap = 40f
    const val HorizonWidth = 520f
    const val HorizonBelowText = 62f
    const val SunWidth = 64f
    const val SunHeight = 32f
    const val MaxShiftX = 60
    const val MaxShiftY = 40
    const val FadeMs = 1500L
}

/** The night look (21:00 to 05:00). One place, so it can later become "black with a tiny clock". */
object NightStyle {
    const val ClockScale = 0.6f
    const val ClockAlpha = 0.55f
    const val GlowScale = 0.5f
    const val HorizonAlpha = 0.4f
    const val ShowGreeting = false
}

/** Pure rules of the clock screensaver (unit-tested). */
object Saver {
    const val Component = "com.nbansal.afterglow/com.nbansal.afterglow.saver.ClockDream"
    const val DreamX = "com.google.android.apps.tv.dreamx"

    private val DateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    fun isNight(time: LocalTime) = GlowPhase.at(time) == GlowPhase.Night

    /** Always 24-hour. */
    fun clockText(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

    fun dateText(date: LocalDate): String = DateFormat.format(date)

    /** The greeting in front of the date, or null (hidden at night). */
    fun greeting(time: LocalTime): String? = if (isNight(time) && !NightStyle.ShowGreeting) null else Greeting.at(time).text

    /** Random shift of the group so it never leaves the screen: [left, top, right, bottom] is the group's box before the shift. */
    fun shift(random: Random, left: Float, top: Float, right: Float, bottom: Float): Pair<Float, Float> {
        val dx = random.nextInt(maxOf(-SaverStyle.MaxShiftX, (-left).toInt()), minOf(SaverStyle.MaxShiftX, (SaverStyle.Width - right).toInt()) + 1)
        val dy = random.nextInt(maxOf(-SaverStyle.MaxShiftY, (-top).toInt()), minOf(SaverStyle.MaxShiftY, (SaverStyle.Height - bottom).toInt()) + 1)
        return dx.toFloat() to dy.toFloat()
    }

    /** Plain words for the value of `screensaver_components`. */
    fun describe(value: String?): String {
        val v = value.orEmpty().trim()
        return when {
            v.isEmpty() || v == "null" -> "off"
            v.contains("com.nbansal.afterglow") -> "Afterglow clock"
            v.contains(DreamX) -> "Google TV ambient mode"
            else -> "something else"
        }
    }

    /** `settings get` prints "null" for nothing. */
    fun parse(output: String): String? = output.trim().takeIf { it.isNotEmpty() && it != "null" }
}

interface SaverStore {
    suspend fun previous(): String?
    suspend fun setPrevious(value: String?)
}

sealed class SaverResult(val message: String) {
    class Ok(message: String) : SaverResult(message)
    class Failed(message: String) : SaverResult(message)
}

/** Switch the TV's screensaver to the Afterglow clock and back. The old value is saved BEFORE anything is changed. */
class SaverFlow(private val adb: Adb, private val store: SaverStore) {
    private suspend fun components() = Saver.parse(adb.run(AdbWhitelist.GetScreensaver).output)

    private suspend fun put(value: String) = adb.run(AdbWhitelist.putScreensaver(value)).exitCode == 0

    suspend fun use(): SaverResult {
        val old = components()
        if (old != Saver.Component) store.setPrevious(old ?: "null")
        val ok = put(Saver.Component) && adb.run(AdbWhitelist.putScreensaverEnabled(true)).exitCode == 0 && components() == Saver.Component
        if (ok) return SaverResult.Ok("The Afterglow clock is now your screensaver. It starts after the TV's own timeout.")
        if (old != null && old != Saver.Component) runCatching { put(old) }
        return SaverResult.Failed("The TV did not accept the change, so nothing was changed. Use the TV's own Screen saver page instead.")
    }

    suspend fun undo(): SaverResult {
        val old = store.previous()
            ?: return SaverResult.Failed("Nothing to undo: no earlier screensaver was saved. Choose one on the TV's own Screen saver page.")
        if (!put(old) || components() != Saver.parse(old)) return SaverResult.Failed("The TV did not accept the change. Use the TV's own Screen saver page.")
        store.setPrevious(null)
        return SaverResult.Ok("Your earlier screensaver is back: ${Saver.describe(old)}.")
    }
}
