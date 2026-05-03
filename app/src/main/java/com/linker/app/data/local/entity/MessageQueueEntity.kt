package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Message Queue Entity - Pending offline messages
 * 
 * Stores messages waiting to be sent via BLE mesh or when online
 */
@Entity(
    tableName = "message_queue",
    indices = [
        Index(value = ["queueStatus"]),
        Index(value = ["priority"]),
        Index(value = ["createdAt"])
    ]
)
data class MessageQueueEntity(
    @PrimaryKey
    val queueId: String,
    val messageId: String,
    val chatId: String,
    val recipientId: String,
    val messagePayload: String, // Serialized message data
    val messageType: MessageType = MessageType.TEXT, // Type of message for proper handling
    val queueStatus: QueueStatus, // PENDING, SENDING, SENT, FAILED
    val deliveryMethod: DeliveryMethod,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val priority: Int = 0, // Higher = more important
    val ttl: Int = 5, // Time-to-live in hops for BLE mesh
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
    val sentAt: Long? = null,
    val errorMessage: String? = null,
    val pendingKeyExchange: Boolean = false // True if waiting for recipient's encryption key
)

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
