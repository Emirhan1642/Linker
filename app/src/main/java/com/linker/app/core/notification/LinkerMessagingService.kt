package com.linker.app.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.MainActivity
import com.linker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d(TAG, "FCM token refreshed: $token")
        serviceScope.launch {
            pushTokenRegistrar.registerToken(token)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "onMessageReceived data=${message.data} notification=${message.notification}")
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

    /**
     * Aynı alıcı hesap + konuşma dalı için tek bildirim:
     * - Özel: recipient + gönderen (uid)
     * - Grup: recipient + chatId
     */
    private fun stableChatNotificationId(recipientId: String, chatId: String, senderId: String, isGroup: Boolean): Int {
        val branch = if (isGroup) "g|$chatId" else "u|$senderId"
        val key = "$recipientId|$branch"
        return key.hashCode() and 0x7fff_fffe
    }

    private fun ensureMessageChannel(recipientUid: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleChatNotification(
        message: RemoteMessage,
        data: Map<String, String>
    ) {
        Log.d(TAG, "handleChatNotification data=$data")
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
            Log.d(TAG, "Ignoring self-sent message notification")
            return
        }

        ensureMessageChannel(recipientId)
        val channelId = ChatNotificationHelper.channelIdForAccount(recipientId)
        val notificationId = stableChatNotificationId(recipientId, chatId, senderId, isGroup)
        
        Log.d(TAG, "Calculated notificationId=$notificationId for recipientId=$recipientId, chatId=$chatId, senderId=$senderId, isGroup=$isGroup")

        // Check if state exists, if not try to recover from existing notification
        var state = ChatNotificationStore.get(notificationId)
        if (state == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "State not found, trying to recover from active notification")
            try {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val activeNotifications = notificationManager.activeNotifications
                val existingNotification = activeNotifications.firstOrNull { it.id == notificationId }
                
                if (existingNotification != null) {
                    Log.d(TAG, "Found existing notification, recovering messages")
                    val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existingNotification.notification)
                    
                    if (messagingStyle != null) {
                        val existingMessages = mutableListOf<String>()
                        for (msg in messagingStyle.messages) {
                            val msgSenderName = msg.person?.name?.toString() ?: ""
                            val text = msg.text?.toString() ?: ""
                            if (text.isNotBlank()) {
                                if (msgSenderName == "Siz") {
                                    existingMessages.add("Siz: $text")
                                } else if (msgSenderName.isNotBlank()) {
                                    existingMessages.add("$msgSenderName: $text")
                                } else {
                                    existingMessages.add(text)
                                }
                            }
                        }
                        
                        if (existingMessages.isNotEmpty()) {
                            Log.d(TAG, "Recovered ${existingMessages.size} messages, creating state")
                            // Create state with empty message first
                            ChatNotificationStore.addIncoming(
                                notificationId,
                                recipientUid = auth.currentUser?.uid ?: recipientId,
                                chatId = chatId,
                                message = "",
                                isGroupChat = isGroup
                            )
                            state = ChatNotificationStore.get(notificationId)
                            // Restore messages
                            if (state != null) {
                                state.messages.clear()
                                state.messages.addAll(existingMessages)
                                Log.d(TAG, "Restored ${existingMessages.size} messages to state")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover messages: ${e.message}", e)
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
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        }

        val current = auth.currentUser?.uid
        if (messageId.isNotBlank() &&
            senderId.isNotBlank() &&
            senderId != current &&
            current != null
        ) {
            serviceScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    // chatId/messageId should be in the FCM payload; use them directly for subcollection path
                    if (chatId.isNotBlank() && messageId.isNotBlank()) {
                        val messageRef = firestore.collection("chats").document(chatId)
                            .collection("messages").document(messageId)
                        val snapshot = messageRef.get().await()
                        if (snapshot.exists()) {
                            messageRef.update(
                                mapOf(
                                    "deliveryReceipts.$current" to now,
                                    "deliveredAt" to now,
                                    "messageStatus" to "DELIVERED"
                                )
                            ).await()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update delivery receipt: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        android.util.Log.d(TAG, "Service destroyed, scope cancelled")
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
            putExtra("deep_link", "$type/$targetId")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            "$type-$targetId".hashCode(),
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

        getNotificationManager().notify("$type-$targetId".hashCode(), notification)
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

        getNotificationManager().notify(java.util.UUID.randomUUID().hashCode(), notification)
    }

    private fun createDefaultChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Handle reaction notifications with grouping
     * Multiple reactions to the same message are grouped into one notification
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleReactionNotification(
        message: RemoteMessage,
        data: Map<String, String>
    ) {
        Log.d(TAG, "handleReactionNotification data=$data")
        val messageId = data["messageId"] ?: return
        val senderName = data["senderName"] ?: data["title"] ?: "Someone"
        val senderId = data["senderId"] ?: ""
        val body = message.notification?.body ?: data["body"] ?: "reacted to your message"
        val recipientId = data["recipientId"] ?: auth.currentUser?.uid ?: return

        Log.d(TAG, "Reaction: messageId=$messageId, sender=$senderName, recipient=$recipientId")

        // Ignore self-reactions
        if (senderId == recipientId) {
            Log.d(TAG, "Ignoring self-reaction notification")
            return
        }

        // Use messageId as notification ID so reactions to same message group together
        val notificationId = "reaction_$messageId".hashCode() and 0x7fff_fffe
        Log.d(TAG, "Calculated notificationId=$notificationId for messageId=$messageId")

        // Track reactors for this message
        val reactorsKey = "reactors_$messageId"
        val prefs = getSharedPreferences("reaction_notifications", MODE_PRIVATE)
        val existingReactors = prefs.getStringSet(reactorsKey, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        val wasEmpty = existingReactors.isEmpty()
        existingReactors.add(senderId)
        prefs.edit().putStringSet(reactorsKey, existingReactors).apply()

        val reactionCount = existingReactors.size
        Log.d(TAG, "Reactor count: $reactionCount (was empty: $wasEmpty)")
        
        val notificationTitle = if (reactionCount == 1) {
            "Linker • $senderName"
        } else {
            "Linker • $reactionCount kişi"
        }
        
        val notificationBody = if (reactionCount == 1) {
            body // "Mesajınıza 👍 ile tepki verdi"
        } else {
            "Mesajınıza tepki verdi"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link", "message/$messageId")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create delete intent to clear reactors when notification is dismissed
        val deleteIntent = Intent(this, ReactionNotificationDismissReceiver::class.java).apply {
            putExtra("messageId", messageId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ensure reaction channel exists
        ensureReactionChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_REACTION_ID)
            .setSmallIcon(R.drawable.ic_ai_homepage_outline)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(reactionCount > 1) // Only alert on first reaction, updates are silent
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
        Log.d(TAG, "Reaction notification shown/updated: $reactionCount reactor(s), title='$notificationTitle', body='$notificationBody'")
    }

    private fun ensureReactionChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
    }

    /**
     * Handle delete notification command from backend
     * Dismisses notification for a specific message
     */
    private fun handleDeleteNotification(data: Map<String, String>) {
        val messageId = data["messageId"] ?: return
        val chatId = data["chatId"]
        android.util.Log.d(TAG, "handleDeleteNotification messageId=$messageId, chatId=$chatId")
        
        // For reaction notifications, we can calculate the ID directly
        val reactionNotificationId = "reaction_$messageId".hashCode() and 0x7fff_fffe
        NotificationManagerCompat.from(this).cancel(reactionNotificationId)
        android.util.Log.d(TAG, "Cancelled reaction notification: $reactionNotificationId")
        
        // Clear reactor tracking for this message
        val reactorsKey = "reactors_$messageId"
        val prefs = getSharedPreferences("reaction_notifications", Context.MODE_PRIVATE)
        prefs.edit().remove(reactorsKey).apply()
        
        // For chat notifications, if we have chatId, we can find it in ChatNotificationStore
        var foundInStore = false
        if (chatId != null) {
            val currentUserId = auth.currentUser?.uid
            android.util.Log.d(TAG, "Current user: $currentUserId")
            if (currentUserId != null) {
                // Try to find the notification by scanning ChatNotificationStore
                val allStates = ChatNotificationStore.getAll()
                android.util.Log.d(TAG, "ChatNotificationStore has ${allStates.size} entries")
                for ((notificationId, state) in allStates) {
                    android.util.Log.d(TAG, "Checking notificationId=$notificationId, chatId=${state.chatId}, recipientUid=${state.recipientUid}")
                    if (state.chatId == chatId && state.recipientUid == currentUserId) {
                        android.util.Log.d(TAG, "Found chat notification for chatId=$chatId, cancelling notificationId=$notificationId")
                        NotificationManagerCompat.from(this).cancel(notificationId)
                        ChatNotificationStore.clear(notificationId)
                        foundInStore = true
                    }
                }
            }
        }
        
        android.util.Log.d(TAG, "Found in store: $foundInStore")
        
        // Fallback: scan active notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val activeNotifications = notificationManager.activeNotifications
                android.util.Log.d(TAG, "Active notifications count: ${activeNotifications.size}")
                
                for (statusBarNotification in activeNotifications) {
                    val notification = statusBarNotification.notification
                    val extras = notification.extras
                    
                    android.util.Log.d(TAG, "Checking active notification id=${statusBarNotification.id}, tag=${statusBarNotification.tag}")
                    
                    // Check if this notification contains the deleted message
                    val notificationMessageId = extras?.getString(ChatNotificationHelper.EXTRA_MESSAGE_ID)
                    val notificationChatId = extras?.getString(ChatNotificationHelper.EXTRA_CHAT_ID)
                    
                    android.util.Log.d(TAG, "  messageId=$notificationMessageId, chatId=$notificationChatId")
                    
                    if (notificationMessageId == messageId || (chatId != null && notificationChatId == chatId)) {
                        android.util.Log.d(TAG, "MATCH! Cancelling notification id=${statusBarNotification.id}")
                        notificationManager.cancel(statusBarNotification.id)
                        ChatNotificationStore.clear(statusBarNotification.id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to scan active notifications: ${e.message}", e)
            }
        }
        
        android.util.Log.d(TAG, "Finished handleDeleteNotification for messageId=$messageId, chatId=$chatId")
    }

    companion object {
        private const val TAG = "LinkerMessaging"
        private const val CHANNEL_SOCIAL_ID = "linker_social"
        private const val CHANNEL_GENERAL_ID = "linker_general"
        private const val CHANNEL_REACTION_ID = "linker_reactions"
        const val CHANNEL_ID: String = CHANNEL_SOCIAL_ID
    }
}
