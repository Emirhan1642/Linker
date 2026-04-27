package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.BleNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for BLE Mesh Node operations
 */
@Dao
interface BleNodeDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: BleNodeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<BleNodeEntity>)
    
    @Update
    suspend fun updateNode(node: BleNodeEntity)
    
    @Delete
    suspend fun deleteNode(node: BleNodeEntity)
    
    @Query("SELECT * FROM ble_nodes WHERE nodeId = :nodeId")
    suspend fun getNodeById(nodeId: String): BleNodeEntity?
    
    @Query("SELECT * FROM ble_nodes WHERE deviceAddress = :deviceAddress")
    suspend fun getNodeByAddress(deviceAddress: String): BleNodeEntity?
    
    @Query("SELECT * FROM ble_nodes WHERE isConnected = 1")
    suspend fun getConnectedNodes(): List<BleNodeEntity>
    
    @Query("SELECT * FROM ble_nodes WHERE isConnected = 1")
    fun observeConnectedNodes(): Flow<List<BleNodeEntity>>
    
    @Query("SELECT * FROM ble_nodes ORDER BY lastSeen DESC")
    suspend fun getAllNodes(): List<BleNodeEntity>
    
    @Query("SELECT * FROM ble_nodes ORDER BY lastSeen DESC")
    fun observeAllNodes(): Flow<List<BleNodeEntity>>
    
    /**
     * Get nodes seen within the last N milliseconds
     */
    @Query("SELECT * FROM ble_nodes WHERE lastSeen > :sinceTimestamp ORDER BY lastSeen DESC")
    suspend fun getRecentNodes(sinceTimestamp: Long): List<BleNodeEntity>
    
    /**
     * Delete nodes not seen since the given timestamp (stale nodes)
     */
    @Query("DELETE FROM ble_nodes WHERE lastSeen < :beforeTimestamp")
    suspend fun deleteStaleNodes(beforeTimestamp: Long): Int
    
    /**
     * Update connection status for a node
     */
    @Query("UPDATE ble_nodes SET isConnected = :isConnected, updatedAt = :timestamp WHERE nodeId = :nodeId")
    suspend fun updateConnectionStatus(nodeId: String, isConnected: Boolean, timestamp: Long)
    
    /**
     * Update RSSI and last seen timestamp
     */
    @Query("UPDATE ble_nodes SET rssi = :rssi, lastSeen = :timestamp, updatedAt = :timestamp WHERE nodeId = :nodeId")
    suspend fun updateRssiAndLastSeen(nodeId: String, rssi: Int, timestamp: Long)
    
    /**
     * Get nodes with signal strength above threshold
     */
    @Query("SELECT * FROM ble_nodes WHERE rssi > :minRssi AND isConnected = 1 ORDER BY rssi DESC")
    suspend fun getNodesWithGoodSignal(minRssi: Int): List<BleNodeEntity>
    
    /**
     * Clear all nodes
     */
    @Query("DELETE FROM ble_nodes")
    suspend fun clearAll()
    
    /**
     * Get node count
     */
    @Query("SELECT COUNT(*) FROM ble_nodes")
    suspend fun getNodeCount(): Int
    
    @Query("SELECT COUNT(*) FROM ble_nodes WHERE isConnected = 1")
    suspend fun getConnectedNodeCount(): Int
    
    @Query("SELECT COUNT(*) FROM ble_nodes WHERE isConnected = 1")
    fun observeConnectedNodeCount(): Flow<Int>
}
