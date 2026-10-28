package com.nbansal.afterglow.showcase

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** TMDB over HttpURLConnection with a JSON cache in filesDir. Blocking: call on Dispatchers.IO. Nothing here logs. */
class TmdbRepo(private val context: Context) {
    private val trendingFile get() = File(context.filesDir, "showcase-trending.json")
    private val genresFile get() = File(context.filesDir, "showcase-genres.json")

    /** The last saved list (any age), or empty. */
    fun cached(): List<ShowTitle> = runCatching {
        if (!trendingFile.exists()) emptyList()
        else Tmdb.parseTrending(trendingFile.readText(), genres())
    }.getOrDefault(emptyList())

    private fun genres(): Map<Int, String> = runCatching {
        if (genresFile.exists()) Tmdb.genresFromJson(genresFile.readText()) else emptyMap()
    }.getOrDefault(emptyMap())

    /** The cache when it is under 6 hours old, else a fresh download (genres at most every 30 days). Throws on network errors. */
    fun refreshIfDue(key: String, nowMs: Long): List<ShowTitle> {
        if (ShowcaseLogic.fresh(trendingFile.lastModified(), nowMs, ShowcaseLogic.REFRESH_MS)) {
            val c = cached()
            if (c.isNotEmpty()) return c
        }
        if (!ShowcaseLogic.fresh(genresFile.lastModified(), nowMs, ShowcaseLogic.GENRES_MS)) {
            runCatching {
                val merged = LinkedHashMap<Int, String>()
                merged.putAll(Tmdb.parseGenres(get(Tmdb.GENRES_MOVIE, key)))
                merged.putAll(Tmdb.parseGenres(get(Tmdb.GENRES_TV, key)))
                if (merged.isNotEmpty()) genresFile.writeText(Tmdb.genresToJson(merged))
            }
        }
        val body = get(Tmdb.TRENDING, key)
        val titles = Tmdb.parseTrending(body, genres())
        if (titles.isNotEmpty()) trendingFile.writeText(body)
        return titles
    }

    /** Settings > Look > Test key. Throws when the key is not accepted. */
    fun testKey(key: String) {
        get(Tmdb.CONFIG, key)
    }

    private fun get(path: String, key: String): String {
        val conn = (URL(Tmdb.requestUrl(path, key)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            Tmdb.bearer(key)?.let { setRequestProperty("Authorization", it) }
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw TmdbHttpException(code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
