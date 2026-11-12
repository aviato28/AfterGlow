package com.nbansal.afterglow.companion

import com.nbansal.afterglow.data.ButtonMap
import com.nbansal.afterglow.data.PrefState
import com.nbansal.afterglow.home.cleanName
import com.nbansal.afterglow.showcase.Tmdb
import com.nbansal.afterglow.takeover.Takeover
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a backup file holds and a restore may change. Takeover state (disabled packages, saved screensaver, firmware flags),
 * the quick-panel switch (it needs permissions granted on the TV) and the onboarding flag are deliberately not here.
 * [tmdbKey] is null when the file has no key (then the key on the TV stays as it is).
 */
class Backup(
    val favourites: List<String>?,
    val hidden: Set<String>,
    val names: Map<String, String>,
    val buttons: Map<Int, String>,
    val userName: String,
    val showcase: Boolean,
    val openWith: String,
    val tmdbKey: String?,
)

object BackupCodec {
    const val Version = 1
    const val MaxNameChars = 60

    fun of(s: PrefState, withKey: Boolean) = Backup(
        favourites = s.favourites, hidden = s.hidden, names = s.names, buttons = s.buttons,
        userName = s.userName, showcase = s.showcase, openWith = s.openWith,
        tmdbKey = if (withKey && s.tmdbKey.isNotEmpty()) s.tmdbKey else null,
    )

    fun encode(b: Backup): String {
        val o = JSONObject()
        o.put("app", "afterglow")
        o.put("version", Version)
        b.favourites?.let { o.put("favourites", JSONArray(it)) }
        o.put("hidden", JSONArray(b.hidden.sorted()))
        o.put("names", JSONObject(b.names))
        val buttons = JSONObject()
        b.buttons.forEach { (code, pkg) -> buttons.put(code.toString(), pkg) }
        o.put("buttons", buttons)
        o.put("userName", b.userName)
        o.put("showcase", b.showcase)
        o.put("openWith", b.openWith)
        b.tmdbKey?.let { o.put("tmdbKey", it) }
        return o.toString()
    }

    /** Strict: null when the file is not an Afterglow backup or any value is invalid (nothing is then applied). */
    fun decode(json: String): Backup? = try {
        parse(JSONObject(json))
    } catch (_: Exception) {
        null
    }

    private fun pkgOk(p: String) = Takeover.PackageName.matches(p)

    private fun strings(a: JSONArray?): List<String>? {
        if (a == null) return null
        val out = ArrayList<String>()
        for (i in 0 until a.length()) out.add(a.get(i) as? String ?: return null)
        return out
    }

    private fun parse(o: JSONObject): Backup? {
        if (o.optString("app") != "afterglow" || o.optInt("version", -1) != Version) return null
        var favourites: List<String>? = null
        if (o.has("favourites")) {
            favourites = strings(o.optJSONArray("favourites")) ?: return null
            if (favourites.size > 500 || !favourites.all(::pkgOk)) return null
        }
        val hidden = (strings(o.optJSONArray("hidden")) ?: emptyList())
        if (hidden.size > 2000 || !hidden.all(::pkgOk)) return null

        val names = LinkedHashMap<String, String>()
        o.optJSONObject("names")?.let { n ->
            for (k in n.keys()) {
                val v = n.get(k) as? String ?: return null
                if (!pkgOk(k) || v.isBlank() || v.length > MaxNameChars) return null
                names[k] = v
            }
        }
        val buttons = LinkedHashMap<Int, String>()
        o.optJSONObject("buttons")?.let { b ->
            for (k in b.keys()) {
                val code = k.toIntOrNull() ?: return null
                val v = b.get(k) as? String ?: return null
                if (!ButtonMap.isLearnable(code) || !pkgOk(v)) return null
                buttons[code] = v
            }
        }
        val userName = o.optString("userName", "")
        if (userName != cleanName(userName)) return null
        val openWith = o.optString("openWith", "")
        if (openWith.isNotEmpty() && !pkgOk(openWith)) return null
        var key: String? = null
        if (o.has("tmdbKey")) {
            key = (o.get("tmdbKey") as? String)?.trim() ?: return null
            if (!Tmdb.isValidKey(key)) return null
        }
        return Backup(favourites, hidden.toSet(), names, buttons, userName, o.optBoolean("showcase", false), openWith, key)
    }
}
