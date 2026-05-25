package com.linker.app.data.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Connectivity monitoring error types
 */
sealed class ConnectivityError {
    data class PermissionDenied(val permission: String) : ConnectivityError()
    data class SystemError(val throwable: Throwable) : ConnectivityError()
    object MonitoringNotStarted : ConnectivityError()
}

/**
 * Network connection quality information
 */
data class ConnectionQuality(
    val downloadBandwidthKbps: Int,
    val uploadBandwidthKbps: Int,
    val signalStrength: Int? = null // 0-4 for cellular, null for others
)

/**
 * Connectivity monitoring metrics
 */
data class ConnectivityMetrics(
    val totalStateChanges: Int,
    val onlineTime: Long, // milliseconds
    val offlineTime: Long, // milliseconds
    val limitedTime: Long, // milliseconds
    val averageOnlineDuration: Long, // milliseconds
    val averageOfflineDuration: Long, // milliseconds
    val lastStateChange: Long // timestamp
)

/**
 * Interface for monitoring network connectivity state
 * 
 * Provides real-time updates on network availability and internet validation.
 * Used to determine whether to use online or offline delivery methods.
 * 
 * Lifecycle Management:
 * - Call startMonitoring() when monitoring is needed
 * - Call stopMonitoring() to cleanup resources
 * - Or use observeConnectivityState() with lifecycle-aware collectors
 */
interface ConnectivityMonitor {
    
    /**
     * Start monitoring network connectivity changes
     * Should be called from onStart() or when monitoring is needed
     */
    fun startMonitoring()
    
    /**
     * Stop monitoring network connectivity changes
     * Should be called from onStop() or when monitoring is no longer needed
     */
    fun stopMonitoring()
    
    /**
     * Check if monitoring is currently active
     * @return true if monitoring, false otherwise
     */
    fun isMonitoring(): Boolean
    
    /**
     * Get current connectivity state synchronously
     * @return Current connectivity state
     */
    fun getCurrentState(): ConnectivityState

    /**
     * Check if device currently has validated internet connection
     * @return true if online with validated internet, false otherwise
     */
    fun isOnline(): Boolean
    
    /**
     * Check if current connection is metered (e.g., mobile data)
     * @return true if connection is metered, false otherwise
     */
    fun isMetered(): Boolean
    
    /**
     * Observe connectivity state changes
     * @return Flow emitting connectivity state updates
     */
    fun observeConnectivityState(): Flow<ConnectivityState>
    
    /**
     * Observe connectivity errors
     * @return Flow emitting connectivity errors
     */
    fun observeErrors(): Flow<ConnectivityError>
    
    /**
     * Set error callback for synchronous error handling
     * @param callback Error callback function
     */
    fun setErrorCallback(callback: (ConnectivityError) -> Unit)
    
    /**
     * Get current connection quality
     * @return ConnectionQuality if available, null otherwise
     */
    fun getConnectionQuality(): ConnectionQuality?
    
    /**
     * Observe connection quality changes
     * @return Flow emitting connection quality updates
     */
    fun observeConnectionQuality(): Flow<ConnectionQuality?>
    
    /**
     * Check if connection quality is sufficient for given operation
     * @param minDownloadKbps Minimum required download bandwidth
     * @param minUploadKbps Minimum required upload bandwidth
     * @return true if quality is sufficient, false otherwise
     */
    fun hasMinimumQuality(minDownloadKbps: Int, minUploadKbps: Int): Boolean
    
    /**
     * Wait until device is online
     * Suspends until connectivity is established
     * @param timeoutMs Timeout in milliseconds, null for no timeout
     * @return true if online, false if timeout
     */
    suspend fun waitUntilOnline(timeoutMs: Long? = null): Boolean
    
    /**
     * Perform network check asynchronously
     * @return true if online, false otherwise
     */
    suspend fun checkConnectivity(): Boolean
    
    /**
     * Ping a specific host to validate connectivity
     * @param host Host to ping
     * @param timeoutMs Timeout in milliseconds
     * @return true if reachable, false otherwise
     */
    suspend fun pingHost(host: String, timeoutMs: Long = 5000): Boolean
    
    /**
     * Get current network transport type
     * @return ConnectionType if connected, UNKNOWN otherwise
     */
    fun getNetworkTransport(): ConnectionType
    
    /**
     * Check if connected via WiFi
     * @return true if WiFi, false otherwise
     */
    fun isWiFi(): Boolean
    
    /**
     * Check if connected via cellular
     * @return true if cellular, false otherwise
     */
    fun isCellular(): Boolean
    
    /**
     * Check if VPN is active
     * @return true if VPN active, false otherwise
     */
    fun isVpnActive(): Boolean
    
    /**
     * Get connectivity metrics
     * @return ConnectivityMetrics
     */
    fun getMetrics(): ConnectivityMetrics
    
    /**
     * Reset metrics
     */
    fun resetMetrics()
    
    /**
     * Observe metrics updates
     * @return Flow emitting metrics updates
     */
    fun observeMetrics(): Flow<ConnectivityMetrics>
}
