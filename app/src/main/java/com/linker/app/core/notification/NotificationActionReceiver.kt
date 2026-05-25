package com.linker.app.core.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.linker.app.core.session.HybridAccountManager
import com.linker.app.domain.model.ChatType
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.core.util.Result as LinkerResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var hybridAccountManager: HybridAccountManager
    @Inject lateinit var stateRecovery: NotificationStateRecovery

    override fun onReceive(context: Context, intent: Intent) {
        ChatNotificationStore.initialize(context)
        val chatId = intent.getStringExtra(NotificationConstants.EXTRA_CHAT_ID) ?: return
        val messageId = intent.getStringExtra(NotificationConstants.EXTRA_MESSAGE_ID) ?: return
        val senderId = intent.getStringExtra(NotificationConstants.EXTRA_SENDER_ID) ?: ""
        val senderName = intent.getStringExtra(NotificationConstants.EXTRA_SENDER_NAME) ?: "Message"
        val notificationId = intent.getIntExtra(NotificationConstants.EXTRA_NOTIFICATION_ID, senderId.hashCode())
        val targetAccountUid = intent.getStringExtra(NotificationConstants.EXTRA_TARGET_ACCOUNT_UID).orEmpty()

        NotificationLogger.d("onReceive: action=\${intent.action}, notificationId=\$notificationId, chatId=\$chatId")

        val pendingResult = goAsync()
        val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        operationScope.launch {
            try {
                when (intent.action) {
                    NotificationConstants.ACTION_REPLY -> handleReply(context, chatId, messageId, senderId, senderName, notificationId, targetAccountUid, intent)
                    NotificationConstants.ACTION_LIKE -> handleLike(context, chatId, messageId, targetAccountUid, notificationId)
                    NotificationConstants.ACTION_READ -> handleRead(context, chatId, notificationId, targetAccountUid)
                }
            } catch (e: Exception) {
                NotificationLogger.e("Error handling notification action", e)
            } finally {
                operationScope.cancel()
                pendingResult.finish()
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleReply(
        context: Context,
        chatId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        notificationId: Int,
        targetAccountUid: String,
        intent: Intent
    ) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationConstants.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            ?: return

        val activeUid = accountRepository.getActiveUid()
        
        val result = if (activeUid == targetAccountUid) {
            chatRepository.sendMessage(chatId, MessageType.TEXT, replyText)
        } else {
            hybridAccountManager.sendMessageFromPassiveAccount(targetAccountUid, chatId, replyText)
        }
        
        when (result) {
            is LinkerResult.Success -> {
                var state = ChatNotificationStore.get(notificationId)
                
                if (state == null) {
                    val recoveredMessages = stateRecovery.recoverMessagesFromNotification(notificationId)
                    
                    val isGroupChat = try {
                        when (val chatResult = chatRepository.getChatById(chatId)) {
                            is LinkerResult.Success -> chatResult.data.chatType == ChatType.GROUP
                            else -> false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    
                    ChatNotificationStore.addIncoming(
                        notificationId = notificationId,
                        recipientUid = targetAccountUid,
                        chatId = chatId,
                        message = "",
                        isGroupChat = isGroupChat
                    )
                    state = ChatNotificationStore.get(notificationId)
                    
                    if (state != null && recoveredMessages.isNotEmpty()) {
                        state.clearMessages()
                        recoveredMessages.forEach { state.addMessage(it) }
                    }
                }
                
                if (state != null) {
                    ChatNotificationStore.addOutgoing(notificationId, replyText)
                    
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
                        messages = state.messages,
                        remoteInputHistory = null,
                        isGroupChat = state.isGroupChat
                    ).build()
                    
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                }
            }
            is LinkerResult.Error -> {
                launchToast(context, "Failed to send message: \${result.message}")
                
                var state = ChatNotificationStore.get(notificationId)
                if (state == null) {
                    val isGroupChat = try {
                        when (val chatResult = chatRepository.getChatById(chatId)) {
                            is LinkerResult.Success -> chatResult.data.chatType == ChatType.GROUP
                            else -> false
                        }
                    } catch (e: Exception) {
                        false
                    }
                    
                    ChatNotificationStore.addIncoming(
                        notificationId = notificationId,
                        recipientUid = targetAccountUid,
                        chatId = chatId,
                        message = "",
                        isGroupChat = isGroupChat
                    )
                    state = ChatNotificationStore.get(notificationId)
                }
                
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
                        messages = state.messages,
                        remoteInputHistory = null,
                        isGroupChat = state.isGroupChat
                    ).build()
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                }
            }
            else -> {}
        }
    }

    private suspend fun handleLike(context: Context, chatId: String, messageId: String, targetAccountUid: String, notificationId: Int) {
        ChatNotificationStore.clear(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
        
        val activeUid = accountRepository.getActiveUid()
        val result = if (activeUid == targetAccountUid) {
            chatRepository.reactToMessage(messageId, "\uD83D\uDC4D")
        } else {
            hybridAccountManager.reactToMessageFromPassiveAccount(targetAccountUid, chatId, messageId, "\uD83D\uDC4D")
        }
        
        if (result is LinkerResult.Error) {
            launchToast(context, "Tepki eklenemedi: \${result.message}")
        }
    }

    private suspend fun handleRead(context: Context, chatId: String, notificationId: Int, targetAccountUid: String) {
        val activeUid = accountRepository.getActiveUid()
        val result = if (activeUid == targetAccountUid) {
            chatRepository.markChatAsRead(chatId)
        } else {
            hybridAccountManager.markChatAsReadFromPassiveAccount(targetAccountUid, chatId)
        }
        
        when (result) {
            is LinkerResult.Success -> {
                ChatNotificationStore.clear(notificationId)
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            is LinkerResult.Error -> {
                launchToast(context, "Failed to mark as read: \${result.message}")
            }
            else -> {}
        }
    }
    
    private fun launchToast(context: Context, message: String) {
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
