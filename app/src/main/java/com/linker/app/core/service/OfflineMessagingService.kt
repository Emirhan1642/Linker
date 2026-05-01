package com.linker.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linker.app.R
import com.linker.app.data.ble.BLEMeshManager
import com.linker.app.data.ble.BLEPacket
import com.linker.app.data.local.dao.ChatDao
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.entity.ChatEntity
import com.linker.app.data.local.entity.ChatType
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.MessageEntity
import com.linker.app.data.local.entity.MessageStatus
import com.linker.app.data.local.entity.MessageType
import com.linker.app.data.permission.PermissionManager
import com.linker.app.data.queue.MessageQueueProcessor
import com.linker.app.domain.usecase.user.CurrentUserProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for offline messaging.
 * 
 * Implements Requirements 11.1-11.8:
 * - Runs as foreground service with persistent notification
 * - Manages BLE mesh network lifecycle
 * - Handles scanning and advertising
 * - Processes message queue in background
 * - Supports start/stop/toggle actions
 */
@AndroidEntryPoint
class OfflineMessagingService : Service() {
    
    @Inject
    lateinit var bleMeshManager: BLEMeshManager
    
    @Inject
    lateinit var messageQueueProcessor: MessageQueueProcessor
    
    @Inject
    lateinit var permissionManager: PermissionManager
    
    @Inject
    lateinit var messageDao: MessageDao
    
    @Inject
    lateinit var chatDao: ChatDao
    
    @Inject
    lateinit var currentUserProvider: CurrentUserProvider
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var isScanning = false
    
