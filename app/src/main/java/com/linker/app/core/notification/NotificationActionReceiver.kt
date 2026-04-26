package com.linker.app.core.notification

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var hybridAccountManager: HybridAccountManager

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_ID) ?: return
        val messageId = intent.getStringExtra(ChatNotificationHelper.EXTRA_MESSAGE_ID) ?: return
        val senderId = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_ID) ?: ""
        val senderName = intent.getStringExtra(ChatNotificationHelper.EXTRA_SENDER_NAME) ?: "Message"
        val notificationId = intent.getIntExtra(ChatNotificationHelper.EXTRA_NOTIFICATION_ID, senderId.hashCode())
        val targetAccountUid = intent.getStringExtra(ChatNotificationHelper.EXTRA_TARGET_ACCOUNT_UID).orEmpty()

        android.util.Log.d("NotificationAction", "onReceive: action=${intent.action}, notificationId=$notificationId, chatId=$chatId")
        android.util.Log.d("NotificationAction", "Intent extras: ${intent.extras?.keySet()?.joinToString()}")

        // Use goAsync() to keep the receiver alive for async operations
        val pendingResult = goAsync()
        
        when (intent.action) {
            ChatNotificationHelper.ACTION_REPLY ->

                ioScope.launch {
                    try {
                        handleReply(context, chatId, messageId, senderId, senderName, notificationId, targetAccountUid, intent)
                    } finally {
                        pendingResult.finish()
                    }
                }
            ChatNotificationHelper.ACTION_LIKE ->
                ioScope.launch {
                    try {
                        handleLike(context, chatId, messageId, targetAccountUid, notificationId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            ChatNotificationHelper.ACTION_READ ->
                ioScope.launch {
                    try {
                        handleRead(context, chatId, notificationId, targetAccountUid)
                    } finally {
                        pendingResult.finish()
                    }
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
        Log.d("NotificationAction", "handleReply: notificationId=$notificationId, chatId=$chatId")
        
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ChatNotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            ?: return

        Log.d("NotificationAction", "Reply text: $replyText")

        val activeUid = accountRepository.getActiveUid()
        
        val result = if (activeUid == targetAccountUid) {
            // Active account - use normal repository
            Log.d("NotificationAction", "Using active account repository")
            chatRepository.sendMessage(chatId, MessageType.TEXT, replyText)
        } else {
            // Passive account - use hybrid manager
            Log.d("NotificationAction", "Using passive account (hybrid manager)")
            hybridAccountManager.sendMessageFromPassiveAccount(targetAccountUid, chatId, replyText)
        }
        
        when (result) {
            is LinkerResult.Success -> {
                Log.d("NotificationAction", "Message sent successfully")
                
                // Get or create notification state
                // Note: State might be null if notification was shown by system tray (notification payload)
                // This is a fallback until backend sends data-only notifications
                var state = ChatNotificationStore.get(notificationId)
                
                if (state == null) {
                    Log.w("NotificationAction", "State not found - trying to recover from active notification")
                    
                    // Try to recover messages from active notification (API 23+)
                    val existingMessages = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val activeNotifications = notificationManager.activeNotifications
                            Log.d("NotificationAction", "Found ${activeNotifications.size} active notifications")
                            
                            val existingNotification = activeNotifications.firstOrNull { it.id == notificationId }
                            
                            if (existingNotification != null) {
                                Log.d("NotificationAction", "Found existing notification with id $notificationId")
                                val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existingNotification.notification)
                                
                                if (messagingStyle != null) {
                                    Log.d("NotificationAction", "MessagingStyle found with ${messagingStyle.messages.size} messages")
                                    for (message in messagingStyle.messages) {
                                        val senderName = message.person?.name?.toString() ?: ""
                                        val text = message.text?.toString() ?: ""
                                        Log.d("NotificationAction", "Message: sender='$senderName', text='$text'")
                                        if (text.isNotBlank()) {
                                            if (senderName == "Siz") {
                                                existingMessages.add("Siz: $text")
                                            } else if (senderName.isNotBlank()) {
                                                existingMessages.add("$senderName: $text")
                                            } else {
                                                existingMessages.add(text)
                                            }
                                        }
                                    }
                                    Log.d("NotificationAction", "Recovered ${existingMessages.size} messages from active notification")
                                } else {
                                    Log.w("NotificationAction", "MessagingStyle is null")
                                }
                            } else {
                                Log.w("NotificationAction", "No active notification found with id $notificationId")
                            }
                        } catch (e: Exception) {
                            Log.e("NotificationAction", "Failed to recover messages: ${e.message}", e)
                        }
                    } else {
                        Log.w("NotificationAction", "API level < 23, cannot recover messages")
                    }
                    
                    // Determine if it's a group chat
                    val isGroupChat = try {
                        when (val chatResult = chatRepository.getChatById(chatId)) {
                            is LinkerResult.Success -> chatResult.data.chatType == ChatType.GROUP
                            else -> false
                        }
                    } catch (e: Exception) {
                        Log.w("NotificationAction", "Failed to determine chat type: ${e.message}")
                        false
                    }
                    
                    // Create state
                    ChatNotificationStore.addIncoming(
                        notificationId = notificationId,
                        recipientUid = targetAccountUid,
                        chatId = chatId,
                        message = "", // Will add recovered messages below
                        isGroupChat = isGroupChat
                    )
                    state = ChatNotificationStore.get(notificationId)
                    
                    // Add recovered messages
                    if (state != null && existingMessages.isNotEmpty()) {
                        state.messages.clear()
                        state.messages.addAll(existingMessages)
                        Log.d("NotificationAction", "Restored ${existingMessages.size} messages to state")
                    }
                }
                
                // Add outgoing message
                if (state != null) {
                    ChatNotificationStore.addOutgoing(notificationId, replyText)
                    
                    Log.d("NotificationAction", "Updating notification with ${state.messages.size} messages")
                    
                    val channelId = ChatNotificationHelper.channelIdForAccount(state.recipientUid)
                    
                    // Build updated notification with sent message in history
                    // Note: We don't use remoteInputHistory because we already added the message to store
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
                        remoteInputHistory = null, // Don't use - we already have the message in store
                        isGroupChat = state.isGroupChat
                    ).build()
                    
                    // Update notification immediately to clear progress indicator
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                    Log.d("NotificationAction", "Notification updated successfully")
                } else {
                    Log.e("NotificationAction", "Failed to create notification state")
                }
            }
            is LinkerResult.Error -> {
                Log.e("NotificationAction", "Failed to send message: ${result.message}")
                
                // Show error toast
                launchToast(context, "Failed to send message: ${result.message}")
                
                // Try to update notification to clear progress indicator
                var state = ChatNotificationStore.get(notificationId)
                
                if (state == null) {
                    // Create minimal state
                    val isGroupChat = try {
                        when (val chatResult = chatRepository.getChatById(chatId)) {
                            is LinkerResult.Success -> chatResult.data.chatType == ChatType.GROUP
                            else -> false
                        }
                    } catch (e: Exception) {
                        Log.w("NotificationAction", "Failed to determine chat type: ${e.message}")
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
            else -> {
                Log.w("NotificationAction", "Unexpected result type")
            }
        }
    }

    private suspend fun handleLike(context: Context, chatId: String, messageId: String, targetAccountUid: String, notificationId: Int) {
        android.util.Log.d("NotificationAction", "handleLike: notificationId=$notificationId, messageId=$messageId")
        
        // Dismiss notification immediately for better UX
        ChatNotificationStore.clear(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
        android.util.Log.d("NotificationAction", "Notification dismissed immediately")
        
        // Send reaction in background
        val activeUid = accountRepository.getActiveUid()
        
        val result = if (activeUid == targetAccountUid) {
            // Active account
            chatRepository.reactToMessage(messageId, "\uD83D\uDC4D")
        } else {
            // Passive account
            hybridAccountManager.reactToMessageFromPassiveAccount(targetAccountUid, chatId, messageId, "\uD83D\uDC4D")
        }
        
        when (result) {
            is LinkerResult.Success -> {
                android.util.Log.d("NotificationAction", "Reaction added successfully")
            }
            is LinkerResult.Error -> {
                android.util.Log.e("NotificationAction", "Failed to react: ${result.message}")
                launchToast(context, "Tepki eklenemedi: ${result.message}")
            }
            else -> {}
        }
    }

    private suspend fun handleRead(context: Context, chatId: String, notificationId: Int, targetAccountUid: String) {
        val activeUid = accountRepository.getActiveUid()
        
        val result = if (activeUid == targetAccountUid) {
            // Active account
            chatRepository.markChatAsRead(chatId)
        } else {
            // Passive account
            hybridAccountManager.markChatAsReadFromPassiveAccount(targetAccountUid, chatId)
        }
        
        when (result) {
            is LinkerResult.Success -> {
                ChatNotificationStore.clear(notificationId)
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            is LinkerResult.Error -> {
                launchToast(context, "Failed to mark as read: ${result.message}")
            }
            else -> {}
        }
    }
    
    private fun launchToast(context: Context, message: String) {
        // Use Handler instead of GlobalScope to avoid memory leaks
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
