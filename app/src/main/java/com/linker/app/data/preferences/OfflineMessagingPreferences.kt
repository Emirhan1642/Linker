package com.linker.app.data.preferences

import kotlinx.serialization.Serializable

/**
 * Offline messaging preferences stored in DataStore
 * 
 * Implements Requirements 15.1-15.9:
 * - Persistent settings storage
 * - Type-safe preferences
 * - Automatic serialization/deserialization
 */
@Serializable
data class OfflineMessagingPreferences(
    // BLE Settings
    val isBleEnabled: Boolean = true,
    val maxTtl: Int = 5,
    
    // Wi-Fi Direct Settings
    val isWifiDirectEnabled: Boolean = true,
    
    // Notification Settings
    val showNotification: Boolean = true,
    
    // Advanced Settings
    val enableLogging: Boolean = false,
    val logLevel: String = "INFO", // DEBUG, INFO, WARN, ERROR
    
    // Last Updated
    val lastUpdatedAt: Long = 0L
)
