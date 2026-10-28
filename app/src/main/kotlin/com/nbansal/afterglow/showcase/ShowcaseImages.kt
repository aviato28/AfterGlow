package com.nbansal.afterglow.showcase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A decoded backdrop and the average colour of its bottom third (for the glow). */
class ShowImage(val bitmap: Bitmap, val tint: Int)

/**
 * Backdrops: files in cacheDir (at most 10), decoded at the screen's width as RGB_565. Memory holds only the titles passed as
 * `keep` (current and next); [clear] drops everything (onTrimMemory).
 */
class ShowcaseImages(private val context: Context) {
    private val memory = LinkedHashMap<Int, ShowImage>()
    private val dir get() = File(context.cacheDir, "showcase")

    fun clear() = synchronized(memory) { memory.clear() }

    suspend fun load(title: ShowTitle, keep: Set<Int>): ShowImage? = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[title.id] }?.let { return@withContext it }
        val image = runCatching { decode(title) }.getOrNull() ?: return@withContext null
        synchronized(memory) {
            memory[title.id] = image
            memory.keys.retainAll(keep + title.id)
        }
        image
    }

    private fun decode(title: ShowTitle): ShowImage? {
        val file = fileFor(title)
        if (!file.exists() || file.length() == 0L) download(title, file)
        val target = context.resources.displayMetrics.widthPixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ShowcaseLogic.sampleSize(bounds.outWidth, target)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: run { file.delete(); return null }
        return ShowImage(bitmap, tintOf(bitmap))
    }

    private fun fileFor(title: ShowTitle) = File(dir, title.backdrop.removePrefix("/").replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun download(title: ShowTitle, file: File) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".part")
        val conn = (URL(Tmdb.imageUrl(title.backdrop)).openConnection() as HttpURLConnection).apply {
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
