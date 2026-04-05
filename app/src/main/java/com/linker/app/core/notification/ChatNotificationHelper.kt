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
        messages: List<String>
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
        ).addRemoteInput(remoteInput).build()

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

        val person = androidx.core.app.Person.Builder()
            .setName(senderName)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(senderName)

        for (msg in messages) {
            messagingStyle.addMessage(msg, System.currentTimeMillis(), person)
        }

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIcon)
            .setContentTitle(senderName)
            .setContentIntent(contentPendingIntent)
            .setStyle(messagingStyle)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setGroup("linker_${targetAccountUid}")
            .setGroupSummary(false)
            .addAction(replyAction)
            .addAction(likeAction)
            .addAction(readAction)
    }
}
