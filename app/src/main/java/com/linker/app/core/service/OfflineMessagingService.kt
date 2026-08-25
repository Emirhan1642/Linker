package com.linker.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.linker.app.BuildConfig
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class OfflineMessagingService : Service() {
    
    @Inject lateinit var bleMeshManager: BLEMeshManager
    @Inject lateinit var messageQueueProcessor: MessageQueueProcessor
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var chatDao: ChatDao
    @Inject lateinit var currentUserProvider: CurrentUserProvider
    @Inject lateinit var encryptionManager: com.linker.app.data.encryption.EncryptionManager
    
    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(supervisorJob + Dispatchers.Default)
    
    private var isScanning = false
    
    private var queueProcessingJob: Job? = null
    private var queueProcessingInterval = 10_000L
    private val minInterval = 5_000L
    private val maxInterval = 60_000L
    
    private var permissionCheckJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val processingMessages = ConcurrentHashMap<String, Boolean>()
    
    private val bleRetryCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val maxBleRetries = 5
    
    companion object {
        const val ACTION_START = "com.linker.app.ACTION_START_OFFLINE_MESSAGING"
        const val ACTION_STOP = "com.linker.app.ACTION_STOP_OFFLINE_MESSAGING"
        const val ACTION_TOGGLE_SCANNING = "com.linker.app.ACTION_TOGGLE_SCANNING"
        const val ACTION_SERVICE_STATE_CHANGED = "com.linker.app.ACTION_SERVICE_STATE_CHANGED"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "offline_messaging_channel"
        const val CHANNEL_NAME = "Offline Messaging"
        private const val TAG = "OfflineMessagingService"
        
        private fun sanitizeUserId(userId: String?): String {
            return if (BuildConfig.DEBUG) {
                userId ?: "null"
            } else {
                userId?.let { "user_${it.hashCode().toString(16)}" } ?: "null"
            }
        }
        
        private fun sanitizeMessageId(messageId: String?): String {
            return if (BuildConfig.DEBUG) {
                messageId ?: "null"
            } else {
                messageId?.let { "msg_${it.hashCode().toString(16)}" } ?: "null"
            }
        }
    }
    
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.w(TAG, "Bluetooth turned off")
                    stopOfflineMessaging()
                    updateNotification("Bluetooth disabled")
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth turned on, restarting service")
                    bleRetryCount.set(0)
                    startOfflineMessaging()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
        
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
                val intent = Intent("com.linker.app.REQUEST_NOTIFICATION_PERMISSION")
                sendBroadcast(intent)
                stopSelf()
                return
            }
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "Foreground service started")
            
            registerReceiverSafe()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
        }
    }

    private fun registerReceiverSafe() {
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                startOfflineMessaging()
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopOfflineMessaging()
                stopSelf()
            }
            ACTION_TOGGLE_SCANNING -> {
                Log.d(TAG, "ACTION_TOGGLE_SCANNING received")
                toggleScanning()
            }
            null -> {
                Log.d(TAG, "Service restarted by system, restoring state")
                restoreServiceState()
            }
        }
        return START_REDELIVER_INTENT
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            // Not registered
        }
        
        permissionCheckJob?.cancel()
        releaseWakeLock()
        stopOfflineMessaging()
        
        supervisorJob.cancel()
        Log.d(TAG, "Service destroyed, all coroutines cancelled")
    }
    
    private fun restoreServiceState() {
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("offline_messaging_state", Context.MODE_PRIVATE)
                if (prefs.getBoolean("was_running", false)) {
                    Log.d(TAG, "Restoring offline messaging service")
                    startOfflineMessaging()
                } else {
                    Log.d(TAG, "Service was not running before, stopping")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring service state", e)
                stopSelf()
            }
        }
    }
    
    private fun saveServiceState(isRunning: Boolean) {
        try {
            getSharedPreferences("offline_messaging_state", Context.MODE_PRIVATE)
                .edit().putBoolean("was_running", isRunning).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving service state", e)
        }
    }
    
    private fun broadcastServiceState(isRunning: Boolean) {
        val intent = Intent(ACTION_SERVICE_STATE_CHANGED).apply {
            putExtra("isRunning", isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Linker::OfflineMessagingWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(10 * 60 * 1000L) // 10 minutes timeout
                Log.d(TAG, "WakeLock acquired")
            }
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
    }
    
    private fun startOfflineMessaging() {
        serviceScope.launch {
            var retryDelay = 2000L
            while (bleRetryCount.get() < maxBleRetries && isActive) {
                try {
                    acquireWakeLock()
                    val currentAttempt = bleRetryCount.get() + 1
                    Log.d(TAG, "Starting offline messaging (attempt $currentAttempt/$maxBleRetries)...")
                    
                    if (!permissionManager.hasBluetoothPermissions()) {
                        Log.e(TAG, "Bluetooth permissions not granted")
                        updateNotification("Waiting for Bluetooth permissions")
                        stopSelf()
                        return@launch
                    }
                    
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                        Log.e(TAG, "Bluetooth adapter not available or disabled")
                        updateNotification("Please enable Bluetooth")
                        delay(retryDelay)
                        bleRetryCount.incrementAndGet()
                        retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(30_000)
                        continue
                    }
                    
                    Log.d(TAG, "Permissions OK, initializing BLE mesh manager")
                    
                    try {
                        bleMeshManager.initialize()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize BLE mesh manager", e)
                        throw e
                    }
                    
                    try {
                        bleMeshManager.startMeshNetwork()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start mesh network", e)
                        throw e
                    }
                    
                    try {
                        bleMeshManager.startScanning()
                        isScanning = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start scanning", e)
                        throw e
                    }
                    
                    try {
                        bleMeshManager.startAdvertising()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start advertising", e)
                    }
                    
                    setupMessageReceivedCallback()
                    startQueueProcessing()
                    startPermissionMonitoring()
                    
                    saveServiceState(true)
                    broadcastServiceState(true)
                    
                    bleRetryCount.set(0)
                    updateNotification("Offline messaging active")
                    Log.d(TAG, "Offline messaging started successfully")
                    break
                    
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception: ${e.message}", e)
                    updateNotification("Permissions error")
                    stopSelf()
                    break
                } catch (e: Exception) {
                    val count = bleRetryCount.incrementAndGet()
                    Log.e(TAG, "Error starting offline messaging (attempt $count/$maxBleRetries): ${e.message}", e)
                    
                    if (count >= maxBleRetries) {
                        updateNotification("Failed to start after $maxBleRetries attempts")
                        stopSelf()
                        break
                    }
                    
                    delay(retryDelay)
                    retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(30_000)
                } finally {
                    releaseWakeLock()
                }
            }
        }
    }
    
    private fun stopQueueProcessing() {
        queueProcessingJob?.cancel()
        queueProcessingJob = null
    }
    
    private fun stopOfflineMessaging() {
        serviceScope.launch {
            try {
                acquireWakeLock()
                stopQueueProcessing()
                bleMeshManager.stopScanning()
                isScanning = false
                bleMeshManager.stopAdvertising()
                bleMeshManager.stopMeshNetwork()
                
                saveServiceState(false)
                broadcastServiceState(false)
                updateNotification("Offline messaging stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping offline messaging", e)
            } finally {
                releaseWakeLock()
            }
        }
    }
    
    private fun startPermissionMonitoring() {
        permissionCheckJob?.cancel()
        permissionCheckJob = serviceScope.launch {
            while (isActive) {
                delay(30_000)
                if (!permissionManager.hasBluetoothPermissions()) {
                    Log.e(TAG, "Bluetooth permissions revoked during runtime")
                    updateNotification("Bluetooth permissions revoked")
                    stopOfflineMessaging()
                    stopSelf()
                    break
                }
            }
        }
    }
    
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
    
    private fun setupMessageReceivedCallback() {
        bleMeshManager.onMessageReceived { packet ->
            serviceScope.launch {
                try {
                    handleReceivedBLEPacket(packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling received BLE packet: ${e.message}", e)
                }
            }
        }
        Log.d(TAG, "Message received callback set up")
    }
    
    private suspend fun handleReceivedBLEPacket(packet: BLEPacket) {
        try {
            if (processingMessages.putIfAbsent(packet.messageId, true) != null) {
                Log.d(TAG, "Message ${sanitizeMessageId(packet.messageId)} already being processed, skipping")
                return
            }
            
            try {
                val currentUserId = currentUserProvider.getCurrentUserId()
                if (currentUserId == null) {
                    Log.w(TAG, "Cannot save received message: current user not available")
                    return
                }
                
                if (packet.recipientId != currentUserId) {
                    Log.w(TAG, "Received message not for us: recipient=${sanitizeUserId(packet.recipientId)}, current=${sanitizeUserId(currentUserId)}")
                    return
                }
                
                val existingMessage = messageDao.getMessageById(packet.messageId)
                if (existingMessage != null) {
                    Log.d(TAG, "Message ${sanitizeMessageId(packet.messageId)} already exists in database, skipping")
                    return
                }
                
                Log.d(TAG, "Processing received BLE message: ${sanitizeMessageId(packet.messageId)} from ${sanitizeUserId(packet.senderId)}")
                
                val decryptedContent = try {
                    val encryptedMessage = com.linker.app.data.encryption.EncryptedMessage(packet.encryptedPayload)
                    encryptionManager.decryptMessage(packet.senderId, encryptedMessage)
                        .getOrElse { error ->
                            Log.e(TAG, "Failed to decrypt message: ${error.message}", error as? Throwable)
                            return
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption error: ${e.message}", e)
                    return
                }
                
                val senderId = packet.senderId
                val chatId = findOrCreatePrivateChat(senderId, currentUserId)
                
                if (chatId == null) {
                    Log.e(TAG, "Failed to find or create chat with sender ${sanitizeUserId(senderId)}")
                    return
                }
                
                withContext(Dispatchers.IO) {
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
                    
                    messageDao.insertMessage(messageEntity)
                    updateChatAfterMessageReceived(chatId, decryptedContent, now)
                }

                // Show local notification for incoming offline message
                try {
                    val notificationId = com.linker.app.core.notification.NotificationIdGenerator.generateChatNotificationId(currentUserId, chatId, senderId, false)
                    val channelId = com.linker.app.core.notification.ChatNotificationHelper.channelIdForAccount(currentUserId)
                    val notification = com.linker.app.core.notification.ChatNotificationHelper.buildChatNotification(
                        context = this@OfflineMessagingService,
                        notificationId = notificationId,
                        channelId = channelId,
                        targetAccountUid = currentUserId,
                        chatId = chatId,
                        messageId = packet.messageId,
                        senderId = senderId,
                        senderName = "BLE Mesh ($senderId)",
                        messages = listOf(decryptedContent),
                        isGroupChat = false
                    ).build()
                    androidx.core.app.NotificationManagerCompat.from(this@OfflineMessagingService).notify(notificationId, notification)
                } catch (notifEx: Exception) {
                    Log.w(TAG, "Failed to show notification for BLE message: ${notifEx.message}")
                }
                
                Log.d(TAG, "Saved received message ${sanitizeMessageId(packet.messageId)} to database")
            } finally {
                processingMessages.remove(packet.messageId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received BLE packet: ${e.message}", e)
            processingMessages.remove(packet.messageId)
        }
    }
    
    private suspend fun findOrCreatePrivateChat(otherUserId: String, currentUserId: String): String? {
        return try {
            val userIds = listOf(currentUserId, otherUserId).sorted()
            val deterministicChatId = "private_${userIds[0]}_${userIds[1]}"
            
            val existingById = chatDao.getChatById(deterministicChatId)
            if (existingById != null) {
                Log.d(TAG, "Found existing chat by deterministic ID: $deterministicChatId")
                return deterministicChatId
            }

            // Check if any existing private chat has these participants via indexed query
            val existing = chatDao.findPrivateChat(currentUserId, otherUserId)
            if (existing != null) {
                Log.d(TAG, "Found existing private chat: ${existing.chatId}")
                return existing.chatId
            }

            val chatId = deterministicChatId
            
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
            Log.d(TAG, "Created new chat: $chatId")
            chatId
        } catch (e: Exception) {
            Log.e(TAG, "Error finding/creating chat: ${e.message}", e)
            null
        }
    }
    
    private suspend fun updateChatAfterMessageReceived(chatId: String, lastMessage: String, timestamp: Long) {
        try {
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                val updatedChat = chat.copy(
                    lastMessageText = lastMessage.take(100),
                    lastMessageAt = timestamp,
                    updatedAt = timestamp,
                    unreadCount = chat.unreadCount + 1
                )
                chatDao.updateChat(updatedChat)
                Log.d(TAG, "Updated chat $chatId after message received")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating chat: ${e.message}", e)
        }
    }
    
    private fun startQueueProcessing() {
        queueProcessingJob?.cancel()
        queueProcessingJob = serviceScope.launch {
            Log.d(TAG, "Starting queue processing loop")
            try {
                while (isActive) {
                    try {
                        Log.d(TAG, "Processing message queue...")
                        val processedCount = messageQueueProcessor.processQueue()
                        Log.d(TAG, "Queue processing completed: $processedCount messages")
                        
                        queueProcessingInterval = when {
                            processedCount > 10 -> minInterval
                            processedCount > 0 -> 10_000L
                            else -> (queueProcessingInterval * 1.5).toLong().coerceAtMost(maxInterval)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing queue: ${e.message}", e)
                        queueProcessingInterval = maxInterval
                    }
                    delay(queueProcessingInterval)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Queue processing cancelled")
                throw e
            } finally {
                Log.d(TAG, "Queue processing stopped")
            }
        }
    }
    
    fun triggerImmediateQueueProcessing() {
        queueProcessingJob?.cancel()
        queueProcessingInterval = minInterval
        startQueueProcessing()
    }
    
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
    
    private fun createNotification(contentText: String = "Offline messaging is running"): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, OfflineMessagingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val bluetoothIcon = try {
            R.drawable.ic_bluetooth_outline
        } catch (e: Resources.NotFoundException) {
            android.R.drawable.stat_sys_data_bluetooth
        }
        
        val stopIcon = try {
            R.drawable.ic_close_circle_bold
        } catch (e: Resources.NotFoundException) {
            android.R.drawable.ic_menu_close_clear_cancel
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Offline Messaging")
            .setContentText(contentText)
            .setSmallIcon(bluetoothIcon)
            .setContentIntent(pendingIntent)
            .addAction(stopIcon, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }
}
