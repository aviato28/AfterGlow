package com.nbansal.afterglow.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Banner bitmaps drawn at the displayed pixel size, in a small LRU cache (about 10% of the app's memory). */
object Banners {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun clear() = cache.evictAll()

    suspend fun load(context: Context, app: AppEntry, width: Int, height: Int): Bitmap? {
        val key = "${app.pkg}@${app.label}@$width"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching { render(context.applicationContext, app, width, height) }.getOrNull()
        }?.also { cache.put(key, it) }
    }

    private fun render(context: Context, app: AppEntry, w: Int, h: Int): Bitmap {
        val pm = context.packageManager
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val banner: Drawable? = runCatching { pm.getActivityBanner(ComponentName(app.pkg, app.activity)) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.pkg) }.getOrNull()
        if (banner != null) {
            banner.setBounds(0, 0, w, h)
            banner.draw(canvas)
            return bitmap
        }
        // Fallback: icon centred on a dark rounded tile, label under it.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1715.toInt() }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h * 0.11f, h * 0.11f, paint)
        val icon = runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull()
        val size = (h * 0.5f).toInt()
        val left = (w - size) / 2
        val top = (h * 0.12f).toInt()
        icon?.apply { setBounds(left, top, left + size, top + size); draw(canvas) }
        paint.color = 0xFFF3E9DF.toInt()
        paint.textSize = h * 0.125f
        paint.textAlign = Paint.Align.CENTER
        val text = android.text.TextUtils.ellipsize(
            app.label, android.text.TextPaint(paint), w * 0.88f, android.text.TextUtils.TruncateAt.END,
        ).toString()
        canvas.drawText(text, w / 2f, h * 0.88f, paint)
        return bitmap
    }
}

@Composable
fun rememberBanner(app: AppEntry, widthPx: Int, heightPx: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, app.pkg, app.label, widthPx) {
        value = Banners.load(context, app, widthPx, heightPx)?.asImageBitmap()
    }
}
