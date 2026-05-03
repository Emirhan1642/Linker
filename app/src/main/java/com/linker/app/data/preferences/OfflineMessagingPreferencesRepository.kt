package com.linker.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing offline messaging preferences
 * 
 * Provides type-safe access to preferences with reactive updates
 */
@Singleton
class OfflineMessagingPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore: DataStore<OfflineMessagingPreferences>
        get() = context.getOfflineMessagingDataStore()
    
    /**
     * Observe all preferences
     */
    fun observePreferences(): Flow<OfflineMessagingPreferences> {
        return dataStore.data
    }
    
    /**
     * Observe BLE enabled state
     */
    fun observeBleEnabled(): Flow<Boolean> {
        return dataStore.data.map { it.isBleEnabled }
    }
    
    /**
     * Observe Wi-Fi Direct enabled state
     */
    fun observeWifiDirectEnabled(): Flow<Boolean> {
        return dataStore.data.map { it.isWifiDirectEnabled }
    }
    
    /**
     * Observe notification visibility
     */
    fun observeShowNotification(): Flow<Boolean> {
        return dataStore.data.map { it.showNotification }
    }
    
    /**
     * Observe max TTL
     */
    fun observeMaxTtl(): Flow<Int> {
        return dataStore.data.map { it.maxTtl }
    }
    
    /**
     * Update BLE enabled state
     */
    suspend fun setBleEnabled(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.copy(
                isBleEnabled = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update Wi-Fi Direct enabled state
     */
    suspend fun setWifiDirectEnabled(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.copy(
                isWifiDirectEnabled = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update notification visibility
     */
    suspend fun setShowNotification(show: Boolean) {
        dataStore.updateData { preferences ->
            preferences.copy(
                showNotification = show,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update max TTL
     */
    suspend fun setMaxTtl(ttl: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(
                maxTtl = ttl.coerceIn(1, 10), // Clamp between 1 and 10
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update logging settings
     */
    suspend fun setLoggingEnabled(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.copy(
                enableLogging = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update log level
     */
    suspend fun setLogLevel(level: String) {
        dataStore.updateData { preferences ->
            preferences.copy(
                logLevel = level,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Reset all preferences to defaults
     */
    suspend fun resetToDefaults() {
        dataStore.updateData {
            OfflineMessagingPreferences(
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
}
