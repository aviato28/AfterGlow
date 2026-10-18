package com.nbansal.afterglow

import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Favourites
import com.nbansal.afterglow.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesTest {
    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, "$pkg.Main", label)

    @Test
    fun defaultsKeepKnownOrderAndCapAtEight() {
        val installed = Favourites.KnownApps.reversed().map { app(it) } + app("z.other")
        val result = Favourites.defaults(installed)
        assertEquals(Favourites.KnownApps.take(8), result)
    }

    @Test
    fun defaultsSkipMissingKnownApps() {
        val result = Favourites.defaults(listOf(app("org.xbmc.kodi"), app("a.b"), app("com.netflix.ninja")))
        assertEquals(listOf("com.netflix.ninja", "org.xbmc.kodi"), result)
    }

    @Test
    fun defaultsFallBackToFirstSixByName() {
        val installed = listOf("g", "c", "a", "f", "b", "e", "d").map { app("p.$it", it.uppercase()) }
        assertEquals(listOf("p.a", "p.b", "p.c", "p.d", "p.e", "p.f"), Favourites.defaults(installed))
    }

    @Test
    fun addRemoveAndMove() {
        var list = listOf("a", "b", "c")
        list = Favourites.add(list, "d")
        assertEquals(listOf("a", "b", "c", "d"), list)
        assertEquals(list, Favourites.add(list, "a"))
        assertEquals(listOf("a", "c", "b", "d"), Favourites.moveLater(list, "b"))
        assertEquals(listOf("b", "a", "c", "d"), Favourites.moveEarlier(list, "b"))
        assertEquals(list, Favourites.moveEarlier(list, "a"))
        assertEquals(list, Favourites.moveLater(list, "d"))
        assertEquals(list, Favourites.moveLater(list, "missing"))
        assertEquals(listOf("a", "c", "d"), Favourites.remove(list, "b"))
    }

    @Test
    fun hiddenAppsAreFilteredAndNamesApplied() {
        val apps = listOf(app("p.b", "Beta"), app("p.a", "Alpha"), app("p.c", "Gamma"))
        val visible = Favourites.visible(apps, hidden = setOf("p.c"), names = mapOf("p.b" to "Aardvark"))
        assertEquals(listOf("Aardvark", "Alpha"), visible.map { it.label })
        val favs = Favourites.resolve(listOf("p.c", "p.b", "p.gone", "p.a"), apps, setOf("p.c"), emptyMap())
        assertEquals(listOf("p.b", "p.a"), favs.map { it.pkg })
    }

    @Test
    fun renameAndBlankRestores() {
        var names = Favourites.rename(emptyMap(), "p.a", "  My App ")
        assertEquals(mapOf("p.a" to "My App"), names)
        names = Favourites.rename(names, "p.a", "   ")
        assertEquals(emptyMap<String, String>(), names)
    }

    @Test
    fun prefsEncodingRoundTrips() {
        val list = listOf("a.b", "c.d")
        assertEquals(list, Prefs.decodeList(Prefs.encodeList(list)))
        val map = mapOf("a.b" to "Name with spaces", "c.d" to "Other")
        assertEquals(map, Prefs.decodeMap(Prefs.encodeMap(map)))
    }
}
