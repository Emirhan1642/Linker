package com.linker.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.linker.app.domain.repository.ChatRepository

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var chatRepository: ChatRepository

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_ID) ?: return
        val messageId = intent.getStringExtra(ChatNotificationHelper.EXTRA_MESSAGE_ID) ?: return
        val senderId = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_ID) ?: ""
        val senderName = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_NAME) ?: "Message"
        val notificationId = intent.getIntExtra(ChatNotificationHelper.EXTRA_NOTIFICATION_ID, senderId.hashCode())

        when (intent.action) {
            ChatNotificationHelper.ACTION_REPLY -> handleReply(context, chatId, messageId, senderId, senderName, notificationId, intent)
            ChatNotificationHelper.ACTION_LIKE -> handleLike(context, messageId, notificationId)
            ChatNotificationHelper.ACTION_READ -> handleRead(context, chatId, notificationId)
        }
    }

    private fun handleReply(
        context: Context,
        chatId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        notificationId: Int,
        intent: Intent
    ) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ChatNotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            ?: return

        ioScope.launch {
            chatRepository.sendMessage(chatId, com.linker.app.domain.model.MessageType.TEXT, replyText)
        }

        ChatNotificationStore.addOutgoing(notificationId, replyText)
        val state = ChatNotificationStore.get(notificationId)
        if (state != null) {
            val notification = ChatNotificationHelper.buildChatNotification(
                context = context,
                notificationId = notificationId,
                chatId = state.chatId,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                messages = state.messages
            ).build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun handleLike(context: Context, messageId: String, notificationId: Int) {
        ioScope.launch {
            chatRepository.reactToMessage(messageId, "\uD83D\uDC4D")
        }
        // Keep notification
    }

    private fun handleRead(context: Context, chatId: String, notificationId: Int) {
        ioScope.launch {
            chatRepository.markChatAsRead(chatId)
        }
        ChatNotificationStore.clear(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
