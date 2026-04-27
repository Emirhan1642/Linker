package com.linker.app.core.notification

import java.util.concurrent.ConcurrentHashMap

data class ChatNotificationState(
    /** Bu bildirim hangi hesaba ait (çoklu oturum). */
    val recipientUid: String,
    val chatId: String,
    val messages: MutableList<String> = mutableListOf(),
    val isGroupChat: Boolean = false
)

object ChatNotificationStore {
    private val store = ConcurrentHashMap<Int, ChatNotificationState>()
    private const val TAG = "ChatNotificationStore"
    private const val PREFS_NAME = "chat_notification_store"
    private const val KEY_STORE = "store_data"

    fun getOrCreate(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        isGroupChat: Boolean = false
    ): ChatNotificationState {
        return store.getOrPut(notificationId) {
            ChatNotificationState(
                recipientUid = recipientUid,
                chatId = chatId,
                isGroupChat = isGroupChat
            )
        }
    }

    fun addIncoming(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        message: String,
        isGroupChat: Boolean = false
    ) {
        android.util.Log.d(TAG, "addIncoming: notificationId=$notificationId, chatId=$chatId, message=$message, isGroupChat=$isGroupChat")
        val state = getOrCreate(notificationId, recipientUid, chatId, isGroupChat)
        // Only add non-empty messages and avoid duplicates
        if (message.isNotBlank() && !state.messages.contains(message)) {
            state.messages.add(message)
            android.util.Log.d(TAG, "Message added. Total messages: ${state.messages.size}")
        } else if (message.isNotBlank()) {
            android.util.Log.w(TAG, "Duplicate message ignored: $message")
        }
    }

    fun addOutgoing(notificationId: Int, message: String) {
        android.util.Log.d(TAG, "addOutgoing: notificationId=$notificationId, message=$message")
        val state = store[notificationId]
        if (state == null) {
            android.util.Log.w(TAG, "addOutgoing: State not found for notificationId=$notificationId. Available IDs: ${store.keys}")
        } else {
            val formattedMessage = "Siz: $message"
            // Avoid duplicates
            if (!state.messages.contains(formattedMessage)) {
                state.messages.add(formattedMessage)
                android.util.Log.d(TAG, "Outgoing message added. Total messages: ${state.messages.size}")
            } else {
                android.util.Log.w(TAG, "Duplicate outgoing message ignored: $formattedMessage")
            }
        }
    }

    fun get(notificationId: Int): ChatNotificationState? {
        val state = store[notificationId]
        android.util.Log.d(TAG, "get: notificationId=$notificationId, found=${state != null}, messages=${state?.messages?.size ?: 0}. Available IDs: ${store.keys}")
        return state
    }

    fun getAll(): Map<Int, ChatNotificationState> {
        return store.toMap()
    }

    fun clear(notificationId: Int) {
        store.remove(notificationId)
    }
}
