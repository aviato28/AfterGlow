package com.nbansal.afterglow

import com.nbansal.afterglow.keys.KeyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyLogTest {
    private val names = mapOf("KEYCODE_NOTIFICATION" to 83, "KEYCODE_DPAD_UP" to 19)
    private fun parse(line: String) = KeyLog.parse(line) { names[it] ?: 0 }

    private fun line(action: String, code: String, repeat: Int) =
        "D/WindowManager( 1234): handleKeyBeforeQueueing event=KeyEvent { action=$action, keyCode=$code, scanCode=240, metaState=0, " +
            "flags=0x8, repeatCount=$repeat, eventTime=123, downTime=123, deviceId=3, source=0x301, displayId=-1 }"

    @Test
    fun readsTheSettingsKeyByName() = assertEquals(83, parse(line("ACTION_DOWN", "KEYCODE_NOTIFICATION", 0)))

    @Test
    fun readsVendorCodesAsNumbers() {
        assertEquals(4062, parse(line("ACTION_DOWN", "4062", 0)))
        assertEquals(4099, parse(line("ACTION_DOWN", "4099", 0)))
    }

    @Test
    fun ignoresUpsRepeatsAndOtherLines() {
        assertNull(parse(line("ACTION_UP", "KEYCODE_NOTIFICATION", 0)))
        assertNull(parse(line("ACTION_DOWN", "KEYCODE_DPAD_UP", 1)))
        assertNull(parse(line("ACTION_DOWN", "KEYCODE_SOMETHING_NEW", 0)))
        assertNull(parse("D/WindowManager( 1234): handleKeyBeforeDispatching keyCode=83 disable"))
        assertNull(parse("--------- beginning of main"))
        assertNull(parse(""))
    }
}
