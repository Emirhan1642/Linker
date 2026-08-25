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
        isGroupChat: Boolean = false,
        chatName: String? = null
    ): NotificationCompat.Builder {
        require(notificationId >= 0) { "Notification ID must be non-negative" }
        require(channelId.isNotBlank()) { "Channel ID cannot be blank" }
        require(targetAccountUid.isNotBlank()) { "Target account UID cannot be blank" }
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(senderName.isNotBlank()) { "Sender name cannot be blank" }
        require(messages.isNotEmpty()) { "Messages list cannot be empty" }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chatId)
            putExtra(NotificationConstants.EXTRA_TARGET_ACCOUNT_UID, targetAccountUid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String) = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationConstants.EXTRA_CHAT_ID, chatId)
            putExtra(NotificationConstants.EXTRA_MESSAGE_ID, messageId)
            putExtra(NotificationConstants.EXTRA_SENDER_ID, senderId)
            putExtra(NotificationConstants.EXTRA_SENDER_NAME, senderName)
            putExtra(NotificationConstants.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationConstants.EXTRA_TARGET_ACCOUNT_UID, targetAccountUid)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationConstants.getReplyRequestCode(notificationId),
            actionIntent(NotificationConstants.ACTION_REPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(NotificationConstants.KEY_TEXT_REPLY)
            .setLabel("Yanıtla") 
            .build()
            
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_commentary_outline,
            "Yanıtla",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .build()

        val likePendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationConstants.getLikeRequestCode(notificationId),
            actionIntent(NotificationConstants.ACTION_LIKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_heart_outline,
            "Beğen",
            likePendingIntent
        ).build()

        val readPendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationConstants.getReadRequestCode(notificationId),
            actionIntent(NotificationConstants.ACTION_READ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_homepage_outline,
            "Okundu İşaretle",
            readPendingIntent
        ).build()

        val userPerson = androidx.core.app.Person.Builder()
            .setName("Siz")
            .build()

        val messagingStyle = if (isGroupChat) {
            val groupTitle = chatName?.takeIf { it.isNotBlank() } ?: "Grup Sohbeti"
            NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(groupTitle)
                .setGroupConversation(true)
        } else {
            NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(senderName)
                .setGroupConversation(false)
        }

        for (msg in messages) {
            val parsedMessage = NotificationMessage.parse(msg, isGroupChat, senderName)
            val person = if (parsedMessage.isFromCurrentUser) {
                userPerson
            } else {
                androidx.core.app.Person.Builder()
                    .setName(parsedMessage.senderName)
                    .build()
            }
            messagingStyle.addMessage(
                parsedMessage.text,
                parsedMessage.timestamp,
                person
            )
        }

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val lastMessageSender = if (isGroupChat && messages.isNotEmpty()) {
            val lastMsg = NotificationMessage.parse(messages.last(), isGroupChat, senderName)
            if (lastMsg.isFromCurrentUser) context.getString(R.string.notification_sender_you) else lastMsg.senderName
        } else {
            senderName
        }
        
        val notificationTitle = "Linker • " + context.getString(R.string.notification_sent_message, lastMessageSender)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIcon)
            .setContentTitle(notificationTitle)
            .setContentText(messages.lastOrNull() ?: context.getString(R.string.notification_new_message))
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
        
        builder.extras.putString(NotificationConstants.EXTRA_MESSAGE_ID, messageId)
        builder.extras.putString(NotificationConstants.EXTRA_CHAT_ID, chatId)
        
        if (remoteInputHistory != null && remoteInputHistory.isNotEmpty()) {
            builder.setRemoteInputHistory(remoteInputHistory)
        }
        
        return builder
    }
}
