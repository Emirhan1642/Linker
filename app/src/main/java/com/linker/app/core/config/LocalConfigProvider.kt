package com.linker.app.core.config

/**
 * Local configuration provider for testing and development.
 * 
 * Stores configuration values in memory.
 * Useful for unit tests and local development.
 * 
 * Usage:
 * ```
 * val provider = LocalConfigProvider()
 * provider.setInt("max_ble_connections", 5)
 * OfflineMessagingConfig.initialize(provider)
 * ```
 */
class LocalConfigProvider : ConfigProvider {
    
    private val intValues = mutableMapOf<String, Int>()
    private val longValues = mutableMapOf<String, Long>()
    private val doubleValues = mutableMapOf<String, Double>()
    private val booleanValues = mutableMapOf<String, Boolean>()
    private val stringValues = mutableMapOf<String, String>()
    
    override fun getInt(key: String, default: Int): Int {
        return intValues[key] ?: default
    }
    
    override fun getLong(key: String, default: Long): Long {
        return longValues[key] ?: default
    }
    
    override fun getDouble(key: String, default: Double): Double {
        return doubleValues[key] ?: default
    }
    
    override fun getBoolean(key: String, default: Boolean): Boolean {
        return booleanValues[key] ?: default
    }
    
    override fun getString(key: String, default: String): String {
        return stringValues[key] ?: default
    }
    
    /**
     * Set integer configuration value.
     */
    fun setInt(key: String, value: Int) {
        intValues[key] = value
    }
    
    /**
     * Set long configuration value.
     */
    fun setLong(key: String, value: Long) {
        longValues[key] = value
    }
    
    /**
     * Set double configuration value.
     */
    fun setDouble(key: String, value: Double) {
        doubleValues[key] = value
    }
    
    /**
     * Set boolean configuration value.
     */
    fun setBoolean(key: String, value: Boolean) {
        booleanValues[key] = value
    }
    
    /**
     * Set string configuration value.
     */
    fun setString(key: String, value: String) {
        stringValues[key] = value
    }
    
    /**
     * Clear all configuration values.
     */
    fun clear() {
        intValues.clear()
        longValues.clear()
        doubleValues.clear()
        booleanValues.clear()
        stringValues.clear()
    }
    
    /**
     * Get all configuration keys.
     */
    fun getAllKeys(): Set<String> {
        return intValues.keys + longValues.keys + doubleValues.keys + 
               booleanValues.keys + stringValues.keys
    }
}
