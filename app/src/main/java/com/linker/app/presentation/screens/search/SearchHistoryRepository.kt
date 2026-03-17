package com.linker.app.presentation.screens.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "linker_search_history")

/**
 * Hesaba özel arama geçmişini DataStore'da saklar.
 * Her kullanıcı için ayrı bir key: "searches_<uid>"
 * Max 20 kayıt tutulur, yeniler üste eklenir.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val MAX_RECENTS = 20

    private fun keyFor(uid: String) = stringPreferencesKey("searches_$uid")

    fun getRecentSearches(uid: String): Flow<List<String>> {
        return context.searchDataStore.data.map { prefs ->
            val raw = prefs[keyFor(uid)] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }
        }
    }

    suspend fun addSearch(uid: String, query: String) {
        context.searchDataStore.edit { prefs ->
            val key = keyFor(uid)
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrElse { emptyList() }
            } ?: emptyList()

            // Varsa üstten kaldır, başa ekle, max 20 tut
            val updated = (listOf(query) + current.filter { it != query }).take(MAX_RECENTS)
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun removeSearch(uid: String, query: String) {
        context.searchDataStore.edit { prefs ->
            val key = keyFor(uid)
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrElse { emptyList() }
            } ?: emptyList()
            prefs[key] = json.encodeToString(current.filter { it != query })
        }
    }

    suspend fun clearAll(uid: String) {
        context.searchDataStore.edit { prefs ->
            prefs.remove(keyFor(uid))
        }
    }
}
