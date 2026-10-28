package com.nbansal.afterglow

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.home.HomeScreen
import com.nbansal.afterglow.home.TAG_SETTINGS
import com.nbansal.afterglow.home.TAG_ALL_APPS
import com.nbansal.afterglow.ui.TvCanvas
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val apps = List(7) { AppEntry("pkg.app$it", "pkg.app$it.Main", "App $it") }

    private fun checkHome() {
        rule.setContent { TvCanvas { HomeScreen(favourites = apps.take(6), peekApps = apps, userName = "Naman") } }
        rule.waitForIdle()
        rule.onNodeWithText("afterglow").assertIsDisplayed()
        rule.onNodeWithText("FAVOURITES").assertIsDisplayed()
        // The first favourite has focus and its label shows under the shelf.
        rule.onNodeWithText("App 0").assertIsDisplayed()

        // The canvas is 1920 dp wide whatever the panel is: the icon buttons end 8 dp inside the pill, which ends at the 96 dp margin.
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithTag(TAG_SETTINGS).fetchSemanticsNode().boundsInRoot
        assertEquals(1816f / 1920f, settings.right / root.width, 0.01f)
    }

    private fun checkNoFavourites() {
        rule.setContent { TvCanvas { HomeScreen() } }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_ALL_APPS).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-xhdpi")
    fun rendersAt1920x1080() = checkHome()

    @Test
    @Config(qualifiers = "w960dp-h540dp-xhdpi")
    fun rendersAt960x540Density2() = checkHome()

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-xhdpi")
    fun noFavouritesFocusesAllApps() = checkNoFavourites()
}
