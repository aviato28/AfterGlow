package com.nbansal.afterglow.showcase

import org.json.JSONArray
import org.json.JSONObject

/** Settings > Look "More of / Less of" genres and the languages to favour. Genre ids are TMDB's (movie and tv lists merged). */
data class Taste(
    val more: Set<Int> = emptySet(),
    val less: Set<Int> = emptySet(),
    val languages: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = more.isEmpty() && less.isEmpty() && languages.isEmpty()

    /** The post-filter for trending lists: no "less" genre, at least one "more" genre if any are set, and a chosen language (unknown language passes). */
    fun accepts(t: ShowTitle): Boolean {
        if (t.genreIds.any { it in less }) return false
        if (more.isNotEmpty() && t.genreIds.none { it in more }) return false
        if (languages.isNotEmpty() && t.lang.isNotEmpty() && t.lang !in languages) return false
        return true
    }

    /** A genre is either "more of", "less of" or neither: switching one on switches it off in the other list. */
    fun toggleMore(id: Int): Taste = if (id in more) copy(more = more - id) else copy(more = more + id, less = less - id)

    fun toggleLess(id: Int): Taste = if (id in less) copy(less = less - id) else copy(less = less + id, more = more - id)

    fun toggleLanguage(code: String): Taste = copy(languages = if (code in languages) languages - code else languages + code)

    fun toJson(): JSONObject = JSONObject()
        .put("more", JSONArray(more.sorted()))
        .put("less", JSONArray(less.sorted()))
        .put("lang", JSONArray(languages.sorted()))

    companion object {
        fun fromJson(o: JSONObject?): Taste {
            if (o == null) return Taste()
            fun ints(a: JSONArray?): Set<Int> = if (a == null) emptySet() else (0 until a.length()).map { a.optInt(it) }.toSet()
            val l = o.optJSONArray("lang")
            val langs = if (l == null) emptySet() else (0 until l.length()).map { l.optString(it) }.filter { it.isNotEmpty() }.toSet()
            return Taste(ints(o.optJSONArray("more")), ints(o.optJSONArray("less")), langs)
        }
    }
}

/** One TMDB list to read for the pool. [type] is "movie" or "tv" for lists without `media_type` ("" = the items say), [via] the service for the badge. */
class PoolQuery(val path: String, val type: String, val via: String = "")

/** Pool building for Showcase (pure: no network, so it is all unit-tested). */
object Picks {
    const val POOL = 50
    /** Fewer unseen titles than this -> the least recently shown fill the pool up to it. */
    const val MIN_FRESH = 20
    const val REPEAT_MS = 3L * 24 * 60 * 60 * 1000
    const val PRUNE_MS = 7L * 24 * 60 * 60 * 1000
    const val PROVIDERS_MS = 7L * 24 * 60 * 60 * 1000
    const val PROVIDERS = "/watch/providers/movie?language=en-US&watch_region=IN"
    const val TRENDING_DAY = "/trending/all/day?language=en-US"
    const val TRENDING_WEEK = "/trending/all/week?language=en-US"
    /** TMDB takes "|" as OR; it is sent percent-encoded. */
    private const val PIPE = "%7C"

    /** Language chips on the Look page: code to name. */
    val LANGUAGES: List<Pair<String, String>> = listOf(
        "en" to "English", "hi" to "Hindi", "ko" to "Korean", "ja" to "Japanese",
        "es" to "Spanish", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam",
    )

    /** Movie and tv ids overlap in TMDB, so "seen" and "not interested" use this key. */
    fun key(t: ShowTitle): String = "${t.type}:${t.id}"

    /** Same seed all day (UTC), so the shuffle does not change on every refresh. */
    fun dailySeed(nowMs: Long): Long = nowMs / 86_400_000L

    // ---- providers --------------------------------------------------------------------------------------------------------------------

    private class Rule(val pkg: String, val exact: String?, val contains: String?)

