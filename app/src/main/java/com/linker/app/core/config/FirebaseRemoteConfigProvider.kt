package com.linker.app.core.config

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Firebase Remote Config provider for runtime configuration updates.
 * 
 * Enables dynamic configuration changes without app updates.
 * Supports A/B testing and gradual rollouts.
 * 
 * Usage:
 * ```
 * val provider = FirebaseRemoteConfigProvider()
 * provider.initialize()
 * OfflineMessagingConfig.initialize(provider)
 * ```
 * 
 * Configuration Keys (examples):
 * - max_ble_connections: Int
 * - ble_connection_timeout_ms: Long
 * - performance_metrics_sample_rate: Double
 * - enable_performance_metrics: Boolean
 */
class FirebaseRemoteConfigProvider : ConfigProvider {
    
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    
    companion object {
        private const val TAG = "FirebaseRemoteConfigProvider"
        
        /**
         * Minimum fetch interval in seconds (1 hour in production)
         * Set to 0 for development to fetch immediately
         */
        private const val FETCH_INTERVAL_SECONDS = 3600L // 1 hour
        
        /**
         * Fetch timeout in seconds
         */
        private const val FETCH_TIMEOUT_SECONDS = 60L
    }
    
    /**
     * Initialize Firebase Remote Config.
     * 
     * Sets up fetch interval and default values.
     * Call this before using the provider.
     * 
     * @param fetchIntervalSeconds Custom fetch interval (optional)
     */
    fun initialize(fetchIntervalSeconds: Long = FETCH_INTERVAL_SECONDS) {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(fetchIntervalSeconds)
            .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
            .build()
        
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values from OfflineMessagingConfig
        val defaults = mapOf(
            "max_ble_connections" to 7,
            "ble_connection_timeout_ms" to 15000L,
            "ble_connection_retry_timeout_ms" to 8000L,
            "max_queue_size" to 1000,
            "max_queue_memory_bytes" to (50 * 1024 * 1024L),
            "retry_initial_delay_ms" to 5000L,
            "retry_backoff_multiplier" to 3.0,
            "max_retry_attempts" to 3,
            "fragment_timeout_ms" to (20 * 60 * 1000L),
            "fragment_timeout_small_ms" to (2 * 60 * 1000L),
            "max_consecutive_failures" to 5,
            "circuit_breaker_open_duration_ms" to 60000L
        )
        
        remoteConfig.setDefaultsAsync(defaults)
        
        Log.d(TAG, "Firebase Remote Config initialized with fetch interval: ${fetchIntervalSeconds}s")
    }
    
    /**
     * Fetch and activate remote config values.
     * 
     * Call this periodically to get latest configuration.
     * 
     * @param onComplete Callback with success status
     */
    fun fetchAndActivate(onComplete: (Boolean) -> Unit = {}) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote config fetched and activated. Updated: $updated")
                    onComplete(true)
                } else {
                    Log.e(TAG, "Remote config fetch failed", task.exception)
                    onComplete(false)
                }
            }
    }
    
    override fun getInt(key: String, default: Int): Int {
        return try {
            val value = remoteConfig.getLong(key).toInt()
            Log.d(TAG, "getInt($key) = $value (from remote config)")
            value
        } catch (e: Exception) {
            Log.d(TAG, "getInt($key) = $default (using default, error: ${e.message})")
            default
        }
    }
    
    override fun getLong(key: String, default: Long): Long {
        return try {
            val value = remoteConfig.getLong(key)
            Log.d(TAG, "getLong($key) = $value (from remote config)")
            value
        } catch (e: Exception) {
            Log.d(TAG, "getLong($key) = $default (using default, error: ${e.message})")
            default
        }
    }
    
    override fun getDouble(key: String, default: Double): Double {
        return try {
            val value = remoteConfig.getDouble(key)
            Log.d(TAG, "getDouble($key) = $value (from remote config)")
            value
        } catch (e: Exception) {
            Log.d(TAG, "getDouble($key) = $default (using default, error: ${e.message})")
            default
        }
    }
    
    override fun getBoolean(key: String, default: Boolean): Boolean {
        return try {
            val value = remoteConfig.getBoolean(key)
            Log.d(TAG, "getBoolean($key) = $value (from remote config)")
            value
        } catch (e: Exception) {
            Log.d(TAG, "getBoolean($key) = $default (using default, error: ${e.message})")
            default
        }
    }
    
    override fun getString(key: String, default: String): String {
        return try {
            val value = remoteConfig.getString(key)
            if (value.isNotEmpty()) {
                Log.d(TAG, "getString($key) = $value (from remote config)")
                value
            } else {
                Log.d(TAG, "getString($key) = $default (using default, remote value empty)")
                default
            }
        } catch (e: Exception) {
            Log.d(TAG, "getString($key) = $default (using default, error: ${e.message})")
            default
        }
    }
    
    /**
     * Get all remote config keys.
     */
    fun getAllKeys(): Set<String> {
        return remoteConfig.all.keys
    }
    
    /**
     * Get remote config info (last fetch time, status, etc.)
     */
    fun getInfo(): String {
        val info = remoteConfig.info
        return """
            Last Fetch Status: ${info.lastFetchStatus}
            Last Fetch Time: ${info.fetchTimeMillis}
            Config Settings: ${info.configSettings}
        """.trimIndent()
    }
}
