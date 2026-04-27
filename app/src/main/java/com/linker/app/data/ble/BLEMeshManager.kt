package com.linker.app.data.ble

import kotlinx.coroutines.flow.Flow

/**
 * Interface for BLE Mesh Network management
 * 
 * Handles peer discovery, connection management, and message routing
 * for the BLE mesh network.
 */
interface BLEMeshManager {
    
    // Lifecycle
    fun initialize()
    fun startMeshNetwork()
    fun stopMeshNetwork()
    
    // Peer Management
    fun startScanning()
    fun stopScanning()
    fun startAdvertising()
    fun stopAdvertising()
    suspend fun connectToPeer(deviceAddress: String): Result<Unit>
    suspend fun disconnectFromPeer(deviceAddress: String)
    
    // Message Routing
    suspend fun sendMessage(packet: BLEPacket): Result<Unit>
    suspend fun forwardMessage(packet: BLEPacket): Result<Unit>
    fun onMessageReceived(callback: (BLEPacket) -> Unit)
    
    // Routing Table
    suspend fun updateRoutingTable(nodeId: String, rssi: Int, timestamp: Long)
    suspend fun getRouteToPeer(recipientId: String): List<String>?
    suspend fun cleanupStaleNodes()
    
    // State
    fun observeConnectedPeers(): Flow<List<BleNode>>
    fun observeMeshStatus(): Flow<MeshStatus>
}

/**
 * Represents a BLE mesh node
 */
data class BleNode(
    val nodeId: String,
    val deviceAddress: String,
    val rssi: Int,
    val lastSeen: Long,
    val isConnected: Boolean
)

/**
 * Mesh network status
 */
sealed class MeshStatus {
    object Idle : MeshStatus()
    object Scanning : MeshStatus()
    object Advertising : MeshStatus()
    data class Connected(val peerCount: Int) : MeshStatus()
    data class Error(val message: String) : MeshStatus()
}
