package com.linker.app.core.notification

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONArray
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class ChatNotificationState(
    val recipientUid: String,
    val chatId: String,
    private val _messages: MutableList<String> = Collections.synchronizedList(mutableListOf()),
    val isGroupChat: Boolean = false
) {
    val messages: MutableList<String>
        get() = _messages // Expose mutable list for backward compatibility, but it's synchronized

    fun addMessage(message: String) {
        synchronized(_messages) {
            if (message.isNotBlank() && !_messages.contains(message)) {
                _messages.add(message)
            }
        }
    }

    fun clearMessages() {
        synchronized(_messages) {
            _messages.clear()
        }
    }
}

object ChatNotificationStore {
    private val store = ConcurrentHashMap<Int, ChatNotificationState>()
    private const val TAG = "ChatNotificationStore"
    private const val PREFS_NAME = "chat_notification_store"
    private const val KEY_STORE = "store_data"

    private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromPrefs()
        }
    }

    private fun loadFromPrefs() {
        try {
            val jsonStr = prefs?.getString(KEY_STORE, null) ?: return
            val jsonObject = org.json.JSONObject(jsonStr)
            val loaded = mutableMapOf<Int, ChatNotificationState>()
            for (key in jsonObject.keys()) {
                val notificationId = key.toIntOrNull() ?: continue
                val stateObj = jsonObject.getJSONObject(key)
                val recipientUid = stateObj.optString("recipientUid")
                val chatId = stateObj.optString("chatId")
                val isGroupChat = stateObj.optBoolean("isGroupChat", false)
                val messagesArray = stateObj.optJSONArray("messages")
                
                val state = ChatNotificationState(recipientUid, chatId, isGroupChat = isGroupChat)
                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        state.addMessage(messagesArray.getString(i))
                    }
                }
                loaded[notificationId] = state
            }
            store.putAll(loaded)
        } catch (e: Exception) {
            NotificationLogger.e("Failed to load notification store", e)
        }
    }

    private fun saveToPrefs() {
        try {
            val jsonObject = org.json.JSONObject()
            store.forEach { (notificationId, state) ->
                val stateObj = org.json.JSONObject().apply {
                    put("recipientUid", state.recipientUid)
                    put("chatId", state.chatId)
                    put("isGroupChat", state.isGroupChat)
                    val messagesArray = org.json.JSONArray()
                    synchronized(state.messages) {
                        state.messages.forEach { messagesArray.put(it) }
                    }
                    put("messages", messagesArray)
                }
                jsonObject.put(notificationId.toString(), stateObj)
            }
            prefs?.edit()?.putString(KEY_STORE, jsonObject.toString())?.apply()
        } catch (e: Exception) {
            NotificationLogger.e("Failed to save notification store", e)
        }
    }

    fun getOrCreate(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        isGroupChat: Boolean = false
    ): ChatNotificationState {
        val state = store.getOrPut(notificationId) {
            ChatNotificationState(
                recipientUid = recipientUid,
                chatId = chatId,
                isGroupChat = isGroupChat
            )
        }
        saveToPrefs()
        return state
    }

    fun addIncoming(
        notificationId: Int,
        recipientUid: String,
        chatId: String,
        message: String,
        isGroupChat: Boolean = false
    ) {
        NotificationLogger.d("addIncoming: notificationId=$notificationId, chatId=$chatId")
        val state = getOrCreate(notificationId, recipientUid, chatId, isGroupChat)
        if (message.isNotBlank()) {
            state.addMessage(message)
            NotificationLogger.d("Message added. Total messages: ${state.messages.size}")
            saveToPrefs()
        }
    }

    fun addOutgoing(notificationId: Int, message: String) {
        NotificationLogger.d("addOutgoing: notificationId=$notificationId")
        val state = store[notificationId]
        if (state == null) {
            NotificationLogger.w("addOutgoing: State not found for notificationId=$notificationId.")
        } else {
            val formattedMessage = "Siz: $message"
            state.addMessage(formattedMessage)
            NotificationLogger.d("Outgoing message added. Total messages: ${state.messages.size}")
            saveToPrefs()
        }
    }

    fun get(notificationId: Int): ChatNotificationState? {
        return store[notificationId]
    }

    fun getAll(): Map<Int, ChatNotificationState> {
        return store.toMap()
    }

    fun clear(notificationId: Int) {
        store.remove(notificationId)
        saveToPrefs()
    }
}
