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
        Index(value = ["queueStatus", "priority", "createdAt"], name = "idx_queue_processing"),
        Index(value = ["chatId"]),
        Index(value = ["messageId"], unique = true)
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
) {
    init {
        require(queueId.isNotBlank()) { "Queue ID cannot be blank" }
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(recipientId.isNotBlank()) { "Recipient ID cannot be blank" }
        require(messagePayload.isNotBlank()) { "Message payload cannot be blank" }
        require(retryCount >= 0) { "Retry count cannot be negative" }
        require(retryCount <= maxRetries) { "Retry count cannot exceed max retries" }
        require(maxRetries > 0) { "Max retries must be positive" }
        require(priority >= 0) { "Priority cannot be negative" }
        require(ttl > 0) { "TTL must be positive" }
        require(ttl <= 10) { "TTL cannot exceed 10 hops" }
        
        // Status consistency
        if (queueStatus == QueueStatus.SENT) {
            require(sentAt != null) { "SENT status must have sentAt timestamp" }
        }
        
        if (queueStatus == QueueStatus.FAILED) {
            require(retryCount >= maxRetries || errorMessage != null) {
                "FAILED status must have reached max retries or have error message"
            }
        }
        
        // Timestamp validations
        lastAttemptAt?.let {
            require(it >= createdAt) { "Last attempt cannot be before creation" }
        }
        sentAt?.let {
            require(it >= createdAt) { "Sent time cannot be before creation" }
        }
    }

    fun canRetry(): Boolean {
        return queueStatus == QueueStatus.FAILED && retryCount < maxRetries
    }

    fun shouldExpire(maxAge: Long = 86400000L): Boolean {
        return System.currentTimeMillis() - createdAt > maxAge
    }

    fun getNextRetryDelay(): Long {
        // Exponential backoff
        return (1000L * (1 shl retryCount)).coerceAtMost(60000L)
    }

    companion object {
        const val MAX_TTL = 10
        const val DEFAULT_MAX_RETRIES = 3
        const val HIGH_PRIORITY = 10
        const val NORMAL_PRIORITY = 5
        const val LOW_PRIORITY = 1
    }
}

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
