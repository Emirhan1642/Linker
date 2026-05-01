package com.linker.app.data.queue

import android.util.Log
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.ble.BLEPacket
import com.linker.app.data.encryption.EncryptionManager
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.MessageQueueEntity
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.data.local.entity.MessageStatus as EntityMessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MessageQueueProcessor
 * 
 * Handles offline message queueing, processing, and retry logic.
 */
@Singleton
class MessageQueueProcessorImpl @Inject constructor(
    private val messageQueueDao: MessageQueueDao,
    private val messageDao: MessageDao,
    private val bleMeshManager: BLEMeshManager,
    private val encryptionManager: EncryptionManager,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider
) : MessageQueueProcessor {
    
    companion object {
        private const val TAG = "MessageQueueProcessor"
        private const val DEFAULT_TTL: Byte = 5
        private const val MAX_QUEUE_SIZE = 1000
    }
    
    private val _queueStatus = MutableStateFlow(
        com.linker.app.data.queue.QueueStatus(0, 0, 0)
    )
    
    override suspend fun enqueueMessage(
        messageId: String,
        chatId: String,
        recipientId: String,
        payload: String,
        deliveryMethod: DeliveryMethod
    ): Result<Unit> {
        return try {
            // Determine priority based on message type
            // For now, assume text messages (priority 0)
            // In real implementation, check message content type
            val priority = MessagePriority.TEXT
            
            val queueEntity = MessageQueueEntity(
                queueId = UUID.randomUUID().toString(),
                messageId = messageId,
                chatId = chatId,
                recipientId = recipientId,
                messagePayload = payload,
                queueStatus = QueueStatus.PENDING,
                deliveryMethod = deliveryMethod,
                retryCount = 0,
                maxRetries = 3,
                priority = priority,
                ttl = DEFAULT_TTL.toInt(),
                createdAt = System.currentTimeMillis(),
                lastAttemptAt = null,
                sentAt = null,
                errorMessage = null
            )
            
            messageQueueDao.insertQueueItem(queueEntity)
            
            // Check queue size and cleanup if needed
            val queueSize = messageQueueDao.getQueueSize()
            if (queueSize > MAX_QUEUE_SIZE) {
                // Remove oldest SENT messages
                val sentMessages = messageQueueDao.getMessagesByStatus(QueueStatus.SENT)
                    .sortedBy { it.sentAt }
                    .take(queueSize - MAX_QUEUE_SIZE)
                
                sentMessages.forEach { messageQueueDao.deleteQueueItem(it.queueId) }
            }
            
            updateQueueStatus()
            
            Log.d(TAG, "Message $messageId enqueued for $deliveryMethod delivery")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueueing message $messageId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun processQueue() {
        try {
            // Get pending messages ordered by priority (lower = higher priority)
            val pendingMessages = messageQueueDao.getPendingMessages()
                .sortedBy { it.priority }
            
            for (message in pendingMessages) {
                // Check if enough time has passed since last attempt (for retries)
                if (message.retryCount > 0 && message.lastAttemptAt != null) {
                    val requiredDelay = RetryStrategy.calculateDelay(message.retryCount - 1)
                    val timeSinceLastAttempt = System.currentTimeMillis() - message.lastAttemptAt
                    
                    if (timeSinceLastAttempt < requiredDelay) {
                        // Skip this message, not ready for retry yet
                        Log.d(TAG, "Message ${message.messageId} not ready for retry (${requiredDelay - timeSinceLastAttempt}ms remaining)")
                        continue
                    }
                }
                
                processMessage(message)
            }
            
            updateQueueStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue", e)
        }
    }
    
    override suspend fun retryFailedMessages() {
        try {
            val failedMessages = messageQueueDao.getMessagesByStatus(QueueStatus.FAILED)
            
            for (message in failedMessages) {
                if (message.retryCount < message.maxRetries) {
                    // Reset to pending for retry
                    val updated = message.copy(
                        queueStatus = QueueStatus.PENDING,
                        errorMessage = null
                    )
                    messageQueueDao.updateQueueItem(updated)
                }
            }
            
            processQueue()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying failed messages", e)
        }
    }
    
    override suspend fun cancelMessage(messageId: String) {
        try {
            val queueItem = messageQueueDao.getQueueItemByMessageId(messageId)
            
            if (queueItem != null) {
                messageQueueDao.deleteQueueItem(queueItem.queueId)
                updateQueueStatus()
                Log.d(TAG, "Message $messageId cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling message $messageId", e)
        }
    }
    
    override suspend fun clearSentMessages() {
        try {
            val sentMessages = messageQueueDao.getMessagesByStatus(QueueStatus.SENT)
            
            for (message in sentMessages) {
                messageQueueDao.deleteQueueItem(message.queueId)
            }
            
            updateQueueStatus()
            Log.d(TAG, "Cleared ${sentMessages.size} sent messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing sent messages", e)
        }
    }
    
    override fun observeQueueStatus(): Flow<com.linker.app.data.queue.QueueStatus> {
        return _queueStatus.asStateFlow()
    }
    
    override fun observePendingCount(): Flow<Int> {
        return messageQueueDao.observePendingCount()
    }
    
    /**
     * Process a single message
     */
    private suspend fun processMessage(message: MessageQueueEntity) {
        try {
            // Update status to SENDING
            val sending = message.copy(
                queueStatus = QueueStatus.SENDING,
                lastAttemptAt = System.currentTimeMillis()
            )
            messageQueueDao.updateQueueItem(sending)
            
            // Send based on delivery method
            val result = when (message.deliveryMethod) {
                DeliveryMethod.BLE -> sendViaBLE(message)
                DeliveryMethod.WIFI_DIRECT -> sendViaWiFiDirect(message)
                else -> Result.failure(Exception("Invalid delivery method for offline message"))
            }
            
            if (result.isSuccess) {
                // Mark as SENT in queue
                val sent = message.copy(
                    queueStatus = QueueStatus.SENT,
                    sentAt = System.currentTimeMillis()
                )
                messageQueueDao.updateQueueItem(sent)
                
                // Update message status in database
                try {
                    messageDao.updateMessageStatus(message.messageId, EntityMessageStatus.DELIVERED)
                    Log.d(TAG, "Updated message ${message.messageId} status to DELIVERED")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating message status: ${e.message}")
                }
                
                Log.d(TAG, "Message ${message.messageId} sent successfully")
            } else {
                // Handle failure
                handleMessageFailure(message, result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message ${message.messageId}", e)
            handleMessageFailure(message, e)
        }
    }
    
    /**
     * Send message via BLE mesh
     */
    private suspend fun sendViaBLE(message: MessageQueueEntity): Result<Unit> {
        return try {
            // Get current user ID
            val currentUserId = currentUserProvider.getCurrentUserId()
            if (currentUserId == null) {
                Log.e(TAG, "Cannot send BLE message: current user ID not available")
                return Result.failure(Exception("Current user ID not available"))
            }
            
            // Create BLE packet
            val packet = BLEPacket.create(
                messageId = message.messageId,
                senderId = currentUserId,  // Use actual current user ID
                recipientId = message.recipientId,
                ttl = message.ttl.toByte(),
                hopCount = 0,
                encryptedPayload = message.messagePayload.toByteArray()
            )
            
            Log.d(TAG, "Attempting to send BLE packet for message ${message.messageId} from $currentUserId to recipient ${message.recipientId}")
            
            // Send via BLE mesh manager
            val result = bleMeshManager.sendMessage(packet)
            
            if (result.isSuccess) {
                Log.d(TAG, "BLE packet sent successfully for message ${message.messageId}")
            } else {
                Log.e(TAG, "BLE packet send failed for message ${message.messageId}: ${result.exceptionOrNull()?.message}")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via BLE: ${message.messageId}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send message via Wi-Fi Direct
     */
    private suspend fun sendViaWiFiDirect(message: MessageQueueEntity): Result<Unit> {
        // TODO: Implement Wi-Fi Direct sending
        return Result.failure(Exception("Wi-Fi Direct not yet implemented"))
    }
    
    /**
     * Handle message sending failure with exponential backoff
     */
    private suspend fun handleMessageFailure(message: MessageQueueEntity, error: Throwable?) {
        val newRetryCount = message.retryCount + 1
        
        if (!RetryStrategy.shouldRetry(newRetryCount)) {
            // Mark as FAILED
            val failed = message.copy(
                queueStatus = QueueStatus.FAILED,
                retryCount = newRetryCount,
                errorMessage = error?.message ?: "Unknown error"
            )
            messageQueueDao.updateQueueItem(failed)
            
            Log.e(TAG, "Message ${message.messageId} failed after $newRetryCount attempts")
        } else {
            // Reset to PENDING for retry with exponential backoff
            // The actual delay will be calculated by RetryStrategy when processing
            val pending = message.copy(
                queueStatus = QueueStatus.PENDING,
                retryCount = newRetryCount,
                errorMessage = error?.message
            )
            messageQueueDao.updateQueueItem(pending)
            
            val nextDelay = RetryStrategy.calculateDelay(newRetryCount)
            Log.w(TAG, "Message ${message.messageId} will retry in ${nextDelay}ms (attempt $newRetryCount)")
        }
    }
    
    /**
     * Update queue status
     */
    private suspend fun updateQueueStatus() {
        val pending = messageQueueDao.getMessagesByStatus(QueueStatus.PENDING).size
        val sending = messageQueueDao.getMessagesByStatus(QueueStatus.SENDING).size
        val failed = messageQueueDao.getMessagesByStatus(QueueStatus.FAILED).size
        
        _queueStatus.value = com.linker.app.data.queue.QueueStatus(pending, sending, failed)
    }
}
