package com.linker.app.data.queue

import com.linker.app.data.local.entity.DeliveryMethod
import kotlinx.coroutines.flow.Flow

/**
 * Interface for processing offline message queue
 * 
 * This interface handles queueing, processing, and retry logic for offline messages
 * that need to be delivered via BLE or Wi-Fi Direct.
 * 
 * **Thread Safety:**
 * - All methods are thread-safe
 * - Can be called from any coroutine context
 * 
 * **Implementation Notes:**
 * - Messages are processed in priority order (TEXT before MEDIA)
 * - Failed messages are retried with exponential backoff
 * - Queue has a maximum size limit to prevent memory issues
 * 
 * @see MessageQueueProcessorImpl for implementation details
 * @see RetryStrategy for retry logic
 */
interface MessageQueueProcessor {
    
    /**
     * Enqueue a message for offline delivery
     * 
     * Adds a message to the queue for delivery via BLE or Wi-Fi Direct.
     * Messages are prioritized based on type (TEXT > MEDIA).
     * 
     * **Priority:**
     * - TEXT messages: Priority 0 (processed first)
     * - MEDIA messages: Priority 1 (processed after text)
     * 
     * **Queue Management:**
     * - If queue exceeds MAX_QUEUE_SIZE, oldest SENT messages are removed
     * - Duplicate messages are detected and rejected
     * 
     * Example:
     * ```kotlin
     * val result = processor.enqueueMessage(
     *     messageId = "msg_123",
     *     chatId = "chat_456",
     *     recipientId = "user_789",
     *     payload = encryptedPayload,
     *     deliveryMethod = DeliveryMethod.BLE
     * )
     * 
     * if (result.isSuccess) {
     *     Log.d(TAG, "Message enqueued successfully")
     * }
     * ```
     * 
     * @param messageId Unique message ID
     * @param chatId Chat ID where message belongs
     * @param recipientId Recipient user ID
     * @param payload Encrypted message payload
     * @param deliveryMethod Delivery method (BLE or WIFI_DIRECT)
     * @return Result.success if enqueued, Result.failure if error occurred
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    suspend fun enqueueMessage(
        messageId: String,
        chatId: String,
        recipientId: String,
        payload: String,
        deliveryMethod: DeliveryMethod
    ): Result<Unit>
    
    /**
     * Enqueue multiple messages at once
     * 
     * More efficient than calling enqueueMessage() multiple times.
     * Uses batch insert for better database performance.
     * 
     * @param messages List of messages to enqueue
     * @return Result with success/failure counts
     */
    suspend fun enqueueMessages(
        messages: List<QueueMessageRequest>
    ): Result<BatchEnqueueResult>

    /**
     * Process pending messages in the queue
     * 
     * Processes all pending messages in priority order (lower priority number = higher priority).
     * Messages are sent via their specified delivery method (BLE or Wi-Fi Direct).
     * 
     * **Processing Order:**
     * 1. Get all PENDING messages
     * 2. Sort by priority (0 = TEXT, 1 = MEDIA)
     * 3. Check retry delay for previously failed messages
     * 4. Send message via appropriate delivery method
     * 5. Update status to SENT or FAILED
     * 
     * **Retry Logic:**
     * - Failed messages are retried with exponential backoff
     * - Retry delays: 5s, 15s, 45s
     * - Max retries: 3
     * 
     * This method should be called:
     * - When network connectivity changes
     * - Periodically (e.g., every 30 seconds)
     * - After new messages are enqueued
     * 
     * @return Number of messages processed
     */
    suspend fun processQueue(): Int
    
    /**
     * Retry failed messages
     * 
     * Resets FAILED messages to PENDING status and processes them again.
     * Only messages that haven't exceeded max retries are retried.
     */
    suspend fun retryFailedMessages()
    
    /**
     * Cancel a specific message
     * 
     * Removes a message from the queue regardless of its status.
     * 
     * @param messageId Message ID to cancel
     * @return Result indicating success or failure
     */
    suspend fun cancelMessage(messageId: String): Result<Unit>
    
    /**
     * Cancel multiple messages at once
     * 
     * @param messageIds List of message IDs to cancel
     * @return Number of messages cancelled
     */
    suspend fun cancelMessages(messageIds: List<String>): Int

    /**
     * Clear sent messages from queue
     * 
     * Removes all messages with SENT status from the queue.
     * This helps keep the queue size manageable and improves performance.
     */
    suspend fun clearSentMessages()
    
    /**
     * Clear messages by status
     * 
     * @param status Status to clear (PENDING, SENDING, FAILED, SENT)
     * @return Number of messages cleared
     */
    suspend fun clearMessagesByStatus(status: com.linker.app.data.local.entity.QueueStatus): Int

    /**
     * Observe queue status reactively
     * 
     * Emits QueueStatus whenever queue state changes.
     * Useful for updating UI with queue statistics.
     * 
     * @return Flow of QueueStatus that emits on every queue state change
     */
    fun observeQueueStatus(): Flow<QueueStatus>
    
    /**
     * Observe pending message count reactively
     * 
     * Emits the number of pending messages whenever it changes.
     * More lightweight than observeQueueStatus() if you only need pending count.
     * 
     * @return Flow of Int representing pending message count
     */
    fun observePendingCount(): Flow<Int>
}

/**
 * Request for batch enqueue operation
 */
data class QueueMessageRequest(
    val messageId: String,
    val chatId: String,
    val recipientId: String,
    val payload: String,
    val deliveryMethod: DeliveryMethod
)

/**
 * Result of batch enqueue operation
 */
data class BatchEnqueueResult(
    val successCount: Int,
    val failedCount: Int,
    val errors: List<String>
)

/**
 * Queue status information
 */
data class QueueStatus(
    val pendingCount: Int,
    val sendingCount: Int,
    val failedCount: Int
)

/**
 * Message priority constants for queue processing
 * 
 * **Priority System:**
 * - LOWER number = HIGHER priority (processed first)
 * - Follows Unix nice value convention
 * - Range: 0 (highest) to Integer.MAX_VALUE (lowest)
 * 
 * **Rationale:**
 * - TEXT messages are small and urgent (user expects immediate delivery)
 * - MEDIA messages are large and can tolerate delay
 * - Processing TEXT first improves perceived performance
 */
object MessagePriority {
    /**
     * Text messages priority (highest)
     * 
     * Includes: TEXT, LINK, CONTACT, LOCATION
     */
    const val TEXT = 0      
    
    /**
     * Media messages priority (lower)
     * 
     * Includes: IMAGE, VIDEO, GIF, AUDIO, FILE, STICKER
     */
    const val MEDIA = 1     
}
