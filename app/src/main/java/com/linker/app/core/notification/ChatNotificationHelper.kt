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

    const val ACTION_REPLY = "com.linker.app.notification.REPLY"
    const val ACTION_LIKE = "com.linker.app.notification.LIKE"
    const val ACTION_READ = "com.linker.app.notification.READ"

    fun buildChatNotification(
        context: Context,
        notificationId: Int,
        chatId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        messages: List<String>
    ): NotificationCompat.Builder {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_commentary_outline,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val likeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_LIKE
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val likePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            likeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_heart_outline,
            "Like",
            likePendingIntent
        ).build()

        val readIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_READ
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val readPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            R.drawable.ic_ai_homepage_outline,
            "Read",
            readPendingIntent
        ).build()

        val person = androidx.core.app.Person.Builder()
            .setName(senderName)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(senderName)

        for (msg in messages) {
            messagingStyle.addMessage(msg, System.currentTimeMillis(), person)
        }

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(context, LinkerMessagingService.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIcon)
            .setContentTitle(senderName)
            .setContentIntent(contentPendingIntent)
            .setStyle(messagingStyle)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .addAction(likeAction)
            .addAction(readAction)
    }
}
