package com.nbansal.afterglow

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.nbansal.afterglow.settings.SettingsHub
import com.nbansal.afterglow.settings.SettingsHubState
import com.nbansal.afterglow.settings.SettingsRow
import com.nbansal.afterglow.settings.SettingsSection
import com.nbansal.afterglow.settings.TAG_RAIL_PREFIX
import com.nbansal.afterglow.ui.IconHome
import com.nbansal.afterglow.ui.IconInfo
import com.nbansal.afterglow.ui.TvCanvas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsHubTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-xhdpi")
    fun opensWithTheRailFocusedOnTheFirstSection() {
        val sections = listOf(
            SettingsSection("Home & look", "Name and theme", IconHome, listOf(SettingsRow("Your name", "Not set") {})),
            SettingsSection("About", "Version", IconInfo, listOf(SettingsRow("About") {})),
        )
        rule.setContent { TvCanvas { SettingsHub(sections, SettingsHubState()) } }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_RAIL_PREFIX + 0).assertIsFocused()
    }
}
