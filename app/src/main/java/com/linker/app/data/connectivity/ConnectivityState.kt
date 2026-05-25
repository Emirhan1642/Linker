package com.linker.app.data.connectivity

import kotlinx.serialization.Serializable

/**
 * Network connection type
 */
enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    UNKNOWN
}

/**
 * Represents the current network connectivity state with timestamp
 */
@Serializable
sealed class ConnectivityState {
    abstract val timestamp: Long

    /**
     * Device has internet connection and it's validated
     * @param connectionType Type of network connection
     * @param metered Whether the connection is metered
     */
    @Serializable
    data class Online(
        val connectionType: ConnectionType = ConnectionType.UNKNOWN,
        val metered: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ConnectivityState()
    
    /**
     * Device has no network connection
     */
    @Serializable
    data class Offline(
        override val timestamp: Long = System.currentTimeMillis()
    ) : ConnectivityState()
    
    /**
     * Device is connected to network but internet is not validated
     * @param connectionType Type of network connection
     * @param metered Whether the connection is metered (e.g., mobile data)
     */
    @Serializable
    data class Limited(
        val connectionType: ConnectionType = ConnectionType.UNKNOWN,
        val metered: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ConnectivityState()
    
    /**
     * Check if device has any network connection (validated or not)
     */
    fun hasConnection(): Boolean = when (this) {
        is Online -> true
        is Limited -> true
        is Offline -> false
    }
    
    /**
     * Check if device has validated internet connection
     */
    fun isOnline(): Boolean = this is Online
    
    /**
     * Check if device is offline
     */
    fun isOffline(): Boolean = this is Offline
    
    /**
     * Check if connection is limited (not validated)
     */
    fun isLimited(): Boolean = this is Limited
    
    /**
     * Check if current connection is metered
     */
    fun isMetered(): Boolean = when (this) {
        is Limited -> metered
        is Online -> metered
        is Offline -> false
    }
    
    /**
     * Get human-readable description of the state
     */
    fun getDescription(): String = when (this) {
        is Online -> "Connected to internet (${connectionType.name})"
        is Offline -> "No connection"
        is Limited -> if (isMetered()) {
            "Limited connection (metered, ${connectionType.name})"
        } else {
            "Limited connection (${connectionType.name})"
        }
    }
}
