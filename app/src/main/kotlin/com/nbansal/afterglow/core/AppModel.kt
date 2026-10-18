package com.nbansal.afterglow.core

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.apps.AppRepository
import com.nbansal.afterglow.data.Favourites
import com.nbansal.afterglow.data.PrefState
import com.nbansal.afterglow.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide state holder (plain, no ViewModel library): installed apps + preferences, observed by Compose. */
class AppModel(val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(app)
    val repo = AppRepository(app)

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var state by mutableStateOf(PrefState()); private set
    var loaded by mutableStateOf(false); private set

    init {
        scope.launch {
            prefs.state.collect {
                state = it
                seedDefaults()
            }
        }
    }

    val favourites: List<AppEntry>
        get() = Favourites.resolve(state.favourites.orEmpty(), apps, state.hidden, state.names)

    val visibleApps: List<AppEntry>
        get() = Favourites.visible(apps, state.hidden, state.names)

    val hiddenApps: List<AppEntry>
        get() = apps.filter { it.pkg in state.hidden }.map { Favourites.named(it, state.names) }

    fun isFavourite(pkg: String) = pkg in state.favourites.orEmpty()

    fun refresh() {
        scope.launch {
            apps = withContext(Dispatchers.IO) { repo.load() }
            loaded = true
            seedDefaults()
        }
    }

    private fun seedDefaults() {
        if (!loaded || apps.isEmpty() || state.favourites != null) return
        scope.launch { prefs.setFavourites(Favourites.defaults(apps)) }
    }

    private fun favs() = state.favourites.orEmpty()

    fun toggleFavourite(pkg: String) = scope.launch {
        prefs.setFavourites(if (isFavourite(pkg)) Favourites.remove(favs(), pkg) else Favourites.add(favs(), pkg))
    }

    fun moveEarlier(pkg: String) = scope.launch { prefs.setFavourites(Favourites.moveEarlier(favs(), pkg)) }
    fun moveLater(pkg: String) = scope.launch { prefs.setFavourites(Favourites.moveLater(favs(), pkg)) }

    fun hide(pkg: String) = scope.launch { prefs.setHidden(state.hidden + pkg) }
    fun unhide(pkg: String) = scope.launch { prefs.setHidden(state.hidden - pkg) }
    fun rename(pkg: String, name: String) = scope.launch { prefs.setNames(Favourites.rename(state.names, pkg, name)) }
}
