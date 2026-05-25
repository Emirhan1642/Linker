package com.linker.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for DataStore preferences dependencies
 * 
 * Provides:
 * - DataStore instances for different preference types
 * - Corruption handling with automatic recovery
 * - Separate scopes for different preference categories
 * 
 * **Architecture:**
 * - OfflineMessaging: BLE, WiFi Direct, queue settings
 * - AppSettings: Theme, language, notification preferences
 * - UserPreferences: User-specific settings
 * 
 * **Data Safety:**
 * - Corruption handler replaces corrupted data with empty preferences
 * - Each DataStore has its own file to prevent cross-contamination
 * - IO dispatcher for file operations
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    
    private const val OFFLINE_MESSAGING_PREFS = "offline_messaging_prefs"
    private const val APP_SETTINGS_PREFS = "app_settings_prefs"
    private const val USER_PREFS = "user_prefs"
    
    /**
     * Provides DataStore for offline messaging preferences
     * 
     * SETTINGS:
     * - BLE enabled/disabled
     * - WiFi Direct enabled/disabled
     * - Max queue size
     * - Sync interval
     * - Battery optimization level
     */
    @Provides
    @Singleton
    @OfflineMessagingDataStore
    fun provideOfflineMessagingDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = {
                context.preferencesDataStoreFile(OFFLINE_MESSAGING_PREFS)
            }
        )
    }
    
    /**
     * Provides DataStore for app settings
     * 
     * SETTINGS:
     * - Theme (light/dark/system)
     * - Language preference
     * - Notification settings
     * - Privacy settings
     */
    @Provides
    @Singleton
    @AppSettingsDataStore
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = {
                context.preferencesDataStoreFile(APP_SETTINGS_PREFS)
            }
        )
    }
    
    /**
     * Provides DataStore for user preferences
     * 
     * SETTINGS:
     * - User-specific preferences
     * - Profile settings
     * - Display preferences
     */
    @Provides
    @Singleton
    @UserDataStore
    fun provideUserDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = {
                context.preferencesDataStoreFile(USER_PREFS)
            }
        )
    }
    
    /**
     * Provides EncryptedSharedPreferences for sensitive data
     * 
     * SECURITY:
     * - AES256_GCM for value encryption
     * - AES256_SIV for key encryption
     * - Hardware-backed keystore on supported devices
     * 
     * USE CASES:
     * - API tokens
     * - User credentials (temporary)
     * - Sensitive user preferences
     * 
     * NOTE: For most preferences, use regular DataStore.
     * Only use this for highly sensitive data.
     */
    @Provides
    @Singleton
    @EncryptedPreferences
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): android.content.SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "linker_encrypted_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Provides PreferencesMigrator for migrating from old SharedPreferences
     * 
     * FUNCTIONALITY:
     * - Migrates from legacy SharedPreferences to DataStore
     * - One-time migration on first app launch after update
     * - Clears old preferences after successful migration
     * - Logs migration status
     */
    @Provides
    @Singleton
    fun providePreferencesMigrator(
        @ApplicationContext context: Context,
        @OfflineMessagingDataStore dataStore: DataStore<Preferences>
    ): PreferencesMigrator {
        return PreferencesMigrator(context, dataStore)
    }
    
    /**
     * Provides PreferencesBackup for backup/restore functionality
     * 
     * FUNCTIONALITY:
     * - Backup preferences to JSON
     * - Restore preferences from JSON
     * - Cloud backup support (future)
     */
    @Provides
    @Singleton
    fun providePreferencesBackup(
        @OfflineMessagingDataStore dataStore: DataStore<Preferences>
    ): PreferencesBackup {
        return PreferencesBackup(dataStore)
    }
}

/**
 * Preferences Migrator
 * 
 * Migrates preferences from old SharedPreferences to DataStore.
 */
