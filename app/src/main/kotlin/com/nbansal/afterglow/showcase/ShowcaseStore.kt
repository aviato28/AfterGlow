package com.nbansal.afterglow.showcase

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What Showcase remembers between runs. [shown] is pool key -> last shown (epoch ms), [hidden] the "Not interested" keys (kept forever). */
data class ShowcaseState(
    val shown: Map<String, Long> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val taste: Taste = Taste(),
)

/** One small JSON file in filesDir (no secrets in it). Blocking: call on Dispatchers.IO. */
class ShowcaseStore(private val context: Context) {
    private val file get() = File(context.filesDir, "showcase-state.json")
    private val lock = Any()

    fun read(): ShowcaseState = synchronized(lock) { readLocked() }

    fun update(block: (ShowcaseState) -> ShowcaseState) {
        synchronized(lock) { runCatching { writeLocked(block(readLocked())) } }
    }

    private fun readLocked(): ShowcaseState = runCatching {
        if (!file.exists()) return@runCatching ShowcaseState()
        val o = JSONObject(file.readText())
        val shown = HashMap<String, Long>()
        o.optJSONObject("shown")?.let { s -> for (k in s.keys()) shown[k] = s.optLong(k) }
        val h = o.optJSONArray("hidden")
        val hidden = if (h == null) emptySet() else (0 until h.length()).map { h.optString(it) }.filter { it.isNotEmpty() }.toSet()
        ShowcaseState(shown, hidden, Taste.fromJson(o.optJSONObject("taste")))
    }.getOrDefault(ShowcaseState())

    private fun writeLocked(s: ShowcaseState) {
        val shown = JSONObject()
        s.shown.forEach { (k, v) -> shown.put(k, v) }
        val o = JSONObject().put("shown", shown).put("hidden", JSONArray(s.hidden.sorted())).put("taste", s.taste.toJson())
        file.writeText(o.toString())
    }
}
