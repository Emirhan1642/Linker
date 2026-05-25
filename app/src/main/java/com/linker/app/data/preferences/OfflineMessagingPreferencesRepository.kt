package com.linker.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing offline messaging preferences
 * 
 * Provides type-safe access to preferences with reactive updates and one-time reads.
 * All updates are performed atomically and include comprehensive error handling.
 */
@Singleton
class OfflineMessagingPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OfflineMessagingPreferencesRepository"
    }
    
    private val dataStore: DataStore<OfflineMessagingPreferences> by lazy {
        context.getOfflineMessagingDataStore()
    }
    
    /**
     * Execute DataStore update with error handling
     */
    private suspend fun safeUpdate(
        operation: String,
        block: suspend (OfflineMessagingPreferences) -> OfflineMessagingPreferences
    ): Result<Unit> {
        return try {
            dataStore.updateData(block)
            android.util.Log.d(TAG, "Successfully updated: $operation")
            Result.success(Unit)
        } catch (e: IOException) {
            android.util.Log.e(TAG, "IO error during $operation", e)
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unexpected error during $operation", e)
            Result.failure(e)
        }
    }
    
    /**
     * Observe all preferences reactively
     * 
     * Emits the current preferences immediately and then emits updates
     * whenever preferences change.
     * 
     * @return Flow of OfflineMessagingPreferences that emits on every change
     * 
     * @see getPreferences for one-time read
     */
    fun observePreferences(): Flow<OfflineMessagingPreferences> {
        return dataStore.data
    }
    
    /**
     * Observe BLE enabled state reactively
     * 
     * Emits true/false whenever BLE enabled state changes.
     * 
     * @return Flow of Boolean that emits on every BLE enabled state change
     * 
     * @see getBleEnabled for one-time read
     * @see setBleEnabled for updating the state
     */
    fun observeBleEnabled(): Flow<Boolean> {
        return dataStore.data.map { it.isBleEnabled }
    }
    
    /**
     * Observe Wi-Fi Direct enabled state reactively
     */
    fun observeWifiDirectEnabled(): Flow<Boolean> {
        return dataStore.data.map { it.isWifiDirectEnabled }
    }
    
    /**
     * Observe notification visibility reactively
     */
    fun observeShowNotification(): Flow<Boolean> {
        return dataStore.data.map { it.showNotification }
    }
    
    /**
     * Observe max TTL reactively
     */
    fun observeMaxTtl(): Flow<Int> {
        return dataStore.data.map { it.maxTtl }
    }
    
    /**
     * Get current preferences (one-time read)
     * 
     * @return Current preferences or default if error occurs
     */
    suspend fun getPreferences(): OfflineMessagingPreferences {
        return try {
            dataStore.data.first()
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read preferences, returning defaults", e)
            OfflineMessagingPreferences()
        }
    }
    
    /**
     * Get current BLE enabled state (one-time read)
     */
    suspend fun getBleEnabled(): Boolean {
        return try {
            dataStore.data.first().isBleEnabled
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read BLE enabled state, returning default", e)
            true // Default value
        }
    }
    
    /**
     * Get current Wi-Fi Direct enabled state (one-time read)
     */
    suspend fun getWifiDirectEnabled(): Boolean {
        return try {
            dataStore.data.first().isWifiDirectEnabled
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read Wi-Fi Direct enabled state, returning default", e)
            true
        }
    }
    
    /**
     * Get current max TTL (one-time read)
     */
    suspend fun getMaxTtl(): Int {
        return try {
            dataStore.data.first().maxTtl
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read max TTL, returning default", e)
            OfflineMessagingPreferences.DEFAULT_TTL
        }
    }
    
    /**
     * Get current show notification state (one-time read)
     */
    suspend fun getShowNotification(): Boolean {
        return try {
            dataStore.data.first().showNotification
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read show notification state, returning default", e)
            true
        }
    }
    
    /**
     * Get current logging enabled state (one-time read)
     */
    suspend fun getLoggingEnabled(): Boolean {
        return try {
            dataStore.data.first().enableLogging
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read logging enabled state, returning default", e)
            false
        }
    }
    
    /**
     * Get current log level (one-time read)
     */
    suspend fun getLogLevel(): String {
        return try {
            dataStore.data.first().logLevel
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to read log level, returning default", e)
            "INFO"
        }
    }
    
    /**
     * Update BLE enabled state
     * 
     * Enables or disables BLE mesh networking. When disabled, the app will not
     * scan for or advertise BLE devices.
     * 
     * @param enabled New BLE enabled state
     * @return Result.success if update succeeded, Result.failure if error occurred
     */
    suspend fun setBleEnabled(enabled: Boolean): Result<Unit> {
        android.util.Log.d(TAG, "Setting BLE enabled: $enabled")
        return safeUpdate("BLE enabled") { preferences ->
            preferences.copy(
                isBleEnabled = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }.also { result ->
            if (result.isSuccess) {
                android.util.Log.i(TAG, "BLE enabled updated successfully: $enabled")
            } else {
                android.util.Log.e(TAG, "Failed to update BLE enabled: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    /**
     * Update Wi-Fi Direct enabled state
     * 
     * @param enabled New Wi-Fi Direct enabled state
     * @return Result indicating success or failure
     */
    suspend fun setWifiDirectEnabled(enabled: Boolean): Result<Unit> {
        android.util.Log.d(TAG, "Setting Wi-Fi Direct enabled: $enabled")
        return safeUpdate("Wi-Fi Direct enabled") { preferences ->
            preferences.copy(
                isWifiDirectEnabled = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }.also { result ->
            if (result.isSuccess) {
                android.util.Log.i(TAG, "Wi-Fi Direct enabled updated successfully: $enabled")
            }
        }
    }
    
    /**
     * Update notification visibility
     * 
     * @param show New notification visibility state
     * @return Result indicating success or failure
     */
    suspend fun setShowNotification(show: Boolean): Result<Unit> {
        android.util.Log.d(TAG, "Setting show notification: $show")
        return safeUpdate("show notification") { preferences ->
            preferences.copy(
                showNotification = show,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update max TTL with validation
     * 
     * @param ttl New max TTL value (must be between 1 and 10)
     * @return Result.success if valid, Result.failure if invalid
     */
    suspend fun setMaxTtl(ttl: Int): Result<Unit> {
        android.util.Log.d(TAG, "Setting max TTL: $ttl")
        
        if (ttl !in OfflineMessagingPreferences.MIN_TTL..OfflineMessagingPreferences.MAX_TTL) {
            val errorMsg = "maxTtl must be between ${OfflineMessagingPreferences.MIN_TTL} and ${OfflineMessagingPreferences.MAX_TTL}, got: $ttl"
            android.util.Log.w(TAG, errorMsg)
            return Result.failure(IllegalArgumentException(errorMsg))
        }
        
        return safeUpdate("max TTL") { preferences ->
            preferences.copy(
                maxTtl = ttl,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }.also { result ->
            if (result.isSuccess) {
                android.util.Log.i(TAG, "Max TTL updated successfully: $ttl")
            }
        }
    }
    
    /**
     * Update logging settings
     * 
     * @param enabled New logging enabled state
     * @return Result indicating success or failure
     */
    suspend fun setLoggingEnabled(enabled: Boolean): Result<Unit> {
        android.util.Log.d(TAG, "Setting logging enabled: $enabled")
        return safeUpdate("logging enabled") { preferences ->
            preferences.copy(
                enableLogging = enabled,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Update log level with validation
     * 
     * @param level New log level (must be one of: DEBUG, INFO, WARN, ERROR)
     * @return Result.success if valid, Result.failure if invalid
     */
    suspend fun setLogLevel(level: String): Result<Unit> {
        android.util.Log.d(TAG, "Setting log level: $level")
        val normalizedLevel = level.uppercase()
        
        if (normalizedLevel !in OfflineMessagingPreferences.VALID_LOG_LEVELS) {
            val errorMsg = "logLevel must be one of ${OfflineMessagingPreferences.VALID_LOG_LEVELS}, got: $level"
            android.util.Log.e(TAG, errorMsg)
            return Result.failure(IllegalArgumentException(errorMsg))
        }
        
        return safeUpdate("log level") { preferences ->
            preferences.copy(
                logLevel = normalizedLevel,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }.also { result ->
            if (result.isSuccess) {
                android.util.Log.i(TAG, "Log level updated successfully: $normalizedLevel")
            }
        }
    }
    
    /**
     * Reset all preferences to default values
     * 
     * @return Result.success if reset succeeded, Result.failure if error occurred
     */
    suspend fun resetToDefaults(): Result<Unit> {
        android.util.Log.w(TAG, "Resetting preferences to defaults")
        return safeUpdate("reset to defaults") {
            OfflineMessagingPreferences(
                lastUpdatedAt = System.currentTimeMillis()
            )
        }.also { result ->
            if (result.isSuccess) {
                android.util.Log.i(TAG, "Preferences reset to defaults successfully")
            }
        }
    }

    /**
     * Update multiple preferences atomically
     * 
     * All updates are applied in a single transaction. If any validation fails,
     * no changes are applied.
     * 
     * @param updates Lambda to apply updates to preferences
     * @return Result indicating success or failure
     */
    suspend fun updatePreferences(
        updates: OfflineMessagingPreferences.() -> OfflineMessagingPreferences
    ): Result<Unit> {
        return safeUpdate("batch update") { preferences ->
            try {
                preferences.updates().copy(
                    lastUpdatedAt = System.currentTimeMillis()
                )
            } catch (e: IllegalArgumentException) {
                android.util.Log.e(TAG, "Validation failed during batch update", e)
                throw e
            }
        }
    }

    /**
     * Update multiple preferences with builder pattern
     */
    @JvmName("updatePreferencesWithBuilder")
    suspend fun updatePreferences(
        builder: PreferencesUpdateBuilder.() -> Unit
    ): Result<Unit> {
        val updates = PreferencesUpdateBuilder().apply(builder)
        
        return safeUpdate("batch update") { preferences ->
            var updated = preferences
            
            updates.bleEnabled?.let { updated = updated.copy(isBleEnabled = it) }
            updates.wifiDirectEnabled?.let { updated = updated.copy(isWifiDirectEnabled = it) }
            updates.showNotification?.let { updated = updated.copy(showNotification = it) }
            updates.maxTtl?.let { 
                if (it in OfflineMessagingPreferences.MIN_TTL..OfflineMessagingPreferences.MAX_TTL) {
                    updated = updated.copy(maxTtl = it)
                } else {
                    throw IllegalArgumentException("Invalid maxTtl: $it")
                }
            }
            updates.loggingEnabled?.let { updated = updated.copy(enableLogging = it) }
            updates.logLevel?.let {
                val normalized = it.uppercase()
                if (normalized in OfflineMessagingPreferences.VALID_LOG_LEVELS) {
                    updated = updated.copy(logLevel = normalized)
                } else {
                    throw IllegalArgumentException("Invalid logLevel: $it")
                }
            }
            
            updated.copy(lastUpdatedAt = System.currentTimeMillis())
        }
    }
}

/**
 * Builder for batch preference updates
 */
class PreferencesUpdateBuilder {
    var bleEnabled: Boolean? = null
    var wifiDirectEnabled: Boolean? = null
    var showNotification: Boolean? = null
    var maxTtl: Int? = null
    var loggingEnabled: Boolean? = null
    var logLevel: String? = null
}
