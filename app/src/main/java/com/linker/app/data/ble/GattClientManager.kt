package com.linker.app.data.ble

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages GATT Client connections for transmitting BLE mesh packets
 * 
 * Maintains connections to discovered mesh nodes and handles packet transmission.
 * Enforces Android's 7 concurrent connection limit with priority-based eviction.
 */
@Singleton
class GattClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionPool: BLEConnectionPool
) {
    
    companion object {
        private const val TAG = "GattClientManager"
        private const val MAX_CONNECTIONS = 7 // Android BLE limit
        private const val CONNECTION_TIMEOUT = 5000L // 5 seconds per Requirement 1.4
        private const val MTU_SIZE = 512
    }
    
    private val connections = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectionMutex = Mutex()
    private val pendingWrites = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    
    // Track RSSI for connection pool priority
    private val rssiMap = ConcurrentHashMap<String, Int>()
    
    /**
     * Connect to a BLE device
     * 
     * @param device Bluetooth device to connect to
     * @param rssi Signal strength (optional, for connection pool priority)
     * @return true if connection successful, false otherwise
     */
    suspend fun connectToDevice(device: BluetoothDevice, rssi: Int = -60): Boolean {
        val deviceAddress = device.address
        
        // Check if already connected
        if (connections.containsKey(deviceAddress)) {
            Log.d(TAG, "Already connected to $deviceAddress")
            return true
        }
        
        // Check connection limit with pool
        connectionMutex.withLock {
            if (connections.size >= MAX_CONNECTIONS && connectionPool.isFull()) {
                Log.w(TAG, "Connection limit reached ($MAX_CONNECTIONS), cannot connect to $deviceAddress")
                return false
            }
        }
        
        // Store RSSI for connection pool
        rssiMap[deviceAddress] = rssi
        
        return try {
            withTimeout(CONNECTION_TIMEOUT) {
                suspendCancellableCoroutine { continuation ->
                    var gattConnection: BluetoothGatt? = null
                    var connectionAttempted = false
                    
                    // Setup cancellation handler
                    continuation.invokeOnCancellation {
                        Log.d(TAG, "Connection cancelled for $deviceAddress")
                        gattConnection?.disconnect()
                        gattConnection?.close()
                        connections.remove(deviceAddress)
                    }
                    
                    // Helper function to safely resume continuation
                    fun resumeContinuationIfActive(result: Boolean) {
                        if (continuation.isActive && !connectionAttempted) {
                            connectionAttempted = true
                            continuation.resume(result)
                        }
                    }
                    
                    try {
                        val gattCallback = object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(
                                gatt: BluetoothGatt?,
                                status: Int,
                                newState: Int
                            ) {
                                Log.d(TAG, "onConnectionStateChange - device=$deviceAddress, status=$status, newState=$newState")
                                
                                when (newState) {
                                    BluetoothProfile.STATE_CONNECTED -> {
                                        if (status == BluetoothGatt.GATT_SUCCESS) {
                                            Log.d(TAG, "Connected to $deviceAddress, requesting MTU")
                                            
                                            // Store connection immediately upon successful connection
                                            gatt?.let { 
                                                connections[deviceAddress] = it
                                                Log.d(TAG, "Connection stored in map for $deviceAddress")
                                                
                                                // Add to connection pool
                                                val currentRssi = rssiMap[deviceAddress] ?: -60
                                                connectionPool.addConnection(deviceAddress, it, currentRssi)
                                            }
                                            
                                            // Request MTU first, then discover services in onMtuChanged
                                            gatt?.requestMtu(MTU_SIZE)
                                        } else {
                                            Log.e(TAG, "Connection state changed to CONNECTED but status=$status")
                                            gatt?.disconnect()
                                            resumeContinuationIfActive(false)
                                        }
                                    }
                                    BluetoothProfile.STATE_DISCONNECTED -> {
                                        Log.d(TAG, "Disconnected from $deviceAddress, status=$status")
                                        connections.remove(deviceAddress)
                                        gatt?.close()
                                        resumeContinuationIfActive(false)
                                    }
                                }
                            }
                            
                            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                                Log.d(TAG, "onMtuChanged - device=$deviceAddress, mtu=$mtu, status=$status")
                                
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(TAG, "MTU changed to $mtu for $deviceAddress, discovering services")
                                } else {
                                    Log.w(TAG, "MTU change failed for $deviceAddress: $status, still attempting service discovery")
                                }
                                
                                gatt?.discoverServices()
                            }
                            
                            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                                Log.d(TAG, "onServicesDiscovered - device=$deviceAddress, status=$status")
                                
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(TAG, "Services discovered for $deviceAddress")
                                    
                                    val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                                    if (service != null) {
                                        Log.d(TAG, "Found Linker Mesh Service on $deviceAddress")
                                    } else {
                                        Log.w(TAG, "Linker Mesh Service not found on $deviceAddress, but connection is valid")
                                    }
                                    resumeContinuationIfActive(true)
                                } else {
                                    Log.w(TAG, "Service discovery failed for $deviceAddress: $status, but connection is still valid")
                                    resumeContinuationIfActive(true)
                                }
                            }
                            
                            override fun onCharacteristicWrite(
                                gatt: BluetoothGatt?,
                                characteristic: BluetoothGattCharacteristic?,
                                status: Int
                            ) {
                                val deferred = pendingWrites.remove(deviceAddress)
                                
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(TAG, "Characteristic write successful for $deviceAddress")
                                    deferred?.complete(true)
                                } else {
                                    Log.e(TAG, "Characteristic write failed for $deviceAddress: $status")
                                    deferred?.complete(false)
                                }
                            }
                            
                            override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    Log.d(TAG, "RSSI read successful for $deviceAddress: $rssi dBm")
                                    rssiMap[deviceAddress] = rssi
                                } else {
                                    Log.e(TAG, "RSSI read failed for $deviceAddress: $status")
                                }
                            }
                        }
                        
                        // Connect to GATT server
                        Log.d(TAG, "Initiating GATT connection to $deviceAddress")
                        gattConnection = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        
                        if (gattConnection == null) {
                            Log.e(TAG, "connectGatt() returned null for $deviceAddress")
                            resumeContinuationIfActive(false)
                        } else {
                            Log.d(TAG, "connectGatt() returned successfully for $deviceAddress, waiting for callbacks...")
                        }
                        
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception connecting to $deviceAddress", e)
                        resumeContinuationIfActive(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to $deviceAddress", e)
                        resumeContinuationIfActive(false)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Connection timeout for $deviceAddress after ${CONNECTION_TIMEOUT}ms")
            // Clean up any partial connection
            connections.remove(deviceAddress)?.let { gatt ->
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (ex: Exception) {
                    Log.e(TAG, "Error cleaning up timed out connection", ex)
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error connecting to $deviceAddress", e)
            false
        }
    }
    
    /**
     * Write characteristic with automatic retry on GATT errors (send packet).
     * 
     * Addresses Issue #19 (P3): Add GATT error retry logic
     * 
     * Automatically retries on transient GATT failures to improve reliability.
     * 
     * @param deviceAddress Device address to write to
     * @param data Data to write
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param retryDelayMs Delay between retries in milliseconds (default: 500ms)
     * @return true if write successful, false otherwise
     */
    suspend fun writeCharacteristicWithRetry(
        deviceAddress: String,
        data: ByteArray,
        maxRetries: Int = 3,
        retryDelayMs: Long = 500L
    ): Boolean {
        var attempt = 0
        var lastError: String? = null
        
        while (attempt < maxRetries) {
            attempt++
            
            val result = writeCharacteristic(deviceAddress, data)
            
            if (result) {
                if (attempt > 1) {
                    Log.d(TAG, "Write succeeded on attempt $attempt for $deviceAddress")
                }
                return true
            }
            
            // Check if we should retry
            if (attempt < maxRetries) {
                lastError = "GATT write failed"
                Log.w(TAG, "Write failed for $deviceAddress (attempt $attempt/$maxRetries), retrying in ${retryDelayMs}ms")
                delay(retryDelayMs)
            }
        }
        
        Log.e(TAG, "Write failed after $maxRetries attempts for $deviceAddress: $lastError")
        return false
    }
    
    /**
     * Write characteristic (send packet)
     * 
     * @param deviceAddress Device address to write to
     * @param data Data to write
     * @return true if write successful, false otherwise
     */
    suspend fun writeCharacteristic(deviceAddress: String, data: ByteArray): Boolean {
        val gatt = connections[deviceAddress]
        
        if (gatt == null) {
            Log.e(TAG, "No connection to $deviceAddress")
            return false
        }
        
        Log.d(TAG, "Attempting to write characteristic to $deviceAddress (data size: ${data.size} bytes)")
        
        return try {
            val deferred = CompletableDeferred<Boolean>()
            pendingWrites[deviceAddress] = deferred
            
            val service = gatt.getService(GattServerManager.SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "Service not found for $deviceAddress, cannot write")
                pendingWrites.remove(deviceAddress)
                return false
            }
            
            val characteristic = service.getCharacteristic(GattServerManager.CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.w(TAG, "Characteristic not found for $deviceAddress, cannot write")
                pendingWrites.remove(deviceAddress)
                return false
            }
            
            Log.d(TAG, "Found service and characteristic for $deviceAddress")
            
            val writeInitiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val result = gatt.writeCharacteristic(
                        characteristic,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    Log.d(TAG, "writeCharacteristic (API 33+) returned: $result for $deviceAddress")
                    result == BluetoothGatt.GATT_SUCCESS
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing characteristic on API 33+: ${e.message}")
                    false
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    val result = gatt.writeCharacteristic(characteristic)
                    Log.d(TAG, "writeCharacteristic (API < 33) returned: $result for $deviceAddress")
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing characteristic on API < 33: ${e.message}")
                    false
                }
            }
            
            if (!writeInitiated) {
                pendingWrites.remove(deviceAddress)
                Log.e(TAG, "Failed to initiate write for $deviceAddress")
                return false
            }
            
            Log.d(TAG, "Write initiated for $deviceAddress, waiting for callback")
            
            withTimeout(5000L) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingWrites.remove(deviceAddress)
            Log.e(TAG, "Write timeout for $deviceAddress")
            false
        } catch (e: SecurityException) {
            pendingWrites.remove(deviceAddress)
            Log.e(TAG, "Security exception writing to $deviceAddress", e)
            false
        } catch (e: Exception) {
            pendingWrites.remove(deviceAddress)
            Log.e(TAG, "Error writing to $deviceAddress", e)
            false
        }
    }
    
    /**
     * Disconnect from device
     */
    fun disconnect(deviceAddress: String) {
        try {
            val gatt = connections.remove(deviceAddress)
            gatt?.disconnect()
            gatt?.close()
            
            connectionPool.removeConnection(deviceAddress)
            rssiMap.remove(deviceAddress)
            
            Log.d(TAG, "Disconnected from $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from $deviceAddress", e)
        }
    }
    
    /**
     * Disconnect all devices
     */
    fun disconnectAll() {
        connections.keys.toList().forEach { deviceAddress ->
            disconnect(deviceAddress)
        }
        connectionPool.clear()
        rssiMap.clear()
    }
    
    /**
     * Get connected device count
     */
    fun getConnectionCount(): Int {
        return connections.size
    }
    
    /**
     * Check if connected to device
     */
    fun isConnected(deviceAddress: String): Boolean {
        return connections.containsKey(deviceAddress)
    }
    
    /**
     * Get all connected device addresses
     */
    fun getConnectedDevices(): List<String> {
        return connections.keys.toList()
    }
    
    /**
     * Update pending message count for connection pool priority
     */
    fun updatePendingMessageCount(deviceAddress: String, count: Int) {
        connectionPool.updatePendingMessageCount(deviceAddress, count)
    }
    
    /**
     * Get RSSI for a connected device
     */
    fun getRssi(deviceAddress: String): Int? {
        return rssiMap[deviceAddress]
    }
    
    /**
     * Read remote RSSI for a connected device
     * This triggers an async RSSI read and updates the rssiMap
     */
    fun readRemoteRssi(deviceAddress: String): Boolean {
        val gatt = connections[deviceAddress]
        return if (gatt != null) {
            try {
                gatt.readRemoteRssi()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception reading RSSI for $deviceAddress", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error reading RSSI for $deviceAddress", e)
                false
            }
        } else {
            false
        }
    }
}
