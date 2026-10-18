package com.nbansal.afterglow.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /** Leanback apps plus plain launcher apps that have no leanback entry. Afterglow itself is left out. */
    fun load(): List<AppEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())) {
                val ai = info.activityInfo ?: continue
                if (ai.packageName == context.packageName || !seen.add(ai.packageName)) continue
                out += AppEntry(ai.packageName, ai.name, info.loadLabel(pm).toString())
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun launch(pkg: String) {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun appInfo(pkg: String) = start(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun uninstall(pkg: String) = start(
        Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
