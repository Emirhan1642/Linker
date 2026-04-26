package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.cache.UserCache
import com.linker.app.domain.repository.MessageReactionRepository
import com.linker.app.domain.repository.ReactionDetail
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MessageReactionRepository
 * Handles message reactions (emoji reactions)
 *
 * Firestore path: chats/{chatId}/messages/{messageId}
 */
@Singleton
class MessageReactionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userCache: UserCache,
    private val supabaseNotificationApi: com.linker.app.core.di.SupabaseNotificationApi
) : MessageReactionRepository {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private fun messagesRef(chatId: String) =
        firestore.collection("chats").document(chatId).collection("messages")

    override suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit> = safeCall {
        val messageDoc = messagesRef(chatId).document(messageId).get().await()
        val reactions = (messageDoc.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        val messageSenderId = messageDoc.getString("senderId") ?: ""
        
        if (emoji == null) {
            reactions.remove(currentUserId)
        } else {
            reactions[currentUserId] = emoji
            
            // Send notification to message sender if it's not us
            if (messageSenderId.isNotBlank() && messageSenderId != currentUserId) {
                sendReactionNotification(messageSenderId, messageId, emoji)
            }
        }
        messagesRef(chatId).document(messageId).update("reactions", reactions).await()
    }
    
    private suspend fun sendReactionNotification(recipientId: String, messageId: String, emoji: String) {
        try {
            val senderName = userCache.getDisplayName(currentUserId) ?: "Someone"
            val key = com.linker.app.BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { com.linker.app.BuildConfig.SUPABASE_ANON_KEY }
            
            android.util.Log.d("MessageReaction", "Sending reaction notification to $recipientId for message $messageId with emoji $emoji")
            
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
                android.util.Log.d("MessageReaction", "Reaction notification sent successfully")
            } else {
                android.util.Log.w("MessageReaction", "Failed to send reaction notification: ${response.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MessageReaction", "Error sending reaction notification: ${e.message}", e)
        }
    }

    override suspend fun getMessageReactions(chatId: String, messageId: String): Result<Map<String, String>> = safeCall {
        val doc = messagesRef(chatId).document(messageId).get().await()
        (doc.get("reactions") as? Map<String, String>) ?: emptyMap()
    }

    override suspend fun getReactionDetails(chatId: String, messageId: String): Result<List<ReactionDetail>> = safeCall {
        val reactionsResult = getMessageReactions(chatId, messageId)
        if (reactionsResult is Result.Error) throw Exception("Failed to get reactions")

        val reactions = (reactionsResult as Result.Success).data
        reactions.map { (userId, emoji) ->
            val displayName = userCache.getDisplayName(userId)
                ?: if (userId == currentUserId) "You" else "User"
            ReactionDetail(
                userId = userId,
                userName = displayName,
                avatarUrl = null,
                emoji = emoji
            )
        }
    }
}
