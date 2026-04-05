package com.linker.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    // Service-scoped coroutine scope with proper lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d(TAG, "FCM token refreshed: $token")
        serviceScope.launch {
            pushTokenRegistrar.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        android.util.Log.d(TAG, "onMessageReceived data=${message.data} notification=${message.notification}")
        createNotificationChannel()

        val data = message.data
        val notificationType = data["type"] ?: "general"

        when (notificationType) {
            "MESSAGE" -> handleChatNotification(message, data)
            "LIKE", "COMMENT", "REPLY", "FOLLOW", "MENTION", "RELINK", "STORY_VIEW", "LIVE" ->
                handleSocialNotification(message, data, notificationType)
            else -> handleGeneralNotification(message)
        }
    }

    private fun handleChatNotification(
        message: RemoteMessage,
        data: Map<String, String>
    ) {
        android.util.Log.d(TAG, "handleChatNotification data=$data")
        val chatId = data["chatId"] ?: return
        val messageId = data["messageId"] ?: ""
        val senderName = data["senderName"] ?: data["title"] ?: "New Message"
        val senderId = data["senderId"] ?: ""
        val body = message.notification?.body ?: data["body"] ?: "Sent you a message"

        val notificationId = if (senderId.isNotBlank()) senderId.hashCode() else chatId.hashCode()
        ChatNotificationStore.addIncoming(notificationId, chatId, senderId, senderName, body)
        val state = ChatNotificationStore.get(notificationId)
        if (state != null) {
            val notification = ChatNotificationHelper.buildChatNotification(
                context = this,
                notificationId = notificationId,
                chatId = chatId,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                messages = state.messages
            ).build()
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        }

        if (senderId.isNotBlank() && messageId.isNotBlank() && senderId != auth.currentUser?.uid) {
            serviceScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    firestore.collection("messages")
                        .document(messageId)
                        .update(
                            mapOf(
                                "messageStatus" to "DELIVERED",
                                "deliveredAt" to now
                            )
                        )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to update deliveredAt: ${e.message}")
                }
            }
        }
    }

    // Properly cancel scope when service is destroyed
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
        val body = message.notification?.body ?: data["body"] ?: "New activity"
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ai_homepage_outline)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        getNotificationManager().notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Linker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages, likes, comments, and follow notifications"
                enableLights(true)
                enableVibration(true)
            }
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val TAG = "LinkerMessaging"
        const val CHANNEL_ID = "linker_notifications"
    }
}