    private val RULES = listOf(
        Rule("com.netflix", "netflix", null),
        Rule("com.amazon.amazonvideo.livingroom", "amazonprimevideo", null),
        Rule("in.startv.hotstar", null, "hotstar"),
        Rule("com.sonyliv", "sonyliv", null),
        Rule("com.graymatrix.did", "zee5", null),
    )

    private fun norm(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** TMDB `watch/providers` response -> id to name. */
    fun parseProviders(json: String): Map<Int, String> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyMap()
        val out = LinkedHashMap<Int, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("provider_name")
            val id = o.optInt("provider_id", -1)
            if (id >= 0 && name.isNotEmpty()) out[id] = name
        }
        return out
    }

    /** Providers (id to TMDB name) of the streaming apps in [installedPkgs]; an app TMDB has no provider for is ignored. */
    fun matchProviders(installedPkgs: Collection<String>, providers: Map<Int, String>): Map<Int, String> {
        val out = java.util.TreeMap<Int, String>()
        for (rule in RULES) {
            val has = installedPkgs.any { it == rule.pkg || it.startsWith(rule.pkg + ".") }
            if (!has) continue
            for ((id, name) in providers) {
                val n = norm(name)
                val hit = (rule.exact != null && n == rule.exact) || (rule.contains != null && n.contains(rule.contains))
                if (hit) out[id] = name
            }
        }
        return out
    }

    // ---- queries ----------------------------------------------------------------------------------------------------------------------

    /** `discover` URL path. Providers, genres and language are only added when set. */
    fun discoverPath(type: String, page: Int, providers: Collection<Int>, taste: Taste, language: String?): String {
        val sb = StringBuilder("/discover/").append(type).append("?language=en-US&sort_by=popularity.desc&page=").append(page)
        if (providers.isNotEmpty()) sb.append("&watch_region=IN&with_watch_providers=").append(providers.sorted().joinToString(PIPE))
        if (taste.more.isNotEmpty()) sb.append("&with_genres=").append(taste.more.sorted().joinToString(PIPE))
        if (taste.less.isNotEmpty()) sb.append("&without_genres=").append(taste.less.sorted().joinToString(PIPE))
        if (language != null) sb.append("&with_original_language=").append(language)
        return sb.toString()
    }

    /**
     * Every list the pool is built from. With providers (or a taste that needs it): discover for movies and tv (2 pages; 1 page each when
     * 3 or more languages are chosen, since `with_original_language` takes one code), then trending day and week. Without providers also
     * popular, now playing in India and top rated. A single provider names every discover title; with several, [Picks.attribution] does.
     */
    fun queries(providers: Map<Int, String>, taste: Taste): List<PoolQuery> {
        val out = ArrayList<PoolQuery>()
        val langs: List<String?> = if (taste.languages.isEmpty()) listOf(null) else taste.languages.sorted()
        if (providers.isNotEmpty() || taste.more.isNotEmpty() || taste.languages.isNotEmpty()) {
            val pages = if (langs.size <= 2) 2 else 1
            val via = if (providers.size == 1) providers.values.first() else ""
            for (type in listOf("movie", "tv")) for (lang in langs) for (page in 1..pages) {
                out.add(PoolQuery(discoverPath(type, page, providers.keys, taste, lang), type, via))
            }
        }
        out.add(PoolQuery(TRENDING_DAY, ""))
        out.add(PoolQuery(TRENDING_WEEK, ""))
        if (providers.isEmpty()) {
            out.add(PoolQuery("/movie/popular?language=en-US&region=IN", "movie"))
            out.add(PoolQuery("/tv/popular?language=en-US", "tv"))
            out.add(PoolQuery("/movie/now_playing?language=en-US&region=IN", "movie"))
            out.add(PoolQuery("/movie/top_rated?language=en-US&region=IN", "movie"))
            out.add(PoolQuery("/tv/top_rated?language=en-US", "tv"))
        }
        return out
    }

    /** Fills [ShowTitle.via] from per-provider lists (provider name to its titles): the first provider that lists a title names it. */
    fun attribution(byProvider: List<Pair<String, List<ShowTitle>>>, titles: List<ShowTitle>): List<ShowTitle> {
        val map = HashMap<String, String>()
        for ((name, list) in byProvider) for (t in list) map.getOrPut(key(t)) { name }
        return titles.map { t -> if (t.via.isNotEmpty()) t else map[key(t)]?.let { t.copy(via = it) } ?: t }
    }

    // ---- the pool ---------------------------------------------------------------------------------------------------------------------

    /** One entry per title (first one wins; a later copy only adds the missing [ShowTitle.via]). */
    fun dedupe(list: List<ShowTitle>): List<ShowTitle> {
        val seen = LinkedHashMap<String, ShowTitle>()
        for (t in list) {
            val k = key(t)
            val old = seen[k]
            if (old == null) seen[k] = t
            else if (old.via.isEmpty() && t.via.isNotEmpty()) seen[k] = old.copy(via = t.via)
        }
        return seen.values.toList()
    }

    fun recentlyShown(lastShownMs: Long?, nowMs: Long): Boolean = lastShownMs != null && nowMs >= lastShownMs && nowMs - lastShownMs < REPEAT_MS

    /** Forgets titles last shown over 7 days ago. */
    fun pruneShown(shown: Map<String, Long>, nowMs: Long): Map<String, Long> = shown.filter { nowMs - it.value <= PRUNE_MS }

    /**
     * The pool: deduped, with a backdrop, not hidden, through the taste filter (ignored when it would leave nothing), shuffled with [seed],
     * titles shown in the last 3 days left out (unless fewer than [MIN_FRESH] remain: then the least recently shown fill up), at most [limit].
     */
    fun pool(
        candidates: List<ShowTitle>,
        taste: Taste,
        hidden: Set<String>,
        shown: Map<String, Long>,
        nowMs: Long,
        seed: Long,
        limit: Int = POOL,
    ): List<ShowTitle> {
        val unique = dedupe(candidates).filter { it.backdrop.isNotEmpty() && key(it) !in hidden }
        val tasted = unique.filter { taste.accepts(it) }.ifEmpty { unique }
        val shuffled = tasted.shuffled(java.util.Random(seed))
        val recent = shuffled.filter { recentlyShown(shown[key(it)], nowMs) }
        val unseen = shuffled.filter { !recentlyShown(shown[key(it)], nowMs) }
        val out = ArrayList<ShowTitle>(unseen.take(limit))
        if (out.size < MIN_FRESH) {
            val need = minOf(MIN_FRESH, limit) - out.size
            if (need > 0) out.addAll(recent.sortedBy { shown[key(it)] ?: 0L }.take(need))
        }
        return out
    }

    // ---- saving -----------------------------------------------------------------------------------------------------------------------

    fun titleToJson(t: ShowTitle): JSONObject = JSONObject()
        .put("id", t.id).put("type", t.type).put("title", t.title).put("year", t.year).put("genre", t.genre)
        .put("rating", t.rating).put("overview", t.overview).put("backdrop", t.backdrop).put("via", t.via)
        .put("genreIds", JSONArray(t.genreIds)).put("lang", t.lang)

    fun titleFromJson(o: JSONObject): ShowTitle? {
        val id = o.optInt("id", -1)
        val type = o.optString("type")
        val backdrop = o.optString("backdrop")
        if (id < 0 || (type != "movie" && type != "tv") || !backdrop.startsWith("/")) return null
        val g = o.optJSONArray("genreIds")
        return ShowTitle(
            id, type, o.optString("title"), o.optString("year"), o.optString("genre"), o.optString("rating"),
            o.optString("overview"), backdrop,
            via = o.optString("via"),
            genreIds = if (g == null) emptyList() else (0 until g.length()).map { g.optInt(it) },
            lang = o.optString("lang"),
        )
    }

    fun poolToJson(titles: List<ShowTitle>): String = JSONArray(titles.map { titleToJson(it) }).toString()

    fun poolFromJson(json: String): List<ShowTitle> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::titleFromJson) }
    }
}
