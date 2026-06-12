package com.linker.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
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
import com.linker.app.data.local.entity.UserEntity
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.*
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.repository.ChatSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
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
    private val chatSettingsRepository: ChatSettingsRepository
) : ChatRepository {

    private val chatsCollection = firestore.collection("chats")
    
    // Global chat listener for caching all chats
    private var globalChatListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** Returns the messages subcollection reference for a given chat */
    private fun messagesRef(chatId: String) =
        chatsCollection.document(chatId).collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""
    
    init {
        auth.addAuthStateListener {
            stopGlobalChatListener()
            if (auth.currentUser != null) {
                startGlobalChatListener()
            }
        }
    }

    private fun isUserArchivedChat(data: Map<String, Any?>): Boolean {
        val archivedBy = data["archivedBy"] as? List<*>
        return archivedBy?.contains(currentUserId) == true
    }

    // ── Chat list ──────────────────────────────────────────────────────────

    override fun observeChats(): Flow<Result<List<Chat>>> {
        if (currentUserId.isBlank()) {
            return flowOf(Result.Success(emptyList<Chat>()))
        }

        // Merge Firestore and local database flows
        val firestoreFlow = callbackFlow {
            val listener = chatsCollection
                .whereArrayContains("participantIds", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Firestore error - don't emit, let local flow handle it
                        Log.d("ChatRepository", "Firestore error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val chats = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        if (isUserArchivedChat(data)) return@mapNotNull null
                        mapToChatSync(doc.id, data)
                    }?.sortedByDescending { it.updatedAt } ?: emptyList()
                    
                    // Save chats to local database for offline access
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            chats.forEach { chat ->
                                saveChatToLocal(chat)
                            }
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Error saving chats to local: ${e.message}")
                        }
                    }
                    
                    trySend(Result.Success(chats))
                }
            awaitClose { listener.remove() }
        }
        
        val localFlow = chatDao.observeActiveChats().map { entities ->
            val domainChats = entities.mapNotNull { entity ->
                try {
                    val participants = entity.participantIds.map { uid ->
                        userDao.getUserById(uid)?.toDomain() ?: createUserStub(uid)
                    }
                    val lastMessage = entity.lastMessageId?.let { mid ->
                        messageDao.getMessageById(mid)?.let { messageEntityToDomainSync(it) }
                    }
                    entity.toDomain(participants, lastMessage)
                } catch (e: Exception) {
                    null
                }
            }
            Result.Success(domainChats) as Result<List<Chat>>
        }
        
        // Merge flows: Firestore takes priority, but local DB is fallback
        return merge(firestoreFlow, localFlow)
    }

    override fun observeArchivedChats(): Flow<Result<List<Chat>>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(Result.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(emptyList()))
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    if (!isUserArchivedChat(data)) return@mapNotNull null
                    mapToChatSync(doc.id, data)
                }?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(Result.Success(chats))
            }
        awaitClose { listener.remove() }
    }

    override fun observeTotalUnread(): Flow<Result<Int>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(Result.Success(0))
            awaitClose { }
            return@callbackFlow
        }

        val listener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(0))
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
                trySend(Result.Success(total))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getChatById(chatId: String): Result<Chat> = safeCall {
        try {
            val doc = chatsCollection.document(chatId).get().await()
            val data = doc.data ?: throw Exception("Chat not found")
            mapToChat(doc.id, data)
        } catch (e: Exception) {
            // If Firestore fails (e.g., offline), try local database
            Log.d("ChatRepository", "Firestore getChatById failed: ${e.message}, trying local DB")
            val localChat = chatDao.getChatById(chatId)
            if (localChat != null) {
                Log.d("ChatRepository", "Found chat in local DB: $chatId")
                val participants = localChat.participantIds.map { uid ->
                    userDao.getUserById(uid)?.toDomain() ?: createUserStub(uid)
                }
                val lastMessage = localChat.lastMessageId?.let { mid ->
                    messageDao.getMessageById(mid)?.let { messageEntityToDomainSync(it) }
                }
                localChat.toDomain(participants, lastMessage)
            } else {
                Log.e("ChatRepository", "Chat not found in Firestore or local DB: $chatId")
                throw e
            }
        }
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

        val sortedIds = listOf(currentUserId, recipientUserId).sorted()
        val chatId = "private_${sortedIds[0]}_${sortedIds[1]}"
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
        chatsCollection.document(chatId).set(chatData, com.google.firebase.firestore.SetOptions.merge()).await()

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

    override suspend fun deleteChat(chatId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).delete().await()
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

    /**
     * Resolve message reference with local cache optimization
     * 
     * PERFORMANCE OPTIMIZATION:
     * 1. First check local database (O(1) lookup)
     * 2. If not found, iterate through user's chats (O(n))
     * 
     * FUTURE IMPROVEMENT:
     * Add in-memory cache (messageId -> chatId) to avoid Firestore queries
     * for frequently accessed messages. Consider using LruCache with 100 entries.
     */
    private suspend fun resolveMessageRef(messageId: String): DocumentReference {
        // Fast path: Check local database first
        val local = messageDao.getMessageById(messageId)
        if (local != null) {
            return messagesRef(local.chatId).document(messageId)
        }
        
        if (currentUserId.isBlank()) throw Exception("Not signed in")

        // Slow path: Query all user's chats (O(n) complexity)
        // This is acceptable for infrequent operations (reactions, info)
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
        entity.toDomain(User.deletedUser(entity.senderId))

    private fun createUserStub(userId: String): User = User.deletedUser(userId)

    private fun lastMessagePreviewFromChatDoc(chatId: String, data: Map<String, Any?>): Message? {
        val text = data["lastMessageText"] as? String ?: return null
        val at = (data["lastMessageAt"] as? Number)?.toLong() ?: return null
        val mid = (data["lastMessageId"] as? String).orEmpty()
        return Message(
            messageId = mid,
            chatId = chatId,
            sender = UserReference.deletedUser("unknown"),
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
        val usersList = userDao.getUsersByIds(participantIds)
        val usersMap = usersList.associateBy { it.userId }
        val participants = participantIds.map { uid ->
            usersMap[uid]?.toDomain() ?: createUserStub(uid)
        }

        val chatTypeStr = data["chatType"] as? String ?: "PRIVATE"
        val chatType = if (chatTypeStr == "GROUP") {
            ChatType.GROUP
        } else {
            ChatType.PRIVATE
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
            groupCreatedBy = data["createdBy"] as? String,
            groupPermissions = GroupPermissions.fromMap((data["groupPermissions"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value as Any })
        )
    }

    private fun mapToChatSync(chatId: String, data: Map<String, Any?>): Chat {
        val participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val participants = participantIds.map { uid ->
            createUserStub(uid)
        }

        val chatTypeStr = data["chatType"] as? String ?: "PRIVATE"
        val chatType = if (chatTypeStr == "GROUP") {
            ChatType.GROUP
        } else {
            ChatType.PRIVATE
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
            groupCreatedBy = data["createdBy"] as? String,
            groupPermissions = GroupPermissions.fromMap((data["groupPermissions"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value as Any })
        )
    }

    private fun mapToMessageSync(messageId: String, data: Map<String, Any?>): Message {
        val senderId = data["senderId"] as? String ?: "unknown"
        val resolvedSenderId = if (senderId.isNotBlank()) senderId else "unknown"
        val senderStub = UserReference(
            userId = resolvedSenderId,
            username = "user",
            displayName = "User",
            profileImageUrl = null,
            isVerified = false
        )

        val replyToMessageId = data["replyToMessageId"] as? String
        val replyStub = if (!replyToMessageId.isNullOrBlank()) {
            MessageReference(
                messageId = replyToMessageId,
                senderId = senderId,
                senderName = "",
                content = null,
                messageType = MessageType.TEXT,
                createdAt = 0L
            )
        } else null

        return Message(
            messageId = messageId,
            chatId = data["chatId"] as? String ?: "",
            sender = senderStub,
            messageType = try {
                MessageType.valueOf(data["messageType"] as? String ?: "TEXT")
            } catch (_: Exception) {
                MessageType.TEXT
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
                MessageStatus.valueOf(data["messageStatus"] as? String ?: "SENT")
            } catch (_: Exception) {
                MessageStatus.SENT
            },
            deliveryMethod = try {
                DeliveryMethod.valueOf(data["deliveryMethod"] as? String ?: "ONLINE")
            } catch (_: Exception) {
                DeliveryMethod.ONLINE
            },
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            deliveredAt = (data["deliveredAt"] as? Number)?.toLong(),
            readAt = (data["readAt"] as? Number)?.toLong(),
            readReceipts = (data["readReceipts"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val userId = k as? String ?: return@mapNotNull null
                val seenAt = (v as? Number)?.toLong() ?: return@mapNotNull null
                userId to seenAt
            }?.toMap() ?: emptyMap()
        )
    }


    /**
     * Save a chat from Firestore to local database
     * Used for offline access
     */
    private suspend fun saveChatToLocal(chat: Chat) {
        try {
            val entity = ChatEntity(
                chatId = chat.chatId,
                chatType = if (chat.chatType == ChatType.GROUP) EntityChatType.GROUP else EntityChatType.PRIVATE,
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
                createdAt = chat.createdAt,
                updatedAt = chat.updatedAt
            )
            chatDao.insertChat(entity)
            
            // Also save participants to user cache
            chat.participants.forEach { user ->
                try {
                    val userEntity = UserEntity(
                        userId = user.userId,
                        username = user.username,
                        displayName = user.displayName,
                        profileImageUrl = user.profileImageUrl,
                        bio = user.bio,
                        email = user.getEmail(),
                        phoneNumber = user.getPhoneNumber(),
                        coverImageUrl = user.coverImageUrl,
                        isVerified = user.isVerified,
                        followersCount = user.metrics.followersCount,
                        followingCount = user.metrics.followingCount,
                        likesCount = user.metrics.likesCount,
                        isFollowing = user.relationship.isFollowedBy,
                        isFollowedBy = user.relationship.isFollowedBy,
                        isBlocked = user.relationship.isBlocked,
                        isMuted = user.relationship.isMuted,
                        followRequestSent = user.relationship.followRequestSent,
                        isPrivate = user.privacy.isPrivate,
                        hideFollowLists = user.privacy.hideFollowLists,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt
                    )
                    userDao.insertUser(userEntity)
                } catch (e: Exception) {
                    // Ignore user save errors
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error saving chat ${chat.chatId} to local: ${e.message}")
        }
    }


    /**
     * Start global chat listener to cache all chats
     * This ensures all chats are available offline
     */
    private fun startGlobalChatListener() {
        // Wait for user to be authenticated
        if (currentUserId.isBlank()) {
            android.util.Log.d("ChatRepository", "User not authenticated, skipping global chat listener")
            return
        }
        
        android.util.Log.d("ChatRepository", "Starting global chat listener for user $currentUserId")
        
        // Listen to all chats for this user
        globalChatListener = chatsCollection
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Global chat listener error: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot == null || snapshot.isEmpty) {
                    return@addSnapshotListener
                }
                
                android.util.Log.d("ChatRepository", "Global listener received ${snapshot.documents.size} chats")
                
                // Process chats in background
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        snapshot.documents.forEach { doc ->
                            try {
                                val data = doc.data ?: return@forEach
                                val chat = mapToChatSync(doc.id, data)
                                
                                // Save to local database
                                saveChatToLocal(chat)
                            } catch (e: Exception) {
                                android.util.Log.e("ChatRepository", "Error processing chat ${doc.id}: ${e.message}")
                            }
                        }
                        android.util.Log.d("ChatRepository", "Global listener processed ${snapshot.documents.size} chats")
                    } catch (e: Exception) {
                        android.util.Log.e("ChatRepository", "Error in global chat listener: ${e.message}")
                    }
                }
            }
    }
    
    /**
     * Stop global chat listener
     * Should be called when user logs out
     */
    fun stopGlobalChatListener() {
        globalChatListener?.remove()
        globalChatListener = null
        android.util.Log.d("ChatRepository", "Global chat listener stopped")
    }
}
