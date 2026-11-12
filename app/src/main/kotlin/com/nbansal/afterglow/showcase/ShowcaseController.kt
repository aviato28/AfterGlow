package com.nbansal.afterglow.showcase

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Showcase state for the UI. Nothing touches the network unless Showcase is on and a key is set ([sync]). Errors are kept as
 * short texts for Settings; the home then silently shows Lite.
 */
class ShowcaseController(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo = TmdbRepo(app)
    val images = ShowcaseImages(app)

    var titles by mutableStateOf<List<ShowTitle>>(emptyList()); private set
    var error by mutableStateOf(""); private set
    /** The current title's accent colour (ARGB), 0 = none yet. Set by the hero, read by the focus ring, shelf and dots. */
    var accent by mutableIntStateOf(0); private set
    /** Details (logos, age rating...) of the titles fetched so far; fetched lazily for the next few titles only. */
    var details by mutableStateOf<Map<Int, ShowDetails>>(emptyMap()); private set
    /** Settings > Look "More of / Less of" and languages. */
    var taste by mutableStateOf(Taste()); private set
    /** Genre names (id to name) for the Look page; empty until the first download. */
    var genres by mutableStateOf<Map<Int, String>>(emptyMap()); private set
    var testStatus by mutableStateOf(""); private set
    /** Bumped on onTrimMemory so the home drops and reloads its bitmaps. */
    var trim by mutableIntStateOf(0); private set

    private var syncedOn = false
    private var syncedKey = ""
    private var busy = false
    private var rerun = false
    private val loadingDetails = HashSet<Int>()

    init {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { repo.store.read().taste to repo.cachedGenres() }
            taste = saved.first
            genres = saved.second
        }
    }

    /** Called whenever the saved settings change: only acts when Showcase on/off or the key changed. */
    fun sync(on: Boolean, key: String) {
        val k = key.trim()
        if (on == syncedOn && k == syncedKey) return
        syncedOn = on
        syncedKey = k
        if (!on || k.isEmpty()) {
            titles = emptyList()
            accent = 0
            images.clear()
            error = if (on) "Add a TMDB key to use Showcase" else ""
            return
        }
        scope.launch {
            val cached = withContext(Dispatchers.IO) { repo.cached() to repo.cachedDetails() }
            if (cached.first.isNotEmpty() && titles.isEmpty()) {
                details = cached.second
                titles = cached.first
            }
            refresh()
        }
    }

    /** Home came back to the front: refresh if the cache is over 6 hours old. */
    fun refreshIfDue() {
        if (syncedOn && syncedKey.isNotEmpty()) scope.launch { refresh() }
    }

    private suspend fun refresh(force: Boolean = false) {
        if (busy) {
            if (force) rerun = true
            return
        }
        busy = true
        try {
            var f = force
            do {
                rerun = false
                val key = syncedKey
                val t = taste
                val result = withContext(Dispatchers.IO) {
                    runCatching { repo.refreshIfDue(key, System.currentTimeMillis(), t, f) to repo.cachedDetails() }
                }
                result.onSuccess { (list, saved) ->
                    if (list.isNotEmpty()) {
                        details = saved
                        titles = list
                    }
                    error = if (list.isEmpty() && titles.isEmpty()) "TMDB returned nothing to show" else ""
                }.onFailure { error = Tmdb.errorText(it) }
                genres = withContext(Dispatchers.IO) { repo.cachedGenres() }
                f = true
            } while (rerun)
        } finally {
            busy = false
        }
    }

    /** Settings > Look: new taste. Saved, and the pool is rebuilt right away. */
    fun setTaste(t: Taste) {
        taste = t
        scope.launch {
            withContext(Dispatchers.IO) { repo.store.update { it.copy(taste = t) } }
            if (syncedOn && syncedKey.isNotEmpty()) refresh(force = true)
        }
    }

    /** The hero shows these titles next: fetch details (logos...) for the ones that have none yet. */
    fun ensureDetails(upcoming: List<ShowTitle>) {
        val key = syncedKey
        if (!syncedOn || key.isEmpty()) return
        val missing = ArrayList<ShowTitle>()
        for (t in upcoming) if (t.id !in details && loadingDetails.add(t.id)) missing.add(t)
        if (missing.isEmpty()) return
        scope.launch {
            val got = withContext(Dispatchers.IO) { runCatching { repo.fetchDetails(missing, key) }.getOrDefault(emptyMap()) }
            loadingDetails.removeAll(missing.map { it.id }.toSet())
            if (got.isNotEmpty()) details = details + got
        }
    }

    /** The title is on screen: not picked again for 3 days. */
    fun markShown(t: ShowTitle) {
        val now = System.currentTimeMillis()
        scope.launch { withContext(Dispatchers.IO) { repo.store.update { it.copy(shown = it.shown + (Picks.key(t) to now)) } } }
    }

    /** Long-press menu: never show this title again. */
    fun notInterested(t: ShowTitle) {
        val k = Picks.key(t)
        titles = titles.filter { Picks.key(it) != k }
        scope.launch {
            withContext(Dispatchers.IO) { repo.store.update { it.copy(hidden = it.hidden + k) } }
            if (titles.isEmpty() && syncedOn && syncedKey.isNotEmpty()) refresh(force = true)
        }
    }

    fun testKey(key: String) {
        if (!Tmdb.isValidKey(key)) {
            testStatus = ShowcaseLogic.badKeyMessage(key)
            return
        }
        testStatus = "Testing…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { repo.testKey(key.trim()) } }
            testStatus = r.fold({ "OK" }, { Tmdb.errorText(it) })
        }
    }

    fun updateAccent(argb: Int) {
        accent = argb
    }

    fun trimMemory() {
        images.clear()
        trim++
    }
}
