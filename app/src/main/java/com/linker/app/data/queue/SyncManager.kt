package com.linker.app.data.queue

import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.domain.repository.MessageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages synchronization of offline messages to Firestore when online.
 * 
 * Implements Requirements 7.1-7.8:
 * - Sync pending messages to Firestore
 * - Update delivery method from BLE to ONLINE
 * - Send messages in chronological order
 * - Respect rate limit of 10 messages per second
 * - Retry failed messages with exponential backoff
 * - Clean up SENT queue items older than 7 days
 */
interface SyncManager {
    /**
     * Sync all pending messages to Firestore.
     * 
     * @return Result with success/failure counts
     */
    suspend fun syncPendingMessages(): Result<SyncResult>
    
    /**
     * Retry messages that failed during previous sync attempts.
     * 
     * @return Result with success/failure counts
     */
    suspend fun syncFailedMessages(): Result<SyncResult>
    
    /**
     * Observe current sync status.
     */
    fun observeSyncStatus(): Flow<SyncStatus>
}

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val successCount: Int,
    val failedCount: Int,
    val errors: List<String>
)

/**
 * Current sync status.
 */
sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val progress: Int, val total: Int) : SyncStatus()
    data class Completed(val result: SyncResult) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

@Singleton
class SyncManagerImpl @Inject constructor(
    private val messageQueueDao: MessageQueueDao,
    private val messageDao: MessageDao,
    private val messageRepository: MessageRepository
) : SyncManager {
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    
    companion object {
        private const val RATE_LIMIT_DELAY_MS = 100L // 10 messages per second
        private const val CLEANUP_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    override suspend fun syncPendingMessages(): Result<SyncResult> {
        return try {
            // Get all pending messages ordered by createdAt (chronological)
            val pendingMessages = messageQueueDao.getPendingMessages()
            
            if (pendingMessages.isEmpty()) {
                _syncStatus.value = SyncStatus.Completed(SyncResult(0, 0, emptyList()))
                return Result.success(SyncResult(0, 0, emptyList()))
            }
            
            _syncStatus.value = SyncStatus.Syncing(0, pendingMessages.size)
            
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            pendingMessages.forEachIndexed { index, queueItem ->
                try {
                    // Send message via MessageRepository (which handles Firestore)
                    val result = messageRepository.sendMessage(
                        chatId = queueItem.chatId,
                        messageType = com.linker.app.domain.model.MessageType.TEXT, // TODO: Get from queueItem
                        content = queueItem.messagePayload,
                        mediaUrl = null,
                        replyToMessageId = null
                    )
                    
                    when (result) {
                        is com.linker.app.core.util.Result.Success -> {
                            // Update queue status to SENT
                            messageQueueDao.updateQueueStatus(
                                queueId = queueItem.queueId,
                                status = QueueStatus.SENT,
                                sentAt = System.currentTimeMillis()
                            )
                            
                            // Update message delivery method to ONLINE
                            messageDao.updateDeliveryMethod(
                                messageId = queueItem.messageId,
                                deliveryMethod = DeliveryMethod.ONLINE
                            )
                            
                            successCount++
                        }
                        is com.linker.app.core.util.Result.Error -> {
                            // Increment retry count
                            messageQueueDao.incrementRetryCount(queueItem.queueId)
                            
                            // Update error message
                            messageQueueDao.updateErrorMessage(
                                queueId = queueItem.queueId,
                                errorMessage = result.message
                            )
                            
                            failedCount++
                            errors.add("Message ${queueItem.messageId}: ${result.message}")
                        }
                    }
                    
                    // Update progress
                    _syncStatus.value = SyncStatus.Syncing(index + 1, pendingMessages.size)
                    
                    // Rate limiting: 10 messages per second
                    delay(RATE_LIMIT_DELAY_MS)
                    
                } catch (e: Exception) {
                    failedCount++
                    errors.add("Message ${queueItem.messageId}: ${e.message}")
                    
                    // Increment retry count
                    messageQueueDao.incrementRetryCount(queueItem.queueId)
                    messageQueueDao.updateErrorMessage(
                        queueId = queueItem.queueId,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
            
            // Cleanup old SENT messages
            cleanupOldMessages()
            
            val syncResult = SyncResult(successCount, failedCount, errors)
            _syncStatus.value = SyncStatus.Completed(syncResult)
            
            Result.success(syncResult)
            
        } catch (e: Exception) {
            val errorMessage = "Sync failed: ${e.message}"
            _syncStatus.value = SyncStatus.Error(errorMessage)
            Result.failure(e)
        }
    }
    
    override suspend fun syncFailedMessages(): Result<SyncResult> {
        return try {
            // Get all failed messages that haven't exceeded max retries
            val failedMessages = messageQueueDao.getFailedMessages()
            
            if (failedMessages.isEmpty()) {
                return Result.success(SyncResult(0, 0, emptyList()))
            }
            
            _syncStatus.value = SyncStatus.Syncing(0, failedMessages.size)
            
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            failedMessages.forEachIndexed { index, queueItem ->
                try {
                    // Calculate retry delay based on retry count
                    val retryDelay = RetryStrategy.calculateDelay(queueItem.retryCount)
                    
                    // Check if enough time has passed since last attempt
                    val timeSinceLastAttempt = System.currentTimeMillis() - (queueItem.lastAttemptAt ?: 0)
                    if (timeSinceLastAttempt < retryDelay) {
                        // Skip this message, not ready for retry yet
                        continue
                    }
                    
                    // Update last attempt timestamp
                    messageQueueDao.updateLastAttempt(
                        queueId = queueItem.queueId,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    // Attempt to send
                    val result = messageRepository.sendMessage(
                        chatId = queueItem.chatId,
                        messageType = com.linker.app.domain.model.MessageType.TEXT,
                        content = queueItem.messagePayload,
                        mediaUrl = null,
                        replyToMessageId = null
                    )
                    
                    when (result) {
                        is com.linker.app.core.util.Result.Success -> {
                            messageQueueDao.updateQueueStatus(
                                queueId = queueItem.queueId,
                                status = QueueStatus.SENT,
                                sentAt = System.currentTimeMillis()
                            )
                            
                            messageDao.updateDeliveryMethod(
                                messageId = queueItem.messageId,
                                deliveryMethod = DeliveryMethod.ONLINE
                            )
                            
                            successCount++
                        }
                        is com.linker.app.core.util.Result.Error -> {
                            messageQueueDao.incrementRetryCount(queueItem.queueId)
                            messageQueueDao.updateErrorMessage(
                                queueId = queueItem.queueId,
                                errorMessage = result.message
                            )
                            
                            // Check if max retries exceeded
                            if (queueItem.retryCount + 1 >= queueItem.maxRetries) {
                                messageQueueDao.updateQueueStatus(
                                    queueId = queueItem.queueId,
                                    status = QueueStatus.FAILED,
                                    sentAt = null
                                )
                            }
                            
                            failedCount++
                            errors.add("Message ${queueItem.messageId}: ${result.message}")
                        }
                    }
                    
                    _syncStatus.value = SyncStatus.Syncing(index + 1, failedMessages.size)
                    delay(RATE_LIMIT_DELAY_MS)
                    
                } catch (e: Exception) {
                    failedCount++
                    errors.add("Message ${queueItem.messageId}: ${e.message}")
                    
                    messageQueueDao.incrementRetryCount(queueItem.queueId)
                    messageQueueDao.updateErrorMessage(
                        queueId = queueItem.queueId,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
            
            val syncResult = SyncResult(successCount, failedCount, errors)
            _syncStatus.value = SyncStatus.Completed(syncResult)
            
            Result.success(syncResult)
            
        } catch (e: Exception) {
            val errorMessage = "Failed message sync failed: ${e.message}"
            _syncStatus.value = SyncStatus.Error(errorMessage)
            Result.failure(e)
        }
    }
    
    override fun observeSyncStatus(): Flow<SyncStatus> = _syncStatus.asStateFlow()
    
    /**
     * Clean up SENT queue items older than 7 days.
     */
    private suspend fun cleanupOldMessages() {
        try {
            val cutoffTime = System.currentTimeMillis() - CLEANUP_AGE_MS
            messageQueueDao.deleteOldSentMessages(cutoffTime)
        } catch (e: Exception) {
            // Log error but don't fail the sync
            // TODO: Add proper logging
        }
    }
}
