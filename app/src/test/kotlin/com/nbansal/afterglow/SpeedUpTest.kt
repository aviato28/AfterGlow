package com.nbansal.afterglow

import com.nbansal.afterglow.takeover.AdbWhitelist
import com.nbansal.afterglow.takeover.Debloat
import com.nbansal.afterglow.takeover.DebloatItem
import com.nbansal.afterglow.takeover.SpeedUpFlow
import com.nbansal.afterglow.takeover.Takeover
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeedUpTest {
    private val shipped = File("src/main/assets/debloat/tcl.txt").readText()

    @Test
    fun parsesPackagesReasonsAndComments() {
        val items = Debloat.parse("## header\n\n a.b.c # Does things \nd.e.f\nbad name # x\nnodot # x\na.b.c # again\n  ## indented comment\n")
        assertEquals(listOf(DebloatItem("a.b.c", "Does things"), DebloatItem("d.e.f", "")), items)
    }

    @Test
    fun shippedListIsValidAndHasNoProtectedPackages() {
        val items = Debloat.parse(shipped)
        assertEquals(14, items.size)
        items.forEach {
            assertTrue(it.pkg, it.reason.isNotEmpty())
            assertFalse(it.pkg, Debloat.isProtected(it.pkg))
        }
    }

    @Test
    fun manufacturerFileName() {
        assertEquals("debloat/tcl.txt", Debloat.assetFor("TCL"))
        assertEquals("debloat/tclcorp.txt", Debloat.assetFor("TCL Corp."))
        assertNull(Debloat.assetFor(""))
        assertNull(Debloat.assetFor(null))
    }

    @Test
    fun onlyInstalledUnprotectedAndNotAlreadyOffAreOffered() {
        val items = Debloat.parse("a.one # 1\nb.two # 2\nc.three # 3\ncom.android.vending # no\nd.home # home\n")
        val out = Debloat.offered(items, setOf("a.one", "c.three", "com.android.vending", "d.home"), listOf("d.home"), setOf("c.three"))
        assertEquals(listOf("a.one"), out.map { it.pkg })
    }

    @Test
    fun everyNamedPackageIsProtected() {
        listOf(
            "com.tcl.tv", "com.tcl.tvinput", "com.tcl.dashboard", "com.tcl.suspension", "com.tcl.autopair", "com.tcl.systemserver",
            "com.android.tv.settings", "com.android.settings", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
            "com.android.providers.media", "com.google.android.tvsetup", "com.android.systemui", "com.android.tvprovider",
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
        ).forEach { assertTrue(it, Debloat.isProtected(it)) }
        assertTrue(Debloat.isProtected("x.launcher", listOf("x.launcher")))
        assertFalse(Debloat.isProtected("com.tcl.tv.plus"))
    }

    @Test
    fun appliesOnePackagePerCommandAndSavesFirst() = runBlocking {
        val tv = FakeTv(refuseDisable = setOf("c.c"))
        val store = MemoryStore(setOf("old.pkg"))
        val r = SpeedUpFlow(tv, store).apply(listOf("a.a", "b.b", "c.c", "com.android.vending"), emptyList())
        assertEquals(listOf("pm disable-user --user 0 a.a", "pm disable-user --user 0 b.b", "pm disable-user --user 0 c.c"), tv.log)
        tv.log.forEach { assertTrue(it, AdbWhitelist.allows(it)) }
        assertEquals(listOf("a.a", "b.b"), r.switchedOff)
        assertEquals(listOf("c.c"), r.failed)
        assertEquals(setOf("old.pkg", "a.a", "b.b"), store.value)
    }

    @Test
    fun restoreOneAndAll() = runBlocking {
        val tv = FakeTv(disabled = mutableSetOf("a.a", "b.b"))
        val store = MemoryStore(setOf("a.a", "b.b"))
        val flow = SpeedUpFlow(tv, store)
        flow.restore("a.a")
        assertEquals(setOf("b.b"), store.value)
        assertEquals(setOf("b.b"), tv.disabled)
        flow.restore()
        assertEquals(emptySet<String>(), store.value)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test
    fun restoreWithLostListUsesKnownDisabledPackages() = runBlocking {
        val tv = FakeTv(disabled = mutableSetOf("a.a", "other.app"))
        val store = MemoryStore()
        SpeedUpFlow(tv, store).restore(knownCandidates = setOf("a.a", "b.b"))
        assertEquals(setOf("other.app"), tv.disabled)
        assertTrue(Takeover.enableCommand("a.a") in tv.log)
    }
}
