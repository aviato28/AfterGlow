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
    var testStatus by mutableStateOf(""); private set
    /** Bumped on onTrimMemory so the home drops and reloads its bitmaps. */
    var trim by mutableIntStateOf(0); private set

    private var syncedOn = false
    private var syncedKey = ""
    private var busy = false

    /** Called whenever the saved settings change: only acts when Showcase on/off or the key changed. */
    fun sync(on: Boolean, key: String) {
        val k = key.trim()
        if (on == syncedOn && k == syncedKey) return
        syncedOn = on
        syncedKey = k
        if (!on || k.isEmpty()) {
            titles = emptyList()
            images.clear()
            error = if (on) "Add a TMDB key to use Showcase" else ""
            return
        }
        scope.launch {
            val cached = withContext(Dispatchers.IO) { repo.cached() }
            if (cached.isNotEmpty() && titles.isEmpty()) titles = cached
            refresh()
        }
    }

    /** Home came back to the front: refresh if the cache is over 6 hours old. */
    fun refreshIfDue() {
        if (syncedOn && syncedKey.isNotEmpty()) scope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (busy) return
        busy = true
        val key = syncedKey
        val result = withContext(Dispatchers.IO) { runCatching { repo.refreshIfDue(key, System.currentTimeMillis()) } }
        result.onSuccess {
            if (it.isNotEmpty()) titles = it
            error = if (it.isEmpty() && titles.isEmpty()) "TMDB returned nothing to show" else ""
        }.onFailure { error = Tmdb.errorText(it) }
        busy = false
    }

    fun testKey(key: String) {
        if (!Tmdb.isValidKey(key)) {
            testStatus = "That does not look like a TMDB key (32 letters/digits, or a token starting eyJ)"
            return
        }
        testStatus = "Testing…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { repo.testKey(key.trim()) } }
            testStatus = r.fold({ "OK" }, { Tmdb.errorText(it) })
        }
    }

    fun trimMemory() {
        images.clear()
        trim++
    }
}
