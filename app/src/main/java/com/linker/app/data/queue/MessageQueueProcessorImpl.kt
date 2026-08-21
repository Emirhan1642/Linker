package com.linker.app.data.queue

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.ble.BLEPacket
import com.linker.app.data.ble.MessageBatcher
import com.linker.app.data.encryption.EncryptionManager
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.MessageQueueEntity
import com.linker.app.data.local.entity.QueueStatus as EntityQueueStatus
import com.linker.app.data.local.entity.MessageStatus as EntityMessageStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue processing metrics
 */
data class QueueMetrics(
    val totalEnqueued: Long,
    val totalProcessed: Long,
    val totalSucceeded: Long,
    val totalFailed: Long,
    val averageProcessingTimeMs: Long,
    val successRate: Double
)

@Singleton
class MessageQueueProcessorImpl @Inject constructor(
    private val messageQueueDao: MessageQueueDao,
    private val messageDao: MessageDao,
    private val bleMeshManager: BLEMeshManager,
    private val encryptionManager: EncryptionManager,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider,
    private val messageBatcher: MessageBatcher,
    private val nearbyConnectionsManager: com.linker.app.data.nearby.NearbyConnectionsManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : MessageQueueProcessor {
    
    companion object {
        private const val TAG = "MessageQueueProcessor"
        private const val DEFAULT_TTL: Byte = 5
        private const val MAX_QUEUE_SIZE = 1000
        private const val MAX_PAYLOAD_SIZE = 10 * 1024 * 1024 // 10 MB
    }

    private val processorJob = SupervisorJob()
    private val processorScope = CoroutineScope(Dispatchers.Default + processorJob)
    
    private val _queueStatus = MutableStateFlow(QueueStatus(0, 0, 0))
    
    // Metrics
    private var totalEnqueued = 0L
    private var totalProcessed = 0L
    private var totalSucceeded = 0L
    private var totalFailed = 0L
    private val processingTimes = java.util.concurrent.CopyOnWriteArrayList<Long>()
    
    fun getMetrics(): QueueMetrics {
        val timesSnapshot = processingTimes.toList()
        val avgProcessingTime = if (timesSnapshot.isNotEmpty()) {
            timesSnapshot.average().toLong()
        } else {
            0L
        }
        val successRate = if (totalProcessed > 0) {
            totalSucceeded.toDouble() / totalProcessed
        } else {
            0.0
        }
        return QueueMetrics(
            totalEnqueued = totalEnqueued,
            totalProcessed = totalProcessed,
            totalSucceeded = totalSucceeded,
            totalFailed = totalFailed,
            averageProcessingTimeMs = avgProcessingTime,
            successRate = successRate
        )
    }

    private suspend inline fun <T> withQueueStatusUpdate(block: suspend () -> T): T {
        try {
            return block()
        } finally {
            updateQueueStatus()
        }
    }
    
    init {
        messageBatcher.setOnBatchReady { batch ->
            processorScope.launch {
                batch.forEach { packet ->
                    try {
                        bleMeshManager.sendMessage(packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending batched message ${packet.messageId}", e)
                    }
                }
            }
        }
    }
    
    fun shutdown() {
        try {
            processorJob.cancel()
            Log.d(TAG, "MessageQueueProcessor shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    private suspend fun getCurrentUserIdOrThrow(): String {
        return currentUserProvider.getCurrentUserId()
            ?: throw IllegalStateException("Current user ID not available. User must be logged in.")
    }

    override suspend fun enqueueMessage(
        messageId: String,
        chatId: String,
        recipientId: String,
        payload: String,
        deliveryMethod: DeliveryMethod
    ): Result<Unit> {
        if (messageId.isBlank()) return Result.failure(IllegalArgumentException("messageId cannot be blank"))
        if (chatId.isBlank()) return Result.failure(IllegalArgumentException("chatId cannot be blank"))
        if (recipientId.isBlank()) return Result.failure(IllegalArgumentException("recipientId cannot be blank"))
        if (payload.isBlank()) return Result.failure(IllegalArgumentException("payload cannot be blank"))
        if (payload.length > MAX_PAYLOAD_SIZE) return Result.failure(IllegalArgumentException("payload exceeds maximum size ($MAX_PAYLOAD_SIZE bytes)"))
        if (deliveryMethod != DeliveryMethod.BLE && deliveryMethod != DeliveryMethod.WIFI_DIRECT) {
            return Result.failure(IllegalArgumentException("Invalid delivery method for offline message: $deliveryMethod"))
        }

        return try {
            val message = messageDao.getMessageById(messageId)
            val priority = when (message?.messageType) {
                com.linker.app.data.local.entity.MessageType.TEXT,
                com.linker.app.data.local.entity.MessageType.LINK,
                com.linker.app.data.local.entity.MessageType.CONTACT,
                com.linker.app.data.local.entity.MessageType.LOCATION -> MessagePriority.TEXT
                
                com.linker.app.data.local.entity.MessageType.IMAGE,
                com.linker.app.data.local.entity.MessageType.VIDEO,
                com.linker.app.data.local.entity.MessageType.GIF,
                com.linker.app.data.local.entity.MessageType.AUDIO,
                com.linker.app.data.local.entity.MessageType.FILE,
                com.linker.app.data.local.entity.MessageType.STICKER -> MessagePriority.MEDIA
                
                null -> {
                    Log.w(TAG, "Message $messageId not found in database, defaulting to TEXT priority")
                    MessagePriority.TEXT
                }
            }
            
            val queueEntity = MessageQueueEntity(
                queueId = UUID.randomUUID().toString(),
                messageId = messageId,
                chatId = chatId,
                recipientId = recipientId,
                messagePayload = payload,
                messageType = message?.messageType ?: com.linker.app.data.local.entity.MessageType.TEXT,
                queueStatus = EntityQueueStatus.PENDING,
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
            totalEnqueued++
            
            val queueSize = messageQueueDao.getQueueSize()
            if (queueSize > MAX_QUEUE_SIZE) {
                val messagesToDelete = queueSize - MAX_QUEUE_SIZE
                val deletedCount = messageQueueDao.deleteOldestSentMessages(messagesToDelete)
                if (deletedCount > 0) {
                    Log.d(TAG, "Deleted $deletedCount old SENT messages to maintain queue size")
                }
            }
            
            updateQueueStatus()
            Log.d(TAG, "Message $messageId enqueued for $deliveryMethod delivery")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueueing message $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun enqueueMessages(messages: List<QueueMessageRequest>): Result<BatchEnqueueResult> {
        var successCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        for (request in messages) {
            val result = enqueueMessage(
                request.messageId, request.chatId, request.recipientId, request.payload, request.deliveryMethod
            )
            if (result.isSuccess) successCount++ else {
                failedCount++
                errors.add(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
        return Result.success(BatchEnqueueResult(successCount, failedCount, errors))
    }
    
    override suspend fun processQueue(): Int {
        var processedCount = 0
        try {
            val pendingMessages = messageQueueDao.getPendingMessages().sortedBy { it.priority }
            
            for (message in pendingMessages) {
                if (message.retryCount > 0) {
                    val lastAttemptAt = message.lastAttemptAt
                    if (lastAttemptAt == null) {
                        Log.d(TAG, "Message ${message.messageId} first retry attempt")
                    } else {
                        val requiredDelay = RetryStrategy.calculateDelay(message.retryCount - 1)
                        val timeSinceLastAttempt = System.currentTimeMillis() - lastAttemptAt
                        
                        if (timeSinceLastAttempt < requiredDelay) {
                            val remainingDelay = requiredDelay - timeSinceLastAttempt
                            Log.d(TAG, "Message ${message.messageId} not ready for retry (${remainingDelay}ms remaining, attempt ${message.retryCount})")
                            continue
                        }
                        Log.d(TAG, "Message ${message.messageId} ready for retry (attempt ${message.retryCount})")
                    }
                }
                
                processMessage(message)
                processedCount++
            }
            updateQueueStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue", e)
        }
        return processedCount
    }
    
    override suspend fun retryFailedMessages() {
        try {
            val failedMessages = messageQueueDao.getMessagesByStatus(EntityQueueStatus.FAILED)
            for (message in failedMessages) {
                if (message.retryCount < message.maxRetries) {
                    val updated = message.copy(
                        queueStatus = EntityQueueStatus.PENDING,
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
    
    override suspend fun cancelMessage(messageId: String): Result<Unit> {
        return withQueueStatusUpdate {
            try {
                val queueItem = messageQueueDao.getQueueItemByMessageId(messageId)
                if (queueItem == null) {
                    Log.w(TAG, "Message $messageId not found in queue")
                    return@withQueueStatusUpdate Result.failure(IllegalArgumentException("Message not found in queue"))
                }
                
                if (queueItem.queueStatus == EntityQueueStatus.SENDING) {
                    Log.w(TAG, "Message $messageId is currently being sent, attempting to cancel")
                    try {
                        when (queueItem.deliveryMethod) {
                            DeliveryMethod.BLE -> {
                                // best-effort cancel for BLE
                            }
                            DeliveryMethod.WIFI_DIRECT -> Log.w(TAG, "Cannot cancel ongoing Wi-Fi Direct transfer")
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling ongoing transfer", e)
                    }
                }
                
                messageQueueDao.deleteQueueItem(queueItem.queueId)
                Log.d(TAG, "Message $messageId cancelled")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling message $messageId", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelMessages(messageIds: List<String>): Int {
        var count = 0
        for (id in messageIds) {
            if (cancelMessage(id).isSuccess) count++
        }
        return count
    }
    
    override suspend fun clearSentMessages() {
        withQueueStatusUpdate {
            try {
                val sentMessages = messageQueueDao.getMessagesByStatus(EntityQueueStatus.SENT)
                if (sentMessages.isNotEmpty()) {
                    val queueIds = sentMessages.map { it.queueId }
                    messageQueueDao.deleteQueueItems(queueIds)
                    Log.d(TAG, "Cleared ${sentMessages.size} sent messages using batch delete")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing sent messages", e)
            }
        }
    }

    override suspend fun clearMessagesByStatus(status: EntityQueueStatus): Int {
        var count = 0
        withQueueStatusUpdate {
            try {
                val messages = messageQueueDao.getMessagesByStatus(status)
                if (messages.isNotEmpty()) {
                    val queueIds = messages.map { it.queueId }
                    messageQueueDao.deleteQueueItems(queueIds)
                    count = queueIds.size
                    Log.d(TAG, "Cleared $count messages with status $status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing messages by status", e)
            }
        }
        return count
    }
    
    override fun observeQueueStatus(): Flow<QueueStatus> = _queueStatus.asStateFlow()
    
    override fun observePendingCount(): Flow<Int> = messageQueueDao.observePendingCount()
    
    private suspend fun processMessage(message: MessageQueueEntity) {
        val startTime = System.currentTimeMillis()
        try {
            val sending = message.copy(
                queueStatus = EntityQueueStatus.SENDING,
                lastAttemptAt = System.currentTimeMillis()
            )
            messageQueueDao.updateQueueItem(sending)
            
            val result = when (message.deliveryMethod) {
                DeliveryMethod.BLE -> sendViaBLE(message)
                DeliveryMethod.WIFI_DIRECT -> sendViaWiFiDirect(message)
                else -> Result.failure(Exception("Invalid delivery method for offline message"))
            }
            
            totalProcessed++
            if (result.isSuccess) {
                totalSucceeded++
                val sent = message.copy(
                    queueStatus = EntityQueueStatus.SENT,
                    sentAt = System.currentTimeMillis()
                )
                messageQueueDao.updateQueueItem(sent)
                
                try {
                    messageDao.updateMessageStatus(message.messageId, EntityMessageStatus.SENT)
                    Log.d(TAG, "Updated message ${message.messageId} status to SENT")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating message status: ${e.message}")
                }
                
                Log.d(TAG, "Message ${message.messageId} sent successfully")
            } else {
                totalFailed++
                handleMessageFailure(message, result.exceptionOrNull())
            }
        } catch (e: Exception) {
            totalFailed++
            Log.e(TAG, "Error processing message ${message.messageId}", e)
            handleMessageFailure(message, e)
        } finally {
            val processingTime = System.currentTimeMillis() - startTime
            processingTimes.add(processingTime)
            if (processingTimes.size > 100) processingTimes.removeAt(0)
        }
    }

    private suspend fun encryptMessagePayloadWithRetry(
        recipientId: String,
        plaintext: String,
        maxRetries: Int = 2
    ): ByteArray? {
        repeat(maxRetries + 1) { attempt ->
            try {
                val encryptionResult = encryptionManager.encryptMessage(
                    recipientId = recipientId,
                    plaintext = plaintext
                )
                
                if (encryptionResult.isSuccess) {
                    val encrypted = encryptionResult.getOrNull()?.signalMessage
                    if (encrypted != null) return encrypted
                }
                
                val error = encryptionResult.exceptionOrNull()
                Log.w(TAG, "Encryption attempt ${attempt + 1} failed: ${error?.message}")
                
                if (attempt < maxRetries) delay(1000L * (attempt + 1))
            } catch (e: Exception) {
                Log.e(TAG, "Exception during encryption attempt ${attempt + 1}", e)
                if (attempt < maxRetries) delay(1000L * (attempt + 1))
            }
        }
        Log.e(TAG, "Encryption failed after $maxRetries retries")
        return null
    }
    
    private suspend fun sendViaBLE(
        message: MessageQueueEntity,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Unit> {
        return try {
            val currentUserId = getCurrentUserIdOrThrow()
            onProgress?.invoke(10)
            
            val encryptedPayload = encryptMessagePayloadWithRetry(message.recipientId, message.messagePayload)
                ?: return Result.failure(Exception("Encryption failed"))
            
            onProgress?.invoke(50)
            
            val packet = BLEPacket.create(
                messageId = message.messageId,
                senderId = currentUserId,
                recipientId = message.recipientId,
                ttl = message.ttl.toByte(),
                hopCount = 0,
                encryptedPayload = encryptedPayload
            )
            
            onProgress?.invoke(75)
            val sendResult = bleMeshManager.sendMessage(packet)
            onProgress?.invoke(100)
            
            if (sendResult.isSuccess) {
                Log.d(TAG, "BLE packet sent directly for message ${message.messageId}")
                Result.success(Unit)
            } else {
                messageBatcher.addMessage(packet)
                Log.d(TAG, "BLE packet queued in batcher for message ${message.messageId}")
                Result.failure(sendResult.exceptionOrNull() ?: Exception("BLE send failed, queued in memory batcher"))
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "User not logged in", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via BLE: ${message.messageId}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun sendViaWiFiDirect(message: MessageQueueEntity): Result<Unit> {
        return try {
            val currentUserId = getCurrentUserIdOrThrow()
            
            Log.d(TAG, "Attempting to send message ${message.messageId} via Wi-Fi Direct to ${message.recipientId}")
            
            val discoveryResult = nearbyConnectionsManager.startDiscovery()
            if (discoveryResult.isFailure) {
                return Result.failure(discoveryResult.exceptionOrNull() ?: Exception("Discovery failed"))
            }
            
            val discoveryTimeout = 10_000L
            
            val recipientEndpointId = try {
                withTimeoutOrNull(discoveryTimeout) {
                    nearbyConnectionsManager.observeDiscoveredEndpoints()
                        .mapNotNull { endpoints ->
                            endpoints.find { it.userId == message.recipientId }?.endpointId
                        }
                        .first()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during discovery", e)
                null
            } finally {
                nearbyConnectionsManager.stopDiscovery()
            }
            
            if (recipientEndpointId == null) {
                Log.w(TAG, "Recipient ${message.recipientId} not found via Wi-Fi Direct after ${discoveryTimeout}ms")
                return Result.failure(Exception("Recipient not found via Wi-Fi Direct"))
            }
            
            val connectionResult = nearbyConnectionsManager.connectToEndpoint(recipientEndpointId)
            if (connectionResult.isFailure) {
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Connection failed"))
            }
            
            val encryptedPayload = encryptMessagePayloadWithRetry(message.recipientId, message.messagePayload)
            if (encryptedPayload == null) {
                nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId)
                return Result.failure(Exception("Encryption failed"))
            }
            
            val tempFile = java.io.File.createTempFile("linker_msg_${message.messageId}", ".enc", context.cacheDir)
            try {
                tempFile.writeBytes(encryptedPayload)
                
                var lastProgress = 0L
                val sendResult = nearbyConnectionsManager.sendFile(
                    endpointId = recipientEndpointId,
                    file = tempFile,
                    onProgress = { bytesTransferred, totalBytes ->
                        val progress = (bytesTransferred * 100 / totalBytes).toInt()
                        if (progress - lastProgress >= 10) {
                            Log.d(TAG, "Wi-Fi Direct transfer progress: $progress%")
                            lastProgress = progress.toLong()
                        }
                    }
                )
                
                if (sendResult.isSuccess) {
                    Log.d(TAG, "Message ${message.messageId} sent successfully via Wi-Fi Direct")
                    Result.success(Unit)
                } else {
                    Result.failure(sendResult.exceptionOrNull() ?: Exception("File send failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Wi-Fi Direct file transfer", e)
                Result.failure(e)
            } finally {
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting temp file", e)
                }
                try {
                    nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting endpoint", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via Wi-Fi Direct: ${message.messageId}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun handleMessageFailure(message: MessageQueueEntity, error: Throwable?) {
        val newRetryCount = message.retryCount + 1
        
        if (!RetryStrategy.shouldRetry(newRetryCount)) {
            val failed = message.copy(
                queueStatus = EntityQueueStatus.FAILED,
                retryCount = newRetryCount,
                errorMessage = error?.message ?: "Unknown error"
            )
            messageQueueDao.updateQueueItem(failed)
            Log.e(TAG, "Message ${message.messageId} failed after $newRetryCount attempts")
        } else {
            val pending = message.copy(
                queueStatus = EntityQueueStatus.PENDING,
                retryCount = newRetryCount,
                errorMessage = error?.message
            )
            messageQueueDao.updateQueueItem(pending)
            val nextDelay = RetryStrategy.calculateDelay(newRetryCount)
            Log.w(TAG, "Message ${message.messageId} will retry in ${nextDelay}ms (attempt $newRetryCount)")
        }
    }
    
    private suspend fun updateQueueStatus() {
        try {
            val statusCounts = messageQueueDao.getStatusCounts()
            _queueStatus.value = QueueStatus(
                pendingCount = statusCounts[EntityQueueStatus.PENDING] ?: 0,
                sendingCount = statusCounts[EntityQueueStatus.SENDING] ?: 0,
                failedCount = statusCounts[EntityQueueStatus.FAILED] ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating queue status", e)
        }
    }
}
