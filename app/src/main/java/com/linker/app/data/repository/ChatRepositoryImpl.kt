package com.linker.app.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import java.util.UUID
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.ChatDao
import com.linker.app.data.local.dao.MessageDao
import com.linker.app.data.local.dao.MessageQueueDao
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.ChatEntity
import com.linker.app.data.local.entity.MessageEntity
import com.linker.app.data.local.entity.ChatType as EntityChatType
import com.linker.app.data.local.entity.DeliveryMethod as EntityDeliveryMethod
import com.linker.app.data.local.entity.MessageStatus as EntityMessageStatus
import com.linker.app.data.local.entity.MessageType as EntityMessageType
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.*
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageReactionRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.repository.ReadReceiptRepository
import com.linker.app.domain.repository.ChatSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
    // ✅ DELEGATED: Message operations moved to MessageRepository
    private val messageRepository: MessageRepository,
    private val messageReactionRepository: MessageReactionRepository,
    private val readReceiptRepository: ReadReceiptRepository,
    private val chatSettingsRepository: ChatSettingsRepository
) : ChatRepository {

    private val chatsCollection = firestore.collection("chats")

    /** Returns the messages subcollection reference for a given chat */
    private fun messagesRef(chatId: String) =
        chatsCollection.document(chatId).collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

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
    // ✅ DELEGATED: All message operations are now delegated to MessageRepository

    override fun observeMessages(chatId: String): Flow<List<Message>> = 
        messageRepository.observeMessages(chatId)

    override suspend fun sendMessage(
        chatId: String,
        messageType: MessageType,
        content: String?,
        mediaLocalPath: String?,
        replyToMessageId: String?
    ): Result<Message> = messageRepository.sendMessage(
        chatId = chatId,
        messageType = messageType,
        content = content ?: "",
        mediaUrl = mediaLocalPath,
        replyToMessageId = replyToMessageId
    )

    override suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = 
        messageRepository.editMessage(messageId, newContent)

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit> = 
        messageRepository.deleteMessage(messageId, forEveryone)

    override suspend fun forwardMessage(messageId: String, targetChatId: String): Result<Unit> = 
        messageRepository.forwardMessage(messageId, targetChatId).map { }

    override suspend fun searchMessages(chatId: String, query: String): Result<List<Message>> = 
        messageRepository.searchMessages(chatId, query)

    override suspend fun retryFailedMessages(preferredMethod: DeliveryMethod): Result<Unit> = 
        messageRepository.retryFailedMessages()

    // ✅ DELEGATED: Reaction operations to MessageReactionRepository
    override suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit> = safeCall {
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        val chatId = snap.getString("chatId") ?: ""
        if (chatId.isBlank()) throw Exception("Message chatId missing")
        messageReactionRepository.reactToMessage(chatId, messageId, emoji)
    }

    // ✅ DELEGATED: Read receipt operations to ReadReceiptRepository
    override suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update("unreadCounts.$currentUserId", 0).await()
        chatDao.markAsRead(chatId)
        readReceiptRepository.markChatAsReadUpTo(chatId, Long.MAX_VALUE)
    }

    override suspend fun markChatAsReadUpTo(chatId: String, upToTimestamp: Long): Result<Unit> = 
        readReceiptRepository.markChatAsReadUpTo(chatId, upToTimestamp)

    // ── Chat detail helpers ────────────────────────────────────────────────

    suspend fun getChatParticipantIds(chatId: String): List<String> {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            (doc.get("participantIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveMessageRef(messageId: String): com.google.firebase.firestore.DocumentReference {
        val local = messageDao.getMessageById(messageId)
        if (local != null) {
            return messagesRef(local.chatId).document(messageId)
        }
        if (currentUserId.isBlank()) throw Exception("Not signed in")

        val chatSnapshots = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .get()
            .await()

        for (chatDoc in chatSnapshots.documents) {
            val ref = messagesRef(chatDoc.id).document(messageId)
            val snap = ref.get().await()
            if (snap.exists()) return ref
        }

        throw Exception("Message not found")
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
        MessageType.FILE -> EntityMessageType.FILE
        MessageType.CONTACT -> EntityMessageType.CONTACT
        MessageType.LOCATION -> EntityMessageType.LOCATION
        MessageType.STICKER -> EntityMessageType.STICKER
    }

    private fun domainDeliveryToEntity(d: DeliveryMethod): EntityDeliveryMethod = when (d) {
        DeliveryMethod.ONLINE -> EntityDeliveryMethod.ONLINE
        DeliveryMethod.BLE -> EntityDeliveryMethod.BLE
        DeliveryMethod.WIFI_DIRECT -> EntityDeliveryMethod.WIFI_DIRECT
        DeliveryMethod.P2P_WIFI -> EntityDeliveryMethod.P2P_WIFI
        DeliveryMethod.MESH -> EntityDeliveryMethod.MESH
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

    // ✅ PAGINATION: Load messages with cursor-based pagination
    override suspend fun getMessagesPaged(
        chatId: String,
        beforeTimestamp: Long?,
        limit: Int
    ): Result<List<Message>> = safeCall {
        var query = messagesRef(chatId)
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        if (beforeTimestamp != null) {
            query = query.whereLessThan("createdAt", beforeTimestamp)
        }

        val snapshot = query.get().await()
        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { mapToMessageSync(doc.id, it) }
        }.reversed() // Oldest first
    }

    // ✅ Helper: Get single message by ID
    override suspend fun getMessageById(messageId: String): Message {
        return when (val result = messageRepository.getMessageById(messageId)) {
            is Result.Success -> result.data
            is Result.Error -> throw Exception(result.message ?: "Message not found")
            is Result.Loading -> throw Exception("Message loading")
        }
    }

    // ✅ Helper: Get message reactions
    override suspend fun getMessageReactions(messageId: String): Map<String, String> {
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        return (snap.get("reactions") as? Map<String, String>) ?: emptyMap()
    }

    // ✅ Helper: Get read receipts for a message
    override suspend fun getReadReceipts(messageId: String): Map<String, Long> {
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        val receipts = snap.get("readReceipts") as? Map<String, Any> ?: emptyMap()
        return receipts.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    // ✅ Observe queued message count
    override fun observeQueuedMessageCount(): Flow<Int> =
        messageQueueDao.observePendingCount()
}
