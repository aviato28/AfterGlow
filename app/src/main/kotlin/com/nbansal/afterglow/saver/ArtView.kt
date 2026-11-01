package com.nbansal.afterglow.saver

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import com.nbansal.afterglow.R
import com.nbansal.afterglow.showcase.ShowcaseLogic
import java.io.File
import java.time.LocalTime
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The artwork screensaver: cached Showcase backdrops from [files] (disk only, no network), one at a time, crossfaded, with a slow drift and a small clock.
 * At most two bitmaps are alive (the one fading out and the one fading in). Nothing runs after [stop].
 */
class ArtView(context: Context, files: List<File>) : View(context) {
    private class Slide(val bitmap: Bitmap, val startMs: Long)

    private val files = files.shuffled(Random.Default)
    private var index = -1
    private var current: Slide? = null
    private var previous: Slide? = null
    private var loading = false
    private var running = false
    private var animator: ValueAnimator? = null
    private var executor = Executors.newSingleThreadExecutor()
    private var night = false
    private var clock = ""

    private val photo = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = Color.BLACK; alpha = (ShowcaseLogic.NIGHT_DIM * 255).toInt() }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = runCatching { resources.getFont(R.font.outfit) }.getOrNull()
        runCatching { setFontVariationSettings("'wght' ${SaverStyle.ClockWeight + 100}") }
        textAlign = Paint.Align.RIGHT
        textSize = ClockSize
        color = SaverStyle.Warm
        setShadowLayer(12f, 0f, 2f, 0xAA000000.toInt())
    }

    fun start() {
        if (running) return
        running = true
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        tick()
        advance()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { tick(); invalidate() }
            start()
        }
    }

    /** Stops the animation and the loader and lets go of both bitmaps. */
    fun stop() {
        running = false
        animator?.cancel()
        animator = null
        executor.shutdownNow()
        current?.bitmap?.recycle(); current = null
        previous?.bitmap?.recycle(); previous = null
        loading = false
    }

    private fun tick() {
        val time = LocalTime.now()
        night = Saver.isNight(time)
        clock = Saver.clockText(time)
        val now = SystemClock.uptimeMillis()
        previous?.let { if (now - (current?.startMs ?: now) >= ArtLogic.FADE_MS) { it.bitmap.recycle(); previous = null } }
        val cur = current
        if (cur != null && now - cur.startMs >= ArtLogic.SLIDE_MS) advance()
    }

    /** Loads the next picture off the main thread; shows it when ready. */
    private fun advance() {
        if (loading || files.isEmpty()) return
        loading = true
        index = ArtLogic.next(index, files.size)
        val file = files[index]
        val width = resources.displayMetrics.widthPixels
        runCatching {
            executor.execute {
                val bitmap = decode(file, width)
                post {
                    loading = false
                    if (!running) { bitmap?.recycle(); return@post }
                    if (bitmap == null) return@post // the next tick tries the following file
                    previous?.bitmap?.recycle()
                    previous = current
                    current = Slide(bitmap, SystemClock.uptimeMillis())
                    invalidate()
                }
            }
        }.onFailure { loading = false }
    }

    private fun decode(file: File, targetWidth: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ShowcaseLogic.sampleSize(bounds.outWidth, targetWidth)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.path, opts)
    }.getOrNull()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(SaverStyle.Background)
        val now = SystemClock.uptimeMillis()
        val cur = current
        previous?.let { drawSlide(canvas, it, now, 255) }
        if (cur != null) {
            val alpha = if (previous == null) 1f else ArtLogic.fadeIn(now - cur.startMs)
            drawSlide(canvas, cur, now, (alpha * 255).toInt())
        }
        if (night) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        val scale = width / SaverStyle.Width
        clockPaint.alpha = if (night) (NightStyle.ClockAlpha * 255).toInt() else 255
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawText(clock, SaverStyle.Width - MarginX, SaverStyle.Height - MarginY, clockPaint)
        canvas.restore()
    }

    /** Fills the screen (centre crop) with the drift scale of this slide's age; the outgoing slide stops where its drift ended. */
    private fun drawSlide(canvas: Canvas, slide: Slide, now: Long, alpha: Int) {
        val b = slide.bitmap
        if (b.isRecycled) return
        val fill = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val s = fill * ArtLogic.scale(now - slide.startMs)
        photo.alpha = alpha
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(s, s)
        canvas.drawBitmap(b, -b.width / 2f, -b.height / 2f, photo)
        canvas.restore()
    }

    private companion object {
        const val ClockSize = 64f
        const val MarginX = 96f
        const val MarginY = 54f
    }
}
