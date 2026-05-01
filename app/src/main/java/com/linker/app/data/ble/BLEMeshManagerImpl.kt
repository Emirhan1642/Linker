package com.linker.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
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
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider,
    private val adaptiveScanningStrategy: AdaptiveScanningStrategy,
    private val fragmentManager: FragmentManager
) : BLEMeshManager {
    
    companion object {
        private const val TAG = "BLEMeshManager"
        private const val CONNECTION_RETRY_DELAY_MS = 2000L // 2 seconds initial delay
        private const val CONNECTION_MAX_RETRIES = 3
    }
    
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var isScanning = false
    private var isAdvertising = false
    
    // Track connection attempts to avoid duplicate retries
    private val connectionAttempts = mutableMapOf<String, Int>()
    
    // Map sender user ID to MAC address for routing
    private val userIdToMacAddress = mutableMapOf<String, String>()
    
    // Track incoming connections (MAC address -> connection time)
    private val incomingConnections = mutableMapOf<String, Long>()
    
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
            isAdvertising = true
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
            gattServerManager.incomingPackets.collect { packetWithAddress ->
                handleIncomingPacket(packetWithAddress.packet, packetWithAddress.senderMacAddress)
            }
        }
        
        // Listen to incoming GATT connections from other devices
        coroutineScope.launch {
            gattServerManager.incomingConnections.collect { deviceAddress ->
                Log.d(TAG, "Incoming GATT connection from $deviceAddress")
                // Track incoming connection
                incomingConnections[deviceAddress] = System.currentTimeMillis()
                
                // Add temporary route with MAC address
                // This will be updated when we receive the first packet with sender ID
                routingTable.addRoute(
                    nodeId = deviceAddress,
                    deviceAddress = deviceAddress,
                    rssi = -50, // Default RSSI for connected devices
                    hopCount = 1,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(TAG, "Added temporary route for incoming connection from $deviceAddress")
                
                // Store MAC address for later mapping with user ID
                // We'll update this when we receive a packet from this device
                Log.d(TAG, "Stored MAC address for incoming connection: $deviceAddress")
            }
        }
        
        // Observe adaptive scanning settings
        coroutineScope.launch {
            adaptiveScanningStrategy.scanSettings.collect { settings ->
                // Restart scanning with new settings if currently scanning
                if (isScanning) {
                    stopScanning()
                    startScanning()
                }
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
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopMeshNetwork() {
        stopScanning()
        stopAdvertising()
        gattServerManager.stopServer()
        gattClientManager.disconnectAll()
        
        // Cancel all coroutines
        coroutineScope.coroutineContext[Job]?.cancelChildren()
        
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
            val scanSettings = adaptiveScanningStrategy.scanSettings.value
            
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
                .build()
            
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            
            isScanning = true
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
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
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
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
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
                    
                    // Send a test packet to establish routing
                    // This allows the other device to learn our user ID
                    try {
                        val currentUserId = currentUserProvider.getCurrentUserId()
                        if (currentUserId != null) {
                            Log.d(TAG, "Sending test packet to $deviceAddress to establish routing")
                            
                            val testPacket = BLEPacket.create(
                                messageId = "test-${System.currentTimeMillis()}",
                                senderId = currentUserId,
                                recipientId = deviceAddress, // Use MAC address as temporary recipient
                                ttl = 1,
                                hopCount = 0,
                                encryptedPayload = "HELLO".toByteArray()
                            )
                            
                            val data = BLEPacket.serialize(testPacket)
                            val writeSuccess = gattClientManager.writeCharacteristic(deviceAddress, data)
                            
                            if (writeSuccess) {
                                Log.d(TAG, "Test packet sent successfully to $deviceAddress")
                            } else {
                                Log.w(TAG, "Failed to send test packet to $deviceAddress")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending test packet to $deviceAddress", e)
                    }
                    
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
                Log.w(TAG, "No route to recipient ${packet.recipientId} - checking connected devices")
                
                // Log available routes for debugging
                val connectedDevices = gattClientManager.getConnectedDevices()
                Log.w(TAG, "Currently connected devices: $connectedDevices (count: ${connectedDevices.size})")
                
                return Result.failure(Exception("No route to recipient ${packet.recipientId}"))
            }
            
            Log.d(TAG, "Found route to ${packet.recipientId} via ${route.deviceAddress} (quality: ${route.routeQuality}, hops: ${route.hopCount})")
            
            // Serialize packet
            val data = BLEPacket.serialize(packet)
            
            // Send to next hop
            Log.d(TAG, "Writing characteristic to ${route.deviceAddress} (packet size: ${data.size} bytes)")
            val success = gattClientManager.writeCharacteristic(route.deviceAddress, data)
            
            if (success) {
                Log.d(TAG, "Message ${packet.messageId} sent successfully to ${route.deviceAddress}")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to send message ${packet.messageId} to ${route.deviceAddress}")
                Result.failure(Exception("Write failed to ${route.deviceAddress}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message ${packet.messageId}", e)
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
        // Note: deviceAddress will be updated when actual connection is established
        routingTable.addRoute(
            nodeId = nodeId,
            deviceAddress = nodeId, // Use nodeId as temporary address until connection
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
                    deviceName = try { device.name } catch (e: SecurityException) { null },
                    rssi = rssi,
                    lastSeen = timestamp,
                    isConnected = false,
                    hopCount = 1,
                    routeQuality = 0.0f,
                    createdAt = timestamp,
                    updatedAt = timestamp
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
                
                // Check if we've already attempted this connection
                val attemptCount = connectionAttempts.getOrDefault(device.address, 0)
                
                if (attemptCount < CONNECTION_MAX_RETRIES) {
                    // Calculate delay with exponential backoff
                    val delayMs = CONNECTION_RETRY_DELAY_MS * (1 shl attemptCount) // 2s, 4s, 8s
                    
                    Log.d(TAG, "Scheduling connection attempt ${attemptCount + 1}/$CONNECTION_MAX_RETRIES to ${device.address} after ${delayMs}ms")
                    
                    delay(delayMs)
                    
                    val result = connectToPeer(device.address)
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully connected to ${device.address}")
                        connectionAttempts.remove(device.address)
                        // Update node as connected
                        bleNodeDao.updateConnectionStatusByAddress(device.address, true, System.currentTimeMillis())
                    } else {
                        Log.w(TAG, "Failed to connect to ${device.address} (attempt ${attemptCount + 1}): ${result.exceptionOrNull()?.message}")
                        connectionAttempts[device.address] = attemptCount + 1
                        
                        // Schedule retry if we haven't exceeded max retries
                        if (attemptCount + 1 < CONNECTION_MAX_RETRIES) {
                            Log.d(TAG, "Will retry connection to ${device.address} on next scan")
                        } else {
                            Log.e(TAG, "Max connection attempts reached for ${device.address}, giving up")
                        }
                    }
                } else {
                    Log.w(TAG, "Already attempted max retries for ${device.address}, skipping")
                }
            }
        }
    }
    
    private suspend fun handleIncomingPacket(packet: BLEPacket, senderMacAddress: String) {
            // Check if this is a test packet (used for routing establishment)
            val isTestPacket = packet.messageId.startsWith("test-")
            
            if (isTestPacket) {
                Log.d(TAG, "Received test packet from ${packet.senderId}, establishing bidirectional connection")
            }
            
            // Check for duplicates
            if (messageIdCache.contains(packet.messageId)) {
                Log.d(TAG, "Duplicate packet ${packet.messageId}, ignoring")
                return
            }
            
            // Add to cache
            messageIdCache.add(packet.messageId, packet.senderId)
            
            // Update routing table with sender's user ID
            // This allows us to send messages back to the sender
            Log.d(TAG, "Updating routing table: sender ${packet.senderId} is reachable via BLE")
            try {
                // Map sender user ID to MAC address for future routing
                userIdToMacAddress[packet.senderId] = senderMacAddress
                Log.d(TAG, "Mapped sender ${packet.senderId} to MAC address $senderMacAddress")
                
                // Add route using sender ID as nodeId and MAC address as deviceAddress
                routingTable.addRoute(
                    nodeId = packet.senderId,
                    deviceAddress = senderMacAddress,
                    rssi = -50, // Default RSSI for received packets
                    hopCount = packet.hopCount.toInt() + 1,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(TAG, "Route updated for sender ${packet.senderId} via $senderMacAddress")
                
                // If this is a test packet, establish an outgoing connection to enable bidirectional communication
                if (isTestPacket) {
                    Log.d(TAG, "Initiating outgoing connection to $senderMacAddress for bidirectional messaging")
                    try {
                        val bluetoothAdapter = bluetoothAdapter
                        if (bluetoothAdapter != null) {
                            val device = bluetoothAdapter.getRemoteDevice(senderMacAddress)
                            
                            // Check if not already connected
                            if (!gattClientManager.isConnected(senderMacAddress)) {
                                val connected = gattClientManager.connectToDevice(device)
                                if (connected) {
                                    Log.d(TAG, "Successfully established outgoing connection to $senderMacAddress")
                                } else {
                                    Log.w(TAG, "Failed to establish outgoing connection to $senderMacAddress")
                                }
                            } else {
                                Log.d(TAG, "Already have outgoing connection to $senderMacAddress")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error establishing outgoing connection to $senderMacAddress", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating routing table for sender ${packet.senderId}", e)
            }
            
            // Don't process test packets further
            if (isTestPacket) {
                Log.d(TAG, "Test packet processed, not forwarding")
                return
            }
            
            // Handle fragmentation
            val completePayload = if (packet.totalFragments > 1) {
                // This is a fragmented message
                val reassembledPayload = fragmentManager.addFragment(packet)
                
                if (reassembledPayload == null) {
                    // Waiting for more fragments
                    Log.d(TAG, "Received fragment ${packet.fragmentIndex + 1}/${packet.totalFragments} for ${packet.messageId}")
                    return
                }
                
                // All fragments received, create complete packet with reassembled payload
                packet.copy(encryptedPayload = reassembledPayload)
            } else {
                // Single packet message
                packet
            }
            
            // Get current user ID
            val currentUserId = currentUserProvider.getCurrentUserId()
            
            // Check if packet is for us
            val isForUs = currentUserId != null && completePayload.recipientId == currentUserId
            
            if (isForUs) {
                // Deliver locally
                messageReceivedCallback?.invoke(completePayload)
                Log.d(TAG, "Packet ${completePayload.messageId} delivered locally to $currentUserId")
            } else if (completePayload.ttl > 0) {
                // Forward to next hop if TTL allows
                val result = forwardMessage(completePayload)
                if (result.isSuccess) {
                    Log.d(TAG, "Packet ${completePayload.messageId} forwarded (TTL: ${completePayload.ttl}, Hops: ${completePayload.hopCount})")
                } else {
                    Log.w(TAG, "Failed to forward packet ${completePayload.messageId}: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // TTL exhausted, drop packet
                Log.w(TAG, "Packet ${completePayload.messageId} dropped: TTL exhausted")
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
            isScanning -> MeshStatus.Scanning
            isAdvertising -> MeshStatus.Advertising
            else -> MeshStatus.Idle
        }
    }
}
