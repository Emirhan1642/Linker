package com.linker.app.data.ble

import android.bluetooth.*
import android.content.Context
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
 * Enforces Android's 7 concurrent connection limit.
 */
@Singleton
class GattClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "GattClientManager"
        private const val MAX_CONNECTIONS = 7 // Android BLE limit
        private const val CONNECTION_TIMEOUT = 5000L // 5 seconds
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
            return@withTimeout true
        }
        
        // Check connection limit
        connectionMutex.withLock {
            if (connections.size >= MAX_CONNECTIONS) {
                Log.w(TAG, "Connection limit reached ($MAX_CONNECTIONS), cannot connect to $deviceAddress")
                return@withTimeout false
            }
        }
        
        suspendCoroutine { continuation ->
            try {
                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt?,
                        status: Int,
                        newState: Int
                    ) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                Log.d(TAG, "Connected to $deviceAddress")
                                
                                // Request MTU
                                gatt?.requestMtu(MTU_SIZE)
                                
                                // Discover services
                                gatt?.discoverServices()
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                Log.d(TAG, "Disconnected from $deviceAddress")
                                connections.remove(deviceAddress)
                                gatt?.close()
                            }
                        }
                    }
                    
                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "Services discovered for $deviceAddress")
                            
                            // Store connection
                            gatt?.let { connections[deviceAddress] = it }
                            
                            if (continuation.context.isActive) {
                                continuation.resume(true)
                            }
                        } else {
                            Log.e(TAG, "Service discovery failed for $deviceAddress: $status")
                            gatt?.disconnect()
                            
                            if (continuation.context.isActive) {
                                continuation.resume(false)
                            }
                        }
                    }
                    
                    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "MTU changed to $mtu for $deviceAddress")
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
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception connecting to $deviceAddress", e)
                if (continuation.context.isActive) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to $deviceAddress", e)
                if (continuation.context.isActive) {
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
        
        val service = gatt.getService(GattServerManager.SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service not found for $deviceAddress")
            return false
        }
        
        val characteristic = service.getCharacteristic(GattServerManager.CHARACTERISTIC_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found for $deviceAddress")
            return false
        }
        
        return try {
            val deferred = CompletableDeferred<Boolean>()
            pendingWrites[deviceAddress] = deferred
            
            characteristic.value = data
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            val writeInitiated = gatt.writeCharacteristic(characteristic)
            
            if (!writeInitiated) {
                pendingWrites.remove(deviceAddress)
                Log.e(TAG, "Failed to initiate write for $deviceAddress")
                return false
            }
            
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
