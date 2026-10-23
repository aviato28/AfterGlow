package com.nbansal.afterglow

import android.view.KeyEvent
import com.nbansal.afterglow.data.ButtonMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonMapTest {
    @Test
    fun setAddsAndReplaces() {
        val one = ButtonMap.set(emptyMap(), 4100, "a.b")
        assertEquals(mapOf(4100 to "a.b"), one)
        assertEquals(mapOf(4100 to "c.d"), ButtonMap.set(one, 4100, "c.d"))
    }

    @Test
    fun navigationKeysCannotBeLearned() {
        for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_UNKNOWN)) {
            assertFalse(ButtonMap.isLearnable(code))
            assertEquals(emptyMap<Int, String>(), ButtonMap.set(emptyMap(), code, "a.b"))
        }
        assertTrue(ButtonMap.isLearnable(KeyEvent.KEYCODE_PROG_RED))
    }

    @Test
    fun blankPackageIsIgnored() = assertEquals(emptyMap<Int, String>(), ButtonMap.set(emptyMap(), 4100, " "))

    @Test
    fun removeDropsOnlyThatButton() =
        assertEquals(mapOf(2 to "y"), ButtonMap.remove(mapOf(4100 to "x", 2 to "y"), 4100))

    @Test
    fun encodeDecodeRoundTrip() {
        val map = mapOf(4100 to "a.b", KeyEvent.KEYCODE_PROG_RED to "c.d")
        assertEquals(map, ButtonMap.decode(ButtonMap.encode(map)))
    }

    @Test
    fun decodeSkipsBrokenLines() =
        assertEquals(mapOf(7 to "ok.pkg"), ButtonMap.decode("x\ty\n\n7\tok.pkg\n8\t\nnoTab"))
}
