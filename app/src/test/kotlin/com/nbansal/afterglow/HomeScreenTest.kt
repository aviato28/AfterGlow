package com.nbansal.afterglow

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import com.nbansal.afterglow.home.HomeScreen
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

    private fun checkHome() {
        rule.setContent { TvCanvas { HomeScreen() } }
        rule.waitForIdle()
        rule.onNodeWithText("afterglow").assertIsDisplayed()
        rule.onNodeWithTag(TAG_ALL_APPS).assertIsFocused()

        // The canvas is 1920 dp wide whatever the panel is: the pills end at the 96 dp right margin.
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val pills = rule.onNodeWithTag("pill-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(1824f / 1920f, pills.right / root.width, 0.01f)
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-xhdpi")
    fun rendersAt1920x1080() = checkHome()

    @Test
    @Config(qualifiers = "w960dp-h540dp-xhdpi")
    fun rendersAt960x540Density2() = checkHome()
}
