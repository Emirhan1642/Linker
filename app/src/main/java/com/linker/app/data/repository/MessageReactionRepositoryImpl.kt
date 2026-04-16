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
    private val userCache: UserCache
) : MessageReactionRepository {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private fun messagesRef(chatId: String) =
        firestore.collection("chats").document(chatId).collection("messages")

    override suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit> = safeCall {
        val doc = messagesRef(chatId).document(messageId).get().await()
        val reactions = (doc.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        if (emoji == null) {
            reactions.remove(currentUserId)
        } else {
            reactions[currentUserId] = emoji
        }
        messagesRef(chatId).document(messageId).update("reactions", reactions).await()
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
