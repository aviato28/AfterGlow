package com.nbansal.afterglow.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("afterglow")

/** `favourites == null` means first run: defaults have not been chosen yet. */
data class PrefState(
    val favourites: List<String>? = null,
    val hidden: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
    val disabledHomes: Set<String> = emptySet(),
    val lastUpdateCheck: Long = 0L,
)

class Prefs(context: Context) {
    private val store = context.applicationContext.store

    val state: Flow<PrefState> = store.data.map { p ->
        PrefState(
            favourites = p[FAVOURITES]?.let(::decodeList),
            hidden = p[HIDDEN] ?: emptySet(),
            names = p[NAMES]?.let(::decodeMap) ?: emptyMap(),
            disabledHomes = p[DISABLED] ?: emptySet(),
            lastUpdateCheck = p[LAST_CHECK] ?: 0L,
        )
    }

    suspend fun setFavourites(list: List<String>) = edit { it[FAVOURITES] = encodeList(list) }
    suspend fun setHidden(set: Set<String>) = edit { it[HIDDEN] = set }
    suspend fun setNames(map: Map<String, String>) = edit { it[NAMES] = encodeMap(map) }
    suspend fun setDisabledHomes(set: Set<String>) = edit { it[DISABLED] = set }
    suspend fun setLastUpdateCheck(ms: Long) = edit { it[LAST_CHECK] = ms }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    companion object {
        private val FAVOURITES = stringPreferencesKey("favourites")
        private val HIDDEN = stringSetPreferencesKey("hidden")
        private val NAMES = stringPreferencesKey("names")
        private val DISABLED = stringSetPreferencesKey("disabled_homes")
        private val LAST_CHECK = longPreferencesKey("last_update_check")

        fun encodeList(list: List<String>) = list.joinToString("\n")
        fun decodeList(s: String) = s.split('\n').filter { it.isNotBlank() }
        fun encodeMap(map: Map<String, String>) = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        fun decodeMap(s: String): Map<String, String> = s.split('\n').mapNotNull {
            val i = it.indexOf('\t')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
    }
}
