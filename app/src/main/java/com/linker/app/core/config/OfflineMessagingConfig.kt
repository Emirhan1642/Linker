package com.linker.app.core.config

/**
 * Centralized configuration for offline messaging feature.
 * 
 * All hardcoded values are collected here for easy runtime configuration
 * and testing. Values can be overridden for different build variants or
 * loaded from remote config.
 * 
 * Addresses Issue #17 (P3): Create configuration class for hardcoded values
 */
object OfflineMessagingConfig {
    
    // ========== BLE Configuration ==========
    
    /**
     * Maximum concurrent BLE GATT connections (Android BLE limit)
     */
    const val MAX_BLE_CONNECTIONS = 7
    
    /**
     * BLE GATT connection timeout in milliseconds
     * Per Requirement 1.4: 5 seconds
     */
    const val BLE_CONNECTION_TIMEOUT_MS = 5000L
    
    /**
     * BLE MTU (Maximum Transmission Unit) size in bytes
     */
    const val BLE_MTU_SIZE = 512
    
    /**
     * BLE scan cooldown period in milliseconds
     * Prevents duplicate scan processing
     */
    const val BLE_SCAN_COOLDOWN_MS = 2000L
    
    /**
     * BLE route staleness threshold in milliseconds
     * Routes older than this are considered stale
     */
    const val BLE_ROUTE_STALENESS_MS = 60_000L // 60 seconds
    
    /**
     * BLE node cleanup interval in milliseconds
     * How often to remove stale nodes from routing table
     */
    const val BLE_NODE_CLEANUP_INTERVAL_MS = 60_000L // 60 seconds
    
    // ========== Message Queue Configuration ==========
    
    /**
     * Default TTL (Time-To-Live) for BLE messages in hops
     */
    const val DEFAULT_MESSAGE_TTL: Byte = 5
    
    /**
     * Maximum queue size (number of messages)
     * When exceeded, oldest SENT messages are removed
     */
    const val MAX_QUEUE_SIZE = 1000
    
    /**
     * Message deduplication window in milliseconds
     * Prevents same message from being processed twice when arriving via BLE and online
     */
    const val MESSAGE_DEDUP_WINDOW_MS = 60_000L // 60 seconds
    
    // ========== Retry Configuration ==========
    
    /**
     * Initial retry delay in milliseconds
     * Per Requirement 14.2: 5 seconds
     */
    const val RETRY_INITIAL_DELAY_MS = 5000L
    
    /**
     * Retry backoff multiplier
     * Each retry delay is multiplied by this factor
     * Delays: 5s, 15s, 45s
     */
    const val RETRY_BACKOFF_MULTIPLIER = 3.0
    
    /**
     * Maximum number of retry attempts
     */
    const val MAX_RETRY_ATTEMPTS = 3
    
    // ========== Batching Configuration ==========
    
    /**
     * Message batch size
     * Number of messages to accumulate before sending
     */
    const val MESSAGE_BATCH_SIZE = 5
    
    /**
     * Message batch timeout in milliseconds
     * Flush batch after this time even if not full
     */
    const val MESSAGE_BATCH_TIMEOUT_MS = 5000L
    
    // ========== Fragment Configuration ==========
    
    /**
     * Fragment reassembly timeout in milliseconds
     * Per Requirement 16.5: 30 seconds
     */
    const val FRAGMENT_TIMEOUT_MS = 30_000L
    
    /**
     * Fragment cleanup interval in milliseconds
     * How often to check for stale fragments
     */
    const val FRAGMENT_CLEANUP_INTERVAL_MS = 60_000L
    
    // ========== Sync Configuration ==========
    
    /**
     * Sync rate limit delay in milliseconds
     * Delay between sending messages to respect rate limits
     * Per Requirement 7.6: 10 messages per second = 100ms delay
     */
    const val SYNC_RATE_LIMIT_DELAY_MS = 100L
    
