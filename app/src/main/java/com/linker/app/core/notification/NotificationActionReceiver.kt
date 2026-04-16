package com.linker.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.core.util.Result as LinkerResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var accountRepository: AccountRepository

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_ID) ?: return
        val messageId = intent.getStringExtra(ChatNotificationHelper.EXTRA_MESSAGE_ID) ?: return
        val senderId = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_ID) ?: ""
        val senderName = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_NAME) ?: "Message"
        val notificationId = intent.getIntExtra(ChatNotificationHelper.EXTRA_NOTIFICATION_ID, senderId.hashCode())
        val targetAccountUid = intent.getStringExtra(ChatNotificationHelper.EXTRA_TARGET_ACCOUNT_UID).orEmpty()

        when (intent.action) {
            ChatNotificationHelper.ACTION_REPLY ->
                ioScope.launch {
                    if (ensureActiveAccount(context, targetAccountUid)) {
                        handleReply(context, chatId, messageId, senderId, senderName, notificationId, intent)
                    }
                }
            ChatNotificationHelper.ACTION_LIKE ->
                ioScope.launch {
                    if (ensureActiveAccount(context, targetAccountUid)) {
                        handleLike(messageId)
                    }
                }
            ChatNotificationHelper.ACTION_READ ->
                ioScope.launch {
                    if (ensureActiveAccount(context, targetAccountUid)) {
                        handleRead(context, chatId, notificationId)
                    }
                }
        }
    }

    private suspend fun ensureActiveAccount(context: Context, targetUid: String): Boolean {
        if (targetUid.isBlank()) return false
        val active = accountRepository.getActiveUid()
        if (active == targetUid) return true

        return when (val r = accountRepository.switchToAccount(targetUid)) {
            is LinkerResult.Success -> {
                // If the app is open / foreground, let the user know why the UI is swapping!
                launchToast(context, "Switched to account to perform action.")
                true
            }
            is LinkerResult.Error -> {
                launchToast(context, "Could not perform action. Account switch failed.")
                android.util.Log.w("NotificationAction", "switchToAccount failed: ${r.message}")
                false
            }
            else -> false
        }
    }

    private fun launchToast(context: Context, message: String) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun handleReply(
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

        chatRepository.sendMessage(chatId, com.linker.app.domain.model.MessageType.TEXT, replyText)

        ChatNotificationStore.addOutgoing(notificationId, replyText)
        val state = ChatNotificationStore.get(notificationId)
        if (state != null) {
            val channelId = ChatNotificationHelper.channelIdForAccount(state.recipientUid)
            val notification = ChatNotificationHelper.buildChatNotification(
                context = context,
                notificationId = notificationId,
                channelId = channelId,
                targetAccountUid = state.recipientUid,
                chatId = state.chatId,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                messages = state.messages
            ).build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private suspend fun handleLike(messageId: String) {
        chatRepository.reactToMessage(messageId, "\uD83D\uDC4D")
    }

    private suspend fun handleRead(context: Context, chatId: String, notificationId: Int) {
        chatRepository.markChatAsRead(chatId)
        ChatNotificationStore.clear(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
