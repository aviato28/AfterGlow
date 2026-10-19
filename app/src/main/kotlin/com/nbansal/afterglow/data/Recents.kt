package com.nbansal.afterglow.data

import com.nbansal.afterglow.apps.AppEntry

/** Afterglow's own launch history (no usage-access permission): newest first, one entry per package, capped. */
object Recents {
    const val MAX = 8

    class Entry(val pkg: String, val time: Long)

    fun record(entries: List<Entry>, pkg: String, now: Long): List<Entry> =
        (listOf(Entry(pkg, now)) + entries.filter { it.pkg != pkg }).take(MAX)

    fun encode(entries: List<Entry>): List<String> = entries.map { "${it.pkg}\t${it.time}" }

    fun decode(lines: List<String>): List<Entry> = lines.mapNotNull {
        val i = it.indexOf('\t')
        val t = if (i > 0) it.substring(i + 1).toLongOrNull() else null
        if (t == null) null else Entry(it.substring(0, i), t)
    }.sortedByDescending { it.time }.distinctBy { it.pkg }.take(MAX)

    /** Entries as apps: installed and not hidden only, names applied (favourites stay in). */
    fun resolve(entries: List<Entry>, apps: List<AppEntry>, hidden: Set<String>, names: Map<String, String>): List<AppEntry> {
        val byPkg = apps.associateBy { it.pkg }
        return entries.mapNotNull { byPkg[it.pkg] }.filter { it.pkg !in hidden }.map { Favourites.named(it, names) }
    }
}
