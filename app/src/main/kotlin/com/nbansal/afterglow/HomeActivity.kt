package com.nbansal.afterglow

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.apps.Banners
import com.nbansal.afterglow.core.AfterglowApp
import com.nbansal.afterglow.core.AppModel
import com.nbansal.afterglow.home.AllAppsScreen
import com.nbansal.afterglow.home.AppActions
import com.nbansal.afterglow.home.AppOptions
import com.nbansal.afterglow.home.HomeScreen
import com.nbansal.afterglow.home.openTvSettings
import com.nbansal.afterglow.onboarding.OnboardingScreen
import com.nbansal.afterglow.onboarding.OnboardingStep
import com.nbansal.afterglow.settings.AboutPage
import com.nbansal.afterglow.settings.HiddenAppsPage
import com.nbansal.afterglow.settings.HomeButtonPage
import com.nbansal.afterglow.takeover.Takeover
import com.nbansal.afterglow.settings.PackagesPage
import com.nbansal.afterglow.settings.SettingsPage
import com.nbansal.afterglow.settings.SettingsRow
import com.nbansal.afterglow.settings.SpeedUpPage
import com.nbansal.afterglow.ui.AfterglowBackground
import com.nbansal.afterglow.ui.MenuRow
import com.nbansal.afterglow.ui.RenameDialog
import com.nbansal.afterglow.ui.TvCanvas
import com.nbansal.afterglow.ui.TvMenu
import com.nbansal.afterglow.ui.rememberGlowPhase

class HomeActivity : ComponentActivity() {
    private lateinit var model: AppModel
    private var screen by mutableStateOf(Screen.Home)
    private var homeSignal by mutableIntStateOf(0)
    private val homeFocus = mutableIntStateOf(0)
    private val allAppsFocus = mutableIntStateOf(0)
    private var optionsFor by mutableStateOf<AppEntry?>(null)
    private var renameFor by mutableStateOf<AppEntry?>(null)
    private var settingsMenu by mutableStateOf(false)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = model.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = (application as AfterglowApp).model
        // A launcher must never exit on Back: go up one screen, and on Home do nothing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (inOnboarding()) {
                    if (model.onboarding.step == OnboardingStep.Welcome) { if (screen == Screen.Onboarding) screen = Screen.Settings }
                    else model.onboarding.back()
                    return
                }
                screen.parent?.let { screen = it }
            }
        })
        setContent {
            TvCanvas {
                Box(Modifier.fillMaxSize()) {
                    AfterglowBackground(rememberGlowPhase())
                    Content()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        model.refresh()
        model.checkIfDue()
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    /** The remote's Settings (gear) key used to be handled by Google TV home, which is switched off: open the TV's settings here. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openTvSettings(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Home pressed while Afterglow is already on top: back to the first tile. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            screen = Screen.Home
            optionsFor = null
            renameFor = null
            settingsMenu = false
            homeFocus.intValue = 0
            homeSignal++
        }
    }

    private fun inOnboarding() = (model.state.loaded && !model.state.onboarded) || screen == Screen.Onboarding

    private fun finishOnboarding() {
        model.finishOnboarding()
        screen = Screen.Home
    }

    @Composable
    private fun Content() {
        if (inOnboarding()) {
            OnboardingScreen(model, ::finishOnboarding)
            return
        }
        when (screen) {
            Screen.Home -> HomeScreen(
                favourites = model.favourites,
                focusIndex = homeFocus,
                homeSignal = homeSignal,
                updateAvailable = model.updateAvailable,
                onOpen = { model.launch(it.pkg) },
                onOptions = { optionsFor = it },
                onAllApps = { screen = Screen.AllApps },
                onSettings = { settingsMenu = true },
            )
            Screen.AllApps -> AllAppsScreen(
                apps = model.visibleApps,
                recents = model.recentApps,
                refocus = homeSignal,
                focusIndex = allAppsFocus,
                onOpen = { model.launch(it.pkg) },
                onOptions = { optionsFor = it },
            )
            Screen.Settings -> SettingsPage(
                "Afterglow settings",
                listOf(
                    SettingsRow("Home button", Takeover.homeLabel(this)) { screen = Screen.HomeButton },
                    SettingsRow("Speed up my TV") { screen = Screen.SpeedUp },
                    SettingsRow("Hidden apps", "${model.hiddenApps.size}") { screen = Screen.Hidden },
                    SettingsRow("Installed packages") { screen = Screen.Packages },
                    SettingsRow("Set up again") { model.restartOnboarding(); screen = Screen.Onboarding },
                    SettingsRow("About") { screen = Screen.About },
                ),
            )
            Screen.Onboarding -> Unit
            Screen.SpeedUp -> SpeedUpPage(model)
            Screen.HomeButton -> HomeButtonPage(model)
            Screen.Hidden -> HiddenAppsPage(model)
            Screen.Packages -> PackagesPage()
            Screen.About -> AboutPage(model)
        }

        // When the last popup closes, focus goes back to the item that opened it (the screens remember their index).
        val popupOpen = optionsFor != null || renameFor != null || settingsMenu
        LaunchedEffect(popupOpen) { if (!popupOpen) homeSignal++ }

        if (settingsMenu) {
            TvMenu(
                "Settings",
                listOf(
                    MenuRow("TV settings") { openTvSettings(this) },
                    MenuRow("Afterglow settings") { screen = Screen.Settings },
                ),
            ) { settingsMenu = false }
        }
        optionsFor?.let { app ->
            AppOptions(
                app,
                AppActions(
                    isFavourite = model.isFavourite(app.pkg),
                    onToggleFavourite = { model.toggleFavourite(app.pkg) },
                    onMoveEarlier = { model.moveEarlier(app.pkg) },
                    onMoveLater = { model.moveLater(app.pkg) },
                    onHide = { model.hide(app.pkg) },
                    onRename = { renameFor = app },
                    onInfo = { model.repo.appInfo(app.pkg) },
                    onUninstall = { model.repo.uninstall(app.pkg) },
                ),
            ) { optionsFor = null }
        }
        renameFor?.let { app ->
            RenameDialog(app.label, { model.rename(app.pkg, it) }) { renameFor = null }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Banners.clear()
    }
}
