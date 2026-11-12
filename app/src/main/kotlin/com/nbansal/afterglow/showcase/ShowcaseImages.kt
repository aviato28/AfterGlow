package com.nbansal.afterglow.showcase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A decoded backdrop, the average colour of its bottom third and its one accent colour (ARGB). */
class ShowImage(val bitmap: Bitmap, val tint: Int, val accent: Int)

/**
 * Backdrops and title logos: files in cacheDir (at most 24 together), decoded at the screen's width as RGB_565. Memory holds only the titles passed as
 * `keep` (current and next); [clear] drops everything (onTrimMemory).
 */
class ShowcaseImages(private val context: Context) {
    private val memory = LinkedHashMap<Int, ShowImage>()
    private val logos = LinkedHashMap<Int, Bitmap>()
    /** Logo paths found to have a solid background (guarded by `logos`). */
    private val boxedLogos = HashSet<String>()
    private val dir get() = File(context.cacheDir, "showcase")

    fun clear() {
        synchronized(memory) { memory.clear() }
        synchronized(logos) { logos.clear() }
    }

    suspend fun load(title: ShowTitle, keep: Set<Int>): ShowImage? = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[title.id] }?.let { return@withContext it }
        val image = runCatching { decode(title) }.getOrNull() ?: return@withContext null
        synchronized(memory) {
            memory[title.id] = image
            memory.keys.retainAll(keep + title.id)
        }
        image
    }

    /** The title's logo (ARGB_8888, decoded at its display size), or null when it has none or it cannot be fetched. */
    suspend fun loadLogo(title: ShowTitle, keep: Set<Int>): Bitmap? = withContext(Dispatchers.IO) {
        val candidates = title.details.logos.ifEmpty { listOfNotNull(title.details.logo.takeIf { it.isNotEmpty() }) }
        if (candidates.isEmpty()) return@withContext null
        synchronized(logos) { logos[title.id] }?.let { return@withContext it }
        // A logo with a solid box around it is skipped: the next candidate is tried, else the caller shows the text title.
        for (path in candidates) {
            if (synchronized(logos) { path in boxedLogos }) continue
            val bitmap = runCatching { decodeLogo(path) }.getOrNull() ?: continue
            if (isBoxed(bitmap)) {
                synchronized(logos) { boxedLogos.add(path) }
                bitmap.recycle()
                continue
            }
            synchronized(logos) {
                logos[title.id] = bitmap
                logos.keys.retainAll(keep + title.id)
            }
            return@withContext bitmap
        }
        null
    }

    private fun isBoxed(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return ShowcaseLogic.logoIsBoxed(px, w, h)
    }

    private fun decodeLogo(path: String): Bitmap? {
        val file = File(dir, "logo_" + safeName(path))
        if (!file.exists() || file.length() == 0L) download(Tmdb.logoUrl(path), file)
        // Shown up to 860 dp wide on the 1920-wide canvas.
        val target = context.resources.displayMetrics.widthPixels * 860 / 1920
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ShowcaseLogic.sampleSize(bounds.outWidth, target)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, opts) ?: run { file.delete(); null }
    }

    private fun decode(title: ShowTitle): ShowImage? {
        val file = fileFor(title)
        if (!file.exists() || file.length() == 0L) download(Tmdb.imageUrl(title.backdrop), file)
        val target = context.resources.displayMetrics.widthPixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ShowcaseLogic.sampleSize(bounds.outWidth, target)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: run { file.delete(); return null }
        return ShowImage(bitmap, tintOf(bitmap), accentOf(bitmap))
    }

    companion object {
        /** The backdrop files already on disk (no logos, no half downloads), for the artwork screensaver. Reads the disk only. */
        fun cachedBackdrops(context: Context): List<File> =
            File(context.cacheDir, "showcase").listFiles()
                ?.filter { it.isFile && it.length() > 0 && com.nbansal.afterglow.saver.ArtLogic.isBackdropName(it.name) }
                .orEmpty()
    }

    private fun safeName(path: String) = path.removePrefix("/").replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun fileFor(title: ShowTitle) = File(dir, safeName(title.backdrop))

    private fun download(url: String, file: File) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode != 200) throw TmdbHttpException(conn.responseCode)
            tmp.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
            tmp.renameTo(file)
        } finally {
            conn.disconnect()
            tmp.delete()
        }
        prune()
    }

    /** Keeps the newest [ShowcaseLogic.MAX_IMAGE_FILES] files. */
    private fun prune() {
        dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(ShowcaseLogic.MAX_IMAGE_FILES)?.forEach { it.delete() }
    }

    /** The accent: backdrop scaled down to 16x9, then the most saturated mid-bright pixel. */
    private fun accentOf(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 16, 9, true)
        val px = IntArray(16 * 9)
        small.getPixels(px, 0, 16, 0, 0, 16, 9)
        if (small !== bitmap) small.recycle()
        return ShowcaseLogic.pickAccent(px)
    }

    /** A cheap sample (about 12 x 4 points) of the bottom third. */
    private fun tintOf(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val px = ArrayList<Int>()
        val y0 = h * 2 / 3
        for (yi in 0 until 4) for (xi in 0 until 12) {
            px.add(bitmap.getPixel((w * (2 * xi + 1)) / 24, y0 + ((h - y0) * (2 * yi + 1)) / 8))
        }
        return ShowcaseLogic.averageColor(px.toIntArray())
    }
}
