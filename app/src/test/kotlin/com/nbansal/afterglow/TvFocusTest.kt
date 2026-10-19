package com.nbansal.afterglow

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.unit.dp
import com.nbansal.afterglow.ui.tvFocus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TvFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0
    private var longClicks = 0

    @Before
    fun setUp() {
        val focus = FocusRequester()
        rule.setContent {
            Box(
                Modifier.size(100.dp).focusRequester(focus).testTag("item")
                    .tvFocus(RoundedCornerShape(20.dp), onClick = { clicks++ }, onLongClick = { longClicks++ }),
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
        }
        rule.waitForIdle()
    }

    private fun key(action: Int, repeat: Int = 0) = KeyEvent(
        AndroidKeyEvent(0L, 0L, action, AndroidKeyEvent.KEYCODE_DPAD_CENTER, repeat),
    )

    @OptIn(ExperimentalTestApi::class)
    private fun send(action: Int, repeat: Int = 0) {
        rule.onNodeWithTag("item").performKeyPress(key(action, repeat))
    }

    @Test
    fun shortPressClicksOnce() {
        send(AndroidKeyEvent.ACTION_DOWN)
        assertEquals(0, clicks)
        send(AndroidKeyEvent.ACTION_UP)
        assertEquals(1, clicks)
        assertEquals(0, longClicks)
    }

    @Test
    fun heldPressFiresLongClickOnceAndNoClick() {
        send(AndroidKeyEvent.ACTION_DOWN)
        send(AndroidKeyEvent.ACTION_DOWN, repeat = 1)
        send(AndroidKeyEvent.ACTION_DOWN, repeat = 2)
        send(AndroidKeyEvent.ACTION_UP)
        assertEquals(1, longClicks)
        assertEquals(0, clicks)
    }

    @Test
    fun releaseWithoutPressDoesNotClick() {
        // The release of the key that opened a popup lands on the popup's first row: it must be ignored.
        send(AndroidKeyEvent.ACTION_UP)
        assertEquals(0, clicks)
    }

    @Test
    fun heldKeyRepeatsThenReleaseDoNotClick() {
        // A popup opened by a long press receives the held key's repeats and then its release: no click.
        send(AndroidKeyEvent.ACTION_DOWN, repeat = 1)
        send(AndroidKeyEvent.ACTION_DOWN, repeat = 2)
        send(AndroidKeyEvent.ACTION_UP)
        assertEquals(0, clicks)
    }
}
