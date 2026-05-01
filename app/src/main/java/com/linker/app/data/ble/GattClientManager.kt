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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages GATT Client connections for transmitting BLE mesh packets
 * 
 * Maintains connections to discovered mesh nodes and handles packet transmission.
 * Enforces Android's 7 concurrent connection limit.
 */
@Singleton
class GattClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "GattClientManager"
        private const val MAX_CONNECTIONS = 7 // Android BLE limit
        private const val CONNECTION_TIMEOUT = 20000L // 20 seconds - increased from 5s for GATT handshake
        private const val MTU_SIZE = 512
    }
    
    private val connections = ConcurrentHashMap<String, BluetoothGatt>()
    private val connectionMutex = Mutex()
    private val pendingWrites = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    
    /**
     * Connect to a BLE device
     * 
     * @param device Bluetooth device to connect to
     * @return true if connection successful, false otherwise
     */
    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withTimeout(CONNECTION_TIMEOUT) {
        val deviceAddress = device.address
        
        // Check if already connected
        if (connections.containsKey(deviceAddress)) {
            Log.d(TAG, "Already connected to $deviceAddress")
            return@withTimeout true
        }
        
        // Check connection limit
        connectionMutex.withLock {
            if (connections.size >= MAX_CONNECTIONS) {
                Log.w(TAG, "Connection limit reached ($MAX_CONNECTIONS), cannot connect to $deviceAddress")
                return@withTimeout false
            }
        }
        
        suspendCancellableCoroutine<Boolean> { continuation ->
            var gattConnection: BluetoothGatt? = null
            var connectionAttempted = false
            
            // Setup cancellation handler
            continuation.invokeOnCancellation {
                Log.d(TAG, "Connection cancelled for $deviceAddress")
                gattConnection?.disconnect()
                gattConnection?.close()
                connections.remove(deviceAddress)
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
                                    // This ensures the connection is tracked even if service discovery fails
                                    gatt?.let { 
                                        connections[deviceAddress] = it
                                        Log.d(TAG, "Connection stored in map for $deviceAddress")
                                    }
                                    
                                    // Request MTU first, then discover services in onMtuChanged
                                    gatt?.requestMtu(MTU_SIZE)
                                } else {
                                    Log.e(TAG, "Connection state changed to CONNECTED but status=$status (not GATT_SUCCESS)")
                                    gatt?.disconnect()
                                    
                                    if (continuation.isActive) {
                                        continuation.resume(false)
                                    }
                                }
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Disconnected from $deviceAddress, status=$status")
                                connections.remove(deviceAddress)
                                gatt?.close()
                                
                                // Only resume with false if we haven't already resumed
                                if (continuation.isActive && !connectionAttempted) {
                                    connectionAttempted = true
                                    continuation.resume(false)
                                }
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
                        
                        // Start service discovery after MTU negotiation (even if MTU failed)
                        gatt?.discoverServices()
                    }
                    
                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        Log.d(TAG, "onServicesDiscovered - device=$deviceAddress, status=$status")
                        
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Services discovered for $deviceAddress")
                            
                            // Verify our service exists
                            val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                            if (service != null) {
                                Log.d(TAG, "Found Linker Mesh Service on $deviceAddress")
                                
                                // Connection already stored in onConnectionStateChange
                                if (continuation.isActive) {
                                    connectionAttempted = true
                                    continuation.resume(true)
                                }
                            } else {
                                Log.w(TAG, "Linker Mesh Service not found on $deviceAddress, but connection is valid")
                                
                                // Connection already stored, still consider it successful
                                // The service might be discovered later
                                if (continuation.isActive) {
                                    connectionAttempted = true
                                    continuation.resume(true)
                                }
                            }
                        } else {
                            Log.w(TAG, "Service discovery failed for $deviceAddress: $status, but connection is still valid")
                            
                            // Connection already stored, still consider it successful
                            // We can still try to write characteristics
                            if (continuation.isActive) {
                                connectionAttempted = true
                                continuation.resume(true)
                            }
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
                }
                
                // Connect to GATT server
                Log.d(TAG, "Initiating GATT connection to $deviceAddress")
                gattConnection = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                
                if (gattConnection == null) {
                    Log.e(TAG, "connectGatt() returned null for $deviceAddress")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                } else {
                    Log.d(TAG, "connectGatt() returned successfully for $deviceAddress, waiting for callbacks...")
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception connecting to $deviceAddress", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to $deviceAddress", e)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
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
            
            // Try to get service and characteristic, but don't fail if not found
            // (they might be discovered later or the device might still accept writes)
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
                // API 33+ - Use new API
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
                // API < 33 - Use deprecated API
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
            
            // Wait for write callback with timeout
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
     * 
     * @param deviceAddress Device address to disconnect from
     */
    fun disconnect(deviceAddress: String) {
        try {
            val gatt = connections.remove(deviceAddress)
            gatt?.disconnect()
            gatt?.close()
            
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
}
