package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BLE Mesh Node Entity
 * 
 * Stores information about discovered BLE mesh nodes for routing purposes.
 * Nodes are cached to reduce scan overhead and maintain routing table.
 */
@Entity(
    tableName = "ble_nodes",
    indices = [
        Index(value = ["deviceAddress"], unique = true),
        Index(value = ["isConnected"]),
        Index(value = ["lastSeen"]),
        Index(value = ["rssi"])
    ]
)
data class BleNodeEntity(
    @PrimaryKey
    val nodeId: String,              // User ID of the node
    val deviceAddress: String,       // BLE MAC address
    val deviceName: String?,         // Device name (nullable)
    val rssi: Int,                   // Signal strength (dBm)
    val lastSeen: Long,              // Last seen timestamp (millis)
    val isConnected: Boolean,        // Current connection status
    val hopCount: Int = 1,           // Hops to reach this node
    val routeQuality: Float = 0f,    // Route quality score (0-1)
    val createdAt: Long,             // Creation timestamp
    val updatedAt: Long              // Last update timestamp
) {
    init {
        require(nodeId.isNotBlank()) { "Node ID cannot be blank" }
        require(rssi in -100..0) { "RSSI must be between -100 and 0 dBm" }
        require(hopCount >= 1) { "Hop count must be at least 1" }
        require(routeQuality in 0f..1f) { "Route quality must be between 0 and 1" }
        require(deviceAddress.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
            "Invalid MAC address format"
        }
        require(createdAt > 0) { "Created timestamp must be positive" }
        require(updatedAt >= createdAt) { "Updated timestamp cannot be before created timestamp" }
        require(lastSeen >= createdAt) { "Last seen cannot be before creation" }
    }
}