class PreferencesMigrator(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    suspend fun migrateIfNeeded() {
        val oldPrefs = context.getSharedPreferences("old_preferences", Context.MODE_PRIVATE)
        
        if (oldPrefs.all.isNotEmpty()) {
            android.util.Log.d("PreferencesMigrator", "Migrating ${oldPrefs.all.size} preferences")
            
            dataStore.edit { preferences ->
                oldPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> preferences[androidx.datastore.preferences.core.booleanPreferencesKey(key)] = value
                        is Int -> preferences[androidx.datastore.preferences.core.intPreferencesKey(key)] = value
                        is Long -> preferences[androidx.datastore.preferences.core.longPreferencesKey(key)] = value
                        is Float -> preferences[androidx.datastore.preferences.core.floatPreferencesKey(key)] = value
                        is String -> preferences[androidx.datastore.preferences.core.stringPreferencesKey(key)] = value
                    }
                }
            }
            
            // Clear old preferences after migration
            oldPrefs.edit().clear().apply()
            
            android.util.Log.d("PreferencesMigrator", "Migration completed")
        }
    }
}

/**
 * Preferences Backup
 * 
 * Handles backup and restore of preferences.
 */
class PreferencesBackup(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun backup(): Result<ByteArray> {
        return try {
            val preferences = dataStore.data.first()
            val json = preferencesToJson(preferences)
            Result.success(json.toByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun restore(backup: ByteArray): Result<Unit> {
        return try {
            val json = String(backup)
            val map = kotlinx.serialization.json.Json.decodeFromString<Map<String, Any>>(json)
            
            dataStore.edit { current ->
                current.clear()
                map.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> current[androidx.datastore.preferences.core.booleanPreferencesKey(key)] = value
                        is Int -> current[androidx.datastore.preferences.core.intPreferencesKey(key)] = value
                        is Long -> current[androidx.datastore.preferences.core.longPreferencesKey(key)] = value
                        is Double -> current[androidx.datastore.preferences.core.doublePreferencesKey(key)] = value
                        is String -> current[androidx.datastore.preferences.core.stringPreferencesKey(key)] = value
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun preferencesToJson(preferences: Preferences): String {
        val map = preferences.asMap().mapKeys { it.key.name }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer(),
            map
        )
    }
}

/**
 * Type-safe preference keys with validation
 */
object PreferenceKeys {
    val OFFLINE_MESSAGING_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("offline_messaging_enabled")
    val BLE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("ble_enabled")
    val WIFI_DIRECT_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("wifi_direct_enabled")
    val MAX_QUEUE_SIZE = androidx.datastore.preferences.core.intPreferencesKey("max_queue_size")
    val SYNC_INTERVAL_MS = androidx.datastore.preferences.core.longPreferencesKey("sync_interval_ms")
    val BATTERY_OPTIMIZATION = androidx.datastore.preferences.core.stringPreferencesKey("battery_optimization")
}

/**
 * Validates preference values
 */
class PreferencesValidator {
    fun validateMaxQueueSize(value: Int): Result<Int> {
        return when {
            value < 1 -> Result.failure(
                IllegalArgumentException("Max queue size must be at least 1")
            )
            value > 10_000 -> Result.failure(
                IllegalArgumentException("Max queue size cannot exceed 10,000")
            )
            else -> Result.success(value)
        }
    }
    
    fun validateSyncInterval(value: Long): Result<Long> {
        return when {
            value < 1000 -> Result.failure(
                IllegalArgumentException("Sync interval must be at least 1 second")
            )
            value > 3_600_000 -> Result.failure(
                IllegalArgumentException("Sync interval cannot exceed 1 hour")
            )
            else -> Result.success(value)
        }
    }
    
    fun validateBatteryOptimization(value: String): Result<String> {
        val validValues = setOf("none", "low", "medium", "high")
        return if (value in validValues) {
            Result.success(value)
        } else {
            Result.failure(
                IllegalArgumentException("Invalid battery optimization: $value")
            )
        }
    }
}

/**
 * Qualifier for offline messaging DataStore
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineMessagingDataStore

/**
 * Qualifier for app settings DataStore
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSettingsDataStore

/**
 * Qualifier for user preferences DataStore
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserDataStore

/**
 * Qualifier for encrypted SharedPreferences
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EncryptedPreferences
