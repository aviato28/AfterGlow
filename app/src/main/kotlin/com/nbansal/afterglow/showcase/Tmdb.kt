package com.nbansal.afterglow.showcase

import org.json.JSONObject
import java.util.Locale

/** One trending title, as shown on the Showcase home. [type] is "movie" or "tv". [rating] is "" when TMDB has no votes. */
data class ShowTitle(
    val id: Int,
    val type: String,
    val title: String,
    val year: String,
    val genre: String,
    val rating: String,
    val overview: String,
    val backdrop: String,
)

/** TMDB thrown as a plain status so nothing that could contain the request URL (and so the key) is ever kept or shown. */
class TmdbHttpException(val code: Int) : Exception("HTTP $code")

/** Pure parsing and URL helpers for TMDB (no network here, so everything is unit-tested with fixture JSON). */
object Tmdb {
    const val API = "https://api.themoviedb.org/3"
    const val IMAGE = "https://image.tmdb.org/t/p/w1280"
    const val TRENDING = "/trending/all/week?language=en-US"
    const val GENRES_MOVIE = "/genre/movie/list?language=en-US"
    const val GENRES_TV = "/genre/tv/list?language=en-US"
    const val CONFIG = "/configuration"
    const val COUNT = 10
    const val ATTRIBUTION = "This product uses the TMDB API but is not endorsed or certified by TMDB."

    private val V3 = Regex("[0-9a-fA-F]{32}")

    /** v4 read token (a JWT, starts with `eyJ`) goes in a Bearer header; a v3 key goes in the query. */
    fun usesBearer(key: String): Boolean = key.trim().startsWith("eyJ")

    fun isValidKey(key: String): Boolean {
        val k = key.trim()
        return V3.matches(k) || (usesBearer(k) && k.length > 40 && k.none { it.isWhitespace() })
    }

    /** Full request URL for [path] (which already has a query string). Contains the key for v3: never log it. */
    fun requestUrl(path: String, key: String): String {
        val base = API + path
        if (usesBearer(key)) return base
        return base + (if ('?' in base) "&" else "?") + "api_key=" + key.trim()
    }

    fun bearer(key: String): String? = if (usesBearer(key)) "Bearer ${key.trim()}" else null

    fun imageUrl(backdrop: String): String = IMAGE + backdrop

    fun parseGenres(json: String): Map<Int, String> {
        val arr = JSONObject(json).optJSONArray("genres") ?: return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val name = g.optString("name")
            if (name.isNotEmpty()) out[g.optInt("id")] = name
        }
        return out
    }

    fun genresToJson(genres: Map<Int, String>): String {
        val o = JSONObject()
        genres.forEach { (id, name) -> o.put(id.toString(), name) }
        return o.toString()
    }

    fun genresFromJson(json: String): Map<Int, String> {
        val o = JSONObject(json)
        val out = LinkedHashMap<Int, String>()
        for (k in o.keys()) k.toIntOrNull()?.let { out[it] = o.optString(k) }
        return out
    }

    /** Movie/tv items that have a backdrop, first [limit] in TMDB's order. */
    fun parseTrending(json: String, genres: Map<Int, String>, limit: Int = COUNT): List<ShowTitle> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<ShowTitle>()
        for (i in 0 until results.length()) {
            if (out.size >= limit) break
            val o = results.optJSONObject(i) ?: continue
            val type = o.optString("media_type")
            if (type != "movie" && type != "tv") continue
            if (o.isNull("backdrop_path")) continue
            val backdrop = o.optString("backdrop_path")
            if (backdrop.isEmpty() || !backdrop.startsWith("/")) continue
            val id = o.optInt("id", -1)
            if (id < 0) continue
            val title = (if (type == "movie") o.optString("title") else o.optString("name")).ifEmpty { o.optString("title").ifEmpty { o.optString("name") } }
            if (title.isEmpty()) continue
            val date = if (type == "movie") o.optString("release_date") else o.optString("first_air_date")
            val ids = o.optJSONArray("genre_ids")
            val genre = if (ids != null && ids.length() > 0) genres[ids.optInt(0)].orEmpty() else ""
            out.add(ShowTitle(id, type, title, year(date), genre, rating(o.optDouble("vote_average", 0.0)), o.optString("overview").trim(), backdrop))
        }
        return out
    }

    fun year(date: String?): String = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: ""

    /** One decimal, always a dot; "" when there are no votes. */
    fun rating(v: Double): String = if (v <= 0.0 || v.isNaN()) "" else String.format(Locale.US, "%.1f", v)

    /** "2024 · Sci-Fi · ★ 7.8" with missing parts left out. */
    fun metaLine(t: ShowTitle): String =
        listOf(t.year, t.genre, if (t.rating.isEmpty()) "" else "★ ${t.rating}").filter { it.isNotEmpty() }.joinToString(" · ")

    /** What Watch sends to the chosen app. */
    fun watchUrl(t: ShowTitle): String = "https://www.themoviedb.org/${t.type}/${t.id}"

    /** Title size: 96 sp, smaller for long names so they stay on one line. */
    fun titleSizeSp(title: String): Int = when {
        title.length <= 14 -> 96
        title.length <= 22 -> 72
        else -> 56
    }

    /** Readable message for Settings. Never includes the URL or key. */
    fun errorText(e: Throwable): String = when {
        e is TmdbHttpException && e.code == 401 -> "TMDB did not accept the key (401). Check it in Settings > Look."
        e is TmdbHttpException && e.code == 429 -> "TMDB is busy (429). Try again later."
        e is TmdbHttpException -> "TMDB error ${e.code}"
        e is java.io.IOException -> "Could not reach TMDB (no network?)"
        else -> "TMDB data could not be read"
    }
}
