package com.linker.app.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.MainActivity
import com.linker.app.R
import com.linker.app.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service
 *
 * Handles push notifications from Firebase:
 * - Chat messages → deep link to chat
 * - Likes / Comments / Follows → deep link to content
 * - System notifications
 */
@AndroidEntryPoint
class LinkerMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRegistrar: PushTokenRegistrar
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var stateRecovery: NotificationStateRecovery
    @Inject lateinit var reactionTracker: ReactionTracker

    override fun onCreate() {
        super.onCreate()
        ChatNotificationStore.initialize(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NotificationLogger.d("FCM token refreshed: \${token.take(10)}...")
        applicationScope.launch {
            pushTokenRegistrar.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"]
        NotificationLogger.d("onMessageReceived type=\$type")
        createDefaultChannels()

        val data = message.data
        val notificationType = data["type"] ?: "general"
        val chatType = data["chatType"] ?: ""

        when {
            // Delete notification command
            notificationType == "DELETE_NOTIFICATION" -> handleDeleteNotification(data)
            // Reaction notifications have type=LIKE and chatType=REACTION
            notificationType == "LIKE" && chatType == "REACTION" -> handleReactionNotification(message, data)
            notificationType == "MESSAGE" -> handleChatNotification(message, data)
            notificationType in listOf("LIKE", "COMMENT", "REPLY", "FOLLOW", "MENTION", "RELINK", "STORY_VIEW", "LIVE") ->
                handleSocialNotification(message, data, notificationType)
            else -> handleGeneralNotification(message)
        }
    }

    private fun ensureMessageChannel(recipientUid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channelId = ChatNotificationHelper.channelIdForAccount(recipientUid)
            val nm = getNotificationManager()
            if (nm.getNotificationChannel(channelId) != null) return
            val channel = NotificationChannel(
                channelId,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct and group messages for one signed-in account"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            NotificationLogger.e("Failed to create message channel", e)
        }
    }

    private fun handleChatNotification(
        message: RemoteMessage,
        data: Map<String, String>
    ) {
        val chatId = data["chatId"] ?: return
        val messageId = data["messageId"] ?: ""
        val senderName = data["senderName"] ?: data["title"] ?: "New Message"
        val senderId = data["senderId"] ?: ""
        val body = message.notification?.body ?: data["body"] ?: "Sent you a message"
        val recipientId = data["recipientId"] ?: auth.currentUser?.uid ?: return
        val chatType = data["chatType"] ?: "PRIVATE"
        val chatName = data["chatName"]?.takeIf { it.isNotBlank() }
        val isGroup = chatType == "GROUP"

        // Ignore notifications for messages sent by the recipient (self-sent messages)
        if (senderId == recipientId) {
            NotificationLogger.d("Ignoring self-sent message notification")
            return
        }

        ensureMessageChannel(recipientId)
        val channelId = ChatNotificationHelper.channelIdForAccount(recipientId)
        val notificationId = NotificationIdGenerator.generateChatNotificationId(recipientId, chatId, senderId, isGroup)

        var state = ChatNotificationStore.get(notificationId)
        if (state == null) {
            val recoveredMessages = stateRecovery.recoverMessagesFromNotification(notificationId)
            if (recoveredMessages.isNotEmpty()) {
                NotificationLogger.d("Recovered \${recoveredMessages.size} messages, creating state")
                ChatNotificationStore.addIncoming(
                    notificationId,
                    recipientUid = auth.currentUser?.uid ?: recipientId,
                    chatId = chatId,
                    message = "",
                    isGroupChat = isGroup
                )
                state = ChatNotificationStore.get(notificationId)
                if (state != null) {
                    state.clearMessages()
                    recoveredMessages.forEach { state.addMessage(it) }
                    NotificationLogger.d("Restored \${recoveredMessages.size} messages to state")
                }
            }
        }

        ChatNotificationStore.addIncoming(
            notificationId,
            recipientUid = auth.currentUser?.uid ?: recipientId,
            chatId = chatId,
            message = body,
            isGroupChat = isGroup
        )
        state = ChatNotificationStore.get(notificationId)
        
        if (state != null) {
            val notification = ChatNotificationHelper.buildChatNotification(
                context = this,
                notificationId = notificationId,
                channelId = channelId,
                targetAccountUid = recipientId,
                chatId = chatId,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                messages = state.messages,
                remoteInputHistory = null,
                isGroupChat = state.isGroupChat,
                chatName = chatName
            ).build()
            showNotification(notificationId, notification)
        }

        val current = auth.currentUser?.uid
        if (messageId.isNotBlank() &&
            senderId.isNotBlank() &&
            senderId != current &&
            current != null
        ) {
            applicationScope.launch {
                updateDeliveryReceipt(chatId, messageId, current)
            }
        }
    }

    private suspend fun updateDeliveryReceipt(chatId: String, messageId: String, currentUid: String) {
        var retryCount = 0
        val maxRetries = 3
        while (retryCount < maxRetries) {
            try {
                val now = System.currentTimeMillis()
                val messageRef = firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)

                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(messageRef)
                    if (snapshot.exists()) {
                        transaction.update(
                            messageRef,
                            mapOf(
                                "deliveryReceipts.\$currentUid" to now,
                                "deliveredAt" to now,
                                "messageStatus" to "DELIVERED"
                            )
                        )
                    }
                }.await()
                return // Success
            } catch (e: Exception) {
                retryCount++
                if (retryCount >= maxRetries) {
                    NotificationLogger.e("Failed to update delivery receipt after \$maxRetries attempts", e)
                } else {
                    delay(1000L * retryCount) // Exponential backoff
                }
            }
        }
    }

    private fun showNotification(notificationId: Int, notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    NotificationLogger.w("Cannot show notification: permission not granted")
                    return
                }
            }
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (e: SecurityException) {
            NotificationLogger.e("SecurityException showing notification", e)
        }
    }

    private fun handleSocialNotification(
        message: RemoteMessage,
        data: Map<String, String>,
        type: String
    ) {
        val title = data["title"] ?: message.notification?.title ?: "Linker"
        val body = data["body"] ?: message.notification?.body ?: "New activity"
        val targetId = data["targetEntityId"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link", "\$type/\$targetId")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            "\$type-\$targetId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = when (type) {
            "LIKE" -> R.drawable.ic_heart_outline
            "COMMENT", "REPLY" -> R.drawable.ic_ai_commentary_outline
            "FOLLOW" -> R.drawable.ic_ai_users_outline
            "MENTION" -> R.drawable.ic_hashtag_down_outline
            "RELINK" -> R.drawable.ic_toy_6_outline
            "STORY_VIEW" -> R.drawable.ic_story_outline
            "LIVE" -> R.drawable.ic_play_add_outline
            else -> R.drawable.ic_ai_homepage_outline
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        showNotification("\$type-\$targetId".hashCode(), notification)
    }

    private fun handleGeneralNotification(message: RemoteMessage) {
        val title = message.notification?.title ?: "Linker"
        val body = message.notification?.body ?: "You have a new notification"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_GENERAL_ID)
            .setSmallIcon(R.drawable.ic_ai_homepage_outline)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        showNotification(java.util.UUID.randomUUID().hashCode(), notification)
    }

    private fun createDefaultChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getNotificationManager()
            if (nm.getNotificationChannel(CHANNEL_SOCIAL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SOCIAL_ID,
                        "Social",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Likes, comments, follows"
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_GENERAL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_GENERAL_ID,
                        "General alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "System notifications and general alerts"
                    }
                )
            }
        } catch (e: Exception) {
            NotificationLogger.e("Failed to create default channels", e)
        }
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun handleReactionNotification(
        message: RemoteMessage,
        data: Map<String, String>
    ) {
        val messageId = data["messageId"] ?: return
        val senderName = data["senderName"] ?: data["title"] ?: "Someone"
        val senderId = data["senderId"] ?: ""
        val body = message.notification?.body ?: data["body"] ?: "reacted to your message"
        val recipientId = data["recipientId"] ?: auth.currentUser?.uid ?: return

        // Ignore self-reactions
        if (senderId == recipientId) {
            NotificationLogger.d("Ignoring self-reaction notification")
            return
        }

        val notificationId = "reaction_\$messageId".hashCode() and 0x7fff_fffe
        val reactionCount = reactionTracker.addReactor(messageId, senderId)
        
        val notificationTitle = if (reactionCount == 1) {
            "Linker • \$senderName"
        } else {
            "Linker • \$reactionCount kişi"
        }
        
        val notificationBody = if (reactionCount == 1) {
            body
        } else {
            "Mesajınıza tepki verdi"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link", "message/\$messageId")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(this, ReactionNotificationDismissReceiver::class.java).apply {
            putExtra("messageId", messageId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureReactionChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_REACTION_ID)
            .setSmallIcon(R.drawable.ic_ai_homepage_outline)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(reactionCount > 1)
            .build()

        showNotification(notificationId, notification)
    }

    private fun ensureReactionChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getNotificationManager()
            if (nm.getNotificationChannel(CHANNEL_REACTION_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_REACTION_ID,
                "Reactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emoji reactions to your messages"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        } catch (e: Exception) {
            NotificationLogger.e("Failed to create reaction channel", e)
        }
    }

    private fun handleDeleteNotification(data: Map<String, String>) {
        val messageId = data["messageId"] ?: return
        val chatId = data["chatId"]
        
        val reactionNotificationId = "reaction_\$messageId".hashCode() and 0x7fff_fffe
        NotificationManagerCompat.from(this).cancel(reactionNotificationId)
        reactionTracker.clearReactors(messageId)
        
        var foundInStore = false
        if (chatId != null) {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                val allStates = ChatNotificationStore.getAll()
                for ((notificationId, state) in allStates) {
                    if (state.chatId == chatId && state.recipientUid == currentUserId) {
                        NotificationManagerCompat.from(this).cancel(notificationId)
                        ChatNotificationStore.clear(notificationId)
                        foundInStore = true
                    }
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !foundInStore) {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val activeNotifications = notificationManager.activeNotifications
                
                for (statusBarNotification in activeNotifications) {
                    val notification = statusBarNotification.notification
                    val extras = notification.extras
                    
                    val notificationMessageId = extras?.getString(NotificationConstants.EXTRA_MESSAGE_ID)
                    val notificationChatId = extras?.getString(NotificationConstants.EXTRA_CHAT_ID)
                    
                    if (notificationMessageId == messageId || (chatId != null && notificationChatId == chatId)) {
                        notificationManager.cancel(statusBarNotification.id)
                        ChatNotificationStore.clear(statusBarNotification.id)
                    }
                }
            } catch (e: Exception) {
                NotificationLogger.e("Failed to scan active notifications", e)
            }
        }
    }

    companion object {
        private const val CHANNEL_SOCIAL_ID = "linker_social"
        private const val CHANNEL_GENERAL_ID = "linker_general"
        private const val CHANNEL_REACTION_ID = "linker_reactions"
        const val CHANNEL_ID: String = CHANNEL_SOCIAL_ID
    }
}