    companion object {
        const val ACTION_START = "com.linker.app.ACTION_START_OFFLINE_MESSAGING"
        const val ACTION_STOP = "com.linker.app.ACTION_STOP_OFFLINE_MESSAGING"
        const val ACTION_TOGGLE_SCANNING = "com.linker.app.ACTION_TOGGLE_SCANNING"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "offline_messaging_channel"
        const val CHANNEL_NAME = "Offline Messaging"
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("OfflineMessagingService", "Service onCreate called")
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        android.util.Log.d("OfflineMessagingService", "Foreground service started")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("OfflineMessagingService", "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                android.util.Log.d("OfflineMessagingService", "ACTION_START received")
                startOfflineMessaging()
            }
            ACTION_STOP -> {
                android.util.Log.d("OfflineMessagingService", "ACTION_STOP received")
                stopOfflineMessaging()
                stopSelf()
            }
            ACTION_TOGGLE_SCANNING -> {
                android.util.Log.d("OfflineMessagingService", "ACTION_TOGGLE_SCANNING received")
                toggleScanning()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopOfflineMessaging()
        serviceScope.cancel()
    }
    
    /**
     * Start offline messaging (BLE mesh network and message processing).
     */
    private fun startOfflineMessaging() {
        serviceScope.launch {
            try {
                android.util.Log.d("OfflineMessagingService", "Starting offline messaging...")
                
                // Check permissions
                if (!permissionManager.hasBluetoothPermissions()) {
                    android.util.Log.e("OfflineMessagingService", "Bluetooth permissions not granted")
                    // Cannot start without permissions
                    updateNotification("Waiting for Bluetooth permissions")
                    return@launch
                }
                
                android.util.Log.d("OfflineMessagingService", "Permissions OK, initializing BLE mesh manager")
                
                // Initialize BLE mesh manager
                bleMeshManager.initialize()
                
                // Start mesh network
                bleMeshManager.startMeshNetwork()
                
                // Start scanning
                bleMeshManager.startScanning()
                isScanning = true
                
                // Start advertising
                bleMeshManager.startAdvertising()
                
                android.util.Log.d("OfflineMessagingService", "BLE mesh network started, starting queue processing")
                
                // Set up callback to handle received BLE messages
                setupMessageReceivedCallback()
                
                // Process message queue periodically
                startQueueProcessing()
                
                updateNotification("Offline messaging active")
                android.util.Log.d("OfflineMessagingService", "Offline messaging started successfully")
                
            } catch (e: Exception) {
                android.util.Log.e("OfflineMessagingService", "Error starting offline messaging: ${e.message}", e)
                updateNotification("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Stop offline messaging.
     */
    private fun stopOfflineMessaging() {
        serviceScope.launch {
            try {
                // Stop scanning
                bleMeshManager.stopScanning()
                isScanning = false
                
                // Stop advertising
                bleMeshManager.stopAdvertising()
                
                // Stop mesh network
                bleMeshManager.stopMeshNetwork()
                
                updateNotification("Offline messaging stopped")
                
            } catch (e: Exception) {
                // Log error but continue shutdown
            }
        }
    }
    
    /**
     * Toggle BLE scanning on/off.
     */
    private fun toggleScanning() {
        serviceScope.launch {
            try {
                if (isScanning) {
                    bleMeshManager.stopScanning()
                    isScanning = false
                    updateNotification("Scanning paused")
                } else {
                    bleMeshManager.startScanning()
                    isScanning = true
                    updateNotification("Scanning active")
                }
            } catch (e: Exception) {
                updateNotification("Error toggling scan: ${e.message}")
            }
        }
    }
    
    /**
     * Set up callback to handle received BLE messages.
     * 
     * When a message is received via BLE, decrypt it and save to database.
     */
    private fun setupMessageReceivedCallback() {
        bleMeshManager.onMessageReceived { packet ->
            serviceScope.launch {
                try {
                    handleReceivedBLEPacket(packet)
                } catch (e: Exception) {
                    Log.e("OfflineMessagingService", "Error handling received BLE packet: ${e.message}", e)
                }
            }
        }
        Log.d("OfflineMessagingService", "Message received callback set up")
    }
    
    /**
     * Handle a received BLE packet by saving it to the database.
     * 
     * @param packet The received BLE packet
     */
    private suspend fun handleReceivedBLEPacket(packet: BLEPacket) {
        try {
            val currentUserId = currentUserProvider.getCurrentUserId()
            if (currentUserId == null) {
                Log.w("OfflineMessagingService", "Cannot save received message: current user not available")
                return
            }
            
            // Verify this message is for us
            if (packet.recipientId != currentUserId) {
                Log.w("OfflineMessagingService", "Received message not for us: recipient=${packet.recipientId}, current=$currentUserId")
                return
            }
            
            Log.d("OfflineMessagingService", "Processing received BLE message: ${packet.messageId} from ${packet.senderId}")
            
            // Decrypt payload (for now, assume it's plain text)
            // TODO: Implement proper E2E decryption
            val decryptedContent = String(packet.encryptedPayload)
            
            // Find or create chat with sender
            val senderId = packet.senderId
            val chatId = findOrCreatePrivateChat(senderId, currentUserId)
            
            if (chatId == null) {
                Log.e("OfflineMessagingService", "Failed to find or create chat with sender $senderId")
                return
            }
            
            // Create message entity
            val now = System.currentTimeMillis()
            val messageEntity = MessageEntity(
                messageId = packet.messageId,
                chatId = chatId,
                senderId = senderId,
                messageType = MessageType.TEXT,
                content = decryptedContent,
                messageStatus = MessageStatus.DELIVERED,
                deliveryMethod = DeliveryMethod.BLE,
                createdAt = now,
                updatedAt = now,
                deliveredAt = now
            )
            
            // Save message to database
            messageDao.insertMessage(messageEntity)
            Log.d("OfflineMessagingService", "Saved received message ${packet.messageId} to database")
            
            // Update chat's lastMessage and updatedAt
            updateChatAfterMessageReceived(chatId, decryptedContent, now)
            
        } catch (e: Exception) {
            Log.e("OfflineMessagingService", "Error processing received BLE packet: ${e.message}", e)
        }
    }
    
    /**
     * Find existing private chat with user or create a new one.
     * 
     * @param otherUserId The other user's ID
     * @param currentUserId The current user's ID
     * @return Chat ID or null if creation failed
     */
    private suspend fun findOrCreatePrivateChat(otherUserId: String, currentUserId: String): String? {
        return try {
            // Create a deterministic chat ID based on user IDs (sorted to ensure consistency)
            val userIds = listOf(currentUserId, otherUserId).sorted()
            val chatId = "private_${userIds[0]}_${userIds[1]}"
            
            // Check if chat already exists
            val existingChat = chatDao.getChatById(chatId)
            if (existingChat != null) {
                Log.d("OfflineMessagingService", "Found existing chat: $chatId")
                return chatId
            }
            
            // Create new chat
            val now = System.currentTimeMillis()
            
            val newChat = ChatEntity(
                chatId = chatId,
                chatType = ChatType.PRIVATE,
                chatName = null,
                chatImageUrl = null,
                participantIds = listOf(currentUserId, otherUserId),
                lastMessageId = null,
                lastMessageText = null,
                lastMessageAt = now,
                unreadCount = 1,
                isPinned = false,
                isMuted = false,
                isArchived = false,
                isBlocked = false,
                isFavorited = false,
                createdAt = now,
                updatedAt = now
            )
            
            chatDao.insertChat(newChat)
            Log.d("OfflineMessagingService", "Created new chat: $chatId")
            chatId
        } catch (e: Exception) {
            Log.e("OfflineMessagingService", "Error finding/creating chat: ${e.message}", e)
            null
        }
    }
    
    /**
     * Update chat after receiving a message.
     * 
     * @param chatId Chat ID
     * @param lastMessage Last message content
     * @param timestamp Message timestamp
     */
    private suspend fun updateChatAfterMessageReceived(chatId: String, lastMessage: String, timestamp: Long) {
        try {
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                val updatedChat = chat.copy(
                    lastMessageText = lastMessage.take(100), // Truncate for preview
                    lastMessageAt = timestamp,
                    updatedAt = timestamp,
                    unreadCount = chat.unreadCount + 1
                )
                chatDao.updateChat(updatedChat)
                Log.d("OfflineMessagingService", "Updated chat $chatId after message received")
            }
        } catch (e: Exception) {
            Log.e("OfflineMessagingService", "Error updating chat: ${e.message}", e)
        }
    }
    
    /**
     * Start periodic message queue processing.
     */
    private fun startQueueProcessing() {
        serviceScope.launch {
            // Process queue every 10 seconds
            android.util.Log.d("OfflineMessagingService", "Starting queue processing loop")
            while (true) {
                try {
                    android.util.Log.d("OfflineMessagingService", "Processing message queue...")
                    messageQueueProcessor.processQueue()
                    android.util.Log.d("OfflineMessagingService", "Queue processing completed")
                    kotlinx.coroutines.delay(10_000)
                } catch (e: Exception) {
                    android.util.Log.e("OfflineMessagingService", "Error processing queue: ${e.message}", e)
                    // Log error but continue processing
                    kotlinx.coroutines.delay(10_000)
                }
            }
        }
    }
    
    /**
     * Create notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for offline messaging service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification.
     */
    private fun createNotification(contentText: String = "Offline messaging is running"): Notification {
        // Create intent to open app
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create stop action
        val stopIntent = Intent(this, OfflineMessagingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline Messaging")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_bluetooth_outline) // TODO: Add proper icon
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close_circle_bold, // TODO: Add proper icon
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Update notification with new content.
     */
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }
}
