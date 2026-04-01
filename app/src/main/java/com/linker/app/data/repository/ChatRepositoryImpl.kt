package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.ChatDao
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.ChatEntity
import com.linker.app.data.local.entity.ChatType as EntityChatType
import com.linker.app.data.local.entity.DeliveryMethod as EntityDeliveryMethod
import com.linker.app.data.local.entity.MessageEntity
import com.linker.app.data.local.entity.MessageStatus as EntityMessageStatus
import com.linker.app.data.local.entity.MessageType as EntityMessageType
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.*
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.NotificationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val messageQueueDao: MessageQueueDao,
    private val userDao: UserDao,
    private val notificationRepository: NotificationRepository
) : ChatRepository {

    private val chatsCollection = firestore.collection("chats")
    private val messagesCollection = firestore.collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    // ── Chat list ──────────────────────────────────────────────────────────

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .whereEqualTo("isArchived", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToChatSync(doc.id, it) }
                }?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    override fun observeArchivedChats(): Flow<List<Chat>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .whereEqualTo("isArchived", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToChatSync(doc.id, it) }
                }?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }

    override fun observeTotalUnread(): Flow<Int> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .whereEqualTo("isArchived", false)
            .whereEqualTo("isMuted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val total = snapshot?.documents?.sumOf { doc ->
                    (doc.getLong("unreadCount") ?: 0L).toInt()
                } ?: 0
                trySend(total)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getChatById(chatId: String): Result<Chat> = safeCall {
        val doc = chatsCollection.document(chatId).get().await()
        val data = doc.data ?: throw Exception("Chat not found")
        mapToChat(doc.id, data)
    }

    override suspend fun createPrivateChat(recipientUserId: String): Result<Chat> = safeCall {
        if (recipientUserId == currentUserId) throw Exception("Cannot chat with yourself")

        val existingChats = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .get()
            .await()

        for (doc in existingChats.documents) {
            val participants = doc.get("participantIds") as? List<*> ?: continue
            if (participants.contains(recipientUserId) && participants.size == 2) {
                val data = doc.data ?: continue
                return@safeCall mapToChat(doc.id, data)
            }
        }

        val chatId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val chatData = hashMapOf(
            "chatType" to "PRIVATE",
            "chatName" to null,
            "chatImageUrl" to null,
            "participantIds" to listOf(currentUserId, recipientUserId),
            "lastMessageText" to null,
            "lastMessageAt" to null,
            "unreadCount" to 0,
            "isPinned" to false,
            "isMuted" to false,
            "isArchived" to false,
            "isBlocked" to false,
            "theme" to null,
            "createdAt" to now,
            "updatedAt" to now
        )
        chatsCollection.document(chatId).set(chatData).await()

        val localChat = ChatEntity(
            chatId = chatId,
            chatType = EntityChatType.PRIVATE,
            chatName = null,
            chatImageUrl = null,
            participantIds = listOf(currentUserId, recipientUserId),
            lastMessageId = null,
            lastMessageText = null,
            lastMessageAt = null,
            unreadCount = 0,
            isPinned = false,
            isMuted = false,
            isArchived = false,
            isBlocked = false,
            theme = null,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertChat(localChat)

        mapToChat(chatId, chatData)
    }

    override suspend fun createGroupChat(
        name: String,
        participantIds: List<String>
    ): Result<Chat> = safeCall {
        if (participantIds.size < 1) throw Exception("A group needs at least 2 other participants")

        val chatId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val allParticipants = (participantIds + currentUserId).distinct()
        val chatData = hashMapOf(
            "chatType" to "GROUP",
            "chatName" to name,
            "chatImageUrl" to null,
            "participantIds" to allParticipants,
            "lastMessageText" to null,
            "lastMessageAt" to null,
            "unreadCount" to 0,
            "isPinned" to false,
            "isMuted" to false,
            "isArchived" to false,
            "isBlocked" to false,
            "theme" to null,
            "createdAt" to now,
            "updatedAt" to now
        )
        chatsCollection.document(chatId).set(chatData).await()

        val localChat = ChatEntity(
            chatId = chatId,
            chatType = EntityChatType.GROUP,
            chatName = name,
            chatImageUrl = null,
            participantIds = allParticipants,
            lastMessageId = null,
            lastMessageText = null,
            lastMessageAt = null,
            unreadCount = 0,
            isPinned = false,
            isMuted = false,
            isArchived = false,
            isBlocked = false,
            theme = null,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertChat(localChat)

        mapToChat(chatId, chatData)
    }

    override suspend fun updateChatSettings(
        chatId: String,
        isPinned: Boolean?,
        isMuted: Boolean?,
        isArchived: Boolean?
    ): Result<Unit> = safeCall {
        val updates = mutableMapOf<String, Any>()
        isPinned?.let { updates["isPinned"] = it }
        isMuted?.let { updates["isMuted"] = it }
        isArchived?.let { updates["isArchived"] = it }
        if (updates.isNotEmpty()) {
            chatsCollection.document(chatId).update(updates).await()
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
                    doc.data?.let { mapToMessageSync(doc.id, it) }
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
        val chatResult = getChatById(chatId)
        if (chatResult is Result.Error) return chatResult

        val chat = (chatResult as Result.Success).data
        val isConnected = true
        val deliveryMethod = if (isConnected) DeliveryMethod.ONLINE else DeliveryMethod.BLE

        return safeCall {
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val messageData = hashMapOf(
                "chatId" to chatId,
                "senderId" to currentUserId,
                "messageType" to messageType.name,
                "content" to content,
                "mediaUrl" to null,
                "thumbnailUrl" to null,
                "mediaWidth" to null,
                "mediaHeight" to null,
                "mediaDuration" to null,
                "sharedLinkId" to null,
                "replyToMessageId" to replyToMessageId,
                "forwardedFromMessageId" to null,
                "reactions" to emptyMap<String, String>(),
                "isEdited" to false,
                "isDeleted" to false,
                "deletedForEveryone" to false,
                "messageStatus" to "SENT",
                "deliveryMethod" to deliveryMethod.name,
                "participantIds" to chat.participants.map { it.userId },
                "createdAt" to now,
                "updatedAt" to now,
                "deliveredAt" to null,
                "readAt" to null
            )

            if (deliveryMethod == DeliveryMethod.ONLINE) {
                messagesCollection.document(messageId).set(messageData).await()
            } else {
                val queueItem = com.linker.app.data.local.entity.MessageQueueEntity(
                    queueId = UUID.randomUUID().toString(),
                    messageId = messageId,
                    chatId = chatId,
                    recipientId = chat.participants.firstOrNull { it.userId != currentUserId }?.userId ?: "",
                    messagePayload = content ?: "",
                    queueStatus = QueueStatus.PENDING,
                    deliveryMethod = EntityDeliveryMethod.BLE,
                    retryCount = 0,
                    maxRetries = 3,
                    priority = 0,
                    ttl = 5,
                    createdAt = now
                )
                messageQueueDao.insertQueueItem(queueItem)
            }

            val senderEntity = userDao.getUserById(currentUserId)
            val sender = senderEntity?.toDomain() ?: User(
                userId = currentUserId, username = "", displayName = "",
                email = null, phoneNumber = null, bio = null,
                profileImageUrl = null, coverImageUrl = null,
                isVerified = false, followersCount = 0, followingCount = 0,
                likesCount = 0, isFollowing = false, isFollowedBy = false,
                isBlocked = false, isMuted = false,
                isPrivate = false, followRequestSent = false, hideFollowLists = false,
                createdAt = 0L, updatedAt = 0L
            )

            val message = Message(
                messageId = messageId,
                chatId = chatId,
                sender = sender,
                messageType = messageType,
                content = content,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLink = null,
                replyToMessage = null,
                reactions = emptyMap(),
                isEdited = false,
                isDeleted = false,
                deletedForEveryone = false,
                messageStatus = com.linker.app.domain.model.MessageStatus.SENT,
                deliveryMethod = deliveryMethod,
                createdAt = now,
                updatedAt = now,
                deliveredAt = null,
                readAt = null
            )

            val localMessage = MessageEntity(
                messageId = messageId,
                chatId = chatId,
                senderId = currentUserId,
                messageType = EntityMessageType.TEXT,
                content = content,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLinkId = null,
                replyToMessageId = replyToMessageId,
                forwardedFromMessageId = null,
                reactions = emptyMap(),
                isEdited = false,
                isDeleted = false,
                deletedForEveryone = false,
                messageStatus = EntityMessageStatus.SENT,
                deliveryMethod = EntityDeliveryMethod.ONLINE,
                encryptedContent = null,
                createdAt = now,
                updatedAt = now,
                deliveredAt = null,
                readAt = null
            )
            messageDao.insertMessage(localMessage)

            val displayText = content ?: "[Media]"
            chatDao.updateLastMessage(chatId, messageId, displayText, now)

            chatsCollection.document(chatId).update(
                mapOf(
                    "lastMessageText" to displayText,
                    "lastMessageAt" to now,
                    "updatedAt" to now
                )
            ).await()

            val otherParticipants = chat.participants.filter { it.userId != currentUserId }
            if (otherParticipants.isNotEmpty() && deliveryMethod == DeliveryMethod.ONLINE) {
                val senderName = sender.displayName.ifBlank { sender.username }
                val notificationMessage = when (chat.chatType) {
                    com.linker.app.domain.model.ChatType.PRIVATE -> senderName
                    com.linker.app.domain.model.ChatType.GROUP -> "$senderName: ${displayText.take(50)}"
                }
                for (participant in otherParticipants) {
                    sendChatNotification(
                        recipientUserId = participant.userId,
                        senderName = senderName,
                        messageText = notificationMessage,
                        chatId = chatId,
                        messageId = messageId
                    )
                }
            }

            message
        }
    }

    private suspend fun sendChatNotification(
        recipientUserId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        messageId: String
    ) {
        try {
            val notificationData = hashMapOf(
                "recipientId" to recipientUserId,
                "senderId" to currentUserId,
                "type" to "MESSAGE",
                "title" to senderName,
                "body" to messageText,
                "chatId" to chatId,
                "messageId" to messageId,
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("notifications").add(notificationData).await()

            val localNotification = com.linker.app.data.local.entity.NotificationEntity(
                notificationId = UUID.randomUUID().toString(),
                notificationType = com.linker.app.data.local.entity.NotificationType.MESSAGE,
                actorId = currentUserId,
                targetEntityId = messageId,
                targetEntityType = "message",
                title = senderName,
                message = messageText,
                imageUrl = null,
                actionUrl = "/chat/$chatId",
                isRead = false,
                createdAt = System.currentTimeMillis()
            )
            notificationRepository.insertNotification(localNotification)
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "Failed to send chat notification: ${e.message}")
        }
    }

    override suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        messagesCollection.document(messageId).update(
            mapOf(
                "content" to newContent,
                "isEdited" to true,
                "updatedAt" to now
            )
        ).await()
        messageDao.editMessage(messageId, newContent, now)
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit> = safeCall {
        messagesCollection.document(messageId).update(
            mapOf(
                "isDeleted" to true,
                "deletedForEveryone" to forEveryone
            )
        ).await()
        messageDao.markAsDeleted(messageId, forEveryone)
    }

    override suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit> = safeCall {
        val doc = messagesCollection.document(messageId).get().await()
        val reactions = (doc.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        if (emoji == null) {
            reactions.remove(currentUserId)
        } else {
            reactions[currentUserId] = emoji
        }
        messagesCollection.document(messageId).update("reactions", reactions).await()
    }

    override suspend fun forwardMessage(messageId: String, targetChatId: String): Result<Unit> = safeCall {
        val original = messagesCollection.document(messageId).get().await()
        val content = original.getString("content")
        val messageTypeStr = original.getString("messageType") ?: "TEXT"
        val messageType = try { MessageType.valueOf(messageTypeStr) } catch (_: Exception) { MessageType.TEXT }
        val participantIds = original.get("participantIds") as? List<String> ?: emptyList()

        val newMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val messageData = hashMapOf(
            "chatId" to targetChatId,
            "senderId" to currentUserId,
            "messageType" to messageType.name,
            "content" to content,
            "mediaUrl" to null,
            "thumbnailUrl" to null,
            "mediaWidth" to null,
            "mediaHeight" to null,
            "mediaDuration" to null,
            "sharedLinkId" to null,
            "replyToMessageId" to null,
            "forwardedFromMessageId" to messageId,
            "reactions" to emptyMap<String, String>(),
            "isEdited" to false,
            "isDeleted" to false,
            "deletedForEveryone" to false,
            "messageStatus" to "SENT",
            "deliveryMethod" to "ONLINE",
            "participantIds" to participantIds,
            "createdAt" to now,
            "updatedAt" to now,
            "deliveredAt" to null,
            "readAt" to null
        )
        messagesCollection.document(newMessageId).set(messageData).await()

        chatsCollection.document(targetChatId).update(
            mapOf(
                "lastMessageText" to (content ?: "[Forwarded]"),
                "lastMessageAt" to now,
                "updatedAt" to now
            )
        ).await()
    }

    override suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update("unreadCount", 0).await()
        chatDao.markAsRead(chatId)

        val messages = messageDao.getMessagesByChat(chatId)
        val currentUid = currentUserId
        for (msg in messages) {
            if (msg.senderId != currentUid && msg.messageStatus != EntityMessageStatus.READ) {
                messageDao.updateMessageStatus(msg.messageId, EntityMessageStatus.READ)
            }
        }
    }

    override suspend fun searchMessages(chatId: String, query: String): Result<List<Message>> = safeCall {
        val entities = messageDao.searchMessagesInChat(chatId, query)
        entities.mapNotNull { entity ->
            val senderEntity = userDao.getUserById(entity.senderId)
            val sender = senderEntity?.toDomain() ?: User(
                userId = entity.senderId, username = "", displayName = "",
                email = null, phoneNumber = null, bio = null,
                profileImageUrl = null, coverImageUrl = null,
                isVerified = false, followersCount = 0, followingCount = 0,
                likesCount = 0, isFollowing = false, isFollowedBy = false,
                isBlocked = false, isMuted = false,
                isPrivate = false, followRequestSent = false, hideFollowLists = false,
                createdAt = 0L, updatedAt = 0L
            )
            entity.toDomain(sender)
        }
    }

    override fun observeQueuedMessageCount(): Flow<Int> =
        messageQueueDao.observePendingCount()

    override suspend fun retryFailedMessages(preferredMethod: DeliveryMethod): Result<Unit> = safeCall {
        val pendingItems = messageQueueDao.getQueueItemsByStatus(QueueStatus.PENDING)
        val failedItems = messageQueueDao.getQueueItemsByStatus(QueueStatus.FAILED)

        val itemsToRetry = (pendingItems + failedItems).filter {
            it.retryCount < it.maxRetries
        }

        for (item in itemsToRetry) {
            try {
                val messageData = hashMapOf(
                    "chatId" to item.chatId,
                    "senderId" to currentUserId,
                    "messageType" to "TEXT",
                    "content" to item.messagePayload,
                    "mediaUrl" to null,
                    "thumbnailUrl" to null,
                    "mediaWidth" to null,
                    "mediaHeight" to null,
                    "mediaDuration" to null,
                    "sharedLinkId" to null,
                    "replyToMessageId" to null,
                    "forwardedFromMessageId" to null,
                    "reactions" to emptyMap<String, String>(),
                    "isEdited" to false,
                    "isDeleted" to false,
                    "deletedForEveryone" to false,
                    "messageStatus" to "SENT",
                    "deliveryMethod" to preferredMethod.name,
                    "participantIds" to listOf(item.recipientId, currentUserId),
                    "createdAt" to item.createdAt,
                    "updatedAt" to System.currentTimeMillis(),
                    "deliveredAt" to null,
                    "readAt" to null
                )
                messagesCollection.document(item.messageId).set(messageData).await()
                messageQueueDao.updateQueueStatus(
                    item.queueId,
                    QueueStatus.SENT,
                    System.currentTimeMillis()
                )
            } catch (e: Exception) {
                messageQueueDao.incrementRetryCount(
                    item.queueId,
                    System.currentTimeMillis(),
                    e.message
                )
                if (item.retryCount + 1 >= item.maxRetries) {
                    messageQueueDao.updateQueueStatus(
                        item.queueId,
                        QueueStatus.FAILED,
                        System.currentTimeMillis()
                    )
                }
            }
        }
    }

    // ── Chat detail helpers ────────────────────────────────────────────────

    suspend fun getChatParticipantIds(chatId: String): List<String> {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            (doc.get("participantIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChatLastMessageText(chatId: String): String? {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            doc.getString("lastMessageText")
        } catch (_: Exception) { null }
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private suspend fun mapToChat(chatId: String, data: Map<String, Any?>): Chat {
        val participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val participants = participantIds.map { uid ->
            val cached = userDao.getUserById(uid)
            cached?.toDomain() ?: User(
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
                isPrivate = false,
                followRequestSent = false,
                hideFollowLists = false,
                createdAt = 0L,
                updatedAt = 0L
            )
        }

        val chatTypeStr = data["chatType"] as? String ?: "PRIVATE"
        val chatType = if (chatTypeStr == "GROUP") {
            com.linker.app.domain.model.ChatType.GROUP
        } else {
            com.linker.app.domain.model.ChatType.PRIVATE
        }

        return Chat(
            chatId = chatId,
            chatType = chatType,
            chatName = data["chatName"] as? String,
            chatImageUrl = data["chatImageUrl"] as? String,
            participants = participants,
            lastMessage = null,
            unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
            isPinned = data["isPinned"] as? Boolean ?: false,
            isMuted = data["isMuted"] as? Boolean ?: false,
            isArchived = data["isArchived"] as? Boolean ?: false,
            theme = data["theme"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun mapToChatSync(chatId: String, data: Map<String, Any?>): Chat {
        val participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
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
                isPrivate = false,
                followRequestSent = false,
                hideFollowLists = false,
                createdAt = 0L,
                updatedAt = 0L
            )
        }

        val chatTypeStr = data["chatType"] as? String ?: "PRIVATE"
        val chatType = if (chatTypeStr == "GROUP") {
            com.linker.app.domain.model.ChatType.GROUP
        } else {
            com.linker.app.domain.model.ChatType.PRIVATE
        }

        return Chat(
            chatId = chatId,
            chatType = chatType,
            chatName = data["chatName"] as? String,
            chatImageUrl = data["chatImageUrl"] as? String,
            participants = participants,
            lastMessage = null,
            unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
            isPinned = data["isPinned"] as? Boolean ?: false,
            isMuted = data["isMuted"] as? Boolean ?: false,
            isArchived = data["isArchived"] as? Boolean ?: false,
            theme = data["theme"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun mapToMessageSync(messageId: String, data: Map<String, Any?>): Message {
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
            isPrivate = false,
            followRequestSent = false,
            hideFollowLists = false,
            createdAt = 0L,
            updatedAt = 0L
        )

        return Message(
            messageId = messageId,
            chatId = data["chatId"] as? String ?: "",
            sender = senderStub,
            messageType = try {
                com.linker.app.domain.model.MessageType.valueOf(data["messageType"] as? String ?: "TEXT")
            } catch (_: Exception) {
                com.linker.app.domain.model.MessageType.TEXT
            },
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
            messageStatus = try {
                com.linker.app.domain.model.MessageStatus.valueOf(data["messageStatus"] as? String ?: "SENT")
            } catch (_: Exception) {
                com.linker.app.domain.model.MessageStatus.SENT
            },
            deliveryMethod = try {
                com.linker.app.domain.model.DeliveryMethod.valueOf(data["deliveryMethod"] as? String ?: "ONLINE")
            } catch (_: Exception) {
                com.linker.app.domain.model.DeliveryMethod.ONLINE
            },
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            deliveredAt = (data["deliveredAt"] as? Number)?.toLong(),
            readAt = (data["readAt"] as? Number)?.toLong()
        )
    }
}
