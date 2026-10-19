package com.nbansal.afterglow

import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Recents
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    private fun pkgs(l: List<Recents.Entry>) = l.map { it.pkg }

    @Test
    fun newestFirstNoRepeatsAndCappedAtEight() {
        var l = emptyList<Recents.Entry>()
        for (i in 1..10) l = Recents.record(l, "p.$i", i.toLong())
        assertEquals((10 downTo 3).map { "p.$it" }, pkgs(l))
        l = Recents.record(l, "p.5", 11)
        assertEquals(listOf("p.5", "p.10", "p.9", "p.8", "p.7", "p.6", "p.4", "p.3"), pkgs(l))
    }

    @Test
    fun roundTripsAndIgnoresBadLines() {
        val l = Recents.record(Recents.record(emptyList(), "a.a", 5), "b.b", 9)
        assertEquals(pkgs(l), pkgs(Recents.decode(Recents.encode(l) + listOf("junk", "x.y\tnotanumber"))))
    }

    @Test
    fun resolveDropsHiddenAndUninstalledKeepsFavouritesAndNames() {
        val apps = listOf("a.a", "b.b", "c.c").map { AppEntry(it, "x", it) }
        val l = Recents.record(Recents.record(Recents.record(emptyList(), "a.a", 1), "b.b", 2), "gone.x", 3)
        val out = Recents.resolve(l, apps, hidden = setOf("a.a"), names = mapOf("b.b" to "Bee"))
        assertEquals(listOf("Bee"), out.map { it.label })
    }
}
