package com.nbansal.afterglow.core

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.apps.AppRepository
import com.nbansal.afterglow.data.ButtonMap
import com.nbansal.afterglow.data.Favourites
import com.nbansal.afterglow.data.PrefState
import com.nbansal.afterglow.data.Prefs
import com.nbansal.afterglow.data.Recents
import com.nbansal.afterglow.keys.QuickPanelController
import com.nbansal.afterglow.onboarding.OnboardingState
import com.nbansal.afterglow.saver.SaverController
import com.nbansal.afterglow.showcase.ShowcaseController
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.settings.versionName
import com.nbansal.afterglow.takeover.SpeedUpController
import com.nbansal.afterglow.takeover.TakeoverController
import com.nbansal.afterglow.update.Release
import com.nbansal.afterglow.update.Updater
import com.nbansal.afterglow.update.Versions
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
    val takeover = TakeoverController(app, prefs)
    val speedUp = SpeedUpController(app, prefs)
    val saver = SaverController(app, prefs)
    val quickPanel = QuickPanelController(app, prefs)

    val showcase = ShowcaseController(app)
    /** Showcase needs 2.8 GB of RAM or more; below that the setting is ignored. */
    val showcaseAllowed: Boolean by lazy { ShowcaseLogic.ramOk(ShowcaseLogic.totalRam(app)) }

    val onboarding = OnboardingState { scope.launch { prefs.setFavourites(it) } }

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var state by mutableStateOf(PrefState()); private set
    var loaded by mutableStateOf(false); private set

    init {
        scope.launch {
            prefs.state.collect {
                state = it
                showcase.sync(showcaseOn, it.tmdbKey)
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

    val showcaseOn: Boolean
        get() = state.showcase && showcaseAllowed

    fun setShowcase(on: Boolean) = scope.launch { prefs.setShowcase(on) }
    fun setTmdbKey(key: String) = scope.launch { prefs.setTmdbKey(key.trim()) }
    fun setOpenWith(pkg: String) = scope.launch { prefs.setOpenWith(pkg) }

    /** Opens the walk-through at the start, with today's favourites already ticked. */
    fun restartOnboarding() = onboarding.restart(state.favourites.orEmpty())

    /** Greeting name from onboarding or Afterglow settings: trimmed, 20 characters at most, empty clears it. */
    fun setUserName(name: String) = scope.launch { prefs.setUserName(com.nbansal.afterglow.home.cleanName(name)) }

    fun finishOnboarding() { scope.launch { prefs.setOnboarded(true) } }

    val recentApps: List<AppEntry>
        get() = Recents.resolve(Recents.decode(state.recents), apps, state.hidden, state.names)

    /** Every launch from Afterglow goes through here so Recents can be kept. */
    fun launch(pkg: String) {
        scope.launch { prefs.setRecents(Recents.encode(Recents.record(Recents.decode(state.recents), pkg, System.currentTimeMillis()))) }
        repo.launch(pkg)
    }

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

    /** Package the owner gave this remote button in Settings > Remote buttons, if it is still installed. */
    fun buttonApp(keyCode: Int): String? = state.buttons[keyCode]?.takeIf { pkg -> apps.any { it.pkg == pkg } }
    fun mapButton(keyCode: Int, pkg: String) = scope.launch { prefs.setButtons(ButtonMap.set(state.buttons, keyCode, pkg)) }
    fun unmapButton(keyCode: Int) = scope.launch { prefs.setButtons(ButtonMap.remove(state.buttons, keyCode)) }

    // ---- updates ----
    val updater = Updater(app)
    var latest by mutableStateOf<Release?>(null); private set
    var updateStatus by mutableStateOf(""); private set
    var needInstallPermission by mutableStateOf(false); private set
    private var busy = false

    val updateAvailable: Boolean
        get() = latest?.let { Versions.isNewer(it.tag, versionName(app)) } == true

    /** On app start: at most once per 12 hours. */
    fun checkIfDue() {
        if (Versions.shouldCheck(state.lastUpdateCheck, System.currentTimeMillis())) checkForUpdates()
    }

    fun checkForUpdates() {
        if (busy) return
        busy = true
        updateStatus = "Checking…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { updater.latest() } }
            result.onSuccess {
                latest = it
                updateStatus = if (updateAvailable) "Update available" else "You are up to date"
                prefs.setLastUpdateCheck(System.currentTimeMillis())
            }.onFailure { updateStatus = "Could not check (no network?)" }
            busy = false
        }
    }

    fun downloadAndInstall() {
        val release = latest ?: return
        if (busy) return
        if (!updater.canInstall()) {
            needInstallPermission = true
            updateStatus = "Allow Afterglow to install apps, then try again"
            runCatching { app.startActivity(updater.allowInstallIntent()) }
            return
        }
        needInstallPermission = false
        busy = true
        updateStatus = "Downloading 0%"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = updater.download(release) { updateStatus = "Downloading ${(it * 100).toInt()}%" }
                    if (file == null) null else { updater.install(file); true }
                }
            }
            updateStatus = when {
                result.isFailure -> "Download failed"
                result.getOrNull() == null -> "Download did not pass the safety check"
                else -> "Ready: confirm on the TV"
            }
            busy = false
        }
    }
}
