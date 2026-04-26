package com.linker.app.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.linker.app.MainActivity
import com.linker.app.R

object ChatNotificationHelper {
    const val KEY_TEXT_REPLY = "key_text_reply"

    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_MESSAGE_ID = "extra_message_id"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    /** Bildirimin ait olduğu hesap (çoklu oturum); MainActivity önce buna geçiş yapar. */
    const val EXTRA_TARGET_ACCOUNT_UID = "extra_target_account_uid"

    const val ACTION_REPLY = "com.linker.app.notification.REPLY"
    const val ACTION_LIKE = "com.linker.app.notification.LIKE"
    const val ACTION_READ = "com.linker.app.notification.READ"

    fun channelIdForAccount(recipientUid: String): String = "linker_messages_$recipientUid"

    fun buildChatNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        targetAccountUid: String,
        chatId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        messages: List<String>,
        remoteInputHistory: Array<CharSequence>? = null,
        isGroupChat: Boolean = false
    ): NotificationCompat.Builder {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chatId)
            putExtra(EXTRA_TARGET_ACCOUNT_UID, targetAccountUid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String) = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TARGET_ACCOUNT_UID, targetAccountUid)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            actionIntent(ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_commentary_outline,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .build()

        val likePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            actionIntent(ACTION_LIKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_heart_outline,
            "Like",
            likePendingIntent
        ).build()

        val readPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            actionIntent(ACTION_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_homepage_outline,
            "Read",
            readPendingIntent
        ).build()

        val userPerson = androidx.core.app.Person.Builder()
            .setName("Siz") // The user receiving the notification
            .build()

        // Create messaging style with proper title
        val messagingStyle = if (isGroupChat) {
            // For group chats, show group name or "Group Chat"
            NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle("Group Chat")
                .setGroupConversation(true)
        } else {
            // For private chats, show sender name
            NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(senderName)
                .setGroupConversation(false)
        }

        // Add messages to the style
        for (msg in messages) {
            // Parse message format: "SenderName: message" or "Siz: message" or just "message"
            val (messageSender, messageText) = if (msg.startsWith("Siz: ")) {
                // Message sent by current user
                userPerson to msg.substring(5)
            } else if (isGroupChat && msg.contains(": ")) {
                // Message from another user in group (format: "SenderName: message")
                val colonIndex = msg.indexOf(": ")
                val senderNameInMsg = msg.substring(0, colonIndex)
                val messageContent = msg.substring(colonIndex + 2)
                val sender = androidx.core.app.Person.Builder()
                    .setName(senderNameInMsg)
                    .build()
                sender to messageContent
            } else {
                // Plain message (private chat - sender is the other person)
                val sender = androidx.core.app.Person.Builder()
                    .setName(senderName)
                    .build()
                sender to msg
            }
            
            messagingStyle.addMessage(messageText, System.currentTimeMillis(), messageSender)
        }

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        // Build notification title
        // For group chats, extract sender from last message
        // For private chats, use senderName parameter
        val lastMessageSender = if (isGroupChat && messages.isNotEmpty()) {
            val lastMsg = messages.last()
            if (lastMsg.startsWith("Siz: ")) {
                "Siz"
            } else if (lastMsg.contains(": ")) {
                lastMsg.substring(0, lastMsg.indexOf(": "))
            } else {
                senderName
            }
        } else {
            senderName
        }
        
        val notificationTitle = "Linker • $lastMessageSender bir mesaj gönderdi"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIcon)
            .setContentTitle(notificationTitle)
            .setContentText(messages.lastOrNull() ?: "New message")
            .setContentIntent(contentPendingIntent)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setGroup("linker_chat_${chatId}_${targetAccountUid}")
            .setGroupSummary(false)
            .addAction(replyAction)
            .addAction(likeAction)
            .addAction(readAction)
        
        // Set remote input history to show sent messages and clear progress indicator
        if (remoteInputHistory != null && remoteInputHistory.isNotEmpty()) {
            builder.setRemoteInputHistory(remoteInputHistory)
        }
        
        return builder
    }
}
