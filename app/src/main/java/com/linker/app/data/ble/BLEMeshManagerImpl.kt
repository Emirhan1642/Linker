package com.linker.app.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.linker.app.core.util.SecureLogger as Log
import com.linker.app.data.local.dao.BleNodeDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val currentUserProviderLazy: dagger.Lazy<com.linker.app.domain.usecase.user.CurrentUserProvider>,
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
    
    // Mutex for thread-safe routing table updates
    private val routingTableMutex = kotlinx.coroutines.sync.Mutex()
    
    private var isScanning = false
    private val scanLock = Any()
    private var lastScanStartTime = 0L
    private val MIN_SCAN_RESTART_INTERVAL_MS = 6000L // 6s debounce/cooldown to protect against Android OS scan throttling (max 5 per 30s)
    private val scanStartTimestamps = ArrayDeque<Long>()
    private val SCAN_WINDOW_MS = 30_000L
    private val MAX_SCANS_PER_WINDOW = 4 // Keep strictly under Android's 5 scans per 30s hard limit
    private var pendingScanJob: Job? = null
    @Volatile
    private var shouldBeScanning = false
    private var isAdvertising = false
    private val packetFragmenter = PacketFragmenter()
    
    // Track connection attempts to avoid duplicate retries
    private val connectionAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val pendingConnectingDevices = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    
    // Map sender user ID to MAC address for routing (thread-safe)
    private val userIdToMacAddress = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    // Track incoming connections (MAC address -> connection time)
    private val incomingConnections = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    // Track recently processed scan results to avoid duplicate processing
    private val recentlyProcessedDevices = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val SCAN_PROCESS_COOLDOWN = 2000L // 2 seconds cooldown between processing same device
    
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
        val adapter = bluetoothManager?.adapter
        bluetoothAdapter = adapter
        
        if (adapter == null) {
            Log.e(TAG, "Bluetooth adapter not available")
            _meshStatus.value = MeshStatus.Error("Bluetooth not available")
            return
        }
        
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled")
            _meshStatus.value = MeshStatus.Error("Bluetooth disabled")
            return
        }
        
        bleScanner = adapter.bluetoothLeScanner
        bleAdvertiser = adapter.bluetoothLeAdvertiser
        
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
                
                // Try to get actual RSSI from GattClientManager if we have an outgoing connection
                // Otherwise use default value
                val actualRssi = gattClientManager.getRssi(deviceAddress) ?: run {
                    // If no RSSI available, try to read it
                    gattClientManager.readRemoteRssi(deviceAddress)
                    -60 // Default RSSI while waiting for read result
                }
                
                // Add temporary route with MAC address
                // This will be updated when we receive the first packet with sender ID
                routingTable.addRoute(
                    nodeId = deviceAddress,
                    deviceAddress = deviceAddress,
                    rssi = actualRssi,
                    hopCount = 1,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(TAG, "Added temporary route for incoming connection from $deviceAddress with RSSI $actualRssi")
                
                // Store MAC address for later mapping with user ID
                // We'll update this when we receive a packet from this device
                Log.d(TAG, "Stored MAC address for incoming connection: $deviceAddress")
            }
        }
        
        // Listen to disconnections from GATT client
        coroutineScope.launch {
            gattClientManager.disconnectedDevices.collect { deviceAddress ->
                Log.d(TAG, "GATT client disconnected from $deviceAddress, updating connection status")
                withContext(Dispatchers.IO) {
                    bleNodeDao.updateConnectionStatusByAddress(deviceAddress, false, System.currentTimeMillis())
                }
                updateConnectedPeers()
                updateMeshStatus()
            }
        }
        
        // Listen to disconnections from GATT server
        coroutineScope.launch {
            gattServerManager.incomingDisconnections.collect { deviceAddress ->
                Log.d(TAG, "GATT server connection closed from $deviceAddress")
                incomingConnections.remove(deviceAddress)
            }
        }
        
        // Observe adaptive scanning settings with distinctUntilChanged and debounce to protect against OS scan throttling
        coroutineScope.launch {
            adaptiveScanningStrategy.scanSettings
                .distinctUntilChanged { old, new -> old.scanMode == new.scanMode }
                .drop(1) // Skip initial emission already handled by startScanning
                .debounce(MIN_SCAN_RESTART_INTERVAL_MS)
                .collect { settings ->
                    // Restart scanning with new settings if currently scanning
                    if (isScanning) {
                        Log.d(TAG, "Adaptive scan mode changed to ${settings.scanMode}, safely restarting scanner")
                        stopScanning()
                        startScanning()
                    }
                }
        }
        
        Log.d(TAG, "BLE Mesh Manager initialized")
    }
    
    override fun startMeshNetwork() {
        // Start monitoring battery and screen state for adaptive scanning
        adaptiveScanningStrategy.startMonitoring()
        
        // Start GATT server
        if (!gattServerManager.startServer()) {
            Log.e(TAG, "Failed to start GATT server")
            _meshStatus.value = MeshStatus.Error("Failed to start GATT server")
            return
        }
        
        // Start scanning and advertising
        startScanning()
        startAdvertising()
        
        // Start periodic cleanup of stale routes (every 60 seconds)
        coroutineScope.launch {
            while (isActive) {
                delay(60_000L) // 60 seconds
                try {
                    val removedCount = routingTable.removeStaleRoutes()
                    if (removedCount > 0) {
                        Log.d(TAG, "Removed $removedCount stale routes from routing table")
                    }
                    messageIdCache.cleanup()
                    
                    // Clean up old scan result cache entries (older than 5 minutes)
                    val now = System.currentTimeMillis()
                    val staleDevices = recentlyProcessedDevices.filter { (_, timestamp) ->
                        now - timestamp > 300_000L // 5 minutes
                    }.keys
                    staleDevices.forEach { recentlyProcessedDevices.remove(it) }
                    if (staleDevices.isNotEmpty()) {
                        Log.d(TAG, "Cleaned up ${staleDevices.size} stale scan cache entries")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic cleanup", e)
                }
            }
        }
        
        Log.d(TAG, "Mesh network started")
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopMeshNetwork() {
        stopScanning()
        stopAdvertising()
        adaptiveScanningStrategy.stopMonitoring()
        gattServerManager.stopServer()
        gattClientManager.disconnectAll()
        
        // Clean up fragment manager to prevent memory leaks
        fragmentManager.shutdown()
        
        // Cancel all coroutines but keep scope alive
        coroutineScope.coroutineContext[Job]?.cancelChildren()
        
        _meshStatus.value = MeshStatus.Idle
        _connectedPeers.value = emptyList()
        
        Log.d(TAG, "Mesh network stopped")
    }

    override fun shutdown() {
        try {
            stopScanning()
            stopAdvertising()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping during shutdown", e)
        }
        adaptiveScanningStrategy.stopMonitoring()
        gattServerManager.stopServer()
        gattClientManager.disconnectAll()
        fragmentManager.shutdown()
        coroutineScope.cancel() // Fully cancel scope
        Log.d(TAG, "Mesh manager shutdown complete")
    }
    
    override fun startScanning() {
        synchronized(scanLock) {
            shouldBeScanning = true
            pendingScanJob?.cancel()
            pendingScanJob = null
            
            if (isScanning) {
                Log.d(TAG, "Scanning already in progress, skipping startScan request")
                return
            }
            if (bleScanner == null) {
                Log.e(TAG, "BLE scanner not available")
                return
            }
            
            val now = System.currentTimeMillis()
            // Evict scan timestamps older than 30s sliding window
            while (scanStartTimestamps.isNotEmpty() && (now - scanStartTimestamps.first() > SCAN_WINDOW_MS)) {
                scanStartTimestamps.removeFirst()
            }
            
            // Limit to max 4 scans per 30-second window to strictly avoid Android OS scan throttling (max 5 per 30s)
            if (scanStartTimestamps.size >= MAX_SCANS_PER_WINDOW) {
                val oldest = scanStartTimestamps.first()
                val waitTime = (SCAN_WINDOW_MS - (now - oldest)) + 500L
                Log.w(TAG, "Scan start throttled by sliding 30s window (already ${scanStartTimestamps.size} scans). Delaying for ${waitTime}ms")
                pendingScanJob = coroutineScope.launch {
                    delay(waitTime)
                    synchronized(scanLock) {
                        if (shouldBeScanning && !isScanning) {
                            startScanningInternal()
                        }
                    }
                }
                return
            }
            
            // Enforce minimum interval between consecutive starts
            val timeSinceLastStart = now - lastScanStartTime
            if (timeSinceLastStart < MIN_SCAN_RESTART_INTERVAL_MS) {
                val waitTime = MIN_SCAN_RESTART_INTERVAL_MS - timeSinceLastStart
                Log.w(TAG, "Scan start throttled (last started ${timeSinceLastStart}ms ago, cooldown ${MIN_SCAN_RESTART_INTERVAL_MS}ms). Scheduling in ${waitTime}ms")
                pendingScanJob = coroutineScope.launch {
                    delay(waitTime)
                    synchronized(scanLock) {
                        if (shouldBeScanning && !isScanning) {
                            startScanningInternal()
                        }
                    }
                }
                return
            }
            
            startScanningInternal()
        }
    }
    
    private fun startScanningInternal() {
        if (!shouldBeScanning || isScanning) return
        val scanner = bleScanner ?: return
        
        try {
            val scanSettings = adaptiveScanningStrategy.scanSettings.value
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
                .build()
            
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            
            val now = System.currentTimeMillis()
            lastScanStartTime = now
            scanStartTimestamps.addLast(now)
            isScanning = true
            updateMeshStatus()
            Log.d(TAG, "Scanning started successfully (mode: ${scanSettings.scanMode})")
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
        synchronized(scanLock) {
            shouldBeScanning = false
            pendingScanJob?.cancel()
            pendingScanJob = null
            
            if (!isScanning) {
                Log.d(TAG, "Scanning already stopped")
                return
            }
            try {
                bleScanner?.stopScan(scanCallback)
                isScanning = false
                updateMeshStatus()
                Log.d(TAG, "Scanning stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
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
                // Get RSSI from routing table if available
                val route = routingTable.getRoute(deviceAddress)
                val rssi = route?.rssi ?: -60
                
                val connected = gattClientManager.connectToDevice(device, rssi)
                
                if (connected) {
                    updateConnectedPeers()
                    
                    // Send a test packet to establish routing
                    // This allows the other device to learn our user ID
                    try {
                        val currentUserId = currentUserProviderLazy.get().getCurrentUserId()
                        if (currentUserId != null) {
                            Log.d(TAG, "Sending test packet to $deviceAddress to establish routing")
                            
                            // For test packets, we use a simple encrypted payload
                            // Since we don't know the recipient's user ID yet, we can't use full Signal Protocol encryption
                            // Instead, we'll use a simple encrypted marker that can be recognized by the receiver
                            // Real messages will use full Signal Protocol encryption with the recipient's public key
                            val testPayload = "LINKER_HELLO_${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            val keyStream = digest.digest("LINKER_BLE_MESH_TEST_KEY".toByteArray(Charsets.UTF_8))
                            val encryptedTestPayload = ByteArray(testPayload.size) { i ->
                                (testPayload[i].toInt() xor keyStream[i % keyStream.size].toInt()).toByte()
                            }
                            
                            // Use UUID for message ID to prevent collisions (truncate to 36 chars max)
                            val testPacket = BLEPacket.create(
                                messageId = "test-${java.util.UUID.randomUUID().toString().replace("-", "").take(31)}",
                                senderId = currentUserId,
                                recipientId = deviceAddress, // Use MAC address as temporary recipient
                                ttl = 1,
                                hopCount = 0,
                                encryptedPayload = encryptedTestPayload
                            )
                            
                            val data = BLEPacket.serialize(testPacket)
                            val writeSuccess = gattClientManager.writeCharacteristicWithRetry(deviceAddress, data)
                            
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
            // If the payload exceeds single packet capacity and isn't already fragmented, fragment it first
            if (packet.encryptedPayload.size > BLEPacket.MAX_PAYLOAD_SIZE && packet.totalFragments <= 1) {
                Log.d(TAG, "Payload size (${packet.encryptedPayload.size} bytes) exceeds MTU capacity, fragmenting message ${packet.messageId}")
                val fragments = packetFragmenter.fragment(
                    messageId = packet.messageId,
                    senderId = packet.senderId,
                    recipientId = packet.recipientId,
                    ttl = packet.ttl,
                    encryptedPayload = packet.encryptedPayload,
                    hopCount = packet.hopCount
                )
                var allSuccessful = true
                for (fragment in fragments) {
                    val res = sendMessage(fragment)
                    if (res.isFailure) allSuccessful = false
                }
                return if (allSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to send all fragments for message ${packet.messageId}"))
            }

            // Get route to recipient
            val route = routingTable.getRoute(packet.recipientId)
            val data = BLEPacket.serialize(packet)
            
            if (route == null) {
                Log.w(TAG, "No direct route to recipient ${packet.recipientId} - attempting flooding fallback to connected peers")
                val connectedDevices = gattClientManager.getConnectedDevices()
                if (connectedDevices.isEmpty()) {
                    return Result.failure(Exception("No route to recipient ${packet.recipientId} and no connected peers"))
                }
                var anySent = false
                for (deviceAddr in connectedDevices) {
                    val success = gattClientManager.writeCharacteristicWithRetry(deviceAddr, data)
                    if (success) anySent = true
                }
                return if (anySent) Result.success(Unit) else Result.failure(Exception("Failed to send packet to any connected peer"))
            }
            
            Log.d(TAG, "Found route to ${packet.recipientId} via ${route.deviceAddress} (quality: ${route.routeQuality}, hops: ${route.hopCount})")
            
            // If not connected to route.deviceAddress, attempt connection before sending
            if (!gattClientManager.isConnected(route.deviceAddress)) {
                Log.d(TAG, "Not currently connected to ${route.deviceAddress}, attempting connection before sending")
                val device = bluetoothAdapter?.getRemoteDevice(route.deviceAddress)
                if (device != null) {
                    gattClientManager.connectToDevice(device, route.rssi)
                }
            }

            // Send to next hop with retry
            Log.d(TAG, "Writing characteristic to ${route.deviceAddress} (packet size: ${data.size} bytes)")
            val success = gattClientManager.writeCharacteristicWithRetry(route.deviceAddress, data)
            
            if (success) {
                Log.d(TAG, "Message ${packet.messageId} sent successfully to ${route.deviceAddress}")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Direct write failed to ${route.deviceAddress} for message ${packet.messageId}, invalidating route and attempting fallback")
                routingTable.removeRoute(packet.recipientId)
                
                // Fallback to flooding other connected peers
                val otherPeers = gattClientManager.getConnectedDevices().filter { it != route.deviceAddress }
                var anySent = false
                for (peerAddr in otherPeers) {
                    if (gattClientManager.writeCharacteristicWithRetry(peerAddr, data)) {
                        anySent = true
                    }
                }
                if (anySent) {
                    Log.d(TAG, "Message ${packet.messageId} sent via flooding fallback")
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "Failed to send message ${packet.messageId} to ${route.deviceAddress} or any fallback peer")
                    Result.failure(Exception("Write failed to ${route.deviceAddress} and all fallback peers"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message ${packet.messageId}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun forwardMessage(packet: BLEPacket): Result<Unit> {
        // Check TTL before decrementing
        if (packet.ttl <= 0) {
            Log.w(TAG, "TTL exhausted for message ${packet.messageId}, cannot forward")
            return Result.failure(Exception("TTL exhausted"))
        }
        
        val newTtl = (packet.ttl - 1).toByte()
        val newHopCount = (packet.hopCount + 1).toByte()

        // If the payload exceeds single packet capacity, re-fragment it with recalculated checksums and proper hopCount
        return if (packet.encryptedPayload.size > BLEPacket.MAX_PAYLOAD_SIZE) {
            val fragments = packetFragmenter.fragment(
                messageId = packet.messageId,
                senderId = packet.senderId,
                recipientId = packet.recipientId,
                ttl = newTtl,
                encryptedPayload = packet.encryptedPayload,
                hopCount = newHopCount
            )
            var allSuccessful = true
            for (fragment in fragments) {
                val res = sendMessage(fragment)
                if (res.isFailure) allSuccessful = false
            }
            if (allSuccessful) Result.success(Unit) else Result.failure(Exception("Failed to forward all fragments"))
        } else {
            // Use copyAndRecalculateChecksum to update CRC32 checksum for new TTL and hopCount
            val forwardedPacket = packet.copyAndRecalculateChecksum(
                ttl = newTtl,
                hopCount = newHopCount
            )
            sendMessage(forwardedPacket)
        }
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
        val deviceAddress = device.address
        val now = System.currentTimeMillis()
        
        // Check if we recently processed this device (within cooldown period)
        val lastProcessed = recentlyProcessedDevices[deviceAddress]
        if (lastProcessed != null && (now - lastProcessed) < SCAN_PROCESS_COOLDOWN) {
            // Skip processing, too soon since last scan
            return
        }
        
        // Update last processed time
        recentlyProcessedDevices[deviceAddress] = now
        
        Log.d(TAG, "Discovered device: $deviceAddress, RSSI: $rssi")
        
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
            val nodeId = deviceAddress // Temporary until we exchange actual node ID
            val timestamp = now
            
            bleNodeDao.insertNode(
                com.linker.app.data.local.entity.BleNodeEntity(
                    nodeId = nodeId,
                    deviceAddress = deviceAddress,
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
                deviceAddress = deviceAddress,
                rssi = rssi,
                hopCount = 1,
                timestamp = timestamp
            )
            
            // Attempt connection if not already connected and under connection limit
            if (!gattClientManager.isConnected(deviceAddress) && 
                gattClientManager.getConnectionCount() < 7 &&
                pendingConnectingDevices.putIfAbsent(deviceAddress, true) == null) {
                
                try {
                    // Check if we've already attempted this connection
                    val attemptCount = connectionAttempts.getOrDefault(deviceAddress, 0)
                    
                    if (attemptCount < CONNECTION_MAX_RETRIES) {
                        // Calculate delay with exponential backoff
                        val delayMs = CONNECTION_RETRY_DELAY_MS * (1 shl attemptCount) // 2s, 4s, 8s
                        
                        Log.d(TAG, "Scheduling connection attempt ${attemptCount + 1}/$CONNECTION_MAX_RETRIES to $deviceAddress after ${delayMs}ms")
                        
                        delay(delayMs)
                        
                        val result = connectToPeer(deviceAddress)
                        if (result.isSuccess) {
                            Log.d(TAG, "Successfully connected to $deviceAddress")
                            connectionAttempts.remove(deviceAddress)
                            // Update node as connected
                            bleNodeDao.updateConnectionStatusByAddress(deviceAddress, true, System.currentTimeMillis())
                        } else {
                            Log.w(TAG, "Failed to connect to $deviceAddress (attempt ${attemptCount + 1}): ${result.exceptionOrNull()?.message}")
                            connectionAttempts[deviceAddress] = attemptCount + 1
                            
                            // Schedule retry if we haven't exceeded max retries
                            if (attemptCount + 1 < CONNECTION_MAX_RETRIES) {
                                Log.d(TAG, "Will retry connection to $deviceAddress on next scan")
                            } else {
                                Log.e(TAG, "Max connection attempts reached for $deviceAddress, giving up")
                            }
                        }
                    } else {
                        Log.w(TAG, "Already attempted max retries for $deviceAddress, skipping")
                    }
                } finally {
                    pendingConnectingDevices.remove(deviceAddress)
                }
            }
        }
    }
    
    private suspend fun handleIncomingPacket(packet: BLEPacket, senderMacAddress: String) {
            // Check if this is a test packet (used for routing establishment)
            val isTestPacket = packet.messageId.startsWith("test-")
            
            if (isTestPacket) {
                Log.d(TAG, "Received test packet from ${packet.senderId}, establishing bidirectional connection")
                
                // Decrypt test packet payload (SHA-256 key stream decryption matching connectToPeer)
                try {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val keyStream = digest.digest("LINKER_BLE_MESH_TEST_KEY".toByteArray(Charsets.UTF_8))
                    val decryptedTestPayload = ByteArray(packet.encryptedPayload.size) { i ->
                        (packet.encryptedPayload[i].toInt() xor keyStream[i % keyStream.size].toInt()).toByte()
                    }
                    val testMessage = String(decryptedTestPayload, Charsets.UTF_8)
                    Log.d(TAG, "Test packet decrypted: $testMessage")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decrypt test packet: ${e.message}")
                }
            }
            
            // Check for duplicates (use fragment-aware key for fragmented messages)
            val packetDedupKey = if (packet.totalFragments > 1) {
                "${packet.messageId}_frag_${packet.fragmentIndex}"
            } else {
                packet.messageId
            }
            
            if (messageIdCache.contains(packetDedupKey)) {
                Log.d(TAG, "Duplicate packet $packetDedupKey, ignoring")
                return
            }
            
            // Add to cache
            messageIdCache.add(packetDedupKey, packet.senderId)
            
            // Update routing table with sender's user ID
            // This allows us to send messages back to the sender
            Log.d(TAG, "Updating routing table: sender ${packet.senderId} is reachable via BLE")
            try {
                // Use mutex to ensure atomic update of userIdToMacAddress and routing table
                routingTableMutex.withLock {
                    val oldMac = userIdToMacAddress[packet.senderId]
                    if (oldMac != null && oldMac != senderMacAddress) {
                        Log.d(TAG, "RPA address rotation detected for sender ${packet.senderId}: $oldMac -> $senderMacAddress")
                        // Safely disconnect from old stale MAC address
                        gattClientManager.disconnect(oldMac)
                    }
                    
                    // Map sender user ID to MAC address for future routing
                    userIdToMacAddress[packet.senderId] = senderMacAddress
                    Log.d(TAG, "Mapped sender ${packet.senderId} to MAC address $senderMacAddress")
                    
                    // Try to get actual RSSI from GattClientManager
                    val actualRssi = gattClientManager.getRssi(senderMacAddress) ?: run {
                        // If no RSSI available, try to read it
                        gattClientManager.readRemoteRssi(senderMacAddress)
                        -60 // Default RSSI while waiting for read result
                    }
                    
                    // Add route using sender ID as nodeId and MAC address as deviceAddress
                    routingTable.addRoute(
                        nodeId = packet.senderId,
                        deviceAddress = senderMacAddress,
                        rssi = actualRssi,
                        hopCount = packet.hopCount.toInt() + 1,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Route updated for sender ${packet.senderId} via $senderMacAddress with RSSI $actualRssi")
                }
                
                // Ensure bidirectional communication: establish an outgoing connection to senderMacAddress if not already connected
                if (!gattClientManager.isConnected(senderMacAddress)) {
                    Log.d(TAG, "Initiating outgoing connection to $senderMacAddress for bidirectional messaging")
                    coroutineScope.launch {
                        try {
                            val adapter = bluetoothAdapter
                            if (adapter != null) {
                                val device = adapter.getRemoteDevice(senderMacAddress)
                                val route = routingTable.getRoute(packet.senderId)
                                val rssi = route?.rssi ?: -60
                                val connected = gattClientManager.connectToDevice(device, rssi)
                                if (connected) {
                                    Log.d(TAG, "Successfully established outgoing connection to $senderMacAddress")
                                    updateConnectedPeers()
                                } else {
                                    Log.w(TAG, "Failed to establish outgoing connection to $senderMacAddress")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error establishing outgoing connection to $senderMacAddress", e)
                        }
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
            
            // Check if packet is for us
            val currentUserId = currentUserProviderLazy.get().getCurrentUserId()
            val isForUs = currentUserId != null && packet.recipientId == currentUserId
            
            if (isForUs) {
                // Deliver locally (handle fragmentation if needed)
                if (packet.totalFragments > 1) {
                    val reassembledPayload = fragmentManager.addFragment(packet)
                    if (reassembledPayload == null) {
                        Log.d(TAG, "Received fragment ${packet.fragmentIndex + 1}/${packet.totalFragments} for ${packet.messageId}")
                        return
                    }
                    val completePacket = BLEPacket.create(
                        version = packet.version,
                        messageId = packet.messageId,
                        senderId = packet.senderId,
                        recipientId = packet.recipientId,
                        ttl = packet.ttl,
                        hopCount = packet.hopCount,
                        fragmentIndex = 0,
                        totalFragments = 1,
                        encryptedPayload = reassembledPayload
                    )
                    messageReceivedCallback?.invoke(completePacket)
                    Log.d(TAG, "Fragmented packet ${packet.messageId} reassembled and delivered locally to $currentUserId")
                } else {
                    messageReceivedCallback?.invoke(packet)
                    Log.d(TAG, "Packet ${packet.messageId} delivered locally to $currentUserId")
                }
            } else if (packet.ttl > 0) {
                // Forward packet/fragment directly without reassembly at intermediate hops
                val result = forwardMessage(packet)
                if (result.isSuccess) {
                    Log.d(TAG, "Packet ${packet.messageId} (frag ${packet.fragmentIndex + 1}/${packet.totalFragments}) forwarded (TTL: ${packet.ttl}, Hops: ${packet.hopCount})")
                } else {
                    Log.w(TAG, "Failed to forward packet ${packet.messageId}: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // TTL exhausted, drop packet
                Log.w(TAG, "Packet ${packet.messageId} dropped: TTL exhausted")
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
