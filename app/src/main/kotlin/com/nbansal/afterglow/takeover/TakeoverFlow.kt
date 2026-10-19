package com.nbansal.afterglow.takeover

/** Where the planned list of disabled HOME handlers is kept (DataStore in the app, a fake in tests). */
interface DisabledStore {
    suspend fun get(): Set<String>
    suspend fun set(packages: Set<String>)
}

sealed interface FlowResult {
    /** [homeNow] is the component Home resolves to after the change. */
    class Done(val message: String, val homeNow: String?) : FlowResult
    class Stopped(val message: String) : FlowResult
}

/** Output parsing for the allowed adb commands (pure, unit-tested). */
object HomeParsing {
    private val Component = Regex("^([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/(\\S+)$")

    /** Packages in `query-activities --brief` output: every line that is a `pkg/.Class` component. */
    fun homePackages(output: String): List<String> =
        output.lines().mapNotNull { Component.matchEntire(it.trim())?.groupValues?.get(1) }.distinct()

    /** `resolve-activity --brief` ends with the chosen component; null when nothing resolved. */
    fun resolvedComponent(output: String): String? =
        output.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.takeIf { Component.matches(it) }

    fun disabledPackages(output: String): Set<String> =
        output.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }.toSet()

    fun packageOf(component: String?): String? = component?.substringBefore('/')?.takeIf { it.isNotEmpty() }
}

class TakeoverFlow(private val adb: Adb, private val self: String, private val store: DisabledStore) {
    private val selfHome = "$self/.HomeActivity"

    private suspend fun resolve(): String? = HomeParsing.resolvedComponent(adb.run(AdbWhitelist.ResolveHome).output)

    private fun isSelf(component: String?) = component == selfHome || component == "$self/$self.HomeActivity"

    /** Plain words for "who owns Home" (component from resolve-activity). */
    fun describe(component: String?): String = when {
        component == null -> "something else"
        isSelf(component) -> "Afterglow"
        HomeParsing.packageOf(component)!!.startsWith("com.google.android") -> "Google TV"
        else -> "something else"
    }

    suspend fun makeHome(): FlowResult {
        val homes = HomeParsing.homePackages(adb.run(AdbWhitelist.QueryHomes).output)
        if (self !in homes) return FlowResult.Stopped("Afterglow is not offered to this TV as a Home app. Nothing was changed.")
        val plan = Takeover.candidates(homes, self)
        val before = resolve()
        if (isSelf(before) && plan.isEmpty()) return FlowResult.Done("Afterglow is already your Home.", before)
        if (plan.isEmpty()) return FlowResult.Stopped("Nothing safe to switch off here, so Home stays as it is. Nothing was changed.")

        // The plan is saved BEFORE anything is disabled, so Restore always knows what to bring back.
        val saved = store.get()
        store.set(saved + plan)
        val failed = "This TV did not accept Afterglow as Home. Nothing was changed."
        val after = try {
            for (pkg in plan) {
                val r = adb.run(Takeover.disableCommand(pkg))
                if (!Takeover.disableSucceeded(r.exitCode, r.output)) return undo(plan, saved, failed)
            }
            resolve()
        } catch (e: AdbException) {
            return undo(plan, saved, "The TV stopped answering. Nothing was changed.")
        }
        if (!isSelf(after)) return undo(plan, saved, failed)
        return FlowResult.Done("Done. Press Home to try it.", after)
    }

    /** Packages we turn back on when the saved list is lost (the two the TCL recipe disables). */
    val KnownTakeoverPackages = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")

    /** True when the saved list is not empty, or a known home package is disabled on the TV. */
    suspend fun restoreNeeded(): Boolean =
        store.get().isNotEmpty() ||
            HomeParsing.disabledPackages(adb.run(AdbWhitelist.ListDisabled).output).any { it in KnownTakeoverPackages }

    suspend fun restore(): FlowResult {
        var list = store.get()
        if (list.isEmpty()) {
            list = HomeParsing.disabledPackages(adb.run(AdbWhitelist.ListDisabled).output).filter { it in KnownTakeoverPackages }.toSet()
        }
        if (list.isEmpty()) return FlowResult.Stopped("Nothing to restore: Google TV's home screen is already on.")
        val failed = mutableSetOf<String>()
        for (pkg in list) {
            val r = adb.run(Takeover.enableCommand(pkg))
            if (!Takeover.enableSucceeded(r.exitCode, r.output)) failed += pkg
        }
        store.set(failed)
        val now = resolve()
        if (failed.isNotEmpty()) return FlowResult.Stopped("Some apps could not be switched on again. Try Restore once more, or use the manual route.")
        return FlowResult.Done("Google TV is restored. Home button opens: ${describe(now)}.", now)
    }

    /** Re-enable everything we touched; the saved list only goes back to what it was once every enable worked. */
    private suspend fun undo(plan: List<String>, saved: Set<String>, message: String): FlowResult {
        var allBack = true
        for (pkg in plan) {
            val r = runCatching { adb.run(Takeover.enableCommand(pkg)) }.getOrNull()
            if (r == null || !Takeover.enableSucceeded(r.exitCode, r.output)) allBack = false
        }
        if (allBack) store.set(saved)
        return FlowResult.Stopped(
            if (allBack) message else "$message Some switched-off apps could not be turned back on: use Restore Google TV.",
        )
    }
}
