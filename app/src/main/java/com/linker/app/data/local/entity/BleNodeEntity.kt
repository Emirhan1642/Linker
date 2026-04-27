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
        Index(value = ["lastSeen"]),
        Index(value = ["isConnected"])
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
)
