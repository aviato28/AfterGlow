package com.nbansal.afterglow.takeover

/** One optional package with its plain-language reason. */
data class DebloatItem(val pkg: String, val reason: String)

/** Pure rules for the "Speed up my TV" list (unit-tested). */
object Debloat {
    /** Never offered, on top of [Takeover.isProtected]. */
    val ProtectedExtra = setOf(
        "com.tcl.tv", "com.tcl.tvinput", "com.tcl.dashboard", "com.tcl.suspension", "com.tcl.autopair", "com.tcl.systemserver",
        "com.android.tv.settings", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
        // Apps people watch are never in the list.
        "com.netflix.ninja", "com.netflix.mediaclient",
        "com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvmusic",
        "com.amazon.amazonvideo.livingroom", "com.amazon.avod", "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
    )
    private val ProtectedPrefixes = listOf("com.google.android.gms.", "com.google.android.gsf.")

    fun isProtected(pkg: String, homePackages: Collection<String> = emptyList()) =
        Takeover.isProtected(pkg) || pkg in ProtectedExtra || ProtectedPrefixes.any { pkg.startsWith(it) } || pkg in homePackages

    /** File name of the shipped list for a manufacturer: letters and digits only, lower case (null if nothing is left). */
    fun assetFor(manufacturer: String?): String? {
        val m = manufacturer.orEmpty().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        return if (m.isEmpty()) null else "debloat/$m.txt"
    }

    /** `package # reason`; blank lines and `##` comments are ignored; bad package names are dropped; first line of a package wins. */
    fun parse(text: String): List<DebloatItem> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("##") }
        .mapNotNull { line ->
            val pkg = line.substringBefore('#').trim()
            if (!Takeover.PackageName.matches(pkg)) null else DebloatItem(pkg, line.substringAfter('#', "").trim())
        }.distinctBy { it.pkg }

    /** What the user is shown: installed here, not protected, not a HOME handler, not already switched off by us. */
    fun offered(items: List<DebloatItem>, installed: Set<String>, homePackages: Collection<String>, alreadyOff: Set<String>): List<DebloatItem> =
        items.filter { it.pkg in installed && it.pkg !in alreadyOff && !isProtected(it.pkg, homePackages) }
}

class SpeedUpResult(val switchedOff: List<String>, val failed: List<String>, val stoppedEarly: Boolean)

/** Disable one package per command; the chosen list is saved BEFORE the first command so Restore always knows. */
class SpeedUpFlow(private val adb: Adb, private val store: DisabledStore) {
    suspend fun apply(chosen: List<String>, homePackages: Collection<String>): SpeedUpResult {
        val plan = chosen.distinct().filter { !Debloat.isProtected(it, homePackages) && Takeover.PackageName.matches(it) }
        if (plan.isEmpty()) return SpeedUpResult(emptyList(), emptyList(), false)
        val saved = store.get()
        store.set(saved + plan)
        val ok = mutableListOf<String>()
        val bad = mutableListOf<String>()
        var stopped = false
        for ((i, pkg) in plan.withIndex()) {
            val r = try { adb.run(Takeover.disableCommand(pkg)) } catch (e: AdbException) {
                // Unreachable: the rest were never touched, so they leave the saved list again.
                bad += plan.drop(i); stopped = true; break
            }
            if (Takeover.disableSucceeded(r.exitCode, r.output)) ok += pkg else bad += pkg
        }
        // A package that did not switch off is still on, so it is not "ours" to restore.
        store.set(saved + ok)
        return SpeedUpResult(ok, bad, stopped)
    }

    /** Turn the given packages (or everything saved) back on. A lost list falls back to [knownCandidates] that are switched off. */
    suspend fun restore(only: String? = null, knownCandidates: Set<String> = emptySet()): SpeedUpResult {
        var list = store.get()
        if (only != null) list = list.filter { it == only }.toSet().ifEmpty { setOf(only) }
        else if (list.isEmpty()) {
            list = HomeParsing.disabledPackages(adb.run(AdbWhitelist.ListDisabled).output).filter { it in knownCandidates }.toSet()
        }
        val ok = mutableListOf<String>()
        val bad = mutableListOf<String>()
        for (pkg in list) {
            val r = try { adb.run(Takeover.enableCommand(pkg)) } catch (e: AdbException) { bad += pkg; continue }
            if (Takeover.enableSucceeded(r.exitCode, r.output)) ok += pkg else bad += pkg
        }
        store.set(store.get() - ok.toSet())
        return SpeedUpResult(ok, bad, false)
    }
}