    /**
     * Sync cleanup age in milliseconds
     * SENT queue items older than this are deleted
     * Per Requirement 7.8: 7 days
     */
    const val SYNC_CLEANUP_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    // ========== Wi-Fi Direct Configuration ==========
    
    /**
     * Wi-Fi Direct (Nearby Connections) service ID
     */
    const val WIFI_DIRECT_SERVICE_ID = "com.linker.app.OFFLINE_MESSAGING"
    
    /**
     * Wi-Fi Direct connection timeout in milliseconds
     */
    const val WIFI_DIRECT_CONNECTION_TIMEOUT_MS = 10_000L
    
    /**
     * Wi-Fi Direct discovery timeout in milliseconds
     * How long to wait for recipient to be discovered
     */
    const val WIFI_DIRECT_DISCOVERY_TIMEOUT_MS = 10_000L
    
    /**
     * Wi-Fi Direct maximum retry attempts
     */
    const val WIFI_DIRECT_MAX_RETRIES = 3
    
    /**
     * Wi-Fi Direct media size threshold in bytes
     * Media larger than this uses Wi-Fi Direct instead of BLE
     */
    const val WIFI_DIRECT_MEDIA_THRESHOLD_BYTES = 5 * 1024 * 1024 // 5MB
    
    // ========== Encryption Configuration ==========
    
    /**
     * Encryption key rotation interval in days
     * Per Requirement 6.6: 30 days
     */
    const val KEY_ROTATION_INTERVAL_DAYS = 30
    
    /**
     * Number of Signal Protocol one-time pre-keys to generate
     */
    const val SIGNAL_PREKEY_COUNT = 100
    
    // ========== Battery Optimization Configuration ==========
    
    /**
     * Battery level threshold for low power mode (percentage)
     * Below this, use low power scanning
     */
    const val BATTERY_LOW_THRESHOLD_PERCENT = 20
    
    /**
     * Battery level threshold for critical warning (percentage)
     * Show warning to user below this level
     */
    const val BATTERY_CRITICAL_THRESHOLD_PERCENT = 15
    
    /**
     * Scan interval when screen is off (milliseconds)
     */
    const val SCAN_INTERVAL_SCREEN_OFF_MS = 30_000L // 30 seconds
    
    /**
     * Scan interval when battery is low (milliseconds)
     */
    const val SCAN_INTERVAL_LOW_BATTERY_MS = 60_000L // 60 seconds
    
    // ========== Cache Configuration ==========
    
    /**
     * BLE routing table cache size (number of routes)
     */
    const val ROUTING_TABLE_CACHE_SIZE = 100
    
    /**
     * Message ID cache size (number of message IDs)
     * For BLE packet deduplication
     */
    const val MESSAGE_ID_CACHE_SIZE = 10_000
    
    /**
     * Message ID cache retention time in milliseconds
     * Per Requirement 13.1: 24 hours
     */
    const val MESSAGE_ID_CACHE_RETENTION_MS = 24 * 60 * 60 * 1000L // 24 hours
    
    // ========== Performance Metrics Configuration ==========
    
    /**
     * Enable performance metrics collection
     * Set to false in production for better performance
     */
    var ENABLE_PERFORMANCE_METRICS = true
    
    /**
     * Performance metrics sample rate (0.0 to 1.0)
     * 1.0 = collect all metrics, 0.1 = collect 10% of metrics
     */
    var PERFORMANCE_METRICS_SAMPLE_RATE = 1.0
    
    // ========== Logging Configuration ==========
    
    /**
     * Enable sensitive data logging
     * MUST be false in production builds
     * Per Issue #41 (P3): Disable sensitive data logging in production
     */
    var ENABLE_SENSITIVE_LOGGING = false
    
    /**
     * Enable verbose BLE logging
     * Useful for debugging but impacts performance
     */
    var ENABLE_VERBOSE_BLE_LOGGING = false
}
