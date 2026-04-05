package com.linker.app.core.notification

import java.util.concurrent.ConcurrentHashMap

data class ChatNotificationState(
    /** Bu bildirim hangi hesaba ait (çoklu oturum). */
    val recipientUid: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val messages: MutableList<String> = mutableListOf()
)

object ChatNotificationStore {
    private val store = ConcurrentHashMap<Int, ChatNotificationState>()

    fun getOrCreate(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        senderId: String,
        senderName: String
    ): ChatNotificationState {
        return store.getOrPut(notificationId) {
            ChatNotificationState(
                recipientUid = recipientUid,
                chatId = chatId,
                senderId = senderId,
                senderName = senderName
            )
        }
    }

    fun addIncoming(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        senderId: String,
        senderName: String,
        message: String
    ) {
        val state = getOrCreate(notificationId, recipientUid, chatId, senderId, senderName)
        state.messages.add(message)
    }

    fun addOutgoing(notificationId: Int, message: String) {
        val state = store[notificationId] ?: return
        state.messages.add("Siz: $message")
    }

    fun get(notificationId: Int): ChatNotificationState? = store[notificationId]

    fun clear(notificationId: Int) {
        store.remove(notificationId)
    }
}
