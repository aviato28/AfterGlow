package com.nbansal.afterglow

import com.nbansal.afterglow.takeover.Adb
import com.nbansal.afterglow.takeover.AdbException
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.takeover.ShellResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbWhitelistTest {
    @Test
    fun allowsOnlyTheListedCommands() {
        listOf(
            "pm disable-user --user 0 com.google.android.apps.tv.launcherx",
            "pm enable com.google.android.tungsten.setupwraith",
            AdbWhitelist.ResolveHome, AdbWhitelist.QueryHomes, AdbWhitelist.ListDisabled,
        ).forEach { assertTrue(it, AdbWhitelist.allows(it)) }
    }

    @Test
    fun rejectsEverythingElse() {
        listOf(
            "", "reboot", "pm disable-user --user 0", "pm disable-user --user 0 a.b c.d", "pm disable-user --user 0 a.b; reboot",
            "pm disable --user 0 a.b.c", "pm disable-user --user 1 a.b.c", "pm enable", "pm enable a.b.c d.e.f", "pm enable a.b.c;id",
            "pm enable $(id)", "pm uninstall a.b.c", "pm list packages", "pm list packages -d | cat", "pm list packages -d ",
            "settings put global adb_enabled 0", "sh -c id", "cmd package resolve-activity --brief", AdbWhitelist.ResolveHome + " && reboot",
            " pm enable a.b.c", "pm  enable a.b.c", "pm enable nopackagedot",
        ).forEach { assertFalse("'$it'", AdbWhitelist.allows(it)) }
    }

    @Test
    fun adbRefusesBeforeExecuting() = runBlocking {
        var ran = 0
        val adb = object : Adb() {
            override suspend fun exec(command: String): ShellResult {
                ran++
                return ShellResult(0, "")
            }
        }
        try {
            adb.run("reboot")
            org.junit.Assert.fail("should reject")
        } catch (e: AdbException) {
            assertEquals(AdbException.Kind.Rejected, e.kind)
        }
        assertEquals(0, ran)
        adb.run("pm enable a.b.c")
        assertEquals(1, ran)
    }
}
