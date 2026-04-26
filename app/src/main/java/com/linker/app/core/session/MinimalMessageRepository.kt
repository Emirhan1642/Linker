package com.linker.app.core.session

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.BuildConfig
import com.linker.app.core.di.ChatNotificationRequest
import com.linker.app.core.di.SupabaseNotificationApi
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Minimal Message Repository for passive accounts
 * 
 * Provides basic messaging operations without full repository overhead.
 * Used only for notification actions from passive accounts.
 */
class MinimalMessageRepository(
    private val firestore: FirebaseFirestore,
    private val currentUserId: String,
    private val supabaseNotificationApi: SupabaseNotificationApi
) {
    
    companion object {
        private const val TAG = "MinimalMessageRepo"
    }
    
    /**
     * Send a text message from passive account
     */
    suspend fun sendMessage(chatId: String, content: String): Result<Unit> = safeCall {
        android.util.Log.d(TAG, "Sending message from passive account $currentUserId to chat $chatId")
        
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        // Get chat info
        val chatDoc = try {
            firestore.collection("chats").document(chatId).get().await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Network error fetching chat: ${e.message}", e)
            throw Exception("Network error: Unable to fetch chat. Please check your connection.")
        }
        
        if (!chatDoc.exists()) {
            android.util.Log.e(TAG, "Chat not found: $chatId")
            throw Exception("Chat not found")
        }
        
        val chatData = chatDoc.data ?: throw Exception("Chat data is null")
        val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val chatType = chatData["chatType"] as? String ?: "PRIVATE"
        
        // Create message data
        val messageData = hashMapOf(
            "messageId" to messageId,
            "chatId" to chatId,
            "senderId" to currentUserId,
            "messageType" to "TEXT",
            "content" to content,
            "mediaUrl" to null,
            "thumbnailUrl" to null,
            "mediaWidth" to null,
            "mediaHeight" to null,
            "mediaDuration" to null,
            "sharedLinkId" to null,
            "replyToMessageId" to null,
            "forwardedFromMessageId" to null,
            "reactions" to emptyMap<String, String>(),
            "readReceipts" to emptyMap<String, Long>(),
            "deliveryReceipts" to mapOf(currentUserId to now),
            "isEdited" to false,
            "isDeleted" to false,
            "deletedForEveryone" to false,
            "messageStatus" to "SENT",
            "deliveryMethod" to "ONLINE",
            "participantIds" to participantIds,
            "createdAt" to now,
            "updatedAt" to now,
            "deliveredAt" to now,
            "readAt" to null
        )
        
        // Use batch to ensure atomicity
        val batch = firestore.batch()
        
        // Add message
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        batch.set(messageRef, messageData)
        
        // Update chat
        val chatUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to content,
            "lastMessageAt" to now,
            "lastMessageId" to messageId,
            "updatedAt" to now,
            "unreadCounts.$currentUserId" to 0
        )
        
        participantIds
            .filter { it.isNotBlank() && it != currentUserId }
            .forEach { uid ->
                chatUpdates["unreadCounts.$uid"] = FieldValue.increment(1)
            }
        
        batch.update(firestore.collection("chats").document(chatId), chatUpdates)
        
        // Commit batch
        try {
            batch.commit().await()
            android.util.Log.d(TAG, "Message sent successfully from passive account")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Network error committing message: ${e.message}", e)
            throw Exception("Network error: Unable to send message. Please check your connection.")
        }
        
        // Send notifications to other participants
        sendNotificationsIfNeeded(
            participantIds = participantIds,
            chatType = chatType,
            content = content,
            chatId = chatId,
            messageId = messageId
        )
    }
    
    /**
     * Send notifications to other participants
     */
    private suspend fun sendNotificationsIfNeeded(
        participantIds: List<String>,
        chatType: String,
        content: String,
        chatId: String,
        messageId: String
    ) {
        val otherParticipants = participantIds.filter { it != currentUserId }
        if (otherParticipants.isEmpty()) {
            android.util.Log.d(TAG, "No other participants to notify")
            return
        }
        
        android.util.Log.d(TAG, "Sending notifications to ${otherParticipants.size} participants")
        
        // Get sender info from Firestore
        val senderName = try {
            val userDoc = firestore.collection("users").document(currentUserId).get().await()
            userDoc.getString("displayName")?.ifBlank { userDoc.getString("username") }
                ?: userDoc.getString("username")
                ?: "User"
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to get sender name: ${e.message}")
            "User"
        }
        
        val displayText = content.take(50)
        val notificationMessage = when (chatType) {
            "PRIVATE" -> displayText
            "GROUP" -> "$senderName: $displayText"
            else -> displayText
        }
        
        // Send notification to each participant
        for (recipientId in otherParticipants) {
            sendChatNotification(
                recipientUserId = recipientId,
                senderName = senderName,
                messageText = notificationMessage,
                chatId = chatId,
                messageId = messageId,
                chatType = chatType
            )
        }
    }
    
    /**
     * Send notification to a single user
     */
    private suspend fun sendChatNotification(
        recipientUserId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        messageId: String,
        chatType: String
    ) {
        try {
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
            android.util.Log.d(TAG, "Sending notification to $recipientUserId for message $messageId")
            
            val response = supabaseNotificationApi.sendChatNotification(
                auth = "Bearer $key",
                apiKey = key,
                request = ChatNotificationRequest(
                    recipientId = recipientUserId,
                    senderId = currentUserId,
                    senderName = senderName,
                    message = messageText,
                    chatId = chatId,
                    messageId = messageId,
                    chatType = chatType
                )
            )
            
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Notification sent successfully to $recipientUserId")
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.w(TAG, "Failed to send notification to $recipientUserId: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to send notification to $recipientUserId: ${e.message}", e)
        }
    }
    
    /**
     * React to a message from passive account
     */
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit> = safeCall {
        android.util.Log.d(TAG, "Reacting to message from passive account $currentUserId")
        
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        
        val messageDoc = try {
            messageRef.get().await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Network error fetching message: ${e.message}", e)
            throw Exception("Network error: Unable to fetch message. Please check your connection.")
        }
        
        if (!messageDoc.exists()) {
            android.util.Log.e(TAG, "Message not found: $messageId")
            throw Exception("Message not found")
        }
        
        val reactions = (messageDoc.get("reactions") as? Map<*, *>)?.mapKeys { it.key.toString() }?.toMutableMap() ?: mutableMapOf()
        val messageSenderId = messageDoc.getString("senderId") ?: ""
        
        if (emoji.isNullOrBlank()) {
            // Remove reaction
            reactions.remove(currentUserId)
        } else {
            // Add/update reaction
            reactions[currentUserId] = emoji
            
            // Send notification to message sender if it's not us
            if (messageSenderId.isNotBlank() && messageSenderId != currentUserId) {
                sendReactionNotification(messageSenderId, messageId, emoji)
            }
        }
        
        try {
            messageRef.update("reactions", reactions).await()
            android.util.Log.d(TAG, "Reaction updated successfully from passive account")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Network error updating reaction: ${e.message}", e)
            throw Exception("Network error: Unable to update reaction. Please check your connection.")
        }
    }
    
    /**
     * Send reaction notification
     */
    private suspend fun sendReactionNotification(recipientId: String, messageId: String, emoji: String) {
        try {
            // Get sender name from Firestore
            val senderName = try {
                val userDoc = firestore.collection("users").document(currentUserId).get().await()
                userDoc.getString("displayName")?.ifBlank { userDoc.getString("username") }
                    ?: userDoc.getString("username")
                    ?: "User"
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to get sender name: ${e.message}")
                "User"
            }
            
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
            android.util.Log.d(TAG, "Sending reaction notification to $recipientId for message $messageId with emoji $emoji")
            
            // Use sendChatNotification with a special format for reactions
            val response = supabaseNotificationApi.sendChatNotification(
                auth = "Bearer $key",
                apiKey = key,
                request = com.linker.app.core.di.ChatNotificationRequest(
                    recipientId = recipientId,
                    senderId = currentUserId,
                    senderName = senderName,
                    message = "Mesajınıza $emoji ile tepki verdi",
                    chatId = "", // Not needed for reactions
                    messageId = messageId,
                    chatType = "REACTION" // Special type to distinguish from chat messages
                )
            )
            
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Reaction notification sent successfully")
            } else {
                android.util.Log.w(TAG, "Failed to send reaction notification: ${response.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error sending reaction notification: ${e.message}", e)
        }
    }
    
    /**
     * Mark chat as read from passive account
     */
    suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        android.util.Log.d(TAG, "Marking chat as read from passive account $currentUserId")
        
        val now = System.currentTimeMillis()
        
        // Get all unread messages
        val messagesSnapshot = try {
            firestore.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Network error fetching messages: ${e.message}", e)
            throw Exception("Network error: Unable to fetch messages. Please check your connection.")
        }
        
        val batch = firestore.batch()
        var updateCount = 0
        
        for (doc in messagesSnapshot.documents) {
            val senderId = doc.getString("senderId")
            if (senderId == currentUserId) continue
            
            // Check if already read
            val readReceipts = doc.get("readReceipts") as? Map<*, *> ?: emptyMap<Any, Any>()
            if (readReceipts.containsKey(currentUserId)) continue
            
            // Mark as read
            batch.update(doc.reference, mapOf(
                "readReceipts.$currentUserId" to now,
                "readAt" to now,
                "messageStatus" to "READ"
            ))
            updateCount++
        }
        
        // Update chat unread count
        batch.update(
            firestore.collection("chats").document(chatId),
            "unreadCounts.$currentUserId", 0
        )
        
        if (updateCount > 0) {
            try {
                batch.commit().await()
                android.util.Log.d(TAG, "Chat marked as read successfully from passive account")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Network error marking as read: ${e.message}", e)
                throw Exception("Network error: Unable to mark as read. Please check your connection.")
            }
        }
    }
}
