package com.linker.app.data.queue

import android.util.Log
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.domain.repository.MessageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

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
    
    /**
     * Cancel ongoing sync operation.
     */
    fun cancelSync()
    
    /**
     * Check if a sync is currently in progress.
     */
    fun isSyncing(): Boolean
    
    /**
     * Get sync statistics
     */
    fun getStatistics(): SyncStatistics
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
 * Sync statistics for monitoring
 */
data class SyncStatistics(
    val totalPendingSynced: Long,
    val totalFailedSynced: Long,
    val totalSucceeded: Long,
    val totalFailed: Long,
    val lastSyncTime: Long?
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

/**
 * Token Bucket Rate Limiter
 */
class RateLimiter(private val maxOperations: Int, private val timeWindowMs: Long) {
    private val mutex = Mutex()
    private var tokens = maxOperations
    private var lastRefillTimestamp = System.currentTimeMillis()

    suspend fun acquire() = mutex.withLock {
        val now = System.currentTimeMillis()
        val timePassed = now - lastRefillTimestamp
        val tokensToAdd = (timePassed / timeWindowMs).toInt() * maxOperations
        
        if (tokensToAdd > 0) {
            tokens = min(maxOperations, tokens + tokensToAdd)
            lastRefillTimestamp = now
        }
        
        if (tokens > 0) {
            tokens--
        } else {
            val waitTime = timeWindowMs - (now - lastRefillTimestamp)
            if (waitTime > 0) delay(waitTime)
            tokens = maxOperations - 1
            lastRefillTimestamp = System.currentTimeMillis()
        }
    }
}

@Singleton
class SyncManagerImpl @Inject constructor(
    private val messageQueueDao: MessageQueueDao,
    private val messageDao: MessageDao,
    private val messageRepository: MessageRepository,
    private val messageDeduplicationManager: MessageDeduplicationManager,
    private val database: com.linker.app.data.local.LinkerDatabase
) : SyncManager {
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    private val syncMutex = Mutex()
    private var currentSyncJob: Job? = null
    
    private var totalPendingSynced = 0L
    private var totalFailedSynced = 0L
    private var totalSucceeded = 0L
    private var totalFailed = 0L
    private var lastSyncTime: Long? = null
    
    private val rateLimiter = RateLimiter(10, 1000L) // 10 operations per second
    
    companion object {
        private const val TAG = "SyncManager"
        private const val CLEANUP_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    override fun getStatistics(): SyncStatistics = SyncStatistics(
        totalPendingSynced, totalFailedSynced, totalSucceeded, totalFailed, lastSyncTime
    )

    override fun cancelSync() {
        currentSyncJob?.cancel()
        currentSyncJob = null
        _syncStatus.value = SyncStatus.Idle
        Log.d(TAG, "Sync cancelled")
    }

    override fun isSyncing(): Boolean {
        return syncMutex.isLocked
    }
    
    override suspend fun syncPendingMessages(): Result<SyncResult> {
        if (!syncMutex.tryLock()) {
            Log.w(TAG, "Sync already in progress, skipping")
            return Result.failure(IllegalStateException("Sync already in progress"))
        }
        
        currentSyncJob = currentCoroutineContext()[Job]
        
        try {
            messageDeduplicationManager.cleanupOldEntries()
            
            val pendingMessages = messageQueueDao.getPendingMessages()
            
            if (pendingMessages.isEmpty()) {
                _syncStatus.value = SyncStatus.Completed(SyncResult(0, 0, emptyList()))
                return Result.success(SyncResult(0, 0, emptyList()))
            }
            
            _syncStatus.value = SyncStatus.Syncing(0, pendingMessages.size)
            
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            for ((index, queueItem) in pendingMessages.withIndex()) {
                if (!currentCoroutineContext().isActive) {
                    Log.w(TAG, "Sync cancelled during execution")
                    break
                }
                
                try {
                    rateLimiter.acquire()
                    
                    if (messageDeduplicationManager.isDuplicate(queueItem.messageId)) {
                        messageQueueDao.updateQueueStatus(
                            queueId = queueItem.queueId,
                            status = QueueStatus.SENT,
                            sentAt = System.currentTimeMillis()
                        )
                        successCount++
                        _syncStatus.value = SyncStatus.Syncing(index + 1, pendingMessages.size)
                        continue
                    }

                    messageDeduplicationManager.markAsProcessed(queueItem.messageId)

                    val localMsg = messageDao.getMessageById(queueItem.messageId)
                    val mediaPath = localMsg?.mediaUrl
                    val replyToId = localMsg?.replyToMessageId

                    val result = messageRepository.sendMessage(
                        chatId = queueItem.chatId,
                        messageType = mapQueueMessageTypeToMessageType(queueItem.messageType),
                        content = queueItem.messagePayload,
                        mediaLocalPath = mediaPath,
                        replyToMessageId = replyToId
                    )

                    when (result) {
                        is com.linker.app.core.util.Result.Success -> {
                            updateQueueAndMessageAtomic(
                                queueId = queueItem.queueId,
                                messageId = queueItem.messageId,
                                queueStatus = QueueStatus.SENT,
                                sentAt = System.currentTimeMillis(),
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
                            failedCount++
                            errors.add("Message ${queueItem.messageId}: ${result.message}")
                        }
                        is com.linker.app.core.util.Result.Loading -> { }
                    }

                    _syncStatus.value = SyncStatus.Syncing(index + 1, pendingMessages.size)

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
            
            cleanupOldMessages()
            
            totalPendingSynced += pendingMessages.size
            totalSucceeded += successCount
            totalFailed += failedCount
            lastSyncTime = System.currentTimeMillis()
            
            val syncResult = SyncResult(successCount, failedCount, errors)
            _syncStatus.value = SyncStatus.Completed(syncResult)
            
            return Result.success(syncResult)
            
        } catch (e: Exception) {
            val errorMessage = "Sync failed: ${e.message}"
            _syncStatus.value = SyncStatus.Error(errorMessage)
            return Result.failure(e)
        } finally {
            syncMutex.unlock()
            currentSyncJob = null
        }
    }
    
    override suspend fun syncFailedMessages(): Result<SyncResult> {
        if (!syncMutex.tryLock()) {
            return Result.failure(IllegalStateException("Sync already in progress"))
        }
        
        currentSyncJob = currentCoroutineContext()[Job]
        
        try {
            val failedMessages = messageQueueDao.getFailedMessages()
            
            if (failedMessages.isEmpty()) {
                return Result.success(SyncResult(0, 0, emptyList()))
            }
            
            _syncStatus.value = SyncStatus.Syncing(0, failedMessages.size)
            
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            for ((index, queueItem) in failedMessages.withIndex()) {
                if (!currentCoroutineContext().isActive) break
                
                try {
                    rateLimiter.acquire()
                    
                    if (messageDeduplicationManager.isDuplicate(queueItem.messageId)) {
                        messageQueueDao.updateQueueStatus(
                            queueId = queueItem.queueId,
                            status = QueueStatus.SENT,
                            sentAt = System.currentTimeMillis()
                        )
                        successCount++
                        _syncStatus.value = SyncStatus.Syncing(index + 1, failedMessages.size)
                        continue
                    }
                    
                    val retryDelay = RetryStrategy.calculateDelay(queueItem.retryCount)
                    val lastAttempt = queueItem.lastAttemptAt ?: queueItem.createdAt
                    val timeSinceLastAttempt = System.currentTimeMillis() - lastAttempt
                    
                    if (timeSinceLastAttempt < retryDelay) continue
                    
                    val localMsg = messageDao.getMessageById(queueItem.messageId)
                    val mediaPath = localMsg?.mediaUrl
                    val replyToId = localMsg?.replyToMessageId

                    val result = messageRepository.sendMessage(
                        chatId = queueItem.chatId,
                        messageType = mapQueueMessageTypeToMessageType(queueItem.messageType),
                        content = queueItem.messagePayload,
                        mediaLocalPath = mediaPath,
                        replyToMessageId = replyToId
                    )
                    
                    when (result) {
                        is com.linker.app.core.util.Result.Success -> {
                            updateQueueAndMessageAtomic(
                                queueId = queueItem.queueId,
                                messageId = queueItem.messageId,
                                queueStatus = QueueStatus.SENT,
                                sentAt = System.currentTimeMillis(),
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
                        is com.linker.app.core.util.Result.Loading -> { }
                    }
                    
                    _syncStatus.value = SyncStatus.Syncing(index + 1, failedMessages.size)
                    
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
            
            totalFailedSynced += failedMessages.size
            totalSucceeded += successCount
            totalFailed += failedCount
            lastSyncTime = System.currentTimeMillis()
            
            val syncResult = SyncResult(successCount, failedCount, errors)
            _syncStatus.value = SyncStatus.Completed(syncResult)
            
            return Result.success(syncResult)
            
        } catch (e: Exception) {
            val errorMessage = "Failed message sync failed: ${e.message}"
            _syncStatus.value = SyncStatus.Error(errorMessage)
            return Result.failure(e)
        } finally {
            syncMutex.unlock()
            currentSyncJob = null
        }
    }
    
    override fun observeSyncStatus(): Flow<SyncStatus> = _syncStatus.asStateFlow()
    
    /**
     * Clean up SENT queue items older than 7 days.
     * @return Number of deleted messages or -1 on error
     */
    private suspend fun cleanupOldMessages(): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - CLEANUP_AGE_MS
            val deletedCount = messageQueueDao.deleteOldSentMessages(cutoffTime)
            Log.d(TAG, "Cleaned up $deletedCount old SENT messages (older than 7 days)")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old messages", e)
            -1
        }
    }
    
    private suspend fun updateQueueAndMessageAtomic(
        queueId: String,
        messageId: String,
        queueStatus: QueueStatus,
        sentAt: Long?,
        deliveryMethod: DeliveryMethod
    ) {
        try {
            database.updateQueueAndMessageAtomic(
                queueId = queueId,
                queueStatus = queueStatus,
                sentAt = sentAt,
                messageId = messageId,
                deliveryMethod = deliveryMethod
            )
        } catch (e: Exception) {
            Log.e(TAG, "Database transaction failed for message $messageId", e)
            throw e
        }
    }
    
    private fun mapQueueMessageTypeToMessageType(queueMessageType: com.linker.app.data.local.entity.MessageType): com.linker.app.domain.model.MessageType {
        return when (queueMessageType) {
            com.linker.app.data.local.entity.MessageType.TEXT -> com.linker.app.domain.model.MessageType.TEXT
            com.linker.app.data.local.entity.MessageType.IMAGE -> com.linker.app.domain.model.MessageType.IMAGE
            com.linker.app.data.local.entity.MessageType.VIDEO -> com.linker.app.domain.model.MessageType.VIDEO
            com.linker.app.data.local.entity.MessageType.GIF -> com.linker.app.domain.model.MessageType.GIF
            com.linker.app.data.local.entity.MessageType.LINK -> com.linker.app.domain.model.MessageType.LINK
            com.linker.app.data.local.entity.MessageType.AUDIO -> com.linker.app.domain.model.MessageType.AUDIO
            com.linker.app.data.local.entity.MessageType.FILE -> com.linker.app.domain.model.MessageType.FILE
            com.linker.app.data.local.entity.MessageType.LOCATION -> com.linker.app.domain.model.MessageType.LOCATION
            com.linker.app.data.local.entity.MessageType.CONTACT -> com.linker.app.domain.model.MessageType.CONTACT
            com.linker.app.data.local.entity.MessageType.STICKER -> com.linker.app.domain.model.MessageType.STICKER
        }
    }
}
