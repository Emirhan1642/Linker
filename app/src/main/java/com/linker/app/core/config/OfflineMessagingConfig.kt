package com.linker.app.core.config

import com.linker.app.BuildConfig
import com.linker.app.core.security.SecurityLogger

/**
 * Centralized configuration for offline messaging feature.
 * 
 * All hardcoded values are collected here for easy runtime configuration
 * and testing. Values can be overridden for different build variants or
 * loaded from remote config.
 * 
 * Supports both compile-time defaults and runtime configuration via ConfigProvider.
 * Runtime values take precedence over compile-time defaults when provider is initialized.
 * 
 * Usage:
 * ```
 * // Without remote config (uses compile-time defaults)
 * val timeout = OfflineMessagingConfig.BLE_CONNECTION_TIMEOUT_MS
 * 
 * // With remote config
 * val provider = FirebaseRemoteConfigProvider()
 * provider.initialize()
 * OfflineMessagingConfig.initialize(provider)
 * val timeout = OfflineMessagingConfig.getBleConnectionTimeout()
 * ```
 * 
 * Addresses Issue #17 (P3): Create configuration class for hardcoded values
 * Addresses MEDIUM ISSUE #7: Remote Configuration Support
 */
object OfflineMessagingConfig {
    
    private var configProvider: ConfigProvider? = null
    
    /**
     * Initialize with remote config provider (e.g., Firebase Remote Config).
     * 
     * After initialization, use getter methods (e.g., getBleConnectionTimeout())
     * to get runtime-configurable values.
     * 
     * @param provider Configuration provider
     */
    fun initialize(provider: ConfigProvider) {
        configProvider = provider
    }
    
    /**
     * Check if remote config provider is initialized.
     */
    fun isRemoteConfigEnabled(): Boolean = configProvider != null
    
    // ========== BLE Configuration ==========
    
    /**
     * Maximum concurrent BLE GATT connections (Android BLE limit)
     */
    const val MAX_BLE_CONNECTIONS = 7
    
    /**
     * BLE GATT connection timeout in milliseconds
     * Adjusted from 5s to 15s based on real-world BLE performance
     * 
     * Per Requirement 1.4: Updated to account for:
     * - Crowded BLE environments (multiple devices)
     * - Poor signal conditions
     * - Android BLE stack latency
     */
    const val BLE_CONNECTION_TIMEOUT_MS = 15_000L // 15 seconds
    
    /**
     * BLE connection timeout for retry attempts
     * Shorter timeout for subsequent attempts
     */
    const val BLE_CONNECTION_RETRY_TIMEOUT_MS = 8_000L // 8 seconds
    
    /**
     * BLE MTU (Maximum Transmission Unit) size in bytes
     * 
     * Performance Impact:
     * - Larger MTU = fewer packets, better throughput
     * - Not all devices support 512 bytes (some limited to 185)
     * - Negotiation happens at connection time
     * - Fallback to 23 bytes if negotiation fails
     * 
     * Battery Impact: Minimal (affects packet count, not radio time)
     * Memory Impact: 512 bytes per connection * MAX_BLE_CONNECTIONS = ~3.5KB
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
     * Maximum queue memory size in bytes (50MB)
     * Prevents OOM on low-end devices
     */
    const val MAX_QUEUE_MEMORY_BYTES = 50 * 1024 * 1024L // 50MB
    
    /**
     * Average message size estimate for memory calculations (bytes)
     * Used when actual size is unknown
     */
    const val ESTIMATED_MESSAGE_SIZE_BYTES = 10 * 1024 // 10KB
    
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
     * Increased from 30s to 20 minutes for large media files
     * 
     * Per Requirement 16.5: Updated to support:
     * - Large media files (up to 100MB)
     * - Slow BLE transfer rates (~10-20 KB/s)
     * - Network interruptions and retries
     */
    const val FRAGMENT_TIMEOUT_MS = 20 * 60 * 1000L // 20 minutes
    
    /**
     * Fragment timeout for small messages (<1MB)
     * Shorter timeout for text and small media
     */
    const val FRAGMENT_TIMEOUT_SMALL_MS = 2 * 60 * 1000L // 2 minutes
    
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
     * Wi-Fi Direct (Nearby Connections) service ID prefix
     * Combined with app signature verification for security
     * Format: package_name.FEATURE.signature_hash
     * 
     * In actual implementation, append signature hash:
     * val serviceId = "$WIFI_DIRECT_SERVICE_ID_PREFIX.${getAppSignatureHash()}"
     */
    const val WIFI_DIRECT_SERVICE_ID_PREFIX = "com.linker.app.OFFLINE_MESSAGING"
    
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
    
    // ========== Network Configuration ==========
    
