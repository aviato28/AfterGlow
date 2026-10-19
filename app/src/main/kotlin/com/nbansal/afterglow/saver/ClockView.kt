package com.nbansal.afterglow.saver

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.nbansal.afterglow.R
import com.nbansal.afterglow.ui.Ag
import com.nbansal.afterglow.ui.GlowPhase
import java.time.LocalDateTime
import kotlin.random.Random

/** The whole screensaver: one View that draws everything on a 1920x1080 layout scaled to the screen width. No animation loop. */
class ClockView(context: Context) : View(context) {
    private var now = LocalDateTime.now()
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var fade = 1f
    private var animator: ValueAnimator? = null
    private var registered = false

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = runCatching { resources.getFont(R.font.outfit) }.getOrNull()
        // Thin weight; if the variation does not apply the regular weight is simply used.
        runCatching { setFontVariationSettings("'wght' ${SaverStyle.ClockWeight}") }
        textAlign = Paint.Align.LEFT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = clockPaint.typeface
        runCatching { setFontVariationSettings("'wght' 300") }
        textSize = SaverStyle.TextSize
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunBox = RectF()

    private val ticker = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onMinute()
    }

    fun start() {
        now = LocalDateTime.now()
        if (!registered) {
            registered = true
            context.registerReceiver(ticker, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            })
        }
        invalidate()
    }

    /** Stops every callback; nothing runs while the screensaver is off. */
    fun stop() {
        animator?.cancel()
        animator = null
        if (registered) {
            registered = false
            runCatching { context.unregisterReceiver(ticker) }
        }
    }

    private fun onMinute() {
        now = LocalDateTime.now()
        fromX = toX; fromY = toY
        val night = Saver.isNight(now.toLocalTime())
        val box = groupBox(night)
        val (dx, dy) = Saver.shift(Random.Default, box.left, box.top, box.right, box.bottom)
        toX = dx; toY = dy
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SaverStyle.FadeMs
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun clockSize(night: Boolean) = SaverStyle.ClockSize * if (night) NightStyle.ClockScale else 1f

    /** Where the group sits (before any shift): centred, clock on top, text under it, horizon last. */
    private fun groupBox(night: Boolean): RectF {
        val h = clockSize(night) * 0.7f + SaverStyle.TextGap + SaverStyle.TextSize + SaverStyle.HorizonBelowText
        val top = (SaverStyle.Height - h) / 2f
        return RectF((SaverStyle.Width - clockWidth(night)) / 2f, top, (SaverStyle.Width + clockWidth(night)) / 2f, top + h)
    }

    private fun clockWidth(night: Boolean): Float {
        applyClockPaint(night)
        return maxOf(clockPaint.measureText(Saver.clockText(now.toLocalTime())), SaverStyle.HorizonWidth)
    }

    private fun applyClockPaint(night: Boolean) {
        clockPaint.textSize = clockSize(night)
        clockPaint.letterSpacing = SaverStyle.ClockSpacing / clockPaint.textSize
    }

    override fun onDraw(canvas: Canvas) {
        val scale = width / SaverStyle.Width
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(0f, (height / scale - SaverStyle.Height) / 2f)
        val time = now.toLocalTime()
        val night = Saver.isNight(time)
        canvas.drawColor(SaverStyle.Background)
        drawGlow(canvas, GlowPhase.at(time), if (night) NightStyle.GlowScale else 1f)
        if (fade < 1f) drawGroup(canvas, night, fromX, fromY, 1f - fade)
        drawGroup(canvas, night, toX, toY, fade)
        canvas.restore()
    }

    private fun drawGlow(canvas: Canvas, phase: GlowPhase, scale: Float) {
        val stops = phase.stops(scale)
        fill.shader = RadialGradient(
            0f, 0f, 0.85f, stops.map { it.second.toArgb() }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP,
        )
        canvas.save()
        // The glow is an ellipse centred below the bottom edge, drawn as a unit circle scaled to the screen (as ui/Glow.kt).
        canvas.translate(SaverStyle.Width / 2f, SaverStyle.Height * 1.18f)
        canvas.scale(SaverStyle.Width, SaverStyle.Height)
        canvas.drawRect(-0.5f, -1.18f, 0.5f, -0.18f, fill)
        canvas.restore()
        fill.shader = null
    }

    private fun drawGroup(canvas: Canvas, night: Boolean, dx: Float, dy: Float, alpha: Float) {
        if (alpha <= 0f) return
        val time = now.toLocalTime()
        val box = groupBox(night)
        val cx = SaverStyle.Width / 2f + dx
        applyClockPaint(night)

        val text = Saver.clockText(time)
        val clockAlpha = alpha * if (night) NightStyle.ClockAlpha else 1f
        clockPaint.color = SaverStyle.Warm
        clockPaint.alpha = (clockAlpha * 255).toInt()
        val clockBase = box.top + dy + clockSize(night) * 0.7f
        canvas.drawText(text, cx - clockPaint.measureText(text) / 2f, clockBase, clockPaint)

        val greeting = Saver.greeting(time)?.let { "$it  ·  " }.orEmpty()
        val date = Saver.dateText(now.toLocalDate())
        val gw = textPaint.measureText(greeting)
        var x = cx - (gw + textPaint.measureText(date)) / 2f
        val textBase = clockBase + SaverStyle.TextGap + SaverStyle.TextSize * 0.8f
        textPaint.color = SaverStyle.Warm; textPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(greeting, x, textBase, textPaint)
        x += gw
        textPaint.color = SaverStyle.Secondary; textPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(date, x, textBase, textPaint)

        val horizon = textBase + SaverStyle.HorizonBelowText
        val partial = alpha * if (night) NightStyle.HorizonAlpha else 1f
        val half = SaverStyle.HorizonWidth / 2f
        val line = (SaverStyle.Warm and 0x00FFFFFF) or ((partial * 0.35f * 255).toInt() shl 24)
        fill.shader = LinearGradient(cx - half, 0f, cx + half, 0f, intArrayOf(0, line, line, 0), floatArrayOf(0f, 0.25f, 0.75f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(cx - half, horizon, cx + half, horizon + 2f, fill)

        fill.shader = LinearGradient(0f, horizon - SaverStyle.SunHeight, 0f, horizon, Ag.Amber.toArgb(), Ag.Rose.toArgb(), Shader.TileMode.CLAMP)
        fill.alpha = (partial * 255).toInt()
        sunBox.set(cx - SaverStyle.SunWidth / 2f, horizon - SaverStyle.SunHeight, cx + SaverStyle.SunWidth / 2f, horizon + SaverStyle.SunHeight)
        canvas.drawArc(sunBox, 180f, 180f, true, fill)
        fill.shader = null
        fill.alpha = 255
    }
}
