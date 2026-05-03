package com.linker.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File

/**
 * Extension property for accessing OfflineMessagingPreferences DataStore
 * 
 * Usage:
 * ```kotlin
 * val preferences = context.offlineMessagingDataStore.data.collect { prefs ->
 *     // Use preferences
 * }
 * ```
 */
fun Context.getOfflineMessagingDataStore(): DataStore<OfflineMessagingPreferences> {
    return DataStoreFactory.create(
        serializer = OfflineMessagingPreferencesSerializer,
        produceFile = {
            File(filesDir, "offline_messaging_preferences.pb")
        }
    )
}
