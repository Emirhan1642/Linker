package com.linker.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.BuildConfig
import com.linker.app.core.di.ChatNotificationRequest
import com.linker.app.core.di.SupabaseNotificationApi
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val messageQueueDao: MessageQueueDao,
    private val userDao: UserDao,
    private val notificationRepository: NotificationRepository,
    private val supabaseNotificationApi: SupabaseNotificationApi
) : ChatRepository {

    private val chatsCollection = firestore.collection("chats")
    private val messagesCollection = firestore.collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private fun hasValidatedInternet(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isUserArchivedChat(data: Map<String, Any?>): Boolean {
        val archivedBy = data["archivedBy"] as? List<*>
        return archivedBy?.contains(currentUserId) == true
    }

    // ── Chat list ──────────────────────────────────────────────────────────

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    if (isUserArchivedChat(data)) return@mapNotNull null
                    mapToChatSync(doc.id, data)
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    if (!isUserArchivedChat(data)) return@mapNotNull null
                    mapToChatSync(doc.id, data)
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val total = snapshot?.documents?.sumOf { doc ->
                    val archivedBy = doc.get("archivedBy") as? List<*>
                    val mutedBy = doc.get("mutedBy") as? List<*>
                    if (archivedBy?.contains(currentUserId) == true) return@sumOf 0
                    if (mutedBy?.contains(currentUserId) == true) return@sumOf 0
                    val map = doc.get("unreadCounts") as? Map<*, *>
                    val count = (map?.get(currentUserId) as? Number)?.toInt()
                    count ?: (doc.getLong("unreadCount") ?: 0L).toInt()
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
            "unreadCounts" to mapOf(
                currentUserId to 0,
                recipientUserId to 0
            ),
            "archivedBy" to emptyList<String>(),
            "pinnedBy" to emptyList<String>(),
            "favoritedBy" to emptyList<String>(),
            "mutedBy" to emptyList<String>(),
            "blockedBy" to emptyList<String>(),
            "isPinned" to false,
            "isMuted" to false,
            "isArchived" to false,
            "isBlocked" to false,
            "isFavorited" to false,
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
            isFavorited = false,
            theme = null,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertChat(localChat)

        mapToChat(chatId, chatData)
    }

    override suspend fun createGroupChat(
        name: String,
        participantIds: List<String>,
        permissions: Map<String, Any>?
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
            "createdBy" to currentUserId,
            "adminIds" to listOf(currentUserId),
            "lastMessageText" to null,
            "lastMessageAt" to null,
            "unreadCount" to 0,
            "unreadCounts" to allParticipants.associateWith { 0 },
            "archivedBy" to emptyList<String>(),
            "pinnedBy" to emptyList<String>(),
            "favoritedBy" to emptyList<String>(),
            "mutedBy" to emptyList<String>(),
            "blockedBy" to emptyList<String>(),
            "isPinned" to false,
            "isMuted" to false,
            "isArchived" to false,
            "isBlocked" to false,
            "isFavorited" to false,
            "groupPermissions" to (permissions ?: emptyMap<String, Any>()),
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
            isFavorited = false,
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
        isArchived: Boolean?,
        isBlocked: Boolean?,
        isFavorited: Boolean?
    ): Result<Unit> = safeCall {
        val updates = mutableMapOf<String, Any>()
        isPinned?.let {
            updates["pinnedBy"] = if (it) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
            chatDao.updatePinStatus(chatId, it)
        }
        isMuted?.let {
            updates["mutedBy"] = if (it) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
            chatDao.updateMuteStatus(chatId, it)
        }
        isArchived?.let {
            updates["archivedBy"] = if (it) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
            chatDao.updateArchiveStatus(chatId, it)
        }
        isBlocked?.let {
            updates["blockedBy"] = if (it) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
            chatDao.updateBlockedStatus(chatId, it)
        }
        isFavorited?.let {
            updates["favoritedBy"] = if (it) FieldValue.arrayUnion(currentUserId) else FieldValue.arrayRemove(currentUserId)
            chatDao.updateFavoriteStatus(chatId, it)
        }
        if (updates.isNotEmpty()) {
            chatsCollection.document(chatId).update(updates).await()
        }
    }

    // ── Messages ───────────────────────────────────────────────────────────

    override fun observeMessages(chatId: String): Flow<List<Message>> {
        val firestoreFlow = callbackFlow {
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
        val roomFlow = messageDao.observeMessagesByChat(chatId).map { entities ->
            entities.map { entity -> messageEntityToDomainSync(entity) }
        }
        return combine(firestoreFlow, roomFlow) { remote, local ->
            mergeMessagesById(local, remote)
        }
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
        val isConnected = hasValidatedInternet()
        val deliveryMethod = if (isConnected) DeliveryMethod.ONLINE else DeliveryMethod.BLE

        return safeCall {
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val domainMsgStatus =
                if (isConnected) MessageStatus.SENT else MessageStatus.SENDING
            val entityMsgStatus =
                if (isConnected) EntityMessageStatus.SENT else EntityMessageStatus.SENDING

            val localChat = chatDao.getChatById(chat.chatId)
            if (localChat == null) {
                chatDao.insertChat(
                    ChatEntity(
                        chatId = chat.chatId,
                        chatType = if (chat.chatType == com.linker.app.domain.model.ChatType.GROUP) EntityChatType.GROUP else EntityChatType.PRIVATE,
                        chatName = chat.chatName,
                        chatImageUrl = chat.chatImageUrl,
                        participantIds = chat.participants.map { it.userId },
                        lastMessageId = chat.lastMessage?.messageId,
                        lastMessageText = chat.lastMessage?.content,
                        lastMessageAt = chat.lastMessage?.createdAt,
                        unreadCount = chat.unreadCount,
                        isPinned = chat.isPinned,
                        isMuted = chat.isMuted,
                        isArchived = chat.isArchived,
                        isBlocked = chat.isBlocked,
                        isFavorited = chat.isFavorited,
                        theme = chat.theme,
                        createdAt = chat.createdAt,
                        updatedAt = chat.updatedAt
                    )
                )
            }

            val existingSender = userDao.getUserById(currentUserId)
            if (existingSender == null) {
                val firebaseUser = auth.currentUser
                val displayName = firebaseUser?.displayName ?: ""
                val username = firebaseUser?.displayName ?: ""
                userDao.insertUser(
                    com.linker.app.data.local.entity.UserEntity(
                        userId = currentUserId,
                        username = username,
                        displayName = displayName,
                        email = firebaseUser?.email,
                        phoneNumber = firebaseUser?.phoneNumber,
                        bio = null,
                        profileImageUrl = firebaseUser?.photoUrl?.toString(),
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
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            val participantIds = chat.participants.map { it.userId }
            val messageData = buildFirestoreMessagePayload(
                messageId = messageId,
                chatId = chatId,
                senderId = currentUserId,
                messageType = messageType,
                content = content,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLinkId = null,
                replyToMessageId = replyToMessageId,
                forwardedFromMessageId = null,
                participantIds = participantIds,
                deliveryMethod = deliveryMethod,
                messageStatus = domainMsgStatus,
                createdAt = now,
                updatedAt = now
            )

            if (deliveryMethod == DeliveryMethod.ONLINE) {
                val batch = firestore.batch()
                batch.set(messagesCollection.document(messageId), messageData)
                val displayTextForBatch = content ?: "[Media]"
                val chatUpdates = mutableMapOf<String, Any>(
                    "lastMessageText" to displayTextForBatch,
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
                batch.update(chatsCollection.document(chatId), chatUpdates)
                batch.commit().await()
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
                messageStatus = domainMsgStatus,
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
                messageType = domainMessageTypeToEntity(messageType),
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
                messageStatus = entityMsgStatus,
                deliveryMethod = domainDeliveryToEntity(deliveryMethod),
                encryptedContent = null,
                createdAt = now,
                updatedAt = now,
                deliveredAt = null,
                readAt = null
            )
            messageDao.insertMessage(localMessage)

            val displayText = content ?: "[Media]"
            chatDao.updateLastMessage(chatId, messageId, displayText, now)

            if (!isConnected) {
                val updates = mutableMapOf<String, Any>(
                    "lastMessageText" to displayText,
                    "lastMessageAt" to now,
                    "lastMessageId" to messageId,
                    "updatedAt" to now,
                    "unreadCounts.$currentUserId" to 0
                )
                participantIds
                    .filter { it.isNotBlank() && it != currentUserId }
                    .forEach { uid ->
                        updates["unreadCounts.$uid"] = FieldValue.increment(1)
                    }
                chatsCollection.document(chatId).update(updates).await()
            }

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
                        messageId = messageId,
                        chatType = chat.chatType
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
        messageId: String,
        chatType: ChatType
    ) {
        try {
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
            supabaseNotificationApi.sendChatNotification(
                auth = "Bearer $key",
                apiKey = key,
                request = ChatNotificationRequest(
                    recipientId = recipientUserId,
                    senderId = currentUserId,
                    senderName = senderName,
                    message = messageText,
                    chatId = chatId,
                    messageId = messageId,
                    chatType = chatType.name
                )
            )
            android.util.Log.d("ChatRepository", "sendChatNotification: sent to $recipientUserId")

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
        val messageDoc = messagesCollection.document(messageId).get().await()
        val chatId = messageDoc.getString("chatId") ?: ""

        messagesCollection.document(messageId).update(
            mapOf(
                "isDeleted" to true,
                "deletedForEveryone" to forEveryone
            )
        ).await()
        messageDao.markAsDeleted(messageId, forEveryone)

        if (chatId.isNotBlank()) {
            val lastSnapshot = messagesCollection
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("isDeleted", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val lastDoc = lastSnapshot.documents.firstOrNull()
            if (lastDoc != null) {
                val lastText = lastDoc.getString("content") ?: "[Media]"
                val lastAt = lastDoc.getLong("createdAt") ?: 0L
                chatsCollection.document(chatId).update(
                    mapOf(
                        "lastMessageText" to lastText,
                        "lastMessageAt" to lastAt,
                        "lastMessageId" to lastDoc.id,
                        "updatedAt" to lastAt
                    )
                ).await()
                chatDao.updateLastMessage(chatId, lastDoc.id, lastText, lastAt)
            } else {
                chatsCollection.document(chatId).update(
                    mapOf(
                        "lastMessageText" to null,
                        "lastMessageAt" to null,
                        "lastMessageId" to null
                    )
                ).await()
                chatDao.clearLastMessage(chatId)
            }
        }
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
        val targetParticipantIds = getChatParticipantIds(targetChatId).ifEmpty {
            throw Exception("Target chat not found or has no participants")
        }

        val newMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val messageData = buildFirestoreMessagePayload(
            messageId = newMessageId,
            chatId = targetChatId,
            senderId = currentUserId,
            messageType = messageType,
            content = content,
            mediaUrl = original.getString("mediaUrl"),
            thumbnailUrl = original.getString("thumbnailUrl"),
            mediaWidth = (original.get("mediaWidth") as? Number)?.toInt(),
            mediaHeight = (original.get("mediaHeight") as? Number)?.toInt(),
            mediaDuration = (original.get("mediaDuration") as? Number)?.toInt(),
            sharedLinkId = original.getString("sharedLinkId"),
            replyToMessageId = null,
            forwardedFromMessageId = messageId,
            participantIds = targetParticipantIds,
            deliveryMethod = DeliveryMethod.ONLINE,
            messageStatus = MessageStatus.SENT,
            createdAt = now,
            updatedAt = now
        )
        messagesCollection.document(newMessageId).set(messageData).await()

        chatsCollection.document(targetChatId).update(
            mapOf(
                "lastMessageText" to (content ?: "[Forwarded]"),
                "lastMessageAt" to now,
                "lastMessageId" to newMessageId,
                "updatedAt" to now
            )
        ).await()
    }

    override suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        markChatAsReadUpTo(chatId, Long.MAX_VALUE)
    }

    override suspend fun markChatAsReadUpTo(chatId: String, upToTimestamp: Long): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update("unreadCounts.$currentUserId", 0).await()
        chatDao.markAsRead(chatId)

        val now = System.currentTimeMillis()
        val unreadMessages = messagesCollection
            .whereEqualTo("chatId", chatId)
            .whereLessThanOrEqualTo("createdAt", upToTimestamp)
            .get()
            .await()

        val batch = firestore.batch()
        for (doc in unreadMessages.documents) {
            val senderId = doc.getString("senderId") ?: ""
            if (senderId != currentUserId) {
                val updates = mapOf(
                    "messageStatus" to "READ",
                    "readAt" to now,
                    "deliveredAt" to now,
                    "readReceipts.$currentUserId" to now
                )
                batch.update(doc.reference, updates)
            }
        }
        batch.commit().await()

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
                val participantIds = getChatParticipantIds(item.chatId).ifEmpty {
                    listOf(item.recipientId, currentUserId).filter { it.isNotBlank() }.distinct()
                }
                val messageData = buildFirestoreMessagePayload(
                    messageId = item.messageId,
                    chatId = item.chatId,
                    senderId = currentUserId,
                    messageType = MessageType.TEXT,
                    content = item.messagePayload,
                    mediaUrl = null,
                    thumbnailUrl = null,
                    mediaWidth = null,
                    mediaHeight = null,
                    mediaDuration = null,
                    sharedLinkId = null,
                    replyToMessageId = null,
                    forwardedFromMessageId = null,
                    participantIds = participantIds,
                    deliveryMethod = preferredMethod,
                    messageStatus = MessageStatus.SENT,
                    createdAt = item.createdAt,
                    updatedAt = System.currentTimeMillis()
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

    private fun mergeMessagesById(local: List<Message>, remote: List<Message>): List<Message> {
        val byId = LinkedHashMap<String, Message>()
        local.forEach { byId[it.messageId] = it }
        remote.forEach { byId[it.messageId] = it }
        return byId.values.sortedBy { it.createdAt }
    }

    private fun messageEntityToDomainSync(entity: MessageEntity): Message =
        entity.toDomain(senderStub(entity.senderId))

    private fun senderStub(userId: String) = User(
        userId = userId,
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

    private fun lastMessagePreviewFromChatDoc(chatId: String, data: Map<String, Any?>): Message? {
        val text = data["lastMessageText"] as? String ?: return null
        val at = (data["lastMessageAt"] as? Number)?.toLong() ?: return null
        val mid = (data["lastMessageId"] as? String).orEmpty()
        return Message(
            messageId = mid,
            chatId = chatId,
            sender = senderStub(""),
            messageType = MessageType.TEXT,
            content = text,
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
            messageStatus = MessageStatus.SENT,
            deliveryMethod = DeliveryMethod.ONLINE,
            createdAt = at,
            updatedAt = at,
            deliveredAt = null,
            readAt = null
        )
    }

    private fun domainMessageTypeToEntity(t: MessageType): EntityMessageType = when (t) {
        MessageType.TEXT -> EntityMessageType.TEXT
        MessageType.IMAGE -> EntityMessageType.IMAGE
        MessageType.VIDEO -> EntityMessageType.VIDEO
        MessageType.GIF -> EntityMessageType.GIF
        MessageType.LINK -> EntityMessageType.LINK
        MessageType.AUDIO -> EntityMessageType.AUDIO
    }

    private fun domainDeliveryToEntity(d: DeliveryMethod): EntityDeliveryMethod = when (d) {
        DeliveryMethod.ONLINE -> EntityDeliveryMethod.ONLINE
        DeliveryMethod.BLE -> EntityDeliveryMethod.BLE
        DeliveryMethod.WIFI_DIRECT -> EntityDeliveryMethod.WIFI_DIRECT
    }

    private fun buildFirestoreMessagePayload(
        messageId: String,
        chatId: String,
        senderId: String,
        messageType: MessageType,
        content: String?,
        mediaUrl: String?,
        thumbnailUrl: String?,
        mediaWidth: Int?,
        mediaHeight: Int?,
        mediaDuration: Int?,
        sharedLinkId: String?,
        replyToMessageId: String?,
        forwardedFromMessageId: String?,
        participantIds: List<String>,
        deliveryMethod: DeliveryMethod,
        messageStatus: MessageStatus,
        createdAt: Long,
        updatedAt: Long,
        reactions: Map<String, String> = emptyMap(),
        readReceipts: Map<String, Long> = emptyMap(),
        deliveryReceipts: Map<String, Long> = emptyMap(),
        isEdited: Boolean = false,
        isDeleted: Boolean = false,
        deletedForEveryone: Boolean = false,
    ): HashMap<String, Any?> = hashMapOf(
            "messageId" to messageId,
            "chatId" to chatId,
            "senderId" to senderId,
            "messageType" to messageType.name,
            "content" to content,
            "mediaUrl" to mediaUrl,
            "thumbnailUrl" to thumbnailUrl,
            "mediaWidth" to mediaWidth,
            "mediaHeight" to mediaHeight,
            "mediaDuration" to mediaDuration,
            "sharedLinkId" to sharedLinkId,
            "replyToMessageId" to replyToMessageId,
            "forwardedFromMessageId" to forwardedFromMessageId,
            "reactions" to reactions,
            "readReceipts" to readReceipts,
            "deliveryReceipts" to deliveryReceipts,
            "isEdited" to isEdited,
            "isDeleted" to isDeleted,
            "deletedForEveryone" to deletedForEveryone,
            "messageStatus" to messageStatus.name,
            "deliveryMethod" to deliveryMethod.name,
            "participantIds" to participantIds,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "deliveredAt" to null,
            "readAt" to null
    )

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

        val unreadCounts = data["unreadCounts"] as? Map<*, *>
        val resolvedUnread = (unreadCounts?.get(currentUserId) as? Number)?.toInt()
            ?: (data["unreadCount"] as? Number)?.toInt()

        val archivedBy = data["archivedBy"] as? List<*> ?: emptyList<Any>()
        val pinnedBy = data["pinnedBy"] as? List<*> ?: emptyList<Any>()
        val mutedBy = data["mutedBy"] as? List<*> ?: emptyList<Any>()
        val blockedBy = data["blockedBy"] as? List<*> ?: emptyList<Any>()
        val favoritedBy = data["favoritedBy"] as? List<*> ?: emptyList<Any>()

        return Chat(
            chatId = chatId,
            chatType = chatType,
            chatName = data["chatName"] as? String,
            chatImageUrl = data["chatImageUrl"] as? String,
            participants = participants,
            lastMessage = lastMessagePreviewFromChatDoc(chatId, data),
            unreadCount = resolvedUnread ?: 0,
            isPinned = pinnedBy.contains(currentUserId) || (data["isPinned"] as? Boolean ?: false),
            isMuted = mutedBy.contains(currentUserId) || (data["isMuted"] as? Boolean ?: false),
            isArchived = archivedBy.contains(currentUserId) || (data["isArchived"] as? Boolean ?: false),
            isBlocked = blockedBy.contains(currentUserId) || (data["isBlocked"] as? Boolean ?: false),
            isFavorited = favoritedBy.contains(currentUserId) || (data["isFavorited"] as? Boolean ?: false),
            theme = data["theme"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            groupAdminIds = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            groupCreatedBy = data["createdBy"] as? String
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

        val unreadCounts = data["unreadCounts"] as? Map<*, *>
        val resolvedUnread = (unreadCounts?.get(currentUserId) as? Number)?.toInt()
            ?: (data["unreadCount"] as? Number)?.toInt()

        val archivedBy = data["archivedBy"] as? List<*> ?: emptyList<Any>()
        val pinnedBy = data["pinnedBy"] as? List<*> ?: emptyList<Any>()
        val mutedBy = data["mutedBy"] as? List<*> ?: emptyList<Any>()
        val blockedBy = data["blockedBy"] as? List<*> ?: emptyList<Any>()
        val favoritedBy = data["favoritedBy"] as? List<*> ?: emptyList<Any>()

        return Chat(
            chatId = chatId,
            chatType = chatType,
            chatName = data["chatName"] as? String,
            chatImageUrl = data["chatImageUrl"] as? String,
            participants = participants,
            lastMessage = lastMessagePreviewFromChatDoc(chatId, data),
            unreadCount = resolvedUnread ?: 0,
            isPinned = pinnedBy.contains(currentUserId) || (data["isPinned"] as? Boolean ?: false),
            isMuted = mutedBy.contains(currentUserId) || (data["isMuted"] as? Boolean ?: false),
            isArchived = archivedBy.contains(currentUserId) || (data["isArchived"] as? Boolean ?: false),
            isBlocked = blockedBy.contains(currentUserId) || (data["isBlocked"] as? Boolean ?: false),
            isFavorited = favoritedBy.contains(currentUserId) || (data["isFavorited"] as? Boolean ?: false),
            theme = data["theme"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            groupAdminIds = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            groupCreatedBy = data["createdBy"] as? String
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

        val replyToMessageId = data["replyToMessageId"] as? String
        val replyStub = if (!replyToMessageId.isNullOrBlank()) {
            Message(
                messageId = replyToMessageId,
                chatId = data["chatId"] as? String ?: "",
                sender = senderStub,
                messageType = com.linker.app.domain.model.MessageType.TEXT,
                content = null,
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
                deliveryMethod = com.linker.app.domain.model.DeliveryMethod.ONLINE,
                createdAt = 0L,
                updatedAt = 0L,
                deliveredAt = null,
                readAt = null
            )
        } else null

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
            replyToMessage = replyStub,
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

    override suspend fun promoteGroupAdmin(chatId: String, userId: String): Result<Unit> = safeCall {
        assertIsGroupAdmin(chatId)
        chatsCollection.document(chatId).update("adminIds", FieldValue.arrayUnion(userId)).await()
    }

    override suspend fun demoteGroupAdmin(chatId: String, userId: String): Result<Unit> = safeCall {
        assertIsGroupAdmin(chatId)
        val doc = chatsCollection.document(chatId).get().await()
        val data = doc.data ?: throw Exception("Chat not found")
        val admins = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
            ?: mutableListOf()
        if (!admins.contains(userId)) return@safeCall Unit
        admins.remove(userId)
        if (admins.isEmpty()) throw Exception("The group must keep at least one admin")
        chatsCollection.document(chatId).update("adminIds", admins).await()
    }

    override suspend fun removeGroupMember(chatId: String, userId: String): Result<Unit> = safeCall {
        if (userId == currentUserId) throw Exception("Use leave group to remove yourself")
        assertIsGroupAdmin(chatId)
        val doc = chatsCollection.document(chatId).get().await()
        val data = doc.data ?: throw Exception("Chat not found")
        val participants = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it != userId }
            ?: throw Exception("Invalid participants")
        val admins = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it != userId } ?: emptyList()
        val unreadCounts = (data["unreadCounts"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            if (key == userId) return@mapNotNull null
            key to (v as? Number ?: return@mapNotNull null)
        }?.toMap()?.toMutableMap() ?: mutableMapOf()
        val updates = mutableMapOf<String, Any>(
            "participantIds" to participants,
            "adminIds" to admins,
            "updatedAt" to System.currentTimeMillis()
        )
        unreadCounts.forEach { (k, v) -> updates["unreadCounts.$k"] = v }
        chatsCollection.document(chatId).update(updates).await()
    }

    override suspend fun updateGroupProfile(chatId: String, name: String?, imageUrl: String?): Result<Unit> = safeCall {
        assertIsGroupAdmin(chatId)
        val updates = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
        if (name != null) updates["chatName"] = name
        if (imageUrl != null) updates["chatImageUrl"] = imageUrl
        if (updates.size <= 1) return@safeCall Unit
        chatsCollection.document(chatId).update(updates).await()
    }

    private suspend fun assertIsGroupAdmin(chatId: String) {
        val doc = chatsCollection.document(chatId).get().await()
        val data = doc.data ?: throw Exception("Chat not found")
        if ((data["chatType"] as? String) != "GROUP") throw Exception("Not a group chat")
        val admins = (data["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val createdBy = data["createdBy"] as? String
        val allowed = admins.contains(currentUserId) ||
            (admins.isEmpty() && createdBy == currentUserId)
        if (!allowed) throw Exception("Admin only")
    }
}
