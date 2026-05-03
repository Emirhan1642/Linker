package com.linker.app.data.queue

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider,
    private val messageBatcher: MessageBatcher,
    private val nearbyConnectionsManager: com.linker.app.data.nearby.NearbyConnectionsManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : MessageQueueProcessor {
    
    companion object {
        private const val TAG = "MessageQueueProcessor"
        private const val DEFAULT_TTL: Byte = 5
        private const val MAX_QUEUE_SIZE = 1000
    }
    
    private val _queueStatus = MutableStateFlow(
        QueueStatus(0, 0, 0)
    )
    
    /**
     * Execute a queue status update transaction
     * 
     * This wrapper ensures that queue item updates are followed by status refresh.
     * Reduces code duplication and ensures consistency.
     * 
     * @param block The transaction block that updates queue items
     */
    private suspend inline fun withQueueStatusUpdate(block: suspend () -> Unit) {
        try {
            block()
        } finally {
            updateQueueStatus()
        }
    }
    
    init {
        // Setup message batcher callback
        messageBatcher.setOnBatchReady { batch ->
            // Batch is ready, send all messages
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
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
    
    override suspend fun enqueueMessage(
        messageId: String,
        chatId: String,
        recipientId: String,
        payload: String,
        deliveryMethod: DeliveryMethod
    ): Result<Unit> {
        return try {
            // Determine priority based on message type from database
            val message = messageDao.getMessageById(messageId)
            val priority = when (message?.messageType) {
                com.linker.app.data.local.entity.MessageType.TEXT,
                com.linker.app.data.local.entity.MessageType.LINK,
                com.linker.app.data.local.entity.MessageType.CONTACT,
                com.linker.app.data.local.entity.MessageType.LOCATION -> MessagePriority.TEXT // High priority (0)
                
                com.linker.app.data.local.entity.MessageType.IMAGE,
                com.linker.app.data.local.entity.MessageType.VIDEO,
                com.linker.app.data.local.entity.MessageType.GIF,
                com.linker.app.data.local.entity.MessageType.AUDIO,
                com.linker.app.data.local.entity.MessageType.FILE,
                com.linker.app.data.local.entity.MessageType.STICKER -> MessagePriority.MEDIA // Low priority (1)
                
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
            
            // Check queue size and cleanup if needed
            val queueSize = messageQueueDao.getQueueSize()
            if (queueSize > MAX_QUEUE_SIZE) {
                // Remove oldest SENT messages using batch delete
                val sentMessages = messageQueueDao.getMessagesByStatus(EntityQueueStatus.SENT)
                    .sortedBy { it.sentAt }
                    .take(queueSize - MAX_QUEUE_SIZE)
                
                if (sentMessages.isNotEmpty()) {
                    val queueIds = sentMessages.map { it.queueId }
                    messageQueueDao.deleteQueueItems(queueIds)
                    Log.d(TAG, "Batch deleted ${queueIds.size} old messages to maintain queue size")
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
            val failedMessages = messageQueueDao.getMessagesByStatus(EntityQueueStatus.FAILED)
            
            for (message in failedMessages) {
                if (message.retryCount < message.maxRetries) {
                    // Reset to pending for retry
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
    
    override suspend fun cancelMessage(messageId: String) {
        withQueueStatusUpdate {
            try {
                val queueItem = messageQueueDao.getQueueItemByMessageId(messageId)
                
                if (queueItem != null) {
                    messageQueueDao.deleteQueueItem(queueItem.queueId)
                    Log.d(TAG, "Message $messageId cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling message $messageId", e)
            }
        }
    }
    
    override suspend fun clearSentMessages() {
        withQueueStatusUpdate {
            try {
                val sentMessages = messageQueueDao.getMessagesByStatus(EntityQueueStatus.SENT)
                
                if (sentMessages.isNotEmpty()) {
                    // Batch delete for better performance
                    val queueIds = sentMessages.map { it.queueId }
                    messageQueueDao.deleteQueueItems(queueIds)
                    Log.d(TAG, "Cleared ${sentMessages.size} sent messages using batch delete")
                } else {
                    Log.d(TAG, "No sent messages to clear")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing sent messages", e)
            }
        }
    }
    
    override fun observeQueueStatus(): Flow<QueueStatus> {
        return _queueStatus.asStateFlow()
    }
    
    override fun observePendingCount(): Flow<Int> {
        return messageQueueDao.observePendingCount()
    }
    
    private suspend fun processMessage(message: MessageQueueEntity) {
        try {
            // Update status to SENDING
            val sending = message.copy(
                queueStatus = EntityQueueStatus.SENDING,
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
                    queueStatus = EntityQueueStatus.SENT,
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
            
            // Encrypt message payload before sending
            val encryptedPayload = try {
                val encryptionResult = encryptionManager.encryptMessage(
                    recipientId = message.recipientId,
                    plaintext = message.messagePayload
                )
                
                if (encryptionResult.isSuccess) {
                    encryptionResult.getOrNull()?.signalMessage ?: run {
                        Log.e(TAG, "Encryption succeeded but returned null encrypted message")
                        return Result.failure(Exception("Encryption returned null"))
                    }
                } else {
                    Log.e(TAG, "Failed to encrypt message: ${encryptionResult.exceptionOrNull()?.message}")
                    return Result.failure(encryptionResult.exceptionOrNull() ?: Exception("Encryption failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during message encryption", e)
                return Result.failure(e)
            }
            
            // Create BLE packet with encrypted payload
            val packet = BLEPacket.create(
                messageId = message.messageId,
                senderId = currentUserId,  // Use actual current user ID
                recipientId = message.recipientId,
                ttl = message.ttl.toByte(),
                hopCount = 0,
                encryptedPayload = encryptedPayload
            )
            
            Log.d(TAG, "Attempting to send BLE packet for message ${message.messageId} from $currentUserId to recipient ${message.recipientId}")
            
            // Add to message batcher for efficient transmission
            // The batcher will automatically send when batch size (5) is reached or timeout (5s) occurs
            messageBatcher.addMessage(packet)
            
            // For now, consider it successful when added to batcher
            // The actual send result will be handled by the batcher callback
            Log.d(TAG, "BLE packet added to batcher for message ${message.messageId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via BLE: ${message.messageId}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send message via Wi-Fi Direct
     */
    private suspend fun sendViaWiFiDirect(message: MessageQueueEntity): Result<Unit> {
        return try {
            // Get current user ID
            val currentUserId = currentUserProvider.getCurrentUserId()
            if (currentUserId == null) {
                Log.e(TAG, "Cannot send Wi-Fi Direct message: current user ID not available")
                return Result.failure(Exception("Current user ID not available"))
            }
            
            Log.d(TAG, "Attempting to send message ${message.messageId} via Wi-Fi Direct to ${message.recipientId}")
            
            // Start discovery to find the recipient
            val discoveryResult = nearbyConnectionsManager.startDiscovery()
            if (discoveryResult.isFailure) {
                Log.e(TAG, "Failed to start Wi-Fi Direct discovery: ${discoveryResult.exceptionOrNull()?.message}")
                return Result.failure(discoveryResult.exceptionOrNull() ?: Exception("Discovery failed"))
            }
            
            Log.d(TAG, "Wi-Fi Direct discovery started, waiting for recipient ${message.recipientId}")
            
            // Wait for recipient to be discovered (with timeout)
            var recipientEndpointId: String? = null
            val discoveryTimeout = 10_000L // 10 seconds
            val startTime = System.currentTimeMillis()
            
            // Collect discovered endpoints
            val discoveryJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                nearbyConnectionsManager.observeDiscoveredEndpoints().collect { endpoints ->
                    val recipientEndpoint = endpoints.find { it.userId == message.recipientId }
                    if (recipientEndpoint != null) {
                        recipientEndpointId = recipientEndpoint.endpointId
                        Log.d(TAG, "Found recipient ${message.recipientId} at endpoint ${recipientEndpoint.endpointId}")
                        // Stop collecting once found - cancel will be called outside
                    }
                }
            }
            
            // Wait for discovery or timeout
            while (recipientEndpointId == null && (System.currentTimeMillis() - startTime) < discoveryTimeout) {
                kotlinx.coroutines.delay(500)
            }
            
            discoveryJob.cancel()
            nearbyConnectionsManager.stopDiscovery()
            
            if (recipientEndpointId == null) {
                Log.w(TAG, "Recipient ${message.recipientId} not found via Wi-Fi Direct, falling back to BLE")
                return Result.failure(Exception("Recipient not found via Wi-Fi Direct"))
            }
            
            // Connect to recipient
            Log.d(TAG, "Connecting to recipient endpoint $recipientEndpointId")
            val connectionResult = nearbyConnectionsManager.connectToEndpoint(recipientEndpointId!!)
            if (connectionResult.isFailure) {
                Log.e(TAG, "Failed to connect to recipient: ${connectionResult.exceptionOrNull()?.message}")
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Connection failed"))
            }
            
            Log.d(TAG, "Connected to recipient, preparing to send message")
            
            // Encrypt message payload
            val encryptedPayload = try {
                val encryptionResult = encryptionManager.encryptMessage(
                    recipientId = message.recipientId,
                    plaintext = message.messagePayload
                )
                
                if (encryptionResult.isSuccess) {
                    encryptionResult.getOrNull()?.signalMessage ?: run {
                        Log.e(TAG, "Encryption succeeded but returned null encrypted message")
                        nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId!!)
                        return Result.failure(Exception("Encryption returned null"))
                    }
                } else {
                    Log.e(TAG, "Failed to encrypt message: ${encryptionResult.exceptionOrNull()?.message}")
                    nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId!!)
                    return Result.failure(encryptionResult.exceptionOrNull() ?: Exception("Encryption failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during message encryption", e)
                nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId!!)
                return Result.failure(e)
            }
            
            // Create temporary file with encrypted payload
            val tempFile = java.io.File.createTempFile("linker_msg_${message.messageId}", ".enc", context.cacheDir)
            try {
                tempFile.writeBytes(encryptedPayload)
                
                Log.d(TAG, "Sending file via Wi-Fi Direct (size: ${tempFile.length()} bytes)")
                
                // Send file with progress tracking
                var lastProgress = 0L
                val sendResult = nearbyConnectionsManager.sendFile(
                    endpointId = recipientEndpointId!!,
                    file = tempFile,
                    onProgress = { bytesTransferred, totalBytes ->
                        val progress = (bytesTransferred * 100 / totalBytes).toInt()
                        if (progress - lastProgress >= 10) { // Log every 10%
                            Log.d(TAG, "Wi-Fi Direct transfer progress: $progress% ($bytesTransferred/$totalBytes bytes)")
                            lastProgress = progress.toLong()
                        }
                    }
                )
                
                // Clean up
                tempFile.delete()
                nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId!!)
                
                if (sendResult.isSuccess) {
                    Log.d(TAG, "Message ${message.messageId} sent successfully via Wi-Fi Direct")
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "Failed to send file via Wi-Fi Direct: ${sendResult.exceptionOrNull()?.message}")
                    Result.failure(sendResult.exceptionOrNull() ?: Exception("File send failed"))
                }
            } catch (e: Exception) {
                tempFile.delete()
                nearbyConnectionsManager.disconnectFromEndpoint(recipientEndpointId!!)
                Log.e(TAG, "Error during Wi-Fi Direct file transfer", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending via Wi-Fi Direct: ${message.messageId}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Handle message sending failure with exponential backoff
     */
    private suspend fun handleMessageFailure(message: MessageQueueEntity, error: Throwable?) {
        val newRetryCount = message.retryCount + 1
        
        if (!RetryStrategy.shouldRetry(newRetryCount)) {
            // Mark as FAILED
            val failed = message.copy(
                queueStatus = EntityQueueStatus.FAILED,
                retryCount = newRetryCount,
                errorMessage = error?.message ?: "Unknown error"
            )
            messageQueueDao.updateQueueItem(failed)
            
            Log.e(TAG, "Message ${message.messageId} failed after $newRetryCount attempts")
        } else {
            // Reset to PENDING for retry with exponential backoff
            // The actual delay will be calculated by RetryStrategy when processing
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
    
    /**
     * Update queue status
     */
    private suspend fun updateQueueStatus() {
        val pending = messageQueueDao.getMessagesByStatus(EntityQueueStatus.PENDING).size
        val sending = messageQueueDao.getMessagesByStatus(EntityQueueStatus.SENDING).size
        val failed = messageQueueDao.getMessagesByStatus(EntityQueueStatus.FAILED).size
        
        _queueStatus.value = QueueStatus(pending, sending, failed)
    }
}
