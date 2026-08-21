package com.linker.app.data.ble

import android.bluetooth.BluetoothGatt
import com.linker.app.core.util.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logger = SecureLogger("BLEConnectionPool")
    private val poolLock = Any()
    
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
     * Uses AtomicLong and AtomicInteger for thread-safe mutable fields.
     */
    data class ConnectionInfo(
        val deviceAddress: String,
        val gatt: BluetoothGatt,
        val rssi: Int,
        val connectedAt: Long,
        val lastUsedAt: AtomicLong = AtomicLong(System.currentTimeMillis()),
        val pendingMessageCount: AtomicInteger = AtomicInteger(0)
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
        synchronized(poolLock) {
            // Check if already connected
            if (connections.containsKey(deviceAddress)) {
                logger.d("Device $deviceAddress already connected")
                return true
            }
            
            // Check if pool is full
            if (connections.size >= MAX_CONNECTIONS) {
                // Evict lowest priority connection
                val evicted = evictLowestPriority()
                if (evicted == null) {
                    logger.w("Failed to evict connection, pool full")
                    return false
                }
                
                logger.d("Evicted connection to ${evicted.deviceAddress} (priority: ${calculatePriority(evicted)})")
            }
            
            // Add new connection
            val connectionInfo = ConnectionInfo(
                deviceAddress = deviceAddress,
                gatt = gatt,
                rssi = rssi,
                connectedAt = System.currentTimeMillis(),
                lastUsedAt = AtomicLong(System.currentTimeMillis())
            )
            
            connections[deviceAddress] = connectionInfo
            
            logger.d("Added connection to $deviceAddress (${connections.size}/$MAX_CONNECTIONS)")
            return true
        }
    }
    
    /**
     * Remove a connection from the pool.
     * 
     * @param deviceAddress BLE device address
     * @return ConnectionInfo if removed, null if not found
     */
    fun removeConnection(deviceAddress: String): ConnectionInfo? {
        val connectionInfo = synchronized(poolLock) {
            connections.remove(deviceAddress)
        }
        
        if (connectionInfo != null) {
            // Disconnect and close GATT connection safely to avoid HCI stack leak and DeadObjectException
            try {
                connectionInfo.gatt.disconnect()
            } catch (e: Exception) {
                logger.w("Error disconnecting GATT: ${e.message}")
            }
            try {
                connectionInfo.gatt.close()
            } catch (e: Exception) {
                logger.w("Error closing GATT handle: ${e.message}")
            }
            
            logger.d("Removed connection to $deviceAddress (${connections.size}/$MAX_CONNECTIONS)")
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
        val connectionInfo = synchronized(poolLock) {
            connections[deviceAddress]
        }
        
        // Update last used timestamp (thread-safe)
        connectionInfo?.lastUsedAt?.set(System.currentTimeMillis())
        
        return connectionInfo
    }
    
    /**
     * Update pending message count for a connection.
     * 
     * @param deviceAddress BLE device address
     * @param count Number of pending messages
     */
    fun updatePendingMessageCount(deviceAddress: String, count: Int) {
        synchronized(poolLock) {
            connections[deviceAddress]?.pendingMessageCount?.set(count)
        }
    }
    
    /**
     * Get all active connections.
     */
    fun getAllConnections(): List<ConnectionInfo> {
        return synchronized(poolLock) {
            connections.values.toList()
        }
    }
    
    /**
     * Get connection count.
     */
    fun getConnectionCount(): Int {
        return synchronized(poolLock) {
            connections.size
        }
    }
    
    /**
     * Check if pool is full.
     */
    fun isFull(): Boolean {
        return synchronized(poolLock) {
            connections.size >= MAX_CONNECTIONS
        }
    }
    
    /**
     * Clear all connections.
     */
    fun clear() {
        val connsToClose = synchronized(poolLock) {
            val list = connections.values.toList()
            connections.clear()
            list
        }
        
        connsToClose.forEach { connectionInfo ->
            try {
                connectionInfo.gatt.close()
            } catch (e: Exception) {
                logger.e("Error closing GATT connection", e)
            }
        }
        
        logger.d("Cleared all connections")
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
        val normalizedMessages = (connectionInfo.pendingMessageCount.get().coerceIn(0, 10) / 10f)
        
        // Normalize RSSI (-100 to -30 dBm range)
        val normalizedRssi = ((connectionInfo.rssi.coerceIn(-100, -30) + 100) / 70f)
        
        // Normalize recency (0-60 seconds range)
        val ageSeconds = ((now - connectionInfo.lastUsedAt.get()) / 1000).coerceIn(0, 60)
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
        val evictedInfo = synchronized(poolLock) {
            if (connections.isEmpty()) {
                return null
            }
            
            // Find connection with lowest priority
            val lowestPriority = connections.values.minByOrNull { calculatePriority(it) }
                ?: return null
            
            // Remove connection without closing GATT
            removeConnectionWithoutClose(lowestPriority.deviceAddress)
        }
        
        // Offload GATT close to background thread to avoid blocking
        if (evictedInfo != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    evictedInfo.gatt.disconnect()
                    evictedInfo.gatt.close()
                } catch (e: Exception) {
                    logger.e("Error closing evicted GATT connection", e)
                }
            }
        }
        
        return evictedInfo
    }
    
    /**
     * Remove connection without closing GATT (for eviction).
     */
    private fun removeConnectionWithoutClose(deviceAddress: String): ConnectionInfo? {
        val connectionInfo = connections.remove(deviceAddress)
        
        if (connectionInfo != null) {
            logger.d("Removed connection to $deviceAddress (${connections.size}/$MAX_CONNECTIONS)")
        }
        
        return connectionInfo
    }
}