    /**
     * Network connect timeout in milliseconds
     * How long to wait for TCP connection establishment
     */
    const val NETWORK_CONNECT_TIMEOUT_MS = 30_000L // 30 seconds
    
    /**
     * Network read timeout in milliseconds
     * How long to wait for data from server
     */
    const val NETWORK_READ_TIMEOUT_MS = 30_000L // 30 seconds
    
    /**
     * Network write timeout in milliseconds
     * How long to wait for data to be sent to server
     */
    const val NETWORK_WRITE_TIMEOUT_MS = 30_000L // 30 seconds
    
    // ========== Encryption Configuration ==========
    
    /**
     * Encryption key rotation interval in milliseconds
     * Per Requirement 6.6: 30 days
     */
    const val KEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    
    // Time unit helper constants
    private const val MILLIS_PER_SECOND = 1000L
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
    
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
     * Automatically disabled in production builds
     */
    val ENABLE_PERFORMANCE_METRICS = BuildConfig.DEBUG
    
    /**
     * Performance metrics sample rate (0.0 to 1.0)
     * 1.0 = collect all metrics, 0.1 = collect 10% of metrics
     */
    val PERFORMANCE_METRICS_SAMPLE_RATE = if (BuildConfig.DEBUG) 1.0 else 0.1
    
    // ========== Logging Configuration ==========
    
    /**
     * Enable sensitive data logging
     * MUST be false in production builds - enforced at compile time
     * Per Issue #41 (P3): Disable sensitive data logging in production
     * 
     * Addresses SECURITY RECOMMENDATION #2: Audit Logging for Configuration Access
     */
    val ENABLE_SENSITIVE_LOGGING: Boolean
        get() {
            val value = BuildConfig.DEBUG && BuildConfig.ENABLE_SENSITIVE_LOGS
            if (value) {
                SecurityLogger.logConfigAccess("ENABLE_SENSITIVE_LOGGING", value)
            }
            return value
        }
    
    /**
     * Enable verbose BLE logging
     * Useful for debugging but impacts performance
     */
    val ENABLE_VERBOSE_BLE_LOGGING = BuildConfig.DEBUG
    
    // ========== Error Handling Configuration ==========
    
    /**
     * Maximum consecutive connection failures before circuit breaker opens
     */
    const val MAX_CONSECUTIVE_FAILURES = 5
    
    /**
     * Circuit breaker open duration in milliseconds
     * How long to wait before attempting connections again after repeated failures
     */
    const val CIRCUIT_BREAKER_OPEN_DURATION_MS = 60_000L // 60 seconds
    
    /**
     * Maximum error log entries to keep in memory
     */
    const val MAX_ERROR_LOG_ENTRIES = 100
    
    /**
     * Error log retention time in milliseconds
     */
    const val ERROR_LOG_RETENTION_MS = 24 * 60 * 60 * 1000L // 24 hours
    
    /**
     * Failure rate threshold for alerting (percentage)
     * Alert if failure rate exceeds this value
     */
    const val FAILURE_RATE_ALERT_THRESHOLD_PERCENT = 50
    
    // ========== Runtime Configuration Getters ==========
    // These methods support remote configuration when provider is initialized
    
    /**
     * Get BLE connection timeout with remote config support.
     * Falls back to BLE_CONNECTION_TIMEOUT_MS if provider not initialized.
     */
    fun getBleConnectionTimeout(): Long {
        return configProvider?.getLong("ble_connection_timeout_ms", BLE_CONNECTION_TIMEOUT_MS)
            ?: BLE_CONNECTION_TIMEOUT_MS
    }
    
    /**
     * Get BLE connection retry timeout with remote config support.
     * Falls back to BLE_CONNECTION_RETRY_TIMEOUT_MS if provider not initialized.
     */
    fun getBleConnectionRetryTimeout(): Long {
        return configProvider?.getLong("ble_connection_retry_timeout_ms", BLE_CONNECTION_RETRY_TIMEOUT_MS)
            ?: BLE_CONNECTION_RETRY_TIMEOUT_MS
    }
    
    /**
     * Get max BLE connections with remote config support.
     * Falls back to MAX_BLE_CONNECTIONS if provider not initialized.
     */
    fun getMaxBleConnections(): Int {
        return configProvider?.getInt("max_ble_connections", MAX_BLE_CONNECTIONS)
            ?: MAX_BLE_CONNECTIONS
    }
    
    /**
     * Get max queue size with remote config support.
     * Falls back to MAX_QUEUE_SIZE if provider not initialized.
     */
    fun getMaxQueueSize(): Int {
        return configProvider?.getInt("max_queue_size", MAX_QUEUE_SIZE)
            ?: MAX_QUEUE_SIZE
    }
    
