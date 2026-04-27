package com.linker.app.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.linker.app.data.local.dao.BleNodeDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BLE Mesh Manager
 * 
 * Manages BLE mesh network operations including scanning, advertising,
 * peer connections, and message routing.
 */
@Singleton
class BLEMeshManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gattServerManager: GattServerManager,
    private val gattClientManager: GattClientManager,
    private val routingTable: BLERoutingTable,
    private val messageIdCache: MessageIdCache,
    private val bleNodeDao: BleNodeDao,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider
) : BLEMeshManager {
    
    companion object {
        private const val TAG = "BLEMeshManager"
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _meshStatus = MutableStateFlow<MeshStatus>(MeshStatus.Idle)
    private val _connectedPeers = MutableStateFlow<List<BleNode>>(emptyList())
    
    private var messageReceivedCallback: ((BLEPacket) -> Unit)? = null
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _meshStatus.value = MeshStatus.Error("Scan failed: $errorCode")
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising started successfully")
            updateMeshStatus()
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error code: $errorCode")
            _meshStatus.value = MeshStatus.Error("Advertising failed: $errorCode")
        }
    }
    
    override fun initialize() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter not available")
            _meshStatus.value = MeshStatus.Error("Bluetooth not available")
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled")
            _meshStatus.value = MeshStatus.Error("Bluetooth disabled")
            return
        }
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        
        // Warm up message ID cache
        coroutineScope.launch {
            messageIdCache.warmUpCache()
        }
        
        // Start listening to incoming packets from GATT server
        coroutineScope.launch {
            gattServerManager.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
        
        Log.d(TAG, "BLE Mesh Manager initialized")
    }
    
    override fun startMeshNetwork() {
        // Start GATT server
        if (!gattServerManager.startServer()) {
            Log.e(TAG, "Failed to start GATT server")
            _meshStatus.value = MeshStatus.Error("Failed to start GATT server")
            return
        }
        
        // Start scanning and advertising
        startScanning()
        startAdvertising()
        
        Log.d(TAG, "Mesh network started")
    }
    
    override fun stopMeshNetwork() {
        stopScanning()
        stopAdvertising()
        gattServerManager.stopServer()
        gattClientManager.disconnectAll()
        
        _meshStatus.value = MeshStatus.Idle
        _connectedPeers.value = emptyList()
        
        Log.d(TAG, "Mesh network stopped")
    }
    
    override fun startScanning() {
        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }
        
        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
                .build()
            
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            
            updateMeshStatus()
            Log.d(TAG, "Scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting scan", e)
            _meshStatus.value = MeshStatus.Error("Permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
            _meshStatus.value = MeshStatus.Error("Scan error: ${e.message}")
        }
    }
    
    override fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            updateMeshStatus()
            Log.d(TAG, "Scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }
    
    override fun startAdvertising() {
        if (bleAdvertiser == null) {
            Log.e(TAG, "BLE advertiser not available")
            return
        }
        
        try {
            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0) // Advertise indefinitely
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
                .build()
            
            bleAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            
            Log.d(TAG, "Advertising started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting advertising", e)
            _meshStatus.value = MeshStatus.Error("Permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            _meshStatus.value = MeshStatus.Error("Advertising error: ${e.message}")
        }
    }
    
    override fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            updateMeshStatus()
            Log.d(TAG, "Advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }
    
    override suspend fun connectToPeer(deviceAddress: String): Result<Unit> {
        return try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            
            if (device == null) {
                Result.failure(Exception("Invalid device address"))
            } else {
                val connected = gattClientManager.connectToDevice(device)
                
                if (connected) {
                    updateConnectedPeers()
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Connection failed"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to peer $deviceAddress", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disconnectFromPeer(deviceAddress: String) {
        gattClientManager.disconnect(deviceAddress)
        updateConnectedPeers()
    }
    
    override suspend fun sendMessage(packet: BLEPacket): Result<Unit> {
        return try {
            // Get route to recipient
            val route = routingTable.getRoute(packet.recipientId)
            
            if (route == null) {
                Log.w(TAG, "No route to recipient ${packet.recipientId}")
                return Result.failure(Exception("No route to recipient"))
            }
            
            // Serialize packet
            val data = BLEPacket.serialize(packet)
            
            // Send to next hop
            val success = gattClientManager.writeCharacteristic(route.deviceAddress, data)
            
            if (success) {
                Log.d(TAG, "Message sent successfully to ${route.deviceAddress}")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send message to ${route.deviceAddress}")
                Result.failure(Exception("Write failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }
    
    override suspend fun forwardMessage(packet: BLEPacket): Result<Unit> {
        // Decrement TTL
        if (packet.ttl <= 0) {
            Log.w(TAG, "TTL exhausted for message ${packet.messageId}")
            return Result.failure(Exception("TTL exhausted"))
        }
        
        val forwardedPacket = packet.copy(
            ttl = (packet.ttl - 1).toByte(),
            hopCount = (packet.hopCount + 1).toByte()
        )
        
        return sendMessage(forwardedPacket)
    }
    
    override fun onMessageReceived(callback: (BLEPacket) -> Unit) {
        messageReceivedCallback = callback
    }
    
    override suspend fun updateRoutingTable(nodeId: String, rssi: Int, timestamp: Long) {
        routingTable.addRoute(
            nodeId = nodeId,
            deviceAddress = "", // Will be updated when connecting
            rssi = rssi,
            hopCount = 1,
            timestamp = timestamp
        )
    }
    
    override suspend fun getRouteToPeer(recipientId: String): List<String>? {
        val route = routingTable.getRoute(recipientId)
        return route?.let { listOf(it.deviceAddress) }
    }
    
    override suspend fun cleanupStaleNodes() {
        routingTable.removeStaleRoutes()
        messageIdCache.cleanup()
    }
    
    override fun observeConnectedPeers(): Flow<List<BleNode>> {
        return _connectedPeers.asStateFlow()
    }
    
    override fun observeMeshStatus(): Flow<MeshStatus> {
        return _meshStatus.asStateFlow()
    }
    
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        
        Log.d(TAG, "Discovered device: ${device.address}, RSSI: $rssi")
        
        // Extract protocol version from scan record if available
        val scanRecord = result.scanRecord
        val serviceData = scanRecord?.getServiceData(ParcelUuid(GattServerManager.SERVICE_UUID))
        
        // Verify protocol version compatibility (version 1)
        val protocolVersion = serviceData?.getOrNull(0)?.toInt() ?: 1
        if (protocolVersion != 1) {
            Log.w(TAG, "Incompatible protocol version: $protocolVersion")
            return
        }
        
        // Update routing table and attempt connection
        coroutineScope.launch {
            // Store discovered node in database
            val nodeId = device.address // Temporary until we exchange actual node ID
            val timestamp = System.currentTimeMillis()
            
            bleNodeDao.insertNode(
                com.linker.app.data.local.entity.BleNodeEntity(
                    nodeId = nodeId,
                    deviceAddress = device.address,
                    rssi = rssi,
                    lastSeen = timestamp,
                    isConnected = false
                )
            )
            
            // Update routing table with discovered peer
            routingTable.addRoute(
                nodeId = nodeId,
                deviceAddress = device.address,
                rssi = rssi,
                hopCount = 1,
                timestamp = timestamp
            )
            
            // Attempt connection if not already connected and under connection limit
            if (!gattClientManager.isConnected(device.address) && 
                gattClientManager.getConnectionCount() < 7) {
                val result = connectToPeer(device.address)
                if (result.isSuccess) {
                    Log.d(TAG, "Successfully connected to ${device.address}")
                    // Update node as connected
                    bleNodeDao.updateConnectionStatusByAddress(device.address, true, System.currentTimeMillis())
                } else {
                    Log.w(TAG, "Failed to connect to ${device.address}: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }
    
    private fun handleIncomingPacket(packet: BLEPacket) {
        coroutineScope.launch {
            // Check for duplicates
            if (messageIdCache.contains(packet.messageId)) {
                Log.d(TAG, "Duplicate packet ${packet.messageId}, ignoring")
                return@launch
            }
            
            // Add to cache
            messageIdCache.add(packet.messageId, packet.senderId)
            
            // Get current user ID
            val currentUserId = currentUserProvider.getCurrentUserId()
            
            // Check if packet is for us
            val isForUs = currentUserId != null && packet.recipientId == currentUserId
            
            if (isForUs) {
                // Deliver locally
                messageReceivedCallback?.invoke(packet)
                Log.d(TAG, "Packet ${packet.messageId} delivered locally to $currentUserId")
            } else if (packet.ttl > 0) {
                // Forward to next hop if TTL allows
                val result = forwardMessage(packet)
                if (result.isSuccess) {
                    Log.d(TAG, "Packet ${packet.messageId} forwarded (TTL: ${packet.ttl}, Hops: ${packet.hopCount})")
                } else {
                    Log.w(TAG, "Failed to forward packet ${packet.messageId}: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // TTL exhausted, drop packet
                Log.w(TAG, "Packet ${packet.messageId} dropped: TTL exhausted")
            }
        }
    }
    
    private suspend fun updateConnectedPeers() {
        val connectedAddresses = gattClientManager.getConnectedDevices()
        val nodes = connectedAddresses.mapNotNull { address ->
            bleNodeDao.getNodeByAddress(address)?.let { entity ->
                BleNode(
                    nodeId = entity.nodeId,
                    deviceAddress = entity.deviceAddress,
                    rssi = entity.rssi,
                    lastSeen = entity.lastSeen,
                    isConnected = true
                )
            }
        }
        _connectedPeers.value = nodes
    }
    
    private fun updateMeshStatus() {
        val peerCount = gattClientManager.getConnectionCount()
        _meshStatus.value = when {
            peerCount > 0 -> MeshStatus.Connected(peerCount)
            bleScanner != null -> MeshStatus.Scanning
            bleAdvertiser != null -> MeshStatus.Advertising
            else -> MeshStatus.Idle
        }
    }
}
