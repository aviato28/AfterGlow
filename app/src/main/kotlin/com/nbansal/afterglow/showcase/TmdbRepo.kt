package com.nbansal.afterglow.showcase

import android.content.Context
import com.nbansal.afterglow.apps.AppRepository
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** TMDB over HttpURLConnection with a JSON cache in filesDir. Blocking: call on Dispatchers.IO. Nothing here logs. */
class TmdbRepo(private val context: Context) {
    private val poolFile get() = File(context.filesDir, "showcase-pool.json")
    private val detailsFile get() = File(context.filesDir, "showcase-details.json")
    private val genresFile get() = File(context.filesDir, "showcase-genres.json")
    private val providersFile get() = File(context.filesDir, "showcase-providers.json")
    private val detailsLock = Any()
    val store = ShowcaseStore(context)

    /** The last saved pool (any age) without the titles marked "Not interested", or empty. */
    fun cached(): List<ShowTitle> = runCatching {
        if (!poolFile.exists()) emptyList()
        else {
            val hidden = store.read().hidden
            Picks.poolFromJson(poolFile.readText()).filter { Picks.key(it) !in hidden }
        }
    }.getOrDefault(emptyList())

    /** Details saved so far (they are fetched lazily, a few titles ahead). */
    fun cachedDetails(): Map<Int, ShowDetails> = synchronized(detailsLock) { readDetails() }

    private fun readDetails(): Map<Int, ShowDetails> = runCatching {
        val out = HashMap<Int, ShowDetails>()
        if (detailsFile.exists()) {
            val o = JSONObject(detailsFile.readText())
            for (k in o.keys()) {
                val id = k.toIntOrNull() ?: continue
                o.optJSONObject(k)?.let { out[id] = Tmdb.detailsFromJson(it) }
            }
        }
        out
    }.getOrDefault(emptyMap())

    private fun writeDetails(map: Map<Int, ShowDetails>) {
        val o = JSONObject()
        map.forEach { (id, d) -> o.put(id.toString(), Tmdb.detailsToJson(d)) }
        runCatching { detailsFile.writeText(o.toString()) }
    }

    /** One details call per title (logos, age rating, runtime, genres, release date), merged into the saved details. A failing title is left out. */
    fun fetchDetails(titles: List<ShowTitle>, key: String): Map<Int, ShowDetails> {
        val out = LinkedHashMap<Int, ShowDetails>()
        for (t in titles) {
            runCatching { Tmdb.parseDetails(get(Tmdb.detailsPath(t), key), t.type) }.getOrNull()?.let { out[t.id] = it }
        }
        if (out.isNotEmpty()) synchronized(detailsLock) { writeDetails(readDetails() + out) }
        return out
    }

    private fun genres(): Map<Int, String> = runCatching {
        if (genresFile.exists()) Tmdb.genresFromJson(genresFile.readText()) else emptyMap()
    }.getOrDefault(emptyMap())

    /** Genre names for the Look page chips (movie and tv merged), saved from the last refresh. */
    fun cachedGenres(): Map<Int, String> = genres()

    /** Providers of the installed streaming apps (TMDB list read once a week). Empty when none is installed or the list is unavailable. */
    private fun installedProviders(key: String, nowMs: Long): Map<Int, String> {
        if (!ShowcaseLogic.fresh(providersFile.lastModified(), nowMs, Picks.PROVIDERS_MS)) {
            runCatching {
                val body = get(Picks.PROVIDERS, key)
                if (Picks.parseProviders(body).isNotEmpty()) providersFile.writeText(body)
            }
        }
        val all = runCatching { Picks.parseProviders(providersFile.readText()) }.getOrDefault(emptyMap())
        if (all.isEmpty()) return emptyMap()
        val pkgs = runCatching { AppRepository(context).load().map { it.pkg } }.getOrDefault(emptyList())
        return Picks.matchProviders(pkgs, all)
    }

    /**
     * The cache when it is under 6 hours old (and [force] is false), else a new pool (genres at most every 30 days, provider list every
     * 7 days). Throws when nothing could be downloaded.
     */
    fun refreshIfDue(key: String, nowMs: Long, taste: Taste, force: Boolean = false): List<ShowTitle> {
        if (!force && ShowcaseLogic.fresh(poolFile.lastModified(), nowMs, ShowcaseLogic.REFRESH_MS)) {
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
        val genres = genres()
        val providers = installedProviders(key, nowMs)
        var firstError: Throwable? = null
        val candidates = ArrayList<ShowTitle>()
        for (q in Picks.queries(providers, taste)) {
            val body = try {
                get(q.path, key)
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                continue
            }
            val list = runCatching { Tmdb.parseTrending(body, genres, 100, q.type) }.getOrDefault(emptyList())
            candidates.addAll(if (q.via.isEmpty()) list else list.map { it.copy(via = q.via) })
        }
        val failed = firstError
        if (candidates.isEmpty() && failed != null) throw failed
        var all: List<ShowTitle> = candidates
        if (providers.size > 1) {
            // Which service has which title: page 1 per provider (movies and tv), only to name the badge.
            val byProvider = ArrayList<Pair<String, List<ShowTitle>>>()
            for ((id, name) in providers) {
                val titles = ArrayList<ShowTitle>()
                for (type in listOf("movie", "tv")) {
                    runCatching { get(Picks.discoverPath(type, 1, listOf(id), taste, null), key) }.getOrNull()
                        ?.let { titles.addAll(Tmdb.parseTrending(it, genres, 100, type)) }
                }
                byProvider.add(name to titles)
            }
            all = Picks.attribution(byProvider, candidates)
        }
        store.update { it.copy(shown = Picks.pruneShown(it.shown, nowMs)) }
        val state = store.read()
        val pool = Picks.pool(all, taste, state.hidden, state.shown, nowMs, Picks.dailySeed(nowMs))
        if (pool.isNotEmpty()) {
            poolFile.writeText(Picks.poolToJson(pool))
            val ids = pool.map { it.id }.toSet()
            synchronized(detailsLock) { writeDetails(readDetails().filterKeys { it in ids }) }
        }
        return pool
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