    /**
     * Get max queue memory bytes with remote config support.
     * Falls back to MAX_QUEUE_MEMORY_BYTES if provider not initialized.
     */
    fun getMaxQueueMemoryBytes(): Long {
        return configProvider?.getLong("max_queue_memory_bytes", MAX_QUEUE_MEMORY_BYTES)
            ?: MAX_QUEUE_MEMORY_BYTES
    }
    
    /**
     * Get retry initial delay with remote config support.
     * Falls back to RETRY_INITIAL_DELAY_MS if provider not initialized.
     */
    fun getRetryInitialDelay(): Long {
        return configProvider?.getLong("retry_initial_delay_ms", RETRY_INITIAL_DELAY_MS)
            ?: RETRY_INITIAL_DELAY_MS
    }
    
    /**
     * Get retry backoff multiplier with remote config support.
     * Falls back to RETRY_BACKOFF_MULTIPLIER if provider not initialized.
     */
    fun getRetryBackoffMultiplier(): Double {
        return configProvider?.getDouble("retry_backoff_multiplier", RETRY_BACKOFF_MULTIPLIER)
            ?: RETRY_BACKOFF_MULTIPLIER
    }
    
    /**
     * Get max retry attempts with remote config support.
     * Falls back to MAX_RETRY_ATTEMPTS if provider not initialized.
     */
    fun getMaxRetryAttempts(): Int {
        return configProvider?.getInt("max_retry_attempts", MAX_RETRY_ATTEMPTS)
            ?: MAX_RETRY_ATTEMPTS
    }
    
    /**
     * Get fragment timeout with remote config support.
     * Falls back to FRAGMENT_TIMEOUT_MS if provider not initialized.
     */
    fun getFragmentTimeout(): Long {
        return configProvider?.getLong("fragment_timeout_ms", FRAGMENT_TIMEOUT_MS)
            ?: FRAGMENT_TIMEOUT_MS
    }
    
    /**
     * Get fragment timeout for small messages with remote config support.
     * Falls back to FRAGMENT_TIMEOUT_SMALL_MS if provider not initialized.
     */
    fun getFragmentTimeoutSmall(): Long {
        return configProvider?.getLong("fragment_timeout_small_ms", FRAGMENT_TIMEOUT_SMALL_MS)
            ?: FRAGMENT_TIMEOUT_SMALL_MS
    }
    
    /**
     * Get max consecutive failures with remote config support.
     * Falls back to MAX_CONSECUTIVE_FAILURES if provider not initialized.
     */
    fun getMaxConsecutiveFailures(): Int {
        return configProvider?.getInt("max_consecutive_failures", MAX_CONSECUTIVE_FAILURES)
            ?: MAX_CONSECUTIVE_FAILURES
    }
    
    /**
     * Get circuit breaker open duration with remote config support.
     * Falls back to CIRCUIT_BREAKER_OPEN_DURATION_MS if provider not initialized.
     */
    fun getCircuitBreakerOpenDuration(): Long {
        return configProvider?.getLong("circuit_breaker_open_duration_ms", CIRCUIT_BREAKER_OPEN_DURATION_MS)
            ?: CIRCUIT_BREAKER_OPEN_DURATION_MS
    }
    
    // ========== Configuration Validation ==========
    
    init {
        require(MAX_BLE_CONNECTIONS in 1..7) {
            "MAX_BLE_CONNECTIONS must be between 1 and 7 (Android BLE limit)"
        }
        
        require(BLE_CONNECTION_TIMEOUT_MS > 0) {
            "BLE_CONNECTION_TIMEOUT_MS must be positive"
        }
        
        require(MAX_QUEUE_SIZE > 0) {
            "MAX_QUEUE_SIZE must be positive"
        }
        
        require(RETRY_BACKOFF_MULTIPLIER > 1.0) {
            "RETRY_BACKOFF_MULTIPLIER must be greater than 1.0"
        }
        
        require(MAX_RETRY_ATTEMPTS > 0) {
            "MAX_RETRY_ATTEMPTS must be positive"
        }
        
        require(BATTERY_CRITICAL_THRESHOLD_PERCENT < BATTERY_LOW_THRESHOLD_PERCENT) {
            "BATTERY_CRITICAL_THRESHOLD_PERCENT must be less than BATTERY_LOW_THRESHOLD_PERCENT"
        }
        
        require(PERFORMANCE_METRICS_SAMPLE_RATE in 0.0..1.0) {
            "PERFORMANCE_METRICS_SAMPLE_RATE must be between 0.0 and 1.0"
        }
        
        require(FRAGMENT_TIMEOUT_SMALL_MS < FRAGMENT_TIMEOUT_MS) {
            "FRAGMENT_TIMEOUT_SMALL_MS must be less than FRAGMENT_TIMEOUT_MS"
        }
    }
}
