package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Message ID Cache Entity
 * 
 * Stores received BLE packet message IDs to prevent duplicate processing
 * when the same packet arrives via multiple mesh routes.
 * 
 * Note: This is for BLE packet deduplication, separate from MessageDeduplicationManager
 * which handles race conditions when the same logical message arrives via BLE and online.
 */
@Entity(
    tableName = "message_id_cache",
    indices = [
        Index(value = ["receivedAt"]),
        Index(value = ["sourceNodeId"])
    ]
)
data class MessageIdCacheEntity(
    @PrimaryKey
    val messageId: String,           // BLE packet message UUID
    val receivedAt: Long,            // When packet was first received (millis)
    val sourceNodeId: String         // Node that sent the packet
) {
    init {
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        require(sourceNodeId.isNotBlank()) { "Source node ID cannot be blank" }
        require(receivedAt > 0) { "Received timestamp must be positive" }
    }
}
