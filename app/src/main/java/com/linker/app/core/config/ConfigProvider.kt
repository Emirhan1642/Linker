package com.linker.app.core.config

/**
 * Configuration provider interface for runtime configuration updates.
 * 
 * Enables dynamic configuration changes without app updates.
 * Supports A/B testing and remote configuration (e.g., Firebase Remote Config).
 * 
 * Addresses MEDIUM ISSUE #7: Remote Configuration Support
 * 
 * Usage:
 * ```
 * val provider = FirebaseRemoteConfigProvider()
 * OfflineMessagingConfig.initialize(provider)
 * ```
 */
interface ConfigProvider {
    
    /**
     * Get integer configuration value.
     * 
     * @param key Configuration key
     * @param default Default value if key not found
     * @return Configuration value or default
     */
    fun getInt(key: String, default: Int): Int
    
    /**
     * Get long configuration value.
     * 
     * @param key Configuration key
     * @param default Default value if key not found
     * @return Configuration value or default
     */
    fun getLong(key: String, default: Long): Long
    
    /**
     * Get double configuration value.
     * 
     * @param key Configuration key
     * @param default Default value if key not found
     * @return Configuration value or default
     */
    fun getDouble(key: String, default: Double): Double
    
    /**
     * Get boolean configuration value.
     * 
     * @param key Configuration key
     * @param default Default value if key not found
     * @return Configuration value or default
     */
    fun getBoolean(key: String, default: Boolean): Boolean
    
    /**
     * Get string configuration value.
     * 
     * @param key Configuration key
     * @param default Default value if key not found
     * @return Configuration value or default
     */
    fun getString(key: String, default: String): String
}
