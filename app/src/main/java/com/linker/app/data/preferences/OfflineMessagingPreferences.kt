package com.linker.app.data.preferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Log level enum for type-safe logging configuration
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;
    
    companion object {
        /**
         * Parse log level from string, returns INFO if invalid
         */
        fun fromString(value: String): LogLevel {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                INFO // Default to INFO if invalid
            }
        }
    }
}

/**
 * Offline messaging preferences stored in DataStore
 * 
 * This data class holds all user-configurable settings for offline messaging
 * features including BLE mesh networking, Wi-Fi Direct, and notifications.
 * 
 * Implements Requirements 15.1-15.9:
 * - Persistent settings storage using DataStore
 * - Type-safe preferences with kotlinx.serialization
 * - Automatic serialization/deserialization
 * - Input validation for data integrity
 * 
 * @property isBleEnabled Enable/disable BLE mesh networking. When disabled,
 *           the app will not scan for or advertise BLE devices. Default: true
 * 
 * @property maxTtl Maximum Time To Live (TTL) for messages in the mesh network.
 *           Represents the maximum number of hops a message can travel.
 *           Valid range: 1-10. Default: 5
 *           - Lower values: Faster delivery, less network coverage
 *           - Higher values: Slower delivery, more network coverage
 * 
 * @property isWifiDirectEnabled Enable/disable Wi-Fi Direct (Nearby Connections).
 *           When disabled, the app will not use Wi-Fi Direct for file transfers.
 *           Default: true
 * 
 * @property showNotification Show/hide persistent notification for offline messaging
 *           service. Required for foreground service on Android 8+. Default: true
 * 
 * @property enableLogging Enable/disable debug logging for offline messaging.
 *           When enabled, detailed logs will be written for debugging purposes.
 *           Default: false (disabled in production)
 * 
 * @property logLevel Logging verbosity level. Valid values: DEBUG, INFO, WARN, ERROR
 *           - DEBUG: Most verbose, includes all logs
 *           - INFO: General information logs
 *           - WARN: Warning messages only
 *           - ERROR: Error messages only
 *           Default: INFO
 * 
 * @property lastUpdatedAt Timestamp (milliseconds) of last preference update.
 *           Used for tracking changes and synchronization. Default: 0 (never updated)
 * 
 * @throws IllegalArgumentException if maxTtl is not in range 1-10
 * @throws IllegalArgumentException if logLevel is not a valid value
 * 
 * @see OfflineMessagingPreferencesRepository for reading/writing preferences
 * @see OfflineMessagingPreferencesSerializer for serialization logic
 */
@Serializable
data class OfflineMessagingPreferences(
    @SerialName("ble_enabled")
    val isBleEnabled: Boolean = true,
    
    @SerialName("max_ttl")
    val maxTtl: Int = DEFAULT_TTL,
    
    @SerialName("wifi_direct_enabled")
    val isWifiDirectEnabled: Boolean = true,
    
    @SerialName("show_notification")
    val showNotification: Boolean = true,
    
    @SerialName("enable_logging")
    val enableLogging: Boolean = false,
    
    @SerialName("log_level")
    val logLevel: String = "INFO",
    
    @SerialName("last_updated_at")
    val lastUpdatedAt: Long = 0L
) {
    init {
        require(maxTtl in MIN_TTL..MAX_TTL) {
            "maxTtl must be between $MIN_TTL and $MAX_TTL, got: $maxTtl"
        }
        require(logLevel in VALID_LOG_LEVELS) {
            "logLevel must be one of $VALID_LOG_LEVELS, got: $logLevel"
        }
    }
    
    companion object {
        val VALID_LOG_LEVELS = setOf("DEBUG", "INFO", "WARN", "ERROR")
        const val MIN_TTL = 1
        const val MAX_TTL = 10
        const val DEFAULT_TTL = 5
        
        /**
         * Create preferences with validated values
         * Clamps values to valid ranges instead of throwing exceptions
         */
        fun createSafe(
            isBleEnabled: Boolean = true,
            maxTtl: Int = DEFAULT_TTL,
            isWifiDirectEnabled: Boolean = true,
            showNotification: Boolean = true,
            enableLogging: Boolean = false,
            logLevel: String = "INFO",
            lastUpdatedAt: Long = 0L
        ): OfflineMessagingPreferences {
            return OfflineMessagingPreferences(
                isBleEnabled = isBleEnabled,
                maxTtl = maxTtl.coerceIn(MIN_TTL, MAX_TTL),
                isWifiDirectEnabled = isWifiDirectEnabled,
                showNotification = showNotification,
                enableLogging = enableLogging,
                logLevel = if (logLevel in VALID_LOG_LEVELS) logLevel else "INFO",
                lastUpdatedAt = lastUpdatedAt
            )
        }
    }
    
    /**
     * Get log level as enum
     */
    fun getLogLevelEnum(): LogLevel {
        return LogLevel.fromString(logLevel)
    }
    
    /**
     * Check if any offline messaging feature is enabled
     */
    fun isAnyFeatureEnabled(): Boolean {
        return isBleEnabled || isWifiDirectEnabled
    }
    
    /**
     * Check if all offline messaging features are enabled
     */
    fun areAllFeaturesEnabled(): Boolean {
        return isBleEnabled && isWifiDirectEnabled
    }
    
    /**
     * Validate TTL value
     */
    fun isValidTtl(ttl: Int): Boolean {
        return ttl in MIN_TTL..MAX_TTL
    }
    
    /**
     * Get human-readable summary of preferences
     */
    fun toSummaryString(): String {
        return buildString {
            appendLine("Offline Messaging Preferences:")
            appendLine("  BLE: ${if (isBleEnabled) "Enabled" else "Disabled"}")
            appendLine("  Wi-Fi Direct: ${if (isWifiDirectEnabled) "Enabled" else "Disabled"}")
            appendLine("  Max TTL: $maxTtl hops")
            appendLine("  Notifications: ${if (showNotification) "Shown" else "Hidden"}")
            appendLine("  Logging: ${if (enableLogging) "Enabled ($logLevel)" else "Disabled"}")
            appendLine("  Last Updated: ${if (lastUpdatedAt > 0) java.util.Date(lastUpdatedAt) else "Never"}")
        }
    }
    
    /**
     * Copy with validated maxTtl
     */
    fun copyWithValidatedTtl(newTtl: Int): OfflineMessagingPreferences {
        return copy(
            maxTtl = newTtl.coerceIn(MIN_TTL, MAX_TTL),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Copy with validated logLevel
     */
    fun copyWithValidatedLogLevel(newLogLevel: String): OfflineMessagingPreferences {
        return copy(
            logLevel = if (newLogLevel in VALID_LOG_LEVELS) newLogLevel else "INFO",
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
}
