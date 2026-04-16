package com.linker.app.domain.model

/**
 * Detailed message delivery status for UI display
 */
sealed class MessageDeliveryStatus {
    object Pending : MessageDeliveryStatus()
    object Sending : MessageDeliveryStatus()
    data class Sent(val sentAt: Long) : MessageDeliveryStatus()
    data class Delivered(val deliveredAt: Long) : MessageDeliveryStatus()
    data class Read(val readAt: Long) : MessageDeliveryStatus()
    data class Failed(
        val failedAt: Long,
        val reason: String,
        val canRetry: Boolean
    ) : MessageDeliveryStatus()

    /**
     * Get display text for status
     */
    fun toDisplayText(): String = when (this) {
        is Pending -> "Pending"
        is Sending -> "Sending..."
        is Sent -> "Sent"
        is Delivered -> "Delivered"
        is Read -> "Read"
        is Failed -> "Failed"
    }

    /**
     * Check if status is terminal (won't change without user action)
     */
    fun isTerminal(): Boolean = this is Read || this is Failed
}

/**
 * Convert MessageStatus to DeliveryStatus
 */
fun MessageStatus.toDeliveryStatus(timestamp: Long? = null): MessageDeliveryStatus {
    return when (this) {
        MessageStatus.SENDING -> MessageDeliveryStatus.Sending
        MessageStatus.SENT -> MessageDeliveryStatus.Sent(timestamp ?: System.currentTimeMillis())
        MessageStatus.DELIVERED -> MessageDeliveryStatus.Delivered(timestamp ?: System.currentTimeMillis())
        MessageStatus.READ -> MessageDeliveryStatus.Read(timestamp ?: System.currentTimeMillis())
        MessageStatus.FAILED -> MessageDeliveryStatus.Failed(
            failedAt = timestamp ?: System.currentTimeMillis(),
            reason = "Unknown error",
            canRetry = true
        )
    }
}
