package com.nbansal.afterglow.showcase

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Per-title extras from one TMDB details call. [logo] is a file path like "/abc.png" ("" = none), [released] is "yyyy-MM-dd" or "". */
data class ShowDetails(
    val logo: String = "",
    val cert: String = "",
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val released: String = "",
)

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
    val details: ShowDetails = ShowDetails(),
)

/** TMDB thrown as a plain status so nothing that could contain the request URL (and so the key) is ever kept or shown. */
class TmdbHttpException(val code: Int) : Exception("HTTP $code")

/** Pure parsing and URL helpers for TMDB (no network here, so everything is unit-tested with fixture JSON). */
object Tmdb {
    const val API = "https://api.themoviedb.org/3"
    const val IMAGE = "https://image.tmdb.org/t/p/w1280"
    const val LOGO_IMAGE = "https://image.tmdb.org/t/p/w500"
    const val NEW_DAYS = 60L
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

    fun logoUrl(logo: String): String = LOGO_IMAGE + logo

    /** Details path for one title: one call returns the logos and the age ratings too (`append_to_response`). */
    fun detailsPath(t: ShowTitle): String {
        val extra = if (t.type == "movie") "images,release_dates" else "images,content_ratings"
        return "/${t.type}/${t.id}?language=en-US&append_to_response=$extra&include_image_language=en,null"
    }

    /** Best logo file path: English first, then no language; the highest vote wins; SVG is skipped (cannot be decoded). "" = none. */
    fun pickLogo(logos: JSONArray?): String {
        if (logos == null) return ""
        var best = ""
        var bestRank = Int.MAX_VALUE
        var bestVote = -1.0
        for (i in 0 until logos.length()) {
            val o = logos.optJSONObject(i) ?: continue
            val path = o.optString("file_path")
            if (!path.startsWith("/") || path.endsWith(".svg", ignoreCase = true)) continue
            val rank = when {
                o.isNull("iso_639_1") -> 1
                o.optString("iso_639_1") == "en" -> 0
                else -> continue
            }
            val vote = o.optDouble("vote_average", 0.0)
            if (rank < bestRank || (rank == bestRank && vote > bestVote)) {
                best = path
                bestRank = rank
                bestVote = vote
            }
        }
        return best
    }

    /** Age rating: region IN, else US. Movies: first non-empty `certification` of the region in `release_dates`; tv: `rating` in `content_ratings`. */
    fun pickCert(root: JSONObject, type: String): String {
        val results = if (type == "movie") root.optJSONObject("release_dates")?.optJSONArray("results")
        else root.optJSONObject("content_ratings")?.optJSONArray("results")
        if (results == null) return ""
        for (region in listOf("IN", "US")) {
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                if (r.optString("iso_3166_1") != region) continue
                if (type == "movie") {
                    val list = r.optJSONArray("release_dates") ?: continue
                    for (j in 0 until list.length()) {
                        val c = list.optJSONObject(j)?.optString("certification").orEmpty().trim()
                        if (c.isNotEmpty()) return c
                    }
                } else {
                    val c = r.optString("rating").trim()
                    if (c.isNotEmpty()) return c
                }
            }
        }
        return ""
    }

    fun parseDetails(json: String, type: String): ShowDetails {
        val o = JSONObject(json)
        val runtime = if (type == "movie") o.optInt("runtime", 0) else o.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0
        val genres = ArrayList<String>()
        val arr = o.optJSONArray("genres")
        if (arr != null) for (i in 0 until arr.length()) {
            if (genres.size >= 2) break
            val n = arr.optJSONObject(i)?.optString("name").orEmpty()
            if (n.isNotEmpty()) genres.add(n)
        }
        val date = if (type == "movie") o.optString("release_date") else o.optString("first_air_date")
        return ShowDetails(
            logo = pickLogo(o.optJSONObject("images")?.optJSONArray("logos")),
            cert = pickCert(o, type),
            runtime = runtime.coerceAtLeast(0),
            genres = genres,
            released = date.trim(),
        )
    }

    fun detailsToJson(d: ShowDetails): JSONObject = JSONObject()
        .put("logo", d.logo).put("cert", d.cert).put("runtime", d.runtime)
        .put("genres", JSONArray(d.genres)).put("released", d.released)

    fun detailsFromJson(o: JSONObject): ShowDetails {
        val g = o.optJSONArray("genres")
        val genres = if (g == null) emptyList() else (0 until g.length()).map { g.optString(it) }.filter { it.isNotEmpty() }
        return ShowDetails(o.optString("logo"), o.optString("cert"), o.optInt("runtime", 0), genres, o.optString("released"))
    }

    /** "New on streaming": released 0 to 60 days before [today] (a future date is not new yet). */
    fun isNew(released: String, today: LocalDate): Boolean {
        val d = runCatching { LocalDate.parse(released.take(10)) }.getOrNull() ?: return false
        val days = ChronoUnit.DAYS.between(d, today)
        return days in 0L..NEW_DAYS
    }

    /** 125 -> "2h 5m", 120 -> "2h", 45 -> "45m", 0 -> "". */
    fun runtimeText(min: Int): String = when {
        min <= 0 -> ""
        min < 60 -> "${min}m"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    /** Score ring value: vote average x 10 (rating "7.8" -> 78); 0 when there are no votes. */
    fun scorePercent(rating: String): Int = Math.round((rating.toDoubleOrNull() ?: 0.0) * 10).toInt().coerceIn(0, 100)

    /** Year, up to 2 genres (from details, else the trending genre) and runtime, missing parts left out. */
    fun infoParts(t: ShowTitle): List<String> = listOf(
        t.year,
        t.details.genres.joinToString(" · ").ifEmpty { t.genre },
        runtimeText(t.details.runtime),
    ).filter { it.isNotEmpty() }

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
