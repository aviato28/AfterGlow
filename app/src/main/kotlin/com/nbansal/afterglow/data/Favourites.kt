package com.nbansal.afterglow.data

import com.nbansal.afterglow.apps.AppEntry

/** Pure list logic for favourites, hiding and renaming (unit-tested). */
object Favourites {
    const val MAX_DEFAULT = 8
    const val FALLBACK_COUNT = 6

    val KnownApps = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.plexapp.android",
        "com.spotify.tv.android",
        "com.apple.atve.androidtv.appletv",
        "org.jellyfin.androidtv",
        "org.xbmc.kodi",
    )

    /** First run: known streaming apps that are installed (in the fixed order, max 8), else the first 6 by name. */
    fun defaults(installed: List<AppEntry>): List<String> {
        val have = installed.map { it.pkg }.toSet()
        val known = KnownApps.filter { it in have }.take(MAX_DEFAULT)
        if (known.isNotEmpty()) return known
        return installed.sortedBy { it.label.lowercase() }.take(FALLBACK_COUNT).map { it.pkg }
    }

    fun add(list: List<String>, pkg: String): List<String> = if (pkg in list) list else list + pkg

    fun remove(list: List<String>, pkg: String): List<String> = list - pkg

    fun moveEarlier(list: List<String>, pkg: String): List<String> = swap(list, pkg, -1)

    fun moveLater(list: List<String>, pkg: String): List<String> = swap(list, pkg, +1)

    private fun swap(list: List<String>, pkg: String, delta: Int): List<String> {
        val from = list.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        return list.toMutableList().also { it[from] = it[to]; it[to] = pkg }
    }

    /** Favourites as entries, in order, skipping uninstalled and hidden apps; names applied. */
    fun resolve(favs: List<String>, apps: List<AppEntry>, hidden: Set<String>, names: Map<String, String>): List<AppEntry> {
        val byPkg = apps.associateBy { it.pkg }
        return favs.mapNotNull { byPkg[it] }.filter { it.pkg !in hidden }.map { named(it, names) }
    }

    /** All apps minus hidden ones, names applied, sorted by displayed name. */
    fun visible(apps: List<AppEntry>, hidden: Set<String>, names: Map<String, String>): List<AppEntry> =
        apps.filter { it.pkg !in hidden }.map { named(it, names) }.sortedBy { it.label.lowercase() }

    fun named(app: AppEntry, names: Map<String, String>): AppEntry =
        names[app.pkg]?.takeIf { it.isNotBlank() }?.let { app.copy(label = it) } ?: app

    /** Blank name = back to the original label. */
    fun rename(names: Map<String, String>, pkg: String, name: String): Map<String, String> {
        val clean = name.trim().replace('\n', ' ').replace('\t', ' ')
        return if (clean.isEmpty()) names - pkg else names + (pkg to clean)
    }
}
