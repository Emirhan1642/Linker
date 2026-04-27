package com.linker.app.data.ble

import android.bluetooth.BluetoothGatt
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection pool for managing BLE GATT connections.
 * 
 * Implements Requirements 12.3-12.5:
 * - Maximum 7 concurrent connections (Android BLE limit)
 * - Priority-based eviction when pool is full
 * - Connection quality tracking
 */
@Singleton
class BLEConnectionPool @Inject constructor() {
    
    private val connections = ConcurrentHashMap<String, ConnectionInfo>()
    
    companion object {
        private const val TAG = "BLEConnectionPool"
        private const val MAX_CONNECTIONS = 7
        
        // Priority weights
        private const val WEIGHT_PENDING_MESSAGES = 0.5f
        private const val WEIGHT_RSSI = 0.3f
        private const val WEIGHT_RECENCY = 0.2f
    }
    
    /**
     * Connection information with priority metrics.
     */
    data class ConnectionInfo(
        val deviceAddress: String,
        val gatt: BluetoothGatt,
        val rssi: Int,
        val connectedAt: Long,
        var lastUsedAt: Long,
        var pendingMessageCount: Int = 0
    )
    
    /**
     * Add a connection to the pool.
     * 
     * If pool is full, evicts the lowest priority connection.
     * 
     * @param deviceAddress BLE device address
     * @param gatt BluetoothGatt connection
     * @param rssi Signal strength
     * @return true if added successfully, false if rejected
     */
    fun addConnection(
        deviceAddress: String,
        gatt: BluetoothGatt,
        rssi: Int
    ): Boolean {
        // Check if already connected
        if (connections.containsKey(deviceAddress)) {
            Log.d(TAG, "Device $deviceAddress already connected")
            return true
        }
        
        // Check if pool is full
        if (connections.size >= MAX_CONNECTIONS) {
            // Evict lowest priority connection
            val evicted = evictLowestPriority()
            if (evicted == null) {
                Log.w(TAG, "Failed to evict connection, pool full")
                return false
            }
            
            Log.d(TAG, "Evicted connection to ${evicted.deviceAddress} (priority: ${calculatePriority(evicted)})")
        }
        
        // Add new connection
        val connectionInfo = ConnectionInfo(
            deviceAddress = deviceAddress,
            gatt = gatt,
            rssi = rssi,
            connectedAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis()
        )
        
        connections[deviceAddress] = connectionInfo
        
        Log.d(TAG, "Added connection to $deviceAddress (${connections.size}/$MAX_CONNECTIONS)")
        return true
    }
    
    /**
     * Remove a connection from the pool.
     * 
     * @param deviceAddress BLE device address
     * @return ConnectionInfo if removed, null if not found
     */
    fun removeConnection(deviceAddress: String): ConnectionInfo? {
        val connectionInfo = connections.remove(deviceAddress)
        
        if (connectionInfo != null) {
            // Close GATT connection
            try {
                connectionInfo.gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT connection: ${e.message}")
            }
            
            Log.d(TAG, "Removed connection to $deviceAddress (${connections.size}/$MAX_CONNECTIONS)")
        }
        
        return connectionInfo
    }
    
    /**
     * Get a connection from the pool.
     * 
     * @param deviceAddress BLE device address
     * @return ConnectionInfo if found, null otherwise
     */
    fun getConnection(deviceAddress: String): ConnectionInfo? {
        val connectionInfo = connections[deviceAddress]
        
        // Update last used timestamp
        connectionInfo?.lastUsedAt = System.currentTimeMillis()
        
        return connectionInfo
    }
    
    /**
     * Update pending message count for a connection.
     * 
     * @param deviceAddress BLE device address
     * @param count Number of pending messages
     */
    fun updatePendingMessageCount(deviceAddress: String, count: Int) {
        connections[deviceAddress]?.pendingMessageCount = count
    }
    
    /**
     * Get all active connections.
     */
    fun getAllConnections(): List<ConnectionInfo> {
        return connections.values.toList()
    }
    
    /**
     * Get connection count.
     */
    fun getConnectionCount(): Int {
        return connections.size
    }
    
    /**
     * Check if pool is full.
     */
    fun isFull(): Boolean {
        return connections.size >= MAX_CONNECTIONS
    }
    
    /**
     * Clear all connections.
     */
    fun clear() {
        connections.values.forEach { connectionInfo ->
            try {
                connectionInfo.gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT connection: ${e.message}")
            }
        }
        
        connections.clear()
        Log.d(TAG, "Cleared all connections")
    }
    
    /**
     * Calculate priority for a connection.
     * 
     * Higher priority = more important to keep.
     * 
     * Priority factors:
     * - Pending messages (50%): More pending messages = higher priority
     * - RSSI (30%): Better signal = higher priority
     * - Recency (20%): More recently used = higher priority
     */
    fun calculatePriority(connectionInfo: ConnectionInfo): Float {
        val now = System.currentTimeMillis()
        
        // Normalize pending messages (0-10 range)
        val normalizedMessages = (connectionInfo.pendingMessageCount.coerceIn(0, 10) / 10f)
        
        // Normalize RSSI (-100 to -30 dBm range)
        val normalizedRssi = ((connectionInfo.rssi + 100).coerceIn(0, 70) / 70f)
        
        // Normalize recency (0-60 seconds range)
        val ageSeconds = ((now - connectionInfo.lastUsedAt) / 1000).coerceIn(0, 60)
        val normalizedRecency = 1f - (ageSeconds / 60f)
        
        return (normalizedMessages * WEIGHT_PENDING_MESSAGES) +
               (normalizedRssi * WEIGHT_RSSI) +
               (normalizedRecency * WEIGHT_RECENCY)
    }
    
    /**
     * Evict the lowest priority connection.
     * 
     * @return Evicted ConnectionInfo, or null if pool is empty
     */
    private fun evictLowestPriority(): ConnectionInfo? {
        if (connections.isEmpty()) {
            return null
        }
        
        // Find connection with lowest priority
        val lowestPriority = connections.values.minByOrNull { calculatePriority(it) }
            ?: return null
        
        return removeConnection(lowestPriority.deviceAddress)
    }
}
