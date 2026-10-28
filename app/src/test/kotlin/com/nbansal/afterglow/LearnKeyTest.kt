package com.nbansal.afterglow

import android.view.KeyEvent
import com.nbansal.afterglow.keys.KeyWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnKeyTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Before
    fun reset() { KeyWatch.learning = false; KeyWatch.learned = null }

    @Test
    fun builtInKeysAreLearnedNotLaunched() {
        for (code in listOf(4062, 4099, KeyEvent.KEYCODE_NOTIFICATION)) {
            KeyWatch.learning = true
            assertTrue(KeyWatch.learnerTakes(code, down, 0, nowMs = 1_000))
            assertEquals(code, KeyWatch.learned)
            assertFalse(KeyWatch.learning)
            assertTrue("its key-up is swallowed too", KeyWatch.learnerTakes(code, up, 0))
            assertFalse(KeyWatch.learnerTakes(code, up, 0))
        }
    }

    @Test
    fun everyKeyIsTakenWhileLearningAndBackCancels() {
        KeyWatch.learning = true
        assertTrue(KeyWatch.learnerTakes(KeyEvent.KEYCODE_DPAD_UP, down, 0))
        assertTrue(KeyWatch.learning)
        assertTrue(KeyWatch.learnerTakes(KeyEvent.KEYCODE_BACK, down, 0))
        assertFalse(KeyWatch.learning)
        assertNull(KeyWatch.learned)
        assertTrue("Back's key-up must not also leave the page", KeyWatch.learnerTakes(KeyEvent.KEYCODE_BACK, up, 0))
    }

    @Test
    fun keysPassWhenNotLearning() = assertFalse(KeyWatch.learnerTakes(4062, down, 0))

    @Test
    fun serviceIgnoresKeysWhileLearningAndTheLogCopyOfTheLearnedPress() {
        KeyWatch.learning = true
        assertTrue(KeyWatch.serviceIgnores(4062, 1_000))
        KeyWatch.learnerTakes(4062, down, 0, nowMs = 1_000)
        assertTrue(KeyWatch.serviceIgnores(4062, 1_500))
        assertFalse(KeyWatch.serviceIgnores(4099, 1_500))
        assertFalse(KeyWatch.serviceIgnores(4062, 4_000))
    }

    @Test
    fun theLogCanLearnAKeyTheTvKeepsForItself() {
        KeyWatch.learning = true
        assertTrue(KeyWatch.learnFromLog(4200, nowMs = 1_000))
        assertEquals(4200, KeyWatch.learned)
        assertFalse(KeyWatch.learning)
        // the log copy of the same press is ignored by the service, and the app's own copy is swallowed (down and up)
        assertTrue(KeyWatch.serviceIgnores(4200, 1_500))
        assertTrue(KeyWatch.learnerTakes(4200, down, 0, nowMs = 1_200))
        assertTrue(KeyWatch.learnerTakes(4200, up, 0, nowMs = 1_300))
        assertFalse(KeyWatch.learnerTakes(4200, up, 0, nowMs = 1_400))
    }

    @Test
    fun theLogIgnoresNavigationKeysAndDoesNothingWhenNotLearning() {
        KeyWatch.learning = true
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
        )) {
            assertFalse(KeyWatch.learnFromLog(code, nowMs = 1_000))
            assertTrue(KeyWatch.learning)
            assertNull(KeyWatch.learned)
        }
        KeyWatch.learning = false
        assertFalse(KeyWatch.learnFromLog(4200, nowMs = 1_000))
        assertNull(KeyWatch.learned)
    }

    @Test
    fun theFirstPathWinsWhenBothSeeThePress() {
        KeyWatch.learning = true
        assertTrue(KeyWatch.learnerTakes(4062, down, 0, nowMs = 1_000)) // app path first
        assertFalse(KeyWatch.learnFromLog(4062, nowMs = 1_100))
        assertEquals(4062, KeyWatch.learned)
    }
}
