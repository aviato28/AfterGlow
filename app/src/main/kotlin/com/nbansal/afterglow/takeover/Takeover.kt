package com.nbansal.afterglow.takeover

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Pure rules for taking over the Home button (unit-tested). */
object Takeover {
    val ProtectedExact = setOf(
        "com.android.settings",
        "com.android.tv.settings",
        "com.google.android.tv.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.tvsetup",
        "com.android.systemui",
        "com.android.tvprovider",
    )
    const val ProtectedPrefix = "com.android.providers."

    private val PackageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun isProtected(pkg: String) = pkg in ProtectedExact || pkg.startsWith(ProtectedPrefix)

    /** Packages we may disable: only what handles HOME, never Afterglow itself, never the protected list. */
    fun candidates(homePackages: List<String>, self: String): List<String> =
        homePackages.distinct().filter { it != self && !isProtected(it) && PackageName.matches(it) }

    fun disableCommand(pkg: String): String {
        require(PackageName.matches(pkg)) { "bad package name" }
        return "pm disable-user --user 0 $pkg"
    }

    fun enableCommand(pkg: String): String {
        require(PackageName.matches(pkg)) { "bad package name" }
        return "pm enable $pkg"
    }

    fun disableSucceeded(exitCode: Int, output: String) = exitCode == 0 && output.contains("disabled-user")
    fun enableSucceeded(exitCode: Int, output: String) = exitCode == 0 && output.contains("new state: enabled")

    /** `enabled_accessibility_services` is a colon-separated list of flattened component names. */
    fun serviceEnabled(setting: String?, component: String): Boolean =
        setting.orEmpty().split(':').any { it.equals(component, ignoreCase = true) }

    fun staleNotice(afterglowIsHome: Boolean, disabled: Set<String>) = !afterglowIsHome && disabled.isNotEmpty()

    /** Every package that handles HOME (the TV's own launcher, Afterglow, ...). */
    fun homePackages(pm: PackageManager): List<String> =
        runCatching { pm.queryIntentActivities(homeIntent(), 0) }.getOrDefault(emptyList())
            .mapNotNull { it.activityInfo?.packageName }.distinct()

    /** Package that Home currently resolves to; the system chooser ("android") counts as "ask every time". */
    fun resolvedHome(pm: PackageManager): String? =
        runCatching { pm.resolveActivity(homeIntent(), 0)?.activityInfo?.packageName }.getOrNull()

    fun homeLabel(context: Context): String {
        val pm = context.packageManager
        val pkg = resolvedHome(pm) ?: return "unknown"
        return when {
            pkg == context.packageName -> "Afterglow"
            pkg == "android" -> "a chooser"
            else -> if (pkg.startsWith("com.google.android")) "Google TV" else
                runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
        }
    }

    fun accessibilityOn(context: Context): Boolean = serviceEnabled(
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        "${context.packageName}/${HomeKeyService::class.java.name}",
    )

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}
