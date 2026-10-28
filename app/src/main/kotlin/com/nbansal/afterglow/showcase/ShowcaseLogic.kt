package com.nbansal.afterglow.showcase

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Small pure helpers for Showcase (unit-tested). */
object ShowcaseLogic {
    const val MIN_RAM_BYTES = 2_800_000_000L
    const val REFRESH_MS = 6 * 60 * 60 * 1000L
    const val GENRES_MS = 30L * 24 * 60 * 60 * 1000L
    const val MAX_IMAGE_FILES = 10

    /** Showcase is offered from 2.8 GB of total RAM. */
    fun ramOk(totalBytes: Long): Boolean = totalBytes >= MIN_RAM_BYTES

    fun totalRam(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.totalMem
    }

    /** True when the cache written at [writtenMs] is still fresh at [nowMs] (a clock that went backwards counts as stale). */
    fun fresh(writtenMs: Long, nowMs: Long, maxAgeMs: Long): Boolean = writtenMs > 0 && nowMs >= writtenMs && nowMs - writtenMs < maxAgeMs

    /** Power-of-two `inSampleSize` that keeps the decoded width at or above [targetWidth]. */
    fun sampleSize(srcWidth: Int, targetWidth: Int): Int {
        if (srcWidth <= 0 || targetWidth <= 0) return 1
        var s = 1
        while (srcWidth / (s * 2) >= targetWidth) s *= 2
        return s
    }

    /** Average colour of ARGB [pixels] as opaque ARGB; dark neutral when empty. */
    fun averageColor(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0xFF201814.toInt()
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun tmdbLink(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/movie/1")).addCategory(Intent.CATEGORY_BROWSABLE)

    /** Installed apps that open TMDB links: (package, label), sorted by label. */
    fun openers(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return runCatching {
            pm.queryIntentActivities(tmdbLink(), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != context.packageName }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Watch: sends the TMDB link to [pkg]. Returns null when it was sent, else the message to show. */
    fun watch(context: Context, title: ShowTitle, pkg: String): String? {
        val openers = openers(context)
        if (openers.isEmpty()) return "No app on this TV opens TMDB links"
        if (pkg.isEmpty() || openers.none { it.first == pkg }) return "Choose an app in Settings > Look > Open titles with"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Tmdb.watchUrl(title))).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            null
        } catch (_: Exception) {
            "That app could not open the link"
        }
    }
}
