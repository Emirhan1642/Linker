package com.linker.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * DataStore file name for offline messaging preferences
 */
private const val OFFLINE_MESSAGING_PREFERENCES_FILE_NAME = "offline_messaging_preferences.pb"

/**
 * Extension property for accessing OfflineMessagingPreferences DataStore
 * 
 * This property returns a singleton DataStore instance that is thread-safe
 * and can be accessed from any Context (Activity, Service, Application).
 * 
 * **Thread Safety:**
 * - DataStore operations are thread-safe
 * - All read/write operations are performed on Dispatchers.IO
 * - Multiple concurrent reads/writes are handled safely
 * 
 * **Lifecycle:**
 * - DataStore instance survives configuration changes
 * - No need to close or cleanup DataStore
 * - Automatically handles coroutine scope
 * 
 * **Usage:**
 * ```kotlin
 * // Reading preferences
 * lifecycleScope.launch {
 *     context.offlineMessagingDataStore.data.collect { prefs ->
 *         // Use preferences (runs on Dispatchers.IO)
 *     }
 * }
 * 
 * // Writing preferences
 * lifecycleScope.launch {
 *     context.offlineMessagingDataStore.updateData { prefs ->
 *         prefs.copy(isBleEnabled = true)
 *     }
 * }
 * ```
 * 
 * @return Singleton DataStore instance for OfflineMessagingPreferences
 * @see OfflineMessagingPreferences for available settings
 * @see OfflineMessagingPreferencesRepository for higher-level API
 */
val Context.offlineMessagingDataStore: DataStore<OfflineMessagingPreferences> by dataStore(
    fileName = OFFLINE_MESSAGING_PREFERENCES_FILE_NAME,
    serializer = OfflineMessagingPreferencesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { exception ->
            android.util.Log.e("DataStoreExtensions", "DataStore corrupted, resetting to defaults", exception)
            OfflineMessagingPreferences()
        }
    )
)

/**
 * Extension function to keep backward compatibility with existing code
 */
@JvmName("getOfflineMessagingDataStoreCompat")
fun Context.getOfflineMessagingDataStore(): DataStore<OfflineMessagingPreferences> {
    return this.offlineMessagingDataStore
}

/**
 * Create an in-memory DataStore for testing
 * 
 * Usage in tests:
 * ```kotlin
 * @Test
 * fun testPreferences() = runTest {
 *     val testDataStore = createTestDataStore()
 *     // Use testDataStore in tests
 * }
 * ```
 * 
 * @param scope CoroutineScope for DataStore operations (default: test scope)
 * @return In-memory DataStore instance for testing
 */
fun createTestDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
): DataStore<OfflineMessagingPreferences> {
    return DataStoreFactory.create(
        serializer = OfflineMessagingPreferencesSerializer,
        scope = scope,
        produceFile = {
            File.createTempFile("test_offline_messaging_preferences", ".pb").also {
                it.deleteOnExit()
            }
        }
    )
}
