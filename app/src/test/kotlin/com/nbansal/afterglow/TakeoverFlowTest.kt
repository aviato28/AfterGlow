package com.nbansal.afterglow

import com.nbansal.afterglow.takeover.Adb
import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.takeover.DisabledStore
import com.nbansal.afterglow.takeover.FlowResult
import com.nbansal.afterglow.takeover.HomeParsing
import com.nbansal.afterglow.takeover.ShellResult
import com.nbansal.afterglow.takeover.TakeoverFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Self = "com.nbansal.afterglow"
private const val Launcher = "com.google.android.apps.tv.launcherx"
private const val Wraith = "com.google.android.tungsten.setupwraith"
private const val Settings = "com.android.tv.settings"

/** A pretend TV: HOME handlers in priority order, a set of disabled packages, and a log of every command. */
class FakeTv(
    private val handlers: List<String> = listOf(Wraith, Launcher, Self, Settings),
    private val refuseDisable: Set<String> = emptySet(),
    private val selfWins: Boolean = true,
    val disabled: MutableSet<String> = mutableSetOf(),
) : Adb() {
    val log = mutableListOf<String>()

    private fun active() = handlers.filter { it !in disabled }
    private fun comp(p: String) = if (p == Self) "$p/.HomeActivity" else "$p/.Home"

    override suspend fun exec(command: String): ShellResult {
        log += command
        return when {
            command == AdbWhitelist.QueryHomes ->
                ShellResult(0, "${active().size} activities found:\n" + active().joinToString("\n") { "  ${comp(it)}" })
            command == AdbWhitelist.ResolveHome -> {
                val a = active()
                val pick = if (selfWins && Self in a) Self else a.firstOrNull { it != Self } ?: Self
                ShellResult(0, "priority=0 preferredOrder=0\n${comp(pick)}")
            }
            command == AdbWhitelist.ListDisabled -> ShellResult(0, disabled.joinToString("\n") { "package:$it" })
            command.startsWith("pm disable-user") -> {
                val p = command.substringAfterLast(' ')
                if (p in refuseDisable) ShellResult(1, "Exception: not allowed")
                else { disabled += p; ShellResult(0, "Package $p new state: disabled-user") }
            }
            command.startsWith("pm enable") -> {
                val p = command.substringAfterLast(' ')
                disabled -= p
                ShellResult(0, "Package $p new state: enabled")
            }
            else -> error("unexpected $command")
        }
    }
}

class MemoryStore(var value: Set<String> = emptySet()) : DisabledStore {
    override suspend fun get() = value
    override suspend fun set(packages: Set<String>) { value = packages }
}

class TakeoverFlowTest {
    @Test
    fun parsesHomeHandlers() {
        val out = "3 activities found:\n  priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" +
            "  com.google.android.apps.tv.launcherx/.home.HomeActivity\n  com.nbansal.afterglow/.HomeActivity\n  com.android.tv.settings/.FallbackHome\n"
        assertEquals(
            listOf("com.google.android.apps.tv.launcherx", "com.nbansal.afterglow", "com.android.tv.settings"),
            HomeParsing.homePackages(out),
        )
        assertEquals("com.nbansal.afterglow/.HomeActivity", HomeParsing.resolvedComponent("priority=0\ncom.nbansal.afterglow/.HomeActivity\n"))
        assertEquals(null, HomeParsing.resolvedComponent("No activity found"))
        assertEquals(setOf("a.b", "c.d"), HomeParsing.disabledPackages("package:a.b\r\npackage:c.d\n"))
    }

    @Test
    fun takeoverDisablesOnlyUnprotectedHandlersOnePerCommand() = runBlocking {
        val tv = FakeTv()
        val store = MemoryStore()
        val r = TakeoverFlow(tv, Self, store).makeHome()
        assertTrue(r is FlowResult.Done)
        assertEquals(setOf(Launcher, Wraith), tv.disabled)
        assertEquals(setOf(Launcher, Wraith), store.value)
        assertTrue(tv.log.none { it.contains(Settings) })
        tv.log.filter { it.startsWith("pm ") }.forEach { assertEquals(1, it.split(' ').count { t -> t.contains('.') }) }
    }

    @Test
    fun planIsSavedBeforeAnythingIsDisabled() = runBlocking {
        val tv = FakeTv()
        var seenAtFirstDisable: Set<String>? = null
        val store = MemoryStore()
        val spy = object : Adb() {
            override suspend fun exec(command: String): ShellResult {
                if (command.startsWith("pm disable-user") && seenAtFirstDisable == null) seenAtFirstDisable = store.value
                return tv.run(command)
            }
        }
        TakeoverFlow(spy, Self, store).makeHome()
        assertEquals(setOf(Launcher, Wraith), seenAtFirstDisable)
    }

    @Test
    fun verifyFailsSoEverythingIsUndone() = runBlocking {
        val tv = FakeTv(selfWins = false)
        val store = MemoryStore()
        val r = TakeoverFlow(tv, Self, store).makeHome()
        assertTrue(r is FlowResult.Stopped)
        assertEquals("This TV did not accept Afterglow as Home. Nothing was changed.", (r as FlowResult.Stopped).message)
        assertTrue(tv.disabled.isEmpty())
        assertTrue(store.value.isEmpty())
    }

    @Test
    fun aRefusedDisableUndoesTheOnesAlreadyDone() = runBlocking {
        val tv = FakeTv(refuseDisable = setOf(Launcher))
        val store = MemoryStore()
        val r = TakeoverFlow(tv, Self, store).makeHome()
        assertTrue(r is FlowResult.Stopped)
        assertTrue(tv.disabled.isEmpty())
        assertTrue(store.value.isEmpty())
    }

    @Test
    fun stopsWhenAfterglowIsNotAHomeHandler() = runBlocking {
        val tv = FakeTv(handlers = listOf(Launcher, Settings))
        val r = TakeoverFlow(tv, Self, MemoryStore()).makeHome()
        assertTrue(r is FlowResult.Stopped)
        assertTrue(tv.disabled.isEmpty())
    }
}
