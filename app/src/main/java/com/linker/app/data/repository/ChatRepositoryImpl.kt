package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.domain.model.*
import com.linker.app.domain.repository.ChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ChatRepository {

    private val chatsCollection = firestore.collection("chats")
    private val messagesCollection = firestore.collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // ── Chat list ──────────────────────────────────────────────────────────

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToChat(doc.id, it) }
                }?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    override fun observeArchivedChats(): Flow<List<Chat>> = flow { emit(emptyList()) }

    override fun observeTotalUnread(): Flow<Int> = flow { emit(0) }

    override suspend fun getChatById(chatId: String): Result<Chat> {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            val data = doc.data ?: return Result.Error("Chat not found")
            Result.Success(mapToChat(doc.id, data))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun createPrivateChat(recipientUserId: String): Result<Chat> {
        return try {
            // Check if a private chat already exists between these two users
            val existingChats = chatsCollection
                .whereArrayContains("participantIds", currentUserId)
                .get().await()

            for (doc in existingChats.documents) {
                val participants = doc.get("participantIds") as? List<*> ?: continue
                if (participants.contains(recipientUserId) && participants.size == 2) {
                    val data = doc.data ?: continue
                    return Result.Success(mapToChat(doc.id, data))
                }
            }

            // Create new chat
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val chatData = hashMapOf(
                "chatType" to "PRIVATE",
                "chatName" to null,
                "chatImageUrl" to null,
                "participantIds" to listOf(currentUserId, recipientUserId),
                "lastMessageText" to null,
                "lastMessageAt" to now,
                "unreadCount" to 0,
                "isPinned" to false,
                "isMuted" to false,
                "isArchived" to false,
                "theme" to null,
                "createdAt" to now,
                "updatedAt" to now
            )
            chatsCollection.document(chatId).set(chatData).await()
            getChatById(chatId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun createGroupChat(name: String, participantIds: List<String>): Result<Chat> {
        return try {
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val allParticipants = (participantIds + currentUserId).distinct()
            val chatData = hashMapOf(
                "chatType" to "GROUP",
                "chatName" to name,
                "chatImageUrl" to null,
                "participantIds" to allParticipants,
                "lastMessageText" to null,
                "lastMessageAt" to now,
                "unreadCount" to 0,
                "isPinned" to false,
                "isMuted" to false,
                "isArchived" to false,
                "theme" to null,
                "createdAt" to now,
                "updatedAt" to now
            )
            chatsCollection.document(chatId).set(chatData).await()
            getChatById(chatId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun updateChatSettings(
        chatId: String,
        isPinned: Boolean?,
        isMuted: Boolean?,
        isArchived: Boolean?
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>()
            isPinned?.let { updates["isPinned"] = it }
            isMuted?.let { updates["isMuted"] = it }
            isArchived?.let { updates["isArchived"] = it }
            if (updates.isNotEmpty()) {
                chatsCollection.document(chatId).update(updates).await()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    // ── Messages ───────────────────────────────────────────────────────────

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo("chatId", chatId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToMessage(doc.id, it) }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(
        chatId: String,
        messageType: MessageType,
        content: String?,
        mediaLocalPath: String?,
        replyToMessageId: String?
    ): Result<Message> {
        return try {
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val messageData = hashMapOf(
                "chatId" to chatId,
                "senderId" to currentUserId,
                "messageType" to messageType.name,
                "content" to content,
                "mediaUrl" to null,
                "thumbnailUrl" to null,
                "replyToMessageId" to replyToMessageId,
                "reactions" to emptyMap<String, String>(),
                "isEdited" to false,
                "isDeleted" to false,
                "deletedForEveryone" to false,
                "messageStatus" to "SENT",
                "deliveryMethod" to "ONLINE",
                "createdAt" to now,
                "updatedAt" to now,
                "deliveredAt" to null,
                "readAt" to null
            )

            messagesCollection.document(messageId).set(messageData).await()

            // Update chat's last message
            chatsCollection.document(chatId).update(
                mapOf(
                    "lastMessageText" to (content ?: "[Media]"),
                    "lastMessageAt" to now,
                    "updatedAt" to now
                )
            ).await()

            val message = mapToMessage(messageId, messageData)
            Result.Success(message)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun editMessage(messageId: String, newContent: String): Result<Unit> {
        return try {
            messagesCollection.document(messageId).update(
                mapOf(
                    "content" to newContent,
                    "isEdited" to true,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit> {
        return try {
            messagesCollection.document(messageId).update(
                mapOf(
                    "isDeleted" to true,
                    "deletedForEveryone" to forEveryone
                )
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit> {
        return try {
            val doc = messagesCollection.document(messageId).get().await()
            val reactions = (doc.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
            if (emoji == null) {
                reactions.remove(currentUserId)
            } else {
                reactions[currentUserId] = emoji
            }
            messagesCollection.document(messageId).update("reactions", reactions).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun forwardMessage(messageId: String, targetChatId: String): Result<Unit> {
        return try {
            val original = messagesCollection.document(messageId).get().await()
            val content = original.getString("content")
            sendMessage(targetChatId, MessageType.TEXT, content)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun markChatAsRead(chatId: String): Result<Unit> {
        return try {
            chatsCollection.document(chatId).update("unreadCount", 0).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun searchMessages(chatId: String, query: String): Result<List<Message>> {
        return Result.Success(emptyList()) // Firestore doesn't support full-text search natively
    }

    override fun observeQueuedMessageCount(): Flow<Int> = flow { emit(0) }

    override suspend fun retryFailedMessages(preferredMethod: DeliveryMethod): Result<Unit> {
        return Result.Success(Unit)
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private fun mapToChat(chatId: String, data: Map<String, Any?>): Chat {
        val participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        // Build lightweight User stubs from participant IDs
        val participants = participantIds.map { uid ->
            User(
                userId = uid,
                username = "",
                displayName = "",
                email = null,
                phoneNumber = null,
                bio = null,
                profileImageUrl = null,
                coverImageUrl = null,
                isVerified = false,
                followersCount = 0,
                followingCount = 0,
                likesCount = 0,
                isFollowing = false,
                isFollowedBy = false,
                isBlocked = false,
                isMuted = false,
                createdAt = 0L,
                updatedAt = 0L
            )
        }

        return Chat(
            chatId = chatId,
            chatType = if (data["chatType"] == "GROUP") ChatType.GROUP else ChatType.PRIVATE,
            chatName = data["chatName"] as? String,
            chatImageUrl = data["chatImageUrl"] as? String,
            participants = participants,
            lastMessage = null, // We'll rely on lastMessageText for the list
            unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
            isPinned = data["isPinned"] as? Boolean ?: false,
            isMuted = data["isMuted"] as? Boolean ?: false,
            isArchived = data["isArchived"] as? Boolean ?: false,
            theme = data["theme"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    // Extra field accessor for chat list display
    suspend fun getChatLastMessageText(chatId: String): String? {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            doc.getString("lastMessageText")
        } catch (_: Exception) { null }
    }

    private fun mapToMessage(messageId: String, data: Map<String, Any?>): Message {
        val senderId = data["senderId"] as? String ?: ""
        val senderStub = User(
            userId = senderId,
            username = "",
            displayName = "",
            email = null,
            phoneNumber = null,
            bio = null,
            profileImageUrl = null,
            coverImageUrl = null,
            isVerified = false,
            followersCount = 0,
            followingCount = 0,
            likesCount = 0,
            isFollowing = false,
            isFollowedBy = false,
            isBlocked = false,
            isMuted = false,
            createdAt = 0L,
            updatedAt = 0L
        )

        return Message(
            messageId = messageId,
            chatId = data["chatId"] as? String ?: "",
            sender = senderStub,
            messageType = try { MessageType.valueOf(data["messageType"] as? String ?: "TEXT") } catch (_: Exception) { MessageType.TEXT },
            content = data["content"] as? String,
            mediaUrl = data["mediaUrl"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            mediaWidth = (data["mediaWidth"] as? Number)?.toInt(),
            mediaHeight = (data["mediaHeight"] as? Number)?.toInt(),
            mediaDuration = (data["mediaDuration"] as? Number)?.toInt(),
            sharedLink = null,
            replyToMessage = null,
            reactions = (data["reactions"] as? Map<String, String>) ?: emptyMap(),
            isEdited = data["isEdited"] as? Boolean ?: false,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            deletedForEveryone = data["deletedForEveryone"] as? Boolean ?: false,
            messageStatus = try { MessageStatus.valueOf(data["messageStatus"] as? String ?: "SENT") } catch (_: Exception) { MessageStatus.SENT },
            deliveryMethod = try { DeliveryMethod.valueOf(data["deliveryMethod"] as? String ?: "ONLINE") } catch (_: Exception) { DeliveryMethod.ONLINE },
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            deliveredAt = (data["deliveredAt"] as? Number)?.toLong(),
            readAt = (data["readAt"] as? Number)?.toLong()
        )
    }
}
