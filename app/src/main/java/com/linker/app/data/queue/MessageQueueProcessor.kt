package com.linker.app.data.queue

import com.linker.app.data.local.entity.DeliveryMethod
import kotlinx.coroutines.flow.Flow

/**
 * Interface for processing offline message queue
 * 
 * Handles queueing, processing, and retry logic for offline messages.
 */
interface MessageQueueProcessor {
    
    /**
     * Enqueue a message for offline delivery
     * 
     * @param messageId Message ID
     * @param chatId Chat ID
     * @param recipientId Recipient user ID
     * @param payload Encrypted message payload
     * @param deliveryMethod Delivery method (BLE, WIFI_DIRECT)
     * @return Result indicating success or failure
     */
    suspend fun enqueueMessage(
        messageId: String,
        chatId: String,
        recipientId: String,
        payload: String,
        deliveryMethod: DeliveryMethod
    ): Result<Unit>
    
    /**
     * Process pending messages in the queue
     */
    suspend fun processQueue()
    
    /**
     * Retry failed messages
     */
    suspend fun retryFailedMessages()
    
    /**
     * Cancel a specific message
     * 
     * @param messageId Message ID to cancel
     */
    suspend fun cancelMessage(messageId: String)
    
    /**
     * Clear sent messages from queue
     */
    suspend fun clearSentMessages()
    
    /**
     * Observe queue status
     */
    fun observeQueueStatus(): Flow<QueueStatus>
    
    /**
     * Observe pending message count
     */
    fun observePendingCount(): Flow<Int>
}

/**
 * Queue status information
 */
data class QueueStatus(
    val pendingCount: Int,
    val sendingCount: Int,
    val failedCount: Int
)

/**
 * Message priority constants
 * 
 * LOWER number = HIGHER priority (processed first)
 * This follows Unix nice value convention.
 */
object MessagePriority {
    const val TEXT = 0      // Text messages (small, urgent, processed first)
    const val MEDIA = 1     // Media messages (large, can wait, processed after text)
}
