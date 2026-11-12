package com.nbansal.afterglow.companion

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.companionStore by preferencesDataStore("companion")

data class CompanionState(val enabled: Boolean = false, val tokens: List<String> = emptyList())

/** The companion switch and the paired phones' tokens. A separate file from the settings, so a backup can never carry tokens. */
class CompanionStore(context: Context) {
    private val store = context.applicationContext.companionStore

    val state: Flow<CompanionState> = store.data.map { p ->
        CompanionState(
            enabled = p[ENABLED] ?: false,
            tokens = p[TOKENS]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    suspend fun setEnabled(on: Boolean) { store.edit { it[ENABLED] = on } }

    suspend fun setTokens(tokens: List<String>) {
        store.edit { if (tokens.isEmpty()) it.remove(TOKENS) else it[TOKENS] = tokens.joinToString("\n") }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val TOKENS = stringPreferencesKey("tokens")
    }
}
