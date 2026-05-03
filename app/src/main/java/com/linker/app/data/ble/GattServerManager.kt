package com.linker.app.data.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages GATT Server for receiving BLE mesh packets
 * 
 * Acts as a BLE peripheral that advertises the Linker Mesh Service
 * and receives packets from other mesh nodes.
 */
@Singleton
class GattServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "GattServerManager"
        
        // Linker Mesh Service UUID
        val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
        
        // Message Characteristic UUID (writable)
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    
    // Packet with sender MAC address for routing
    data class IncomingPacketWithAddress(
        val packet: BLEPacket,
        val senderMacAddress: String
    )
    
    private val _incomingPackets = MutableSharedFlow<IncomingPacketWithAddress>(replay = 0, extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<IncomingPacketWithAddress> = _incomingPackets.asSharedFlow()
    
    // Track connected devices from incoming GATT connections
    private val _incomingConnections = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val incomingConnections: SharedFlow<String> = _incomingConnections.asSharedFlow()
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onConnectionStateChange(
            device: BluetoothDevice?,
            status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Device connected: ${device?.address}")
                    // Emit incoming connection
                    device?.address?.let { address ->
                        _incomingConnections.tryEmit(address)
                        Log.d(TAG, "Emitted incoming connection from $address")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Device disconnected: ${device?.address}")
                }
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic?.uuid == CHARACTERISTIC_UUID && value != null) {
                try {
                    // Deserialize packet
                    val packet = BLEPacket.deserialize(value)
                    
                    // Validate checksum
                    if (BLEPacket.validateChecksum(packet)) {
                        // Emit packet with sender MAC address for routing
                        _incomingPackets.tryEmit(IncomingPacketWithAddress(packet, device?.address ?: "unknown"))
                        
                        Log.d(TAG, "Received valid packet from ${device?.address}: messageId=${packet.messageId}")
                        
                        // Send success response
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                offset,
                                null
                            )
                        }
                    } else {
                        Log.w(TAG, "Invalid checksum for packet from ${device?.address}")
                        
                        // Send failure response
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                BluetoothGatt.GATT_FAILURE,
                                offset,
                                null
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet from ${device?.address}", e)
                    
                    // Send failure response
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            offset,
                            null
                        )
                    }
                }
            } else {
                // Unknown characteristic or null value
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null
                    )
                }
            }
        }
        
        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            Log.d(TAG, "MTU changed for ${device?.address}: $mtu bytes")
            // MTU change is handled automatically by Android BLE stack
            // The effective payload size is MTU - 3 bytes (ATT header)
            // Our packet size (512 bytes) is designed to work with standard MTU (517 bytes)
            if (mtu < 512) {
                Log.w(TAG, "MTU ($mtu) is smaller than expected (512), fragmentation may be needed")
            }
        }
    }
    
    /**
     * Start GATT server
     * 
     * @return true if server started successfully, false otherwise
     */
    fun startServer(): Boolean {
        try {
            bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            
            if (bluetoothManager == null) {
                Log.e(TAG, "BluetoothManager not available")
                return false
            }
            
            // Open GATT server
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server")
                return false
            }
            
            // Create service
            val service = BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            // Create characteristic (writable)
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            
            service.addCharacteristic(characteristic)
            
            // Add service to server
            val added = gattServer?.addService(service) ?: false
            
            if (added) {
                Log.d(TAG, "GATT server started successfully")
            } else {
                Log.e(TAG, "Failed to add service to GATT server")
            }
            
            return added
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting GATT server", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GATT server", e)
            return false
        }
    }
    
    /**
     * Stop GATT server
     */
    fun stopServer() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            
            Log.d(TAG, "GATT server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GATT server", e)
        }
    }
    
    /**
     * Check if server is running
     */
    fun isRunning(): Boolean {
        return gattServer != null
    }
}
