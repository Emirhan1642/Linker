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
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.*
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageReactionRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.repository.NotificationRepository
import com.linker.app.domain.repository.ReactionDetail
import com.linker.app.domain.repository.ReadReceiptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MessageRepository
 * Handles all message-related operations (CRUD, sending, editing, deleting)
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val messageQueueDao: MessageQueueDao,
    private val userDao: UserDao,
    private val notificationRepository: NotificationRepository,
    private val messageReactionRepository: MessageReactionRepository,
    private val readReceiptRepository: ReadReceiptRepository,
    private val supabaseNotificationApi: SupabaseNotificationApi,
    private val connectivityMonitor: com.linker.app.data.connectivity.ConnectivityMonitor,
    private val messageQueueProcessor: com.linker.app.data.queue.MessageQueueProcessor,
    private val messageDeduplicationManager: com.linker.app.data.queue.MessageDeduplicationManager
) : MessageRepository {

    private val chatsCollection = firestore.collection("chats")
    
    // Global message listener for caching all messages
    private var globalMessageListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** Returns the messages subcollection reference for a given chat */
    private fun messagesRef(chatId: String) =
        chatsCollection.document(chatId).collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""
    
    init {
        auth.addAuthStateListener {
            stopGlobalMessageListener()
            if (auth.currentUser != null) {
                startGlobalMessageListener()
            }
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMessages(chatId: String): Flow<Result<List<Message>>> {
        return connectivityMonitor.observeConnectivityState().flatMapLatest { connectivityState ->
            val isOnline = connectivityState is com.linker.app.data.connectivity.ConnectivityState.Online
            
            android.util.Log.d("MessageRepository", "observeMessages: connectivity changed to $connectivityState for chat $chatId")
            
            val firestoreFlow = if (isOnline) {
                //android.util.Log.d("MessageRepository", "Online mode: listening to Firestore for chat $chatId")
                callbackFlow {
                    val listener = messagesRef(chatId)
                        .orderBy("createdAt", Query.Direction.ASCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                //android.util.Log.d("MessageRepository", "Firestore listener error for chat $chatId: ${error.message}")
                                trySend(emptyList())
                                return@addSnapshotListener
                            }
                            val messages = snapshot?.documents?.mapNotNull { doc ->
                                doc.data?.let { mapToMessageSync(doc.id, it) }
                            } ?: emptyList()
                            
                            // Save messages to local database for offline access
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    messages.forEach { message ->
                                        saveMessageToLocal(message, chatId)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MessageRepository", "Error saving messages to local: ${e.message}")
                                }
                            }
                            
                            //android.util.Log.d("MessageRepository", "Firestore emitted ${messages.size} messages for chat $chatId")
                            trySend(messages)
                        }
                    awaitClose { listener.remove() }
                }
            } else {
                //android.util.Log.d("MessageRepository", "Offline mode: not listening to Firestore for chat $chatId")
                flowOf(emptyList())
            }
            
            val roomFlow = messageDao.observeMessagesByChat(chatId).map { entities ->
                //android.util.Log.d("MessageRepository", "Local DB emitted ${entities.size} messages for chat $chatId")
                entities.map { entity -> messageEntityToDomainSync(entity) }
            }
            
            combine(firestoreFlow, roomFlow) { remote, local ->
                //android.util.Log.d("MessageRepository", "Combining: remote=${remote.size}, local=${local.size} for chat $chatId")
                if (remote.isEmpty() && local.isNotEmpty()) {
                    //android.util.Log.d("MessageRepository", "Using local messages for chat $chatId (Firestore unavailable)")
                    Result.Success(local)
                } else {
                    Result.Success(mergeMessagesById(local, remote))
                }
            }
        }
    }

    override suspend fun getMessageById(messageId: String): Result<Message> = safeCall {
        // Requires chatId — search across all chats via collectionGroup
        val local = messageDao.getMessageById(messageId)
        if (local != null) {
            val snap = messagesRef(local.chatId).document(messageId).get().await()
            val data = snap.data
            val parsed = if (snap.exists() && data != null) mapToMessageSync(snap.id, data) else null
            parsed ?: messageEntityToDomainSync(local)
        } else {
            val ref = resolveMessageRef(messageId)
            val snap = ref.get().await()
            mapToMessageSync(snap.id, snap.data ?: throw Exception("Message not found"))
                ?: throw Exception("Message parse failed")
        }
    }

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
        }.reversed()
    }

    /**
     * Sync messages from Firestore to local database
     * Used when opening a chat to ensure all messages are available offline
     */
    suspend fun syncMessagesFromFirestore(chatId: String): Result<Unit> = safeCall {
        //android.util.Log.d("MessageRepository", "Syncing messages from Firestore for chat $chatId")
        
        // Get all messages from Firestore (limit to last 500 for performance)
        val snapshot = messagesRef(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(500)
            .get()
            .await()
        
        val messages = snapshot.documents.mapNotNull { doc ->
            doc.data?.let { mapToMessageSync(doc.id, it) }
        }
        
        //android.util.Log.d("MessageRepository", "Downloaded ${messages.size} messages from Firestore for chat $chatId")
        
        // Save to local database
        messages.forEach { message ->
            val entity = MessageEntity(
                messageId = message.messageId,
                chatId = chatId,
                senderId = message.sender.userId,
                content = message.content,
                messageType = when (message.messageType) {
                    MessageType.TEXT -> com.linker.app.data.local.entity.MessageType.TEXT
                    MessageType.IMAGE -> com.linker.app.data.local.entity.MessageType.IMAGE
                    MessageType.VIDEO -> com.linker.app.data.local.entity.MessageType.VIDEO
                    MessageType.AUDIO -> com.linker.app.data.local.entity.MessageType.AUDIO
                    MessageType.FILE -> com.linker.app.data.local.entity.MessageType.FILE
                    else -> {com.linker.app.data.local.entity.MessageType.TEXT}
                },
                createdAt = message.createdAt,
                updatedAt = message.updatedAt,
                messageStatus = when (message.messageStatus) {
                    MessageStatus.SENDING -> EntityMessageStatus.SENDING
                    MessageStatus.SENT -> EntityMessageStatus.SENT
                    MessageStatus.DELIVERED -> EntityMessageStatus.DELIVERED
                    MessageStatus.READ -> EntityMessageStatus.READ
                    MessageStatus.FAILED -> EntityMessageStatus.FAILED
                },
                isDeleted = message.isDeleted,
                deletedForEveryone = message.deletedForEveryone,
                replyToMessageId = message.replyToMessage?.messageId,
                reactions = message.reactions,
                readAt = message.readAt,
                deliveryMethod = EntityDeliveryMethod.ONLINE
            )
            messageDao.insertMessage(entity)
        }
        
        //android.util.Log.d("MessageRepository", "Saved ${messages.size} messages to local DB for chat $chatId")
    }

    override suspend fun sendMessage(
        chatId: String,
        messageType: MessageType,
        content: String?,
        mediaLocalPath: String?,
        replyToMessageId: String?,
        replyToNote: com.linker.app.domain.model.NoteReference?
    ): Result<Message> {
        // 1. First check local Room database for chat metadata to support offline-first mode
        val localChatEntity = chatDao.getChatById(chatId)
        val chat = if (localChatEntity != null) {
            val participants = localChatEntity.participantIds.map { uid ->
                userDao.getUserById(uid)?.toDomain() ?: createUserStub(uid)
            }
            val lastMessage = localChatEntity.lastMessageId?.let { mid ->
                messageDao.getMessageById(mid)?.let { it.toDomain(userDao.getUserById(it.senderId)?.toDomain()) }
            }
            localChatEntity.toDomain(participants, lastMessage)
        } else {
            // Fallback to remote Firestore if not cached locally
            val chatDoc = try {
                chatsCollection.document(chatId).get().await()
            } catch (e: Exception) {
                return Result.Error(e.toString())
            }
            
            if (!chatDoc.exists()) {
                return Result.Error(Exception("Chat not found").toString())
            }
            
            val chatData = chatDoc.data
                ?: return Result.Error(Exception("Failed to parse chat").toString())
            mapToChatSync(chatId, chatData)
        }
        if (chat.chatType == ChatType.GROUP) {
            val restrictToAdmins = !chat.groupPermissions.canSendMessages
            val isCurrentUserAdmin = chat.groupAdminIds.contains(currentUserId) || chat.groupCreatedBy == currentUserId
            if (restrictToAdmins && !isCurrentUserAdmin) {
                return Result.Error("Only admins can send messages")
            }
        }

        // Use ConnectivityMonitor to check connectivity
        val connectivityState = connectivityMonitor.observeConnectivityState().first()
        val isConnected = connectivityState is com.linker.app.data.connectivity.ConnectivityState.Online
        val deliveryMethod = if (isConnected) DeliveryMethod.ONLINE else DeliveryMethod.BLE
        android.util.Log.d("MessageRepository", "sendMessage: connectivityState=$connectivityState, isConnected=$isConnected, deliveryMethod=$deliveryMethod")

        return safeCall {
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            // Check for race condition - prevent duplicate processing
            if (messageDeduplicationManager.isDuplicate(messageId)) {
                android.util.Log.w("MessageRepository", "Duplicate message detected: $messageId")
                throw Exception("Duplicate message")
            }
            
            // Mark as processed to prevent race conditions
            messageDeduplicationManager.markAsProcessed(messageId)
            
            val domainMsgStatus = if (isConnected) MessageStatus.SENT else MessageStatus.SENDING
            val entityMsgStatus = if (isConnected) EntityMessageStatus.SENT else EntityMessageStatus.SENDING

            // Ensure chat exists locally
            ensureChatExistsLocally(chat, now)

            // Ensure sender exists locally
            ensureSenderExistsLocally(now)

            val participantIds = chat.participants.map { it.userId }
            val messageData = buildFirestoreMessagePayload(
                messageId = messageId,
                chatId = chatId,
                senderId = currentUserId,
                messageType = messageType,
                content = content,
                mediaUrl = mediaLocalPath,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLinkId = null,
                replyToMessageId = replyToMessageId,
                replyToNote = replyToNote,
                forwardedFromMessageId = null,
                participantIds = participantIds,
                deliveryMethod = deliveryMethod,
                messageStatus = domainMsgStatus,
                createdAt = now,
                updatedAt = now
            )

            if (deliveryMethod == DeliveryMethod.ONLINE) {
                sendMessageOnline(chatId, messageId, messageData, content, participantIds, now)
            } else {
                // Queue message for offline delivery via MessageQueueProcessor
                val recipientId = chat.participants.firstOrNull { it.userId != currentUserId }?.userId ?: ""
                messageQueueProcessor.enqueueMessage(
                    messageId = messageId,
                    chatId = chatId,
                    recipientId = recipientId,
                    payload = content ?: "",
                    deliveryMethod = com.linker.app.data.local.entity.DeliveryMethod.BLE
                )
            }

            val sender = getSender()
            val message = createMessageDomainObject(
                messageId, chatId, sender, messageType, content,
                mediaLocalPath, domainMsgStatus, deliveryMethod, replyToNote, now
            )

            saveMessageLocally(messageId, chatId, messageType, content,
                entityMsgStatus, deliveryMethod, replyToMessageId, replyToNote, now)

            updateChatLastMessage(chatId, messageId, content, now)

            // Sync with Firestore if offline
            if (!isConnected) {
                syncOfflineMessageToFirestore(chatId, messageId, content, participantIds, now)
            }

            // Send notifications
            sendNotificationsIfNeeded(chat, sender, content, chatId, messageId, deliveryMethod)

            message
        }
    }

    private suspend fun ensureChatExistsLocally(chat: Chat, now: Long) {
        val localChat = chatDao.getChatById(chat.chatId)
        if (localChat == null) {
            chatDao.insertChat(
                ChatEntity(
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
                    theme = chat.theme,
                    createdAt = chat.createdAt,
                    updatedAt = chat.updatedAt
                )
            )
        }
    }

    private suspend fun ensureSenderExistsLocally(now: Long) {
        val existingSender = userDao.getUserById(currentUserId)
        if (existingSender == null) {
            val firebaseUser = auth.currentUser
            userDao.insertUser(
                com.linker.app.data.local.entity.UserEntity(
                    userId = currentUserId,
                    username = firebaseUser?.displayName ?: "",
                    displayName = firebaseUser?.displayName ?: "",
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
    }

    private suspend fun sendMessageOnline(
        chatId: String,
        messageId: String,
        messageData: Map<String, Any?>,
        content: String?,
        participantIds: List<String>,
        now: Long
    ) {
        val batch = firestore.batch()
        
        // Set message with delivery receipts for current user (sender)
        val messageWithDelivery = messageData.toMutableMap()
        messageWithDelivery["deliveredAt"] = now
        messageWithDelivery["deliveryReceipts"] = mapOf(currentUserId to now)
        
        batch.set(messagesRef(chatId).document(messageId), messageWithDelivery)
        val displayText = content ?: "[Media]"
        val chatUpdates = mutableMapOf<String, Any>(
            "lastMessageText" to displayText,
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
        
        try {
            batch.commit().await()
            android.util.Log.d("MessageRepository", "Message sent successfully: $messageId")
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Failed to send message: ${e.message}", e)
            throw e
        }
    }

    private suspend fun getSender(): User {
        val senderEntity = userDao.getUserById(currentUserId)
        return senderEntity?.toDomain() ?: createUserStub(currentUserId)
    }

    private fun createMessageDomainObject(
        messageId: String,
        chatId: String,
        sender: User,
        messageType: MessageType,
        content: String?,
        mediaUrl: String?,
        domainMsgStatus: MessageStatus,
        deliveryMethod: DeliveryMethod,
        replyToNote: com.linker.app.domain.model.NoteReference?,
        now: Long
    ): Message {
        return Message(
            messageId = messageId,
            chatId = chatId,
            sender = UserReference(
                userId = sender.userId,
                username = sender.username,
                displayName = sender.displayName,
                profileImageUrl = sender.profileImageUrl,
                isVerified = sender.isVerified
            ),
            messageType = messageType,
            content = content,
            mediaUrl = mediaUrl,
            thumbnailUrl = null,
            mediaWidth = null,
            mediaHeight = null,
            mediaDuration = null,
            sharedLink = null,
            replyToMessage = null,
            replyToNote = replyToNote,
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
    }

    private suspend fun saveMessageLocally(
        messageId: String,
        chatId: String,
        messageType: MessageType,
        content: String?,
        entityMsgStatus: EntityMessageStatus,
        deliveryMethod: DeliveryMethod,
        replyToMessageId: String?,
        replyToNote: com.linker.app.domain.model.NoteReference?,
        now: Long
    ) {
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
            replyToNoteJson = replyToNote?.let { com.google.gson.Gson().toJson(it) },
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
    }
    
    /**
     * Save a message from Firestore to local database
     * Used for offline access
     */
    private suspend fun saveMessageToLocal(message: Message, chatId: String) {
        try {
            val entity = MessageEntity(
                messageId = message.messageId,
                chatId = chatId,
                senderId = message.sender.userId,
                content = message.content,
                messageType = domainMessageTypeToEntity(message.messageType),
                createdAt = message.createdAt,
                updatedAt = message.updatedAt,
                messageStatus = when (message.messageStatus) {
                    MessageStatus.SENDING -> EntityMessageStatus.SENDING
                    MessageStatus.SENT -> EntityMessageStatus.SENT
                    MessageStatus.DELIVERED -> EntityMessageStatus.DELIVERED
                    MessageStatus.READ -> EntityMessageStatus.READ
                    MessageStatus.FAILED -> EntityMessageStatus.FAILED
                },
                isDeleted = message.isDeleted,
                deletedForEveryone = message.deletedForEveryone,
                replyToMessageId = message.replyToMessage?.messageId,
                replyToNoteJson = message.replyToNote?.let { com.google.gson.Gson().toJson(it) },
                reactions = message.reactions,
                readAt = message.readAt,
                deliveryMethod = domainDeliveryToEntity(message.deliveryMethod),
                mediaUrl = null,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLinkId = null,
                forwardedFromMessageId = null,
                isEdited = false,
                encryptedContent = null,
                deliveredAt = null
            )
            messageDao.insertMessage(entity)
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Error saving message ${message.messageId} to local: ${e.message}")
        }
    }

    private suspend fun updateChatLastMessage(chatId: String, messageId: String, content: String?, now: Long) {
        val displayText = content ?: "[Media]"
        chatDao.updateLastMessage(chatId, messageId, displayText, now)
    }

    private suspend fun syncOfflineMessageToFirestore(
        chatId: String,
        messageId: String,
        content: String?,
        participantIds: List<String>,
        now: Long
    ) {
        try {
            val batch = firestore.batch()
            
            // Create message document in Firestore
            val messageData = buildFirestoreMessagePayload(
                messageId = messageId,
                chatId = chatId,
                senderId = currentUserId,
                messageType = MessageType.TEXT,
                content = content,
                mediaUrl = null,
                thumbnailUrl = null,
                mediaWidth = null,
                mediaHeight = null,
                mediaDuration = null,
                sharedLinkId = null,
                replyToMessageId = null,
                forwardedFromMessageId = null,
                participantIds = participantIds,
                deliveryMethod = DeliveryMethod.BLE,
                messageStatus = MessageStatus.SENDING,
                createdAt = now,
                updatedAt = now,
                replyToNote = null
            )
            
            val messageWithDelivery = messageData.toMutableMap()
            messageWithDelivery["deliveredAt"] = now
            messageWithDelivery["deliveryReceipts"] = mapOf(currentUserId to now)
            
            batch.set(messagesRef(chatId).document(messageId), messageWithDelivery)
            
            // Update chat document
            val updates = mutableMapOf<String, Any>(
                "lastMessageText" to (content ?: "[Media]"),
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
            batch.update(chatsCollection.document(chatId), updates)
            
            batch.commit().await()
            android.util.Log.d("MessageRepository", "Offline message synced to Firestore: $messageId")
        } catch (e: Exception) {
            android.util.Log.e("MessageRepository", "Failed to sync offline message to Firestore: ${e.message}", e)
            // Don't throw — message is already in local database and queue
        }
    }

    private suspend fun sendNotificationsIfNeeded(
        chat: Chat,
        sender: User,
        content: String?,
        chatId: String,
        messageId: String,
        deliveryMethod: DeliveryMethod
    ) {
        // Get participant IDs directly from Firestore to ensure accuracy
        val participantIds = try {
            val doc = chatsCollection.document(chatId).get().await()
            (doc.get("participantIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.w("MessageRepository", "Failed to get participants: ${e.message}")
            emptyList()
        }
        
        val otherParticipants = participantIds.filter { it != currentUserId }
        if (otherParticipants.isNotEmpty() && deliveryMethod == DeliveryMethod.ONLINE) {
            val senderName = sender.displayName.ifBlank { sender.username }
            val displayText = content?.take(50) ?: "[Media]"
            val notificationMessage = when (chat.chatType) {
                ChatType.PRIVATE -> displayText
                ChatType.GROUP -> "$senderName: $displayText"
            }
            for (recipientId in otherParticipants) {
                sendChatNotification(
                    recipientUserId = recipientId,
                    senderName = senderName,
                    messageText = notificationMessage,
                    chatId = chatId,
                    messageId = messageId,
                    chatType = chat.chatType,
                    chatName = if (chat.chatType == ChatType.GROUP) chat.chatName else null
                )
            }
        }
    }

    private suspend fun sendChatNotification(
        recipientUserId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        messageId: String,
        chatType: ChatType,
        chatName: String? = null
    ) {
        try {
            val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
            android.util.Log.d("MessageRepository", "Sending notification to $recipientUserId for message $messageId")
            val response = supabaseNotificationApi.sendChatNotification(
                request = ChatNotificationRequest(
                    recipientId = recipientUserId,
                    senderId = currentUserId,
                    senderName = senderName,
                    message = messageText,
                    chatId = chatId,
                    messageId = messageId,
                    chatType = chatType.name,
                    chatName = chatName
                )
            )
            
            if (response.isSuccessful) {
                android.util.Log.d("MessageRepository", "Notification sent successfully to $recipientUserId")
                saveNotificationToFirestoreAndLocal(recipientUserId, senderName, messageText, chatId, messageId)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.w("MessageRepository", "Failed to send notification to $recipientUserId: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.w("MessageRepository", "Failed to send notification to $recipientUserId: ${e.message}", e)
        }
    }

    private suspend fun saveNotificationToFirestoreAndLocal(
        recipientUserId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        messageId: String
    ) {
        val now = System.currentTimeMillis()
        val notificationData = hashMapOf(
            "senderId" to currentUserId,
            "type" to "MESSAGE",
            "title" to senderName,
            "body" to messageText,
            "chatId" to chatId,
            "messageId" to messageId,
            "isRead" to false,
            "createdAt" to now
        )
        // Write to users/{recipientId}/notifications subcollection
        firestore.collection("users").document(recipientUserId)
            .collection("notifications")
            .add(notificationData)
            .await()

        val localNotification = com.linker.app.domain.model.Notification(
            notificationId = UUID.randomUUID().toString(),
            notificationType = com.linker.app.domain.model.NotificationType.MESSAGE,
            actor = com.linker.app.domain.model.NotificationActor(
                userId = currentUserId, 
                username = senderName, 
                displayName = senderName,
                profileImageUrl = null,
                isVerified = false
            ),
            target = com.linker.app.domain.model.NotificationTarget.MessageTarget(chatId, messageId),
            title = senderName,
            message = messageText,
            imageUrl = null,
            actionUrl = "/chat/$chatId",
            isRead = false,
            createdAt = now
        )
        notificationRepository.insertNotification(localNotification)
    }

    override suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        val ref = resolveMessageRef(messageId)
        ref.update(
            mapOf(
                "content" to newContent,
                "isEdited" to true,
                "updatedAt" to now
            )
        ).await()
        messageDao.editMessage(messageId, newContent, now)
    }

    override suspend fun deleteMessageForMe(messageId: String): Result<Unit> = safeCall {
        deleteMessageInternal(messageId, false)
    }

    override suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> = safeCall {
        deleteMessageInternal(messageId, true)
    }

    private suspend fun deleteMessageInternal(messageId: String, forEveryone: Boolean) {
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        val chatId = snap.getString("chatId") ?: ""
        val senderId = snap.getString("senderId") ?: ""
        val createdAt = snap.getLong("createdAt") ?: 0L
        val now = System.currentTimeMillis()
        
        // Check if message can be deleted for everyone (within 1 hour)
        val canDeleteForEveryone = senderId == currentUserId && (now - createdAt) <= 3600000 // 1 hour in ms
        
        // Determine actual deletion type
        val actualForEveryone = forEveryone && canDeleteForEveryone
        
        android.util.Log.d("MessageRepository", "Delete type: actualForEveryone=$actualForEveryone, canDeleteForEveryone=$canDeleteForEveryone")
        
        if (actualForEveryone) {
            android.util.Log.d("MessageRepository", "Deleting for everyone - updating Firestore")
            // Delete for everyone - update Firestore
            ref.update(
                mapOf(
                    "isDeleted" to true,
                    "deletedForEveryone" to true
                )
            ).await()
            
            // Update local database
            messageDao.markAsDeleted(messageId, true)
            
            // Send delete notification to other participants
            if (chatId.isNotBlank()) {
                try {
                    val chatDoc = chatsCollection.document(chatId).get().await()
                    val participantIds = (chatDoc.get("participantIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val otherParticipants = participantIds.filter { it != currentUserId }
                    
                    val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
                    for (recipientId in otherParticipants) {
                        try {
                            supabaseNotificationApi.deleteChatNotification(
                            request = com.linker.app.core.di.DeleteChatNotificationRequest(
                                recipientId = recipientId,
                                messageId = messageId,
                                chatId = chatId
                            )
                        )
                        android.util.Log.d("MessageRepository", "Delete notification sent to $recipientId for message $messageId in chat $chatId")
                    } catch (e: Exception) {
                        android.util.Log.w("MessageRepository", "Failed to send delete notification to $recipientId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MessageRepository", "Failed to send delete notifications: ${e.message}")
            }
        } else {
            android.util.Log.d("MessageRepository", "Deleting for me only - updating local database")
            android.util.Log.d("MessageRepository", "About to call messageDao.markAsDeleted($messageId, false)")
            // Delete for me only - only update local database, don't touch Firestore
            try {
                messageDao.markAsDeleted(messageId, false)
                android.util.Log.d("MessageRepository", "Local database updated successfully")
            } catch (e: Exception) {
                android.util.Log.e("MessageRepository", "Failed to update local database", e)
                throw e
            }
        }
        
        // Update last message in chat
        if (chatId.isNotBlank()) {
            updateChatLastMessageAfterDeletion(chatId)
        }
    }
    }


    override suspend fun reactToMessage(
        messageId: String,
        emoji: String?
    ): Result<Unit> = safeCall {
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        if (!snap.exists()) throw Exception("Message not found")
        
        val chatId = snap.getString("chatId") ?: throw Exception("Message chatId missing")
        
        // Delegate to MessageReactionRepository to handle notification sending
        messageReactionRepository.reactToMessage(chatId, messageId, emoji)

        // Update local cache via copy+update (Map field requires TypeConverter, not a raw @Query)
        val localMsg = messageDao.getMessageById(messageId)
        if (localMsg != null) {
            val updatedReactions = localMsg.reactions.toMutableMap()
            if (emoji == null) updatedReactions.remove(currentUserId)
            else updatedReactions[currentUserId] = emoji
            messageDao.updateMessage(localMsg.copy(reactions = updatedReactions))
        }
    }

    override suspend fun markMessageAsRead(messageId: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        val ref = resolveMessageRef(messageId)
        val snap = ref.get().await()
        if (!snap.exists()) throw Exception("Message not found")

        // Check if message is already read by current user
        val readReceipts = snap.get("readReceipts") as? Map<String, Any> ?: emptyMap()
        val isAlreadyRead = readReceipts.containsKey(currentUserId)
        
        if (!isAlreadyRead) {
            ref.update(
                mapOf(
                    "readReceipts.$currentUserId" to now,
                    "messageStatus" to "READ",
                    "readAt" to now
                )
            ).await()
        }
        messageDao.updateMessageStatus(messageId, com.linker.app.data.local.entity.MessageStatus.READ)
    }

    override suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        android.util.Log.d("MessageRepository", "Marking chat as read: $chatId")
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            messageDao.markChatMessagesAsRead(chatId, currentUserId)
        }
        val now = System.currentTimeMillis()
        // Reset unread count in Firestore
        chatsCollection.document(chatId)
            .update("unreadCounts.$currentUserId", 0)
            .await()
        // Mark all unread messages as READ
        val allMessages = messagesRef(chatId)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
        val batch = firestore.batch()
        for (doc in allMessages.documents) {
            val senderId = doc.getString("senderId")
            if (senderId == currentUserId) continue
            val status = doc.getString("messageStatus")
            
            // Check if message is already read by current user
            val readReceipts = doc.get("readReceipts") as? Map<String, Any> ?: emptyMap()
            val isAlreadyRead = readReceipts.containsKey(currentUserId)
            
            if (status != "READ" && !isAlreadyRead) {
                batch.update(doc.reference, mapOf(
                    "readReceipts.$currentUserId" to now,
                    "messageStatus" to "READ",
                    "readAt" to now
                ))
            }
        }
        batch.commit().await()
        // Update Room
        chatDao.markAsRead(chatId)
    }

    override suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit> = safeCall {
        // Stub implementation
        android.util.Log.d("MessageRepository", "Marking chat as read up to $timestamp: $chatId")
    }

    private suspend fun updateChatLastMessageAfterDeletion(chatId: String) {
        val lastSnapshot = messagesRef(chatId)
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

    override suspend fun retryFailedMessages(batchSize: Int): Result<Int> = safeCall {
        val now = System.currentTimeMillis()
        var processedCount = 0

        // 1. Retry plain SENDING state items that failed quick edits
        val sendingItems = messageDao.observeMessagesByStatus(status = EntityMessageStatus.SENDING)
            .first()
            .take(batchSize)
            
        sendingItems.forEach { entity ->
                try {
                    // Use chatId from local entity to target the correct subcollection
                    messagesRef(entity.chatId).document(entity.messageId).update(
                        mapOf(
                            "messageStatus" to "SENT",
                            "updatedAt" to now
                        )
                    ).await()
                    messageDao.updateMessageStatus(entity.messageId, EntityMessageStatus.SENT)
                    processedCount++
                } catch (_: Exception) {
                    // Keep as SENDING for retry later
                }
            }

        val remainingBatch = batchSize - processedCount
        if (remainingBatch <= 0) return@safeCall processedCount

        // 2. Process queued items from BLE/offline insertions
        val pendingQueueItems = (
            messageQueueDao.getQueueItemsByStatus(QueueStatus.PENDING) +
                messageQueueDao.getQueueItemsByStatus(QueueStatus.FAILED)
            )
            .distinctBy { it.queueId }
            .take(remainingBatch)
            
        pendingQueueItems.forEach { queueItem ->
            try {
                // Fetch the full message locally to reconstruct the payload
                val messageEntity = messageDao.getMessageById(queueItem.messageId) ?: return@forEach
                val chatEntity = chatDao.getChatById(queueItem.chatId)
                val participantIds = chatEntity?.participantIds ?: listOf(currentUserId, queueItem.recipientId)

                // Update queue status
                messageQueueDao.updateQueueStatus(queueItem.queueId, QueueStatus.SENDING, now)

                // Reconstruct payload and push to Firestore subcollection
                val messageData = buildFirestoreMessagePayload(
                    messageId = messageEntity.messageId,
                    chatId = messageEntity.chatId,
                    senderId = messageEntity.senderId,
                    messageType = entityMessageTypeToDomain(messageEntity.messageType),
                    content = messageEntity.content,
                    mediaUrl = messageEntity.mediaUrl,
                    thumbnailUrl = messageEntity.thumbnailUrl,
                    mediaWidth = messageEntity.mediaWidth,
                    mediaHeight = messageEntity.mediaHeight,
                    mediaDuration = messageEntity.mediaDuration,
                    sharedLinkId = messageEntity.sharedLinkId,
                    replyToMessageId = messageEntity.replyToMessageId,
                    forwardedFromMessageId = messageEntity.forwardedFromMessageId,
                    participantIds = participantIds,
                    deliveryMethod = DeliveryMethod.ONLINE,
                    messageStatus = MessageStatus.SENT,
                    createdAt = messageEntity.createdAt,
                    updatedAt = now,
                    replyToNote = messageEntity.replyToNoteJson?.let { 
                        try { com.google.gson.Gson().fromJson(it, com.linker.app.domain.model.NoteReference::class.java) } 
                        catch(e: Exception) { null } 
                    }
                )
                sendMessageOnline(
                    chatId = messageEntity.chatId,
                    messageId = messageEntity.messageId,
                    messageData = messageData,
                    content = messageEntity.content,
                    participantIds = participantIds,
                    now = now
                )

                // After successful send, clear queue and update local status
                messageDao.updateMessageStatus(messageEntity.messageId, EntityMessageStatus.SENT)
                messageQueueDao.updateQueueStatus(queueItem.queueId, QueueStatus.SENT, now)

                // Send push notification 
                val sender = getSender()
                val chat = Chat(
                    chatId = queueItem.chatId,
                    chatType = if (chatEntity?.chatType == EntityChatType.GROUP) ChatType.GROUP else ChatType.PRIVATE,
                    chatName = chatEntity?.chatName ?: "",
                    chatImageUrl = chatEntity?.chatImageUrl,
                    participants = participantIds.map { participantId ->
                        userDao.getUserById(participantId)?.toDomain() ?: createUserStub(participantId)
                    },
                    lastMessage = null,
                    unreadCount = 0,
                    isPinned = false,
                    isMuted = false,
                    isArchived = false,
                    isBlocked = false,
                    isFavorited = false,
                    theme = "default",
                    createdAt = 0L,
                    updatedAt = 0L
                )
                sendNotificationsIfNeeded(chat, sender, messageEntity.content, messageEntity.chatId, messageEntity.messageId, DeliveryMethod.ONLINE)
                processedCount++

            } catch (e: Exception) {
                messageQueueDao.incrementRetryCount(queueItem.queueId, now, e.message)
                val newRetryCount = queueItem.retryCount + 1
                if (newRetryCount >= queueItem.maxRetries) {
                    messageQueueDao.updateQueueStatus(queueItem.queueId, QueueStatus.FAILED, now)
                    messageDao.updateMessageStatus(queueItem.messageId, EntityMessageStatus.FAILED)
                } else {
                    messageQueueDao.updateQueueStatus(queueItem.queueId, QueueStatus.PENDING, now)
                }
            }
        }
        processedCount
    }

    override suspend fun retryFailedMessagesForChat(chatId: String, batchSize: Int): Result<Int> = safeCall {
        0
    }

    override fun observeMessageReactions(messageId: String): Flow<Result<Map<String, String>>> = kotlinx.coroutines.flow.flowOf()
    override suspend fun getMessageReactions(messageId: String): Result<Map<String, String>> = safeCall { emptyMap() }
    override suspend fun getReactionDetails(messageId: String): Result<List<com.linker.app.domain.repository.ReactionDetail>> = safeCall { emptyList() }
    override suspend fun getReactionDetailsBatch(messageIds: List<String>): Result<Map<String, List<com.linker.app.domain.repository.ReactionDetail>>> = safeCall { emptyMap() }

    override fun observeReadReceipts(messageId: String): Flow<Result<Map<String, Long>>> = kotlinx.coroutines.flow.flowOf()
    override fun observeDeliveryReceipts(messageId: String): Flow<Result<Map<String, Long>>> = kotlinx.coroutines.flow.flowOf()
    override suspend fun getReadReceipts(messageId: String): Result<Map<String, Long>> = safeCall { emptyMap() }
    override suspend fun getReadReceiptsBatch(messageIds: List<String>): Result<Map<String, Map<String, Long>>> = safeCall { emptyMap() }
    override suspend fun getDeliveryReceipts(messageId: String): Result<Map<String, Long>> = safeCall { emptyMap() }
    override suspend fun getDeliveryReceiptsBatch(messageIds: List<String>): Result<Map<String, Map<String, Long>>> = safeCall { emptyMap() }

    override suspend fun forwardMessage(
        messageId: String,
        targetChatId: String
    ): Result<Message> = safeCall<Message> {
        val originalResult = getMessageById(messageId)
        if (originalResult is Result.Error) throw Exception("Original message not found")
        val original = (originalResult as Result.Success).data

        val result = sendMessage(
            chatId = targetChatId,
            messageType = original.messageType,
            content = original.content ?: "",
            mediaLocalPath = original.mediaUrl,
            replyToMessageId = null
        )
        
        when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw Exception("Failed to forward message")
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }

    override suspend fun searchMessages(chatId: String, query: String): Result<List<Message>> = safeCall {
        val snapshot = messagesRef(chatId)
            .whereGreaterThanOrEqualTo("content", query)
            .whereLessThanOrEqualTo("content", query + "\uf8ff")
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            doc.data?.let { mapToMessageSync(doc.id, it) }
        }
    }

    // Helper methods (copied from ChatRepositoryImpl)
    private fun mapToMessageSync(messageId: String, data: Map<String, Any?>): Message? {
        return try {
            val senderId = data["senderId"] as? String ?: return null
            val chatId = data["chatId"] as? String ?: return null
            val sender = mapToUserSync(senderId, data)
            val replyToMessageId = data["replyToMessageId"] as? String
            val replyStub = replyToMessageId
                ?.takeIf { it.isNotBlank() }
                ?.let { MessageReference(
                    messageId = it,
                    senderId = senderId,
                    senderName = "",
                    content = null,
                    messageType = MessageType.TEXT,
                    createdAt = 0L
                ) }

            Message(
                messageId = messageId,
                chatId = chatId,
                sender = sender,
                messageType = mapMessageType(data["messageType"] as? String),
                content = data["content"] as? String,
                mediaUrl = data["mediaUrl"] as? String,
                thumbnailUrl = data["thumbnailUrl"] as? String,
                mediaWidth = (data["mediaWidth"] as? Number)?.toInt(),
                mediaHeight = (data["mediaHeight"] as? Number)?.toInt(),
                mediaDuration = (data["mediaDuration"] as? Number)?.toInt(),
                sharedLink = null,
                replyToMessage = replyStub,
                replyToNote = (data["replyToNote"] as? Map<String, Any?>)?.let { map ->
                    com.linker.app.domain.model.NoteReference(
                        noteId = map["noteId"] as? String ?: "",
                        authorId = map["authorId"] as? String ?: "",
                        authorName = map["authorName"] as? String ?: "",
                        noteType = map["noteType"] as? String ?: "",
                        content = map["content"] as? String,
                        musicTrackName = map["musicTrackName"] as? String,
                        musicArtistName = map["musicArtistName"] as? String,
                        musicAlbumArt = map["musicAlbumArt"] as? String,
                        latitude = (map["latitude"] as? Number)?.toDouble(),
                        longitude = (map["longitude"] as? Number)?.toDouble(),
                        backgroundColor = map["backgroundColor"] as? String,
                        textColor = map["textColor"] as? String,
                        expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: 0L
                    )
                },
                reactions = (data["reactions"] as? Map<String, String>) ?: emptyMap(),
                isEdited = data["isEdited"] as? Boolean ?: false,
                isDeleted = data["isDeleted"] as? Boolean ?: false,
                deletedForEveryone = data["deletedForEveryone"] as? Boolean ?: false,
                messageStatus = parseMessageStatus(data["messageStatus"] as? String),
                deliveryMethod = parseDeliveryMethod(data["deliveryMethod"] as? String),
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
        } catch (_: Exception) {
            null
        }
    }

    private fun mapToUserSync(senderId: String, messageData: Map<String, Any?>): UserReference {
        val senderMap = messageData["sender"] as? Map<String, Any?>
        val rawUsername = (senderMap?.get("username") as? String) ?: ""
        val rawDisplayName = (senderMap?.get("displayName") as? String) ?: rawUsername
        
        return UserReference(
            userId = if (senderId.isNotBlank()) senderId else "unknown",
            username = if (rawUsername.isNotBlank()) rawUsername else "user",
            displayName = if (rawDisplayName.isNotBlank()) rawDisplayName else "User",
            profileImageUrl = senderMap?.get("profileImageUrl") as? String,
            isVerified = senderMap?.get("isVerified") as? Boolean ?: false
        )
    }

    private fun messageEntityToDomainSync(entity: MessageEntity): Message {
        val replyStub = entity.replyToMessageId
            ?.takeIf { it.isNotBlank() }
            ?.let { MessageReference(
                messageId = it,
                senderId = entity.senderId,
                senderName = "",
                content = null,
                messageType = MessageType.TEXT,
                createdAt = 0L
            ) }
        return Message(
            messageId = entity.messageId,
            chatId = entity.chatId,
            sender = UserReference(
                userId = entity.senderId.ifBlank { "unknown" },
                username = "user",
                displayName = "User",
                profileImageUrl = null,
                isVerified = false
            ),
            messageType = entityMessageTypeToDomain(entity.messageType),
            content = entity.content,
            mediaUrl = entity.mediaUrl,
            thumbnailUrl = entity.thumbnailUrl,
            mediaWidth = entity.mediaWidth,
            mediaHeight = entity.mediaHeight,
            mediaDuration = entity.mediaDuration,
            sharedLink = null,
            replyToMessage = replyStub,
            replyToNote = entity.replyToNoteJson?.let { 
                try { com.google.gson.Gson().fromJson(it, com.linker.app.domain.model.NoteReference::class.java) } 
                catch(e: Exception) { null } 
            },
            reactions = entity.reactions,
            isEdited = entity.isEdited,
            isDeleted = entity.isDeleted,
            deletedForEveryone = entity.deletedForEveryone,
            messageStatus = entityMessageStatusToDomain(entity.messageStatus),
            deliveryMethod = entityDeliveryMethodToDomain(entity.deliveryMethod),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deliveredAt = entity.deliveredAt,
            readAt = entity.readAt,
            readReceipts = emptyMap()
        )
    }

    private fun mergeMessagesById(local: List<Message>, remote: List<Message>): List<Message> {
        val merged = mutableMapOf<String, Message>()
        local.forEach { merged[it.messageId] = it }
        remote.forEach { merged[it.messageId] = it }
        return merged.values.sortedBy { it.createdAt }
    }

    private fun mapToChatSync(chatId: String, data: Map<String, Any?>): Chat {
        val participantIds = (data["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val participants = participantIds.map { uid -> User(userId = uid) }

        val chatTypeStr = data["chatType"] as? String ?: "PRIVATE"
        val chatType = if (chatTypeStr == "GROUP") ChatType.GROUP else ChatType.PRIVATE

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
            lastMessage = null,
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
        replyToNote: com.linker.app.domain.model.NoteReference?,
        forwardedFromMessageId: String?,
        participantIds: List<String>,
        deliveryMethod: DeliveryMethod,
        messageStatus: MessageStatus,
        createdAt: Long,
        updatedAt: Long
    ): Map<String, Any?> {
        return hashMapOf(
            "messageId" to messageId,
            "chatId" to chatId,
            "senderId" to senderId,
            "sender" to mapOf(
                "userId" to senderId,
                "username" to (auth.currentUser?.displayName ?: ""),
                "displayName" to (auth.currentUser?.displayName ?: ""),
                "profileImageUrl" to (auth.currentUser?.photoUrl?.toString())
            ),
            "messageType" to messageType.name,
            "content" to content,
            "mediaUrl" to mediaUrl,
            "thumbnailUrl" to thumbnailUrl,
            "mediaWidth" to mediaWidth,
            "mediaHeight" to mediaHeight,
            "mediaDuration" to mediaDuration,
            "sharedLinkId" to sharedLinkId,
            "replyToMessageId" to replyToMessageId,
            "replyToNote" to replyToNote?.let { ref ->
                mapOf(
                    "noteId" to ref.noteId,
                    "authorId" to ref.authorId,
                    "authorName" to ref.authorName,
                    "noteType" to ref.noteType,
                    "content" to ref.content,
                    "musicTrackName" to ref.musicTrackName,
                    "musicArtistName" to ref.musicArtistName,
                    "musicAlbumArt" to ref.musicAlbumArt,
                    "latitude" to ref.latitude,
                    "longitude" to ref.longitude,
                    "backgroundColor" to ref.backgroundColor,
                    "textColor" to ref.textColor,
                    "expiresAt" to ref.expiresAt
                )
            },
            "forwardedFromMessageId" to forwardedFromMessageId,
            "participantIds" to participantIds,
            "reactions" to emptyMap<String, String>(),
            "isEdited" to false,
            "isDeleted" to false,
            "deletedForEveryone" to false,
            "messageStatus" to messageStatus.name,
            "deliveryMethod" to deliveryMethod.name,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "deliveredAt" to null,
            "readAt" to null,
            "readReceipts" to emptyMap<String, Long>(),
            "deliveryReceipts" to emptyMap<String, Long>()
        )
    }

    private fun mapMessageType(type: String?): MessageType {
        return when (type) {
            "TEXT" -> MessageType.TEXT
            "IMAGE" -> MessageType.IMAGE
            "VIDEO" -> MessageType.VIDEO
            "AUDIO" -> MessageType.AUDIO
            "FILE" -> MessageType.FILE
            "LOCATION" -> MessageType.LOCATION
            "CONTACT" -> MessageType.CONTACT
            "STICKER" -> MessageType.STICKER
            else -> MessageType.TEXT
        }
    }

    private fun parseMessageStatus(status: String?): MessageStatus {
        return when (status) {
            "SENDING" -> MessageStatus.SENDING
            "SENT" -> MessageStatus.SENT
            "DELIVERED" -> MessageStatus.DELIVERED
            "READ" -> MessageStatus.READ
            "FAILED" -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }
    }

    private fun parseDeliveryMethod(method: String?): DeliveryMethod {
        return when (method) {
            "ONLINE" -> DeliveryMethod.ONLINE
            "BLE" -> DeliveryMethod.BLE
            "WIFI_DIRECT" -> DeliveryMethod.WIFI_DIRECT
            else -> DeliveryMethod.ONLINE
        }
    }

    private fun domainMessageTypeToEntity(type: MessageType): com.linker.app.data.local.entity.MessageType {
        return when (type) {
            MessageType.TEXT -> com.linker.app.data.local.entity.MessageType.TEXT
            MessageType.IMAGE -> com.linker.app.data.local.entity.MessageType.IMAGE
            MessageType.VIDEO -> com.linker.app.data.local.entity.MessageType.VIDEO
            MessageType.GIF -> com.linker.app.data.local.entity.MessageType.GIF
            MessageType.LINK -> com.linker.app.data.local.entity.MessageType.LINK
            MessageType.AUDIO -> com.linker.app.data.local.entity.MessageType.AUDIO
            MessageType.FILE -> com.linker.app.data.local.entity.MessageType.FILE
            MessageType.LOCATION -> com.linker.app.data.local.entity.MessageType.LOCATION
            MessageType.CONTACT -> com.linker.app.data.local.entity.MessageType.CONTACT
            MessageType.STICKER -> com.linker.app.data.local.entity.MessageType.STICKER
        }
    }

    private fun entityMessageTypeToDomain(type: com.linker.app.data.local.entity.MessageType): MessageType {
        return when (type) {
            com.linker.app.data.local.entity.MessageType.TEXT -> MessageType.TEXT
            com.linker.app.data.local.entity.MessageType.IMAGE -> MessageType.IMAGE
            com.linker.app.data.local.entity.MessageType.VIDEO -> MessageType.VIDEO
            com.linker.app.data.local.entity.MessageType.GIF -> MessageType.GIF
            com.linker.app.data.local.entity.MessageType.LINK -> MessageType.LINK
            com.linker.app.data.local.entity.MessageType.AUDIO -> MessageType.AUDIO
            com.linker.app.data.local.entity.MessageType.FILE -> MessageType.FILE
            com.linker.app.data.local.entity.MessageType.LOCATION -> MessageType.LOCATION
            com.linker.app.data.local.entity.MessageType.CONTACT -> MessageType.CONTACT
            com.linker.app.data.local.entity.MessageType.STICKER -> MessageType.STICKER
        }
    }

    private fun domainDeliveryToEntity(method: DeliveryMethod): EntityDeliveryMethod {
        return when (method) {
            DeliveryMethod.ONLINE -> EntityDeliveryMethod.ONLINE
            DeliveryMethod.BLE -> EntityDeliveryMethod.BLE
            DeliveryMethod.WIFI_DIRECT -> EntityDeliveryMethod.WIFI_DIRECT
        }
    }

    private fun entityDeliveryMethodToDomain(method: EntityDeliveryMethod): DeliveryMethod {
        return when (method) {
            EntityDeliveryMethod.ONLINE -> DeliveryMethod.ONLINE
            EntityDeliveryMethod.BLE -> DeliveryMethod.BLE
            EntityDeliveryMethod.WIFI_DIRECT -> DeliveryMethod.WIFI_DIRECT
        }
    }

    private fun entityMessageStatusToDomain(status: EntityMessageStatus): MessageStatus {
        return when (status) {
            EntityMessageStatus.SENDING -> MessageStatus.SENDING
            EntityMessageStatus.SENT -> MessageStatus.SENT
            EntityMessageStatus.DELIVERED -> MessageStatus.DELIVERED
            EntityMessageStatus.READ -> MessageStatus.READ
            EntityMessageStatus.FAILED -> MessageStatus.FAILED
        }
    }
    
    /**
     * Start global message listener to cache all incoming messages
     * This ensures all messages are available offline, even if chat is not open
     */
    private fun startGlobalMessageListener() {
        // Wait for user to be authenticated
        if (currentUserId.isBlank()) {
            android.util.Log.d("MessageRepository", "User not authenticated, skipping global message listener")
            return
        }
        
        android.util.Log.d("MessageRepository", "Starting global message listener for user $currentUserId")
        
        // Listen to all messages across all chats using collectionGroup
        globalMessageListener = firestore.collectionGroup("messages")
            .whereArrayContains("participantIds", currentUserId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1000) // Limit to last 1000 messages for performance
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MessageRepository", "Global message listener error: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot == null || snapshot.isEmpty) {
                    return@addSnapshotListener
                }
                
                android.util.Log.d("MessageRepository", "Global listener received ${snapshot.documents.size} messages")
                
                // Process messages in background
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        snapshot.documents.forEach { doc ->
                            try {
                                val data = doc.data ?: return@forEach
                                val message = mapToMessageSync(doc.id, data) ?: return@forEach
                                
                                // Extract chatId from document path (chats/{chatId}/messages/{messageId})
                                val chatId = doc.reference.parent.parent?.id ?: return@forEach
                                
                                // Save to local database
                                saveMessageToLocal(message, chatId)
                            } catch (e: Exception) {
                                android.util.Log.e("MessageRepository", "Error processing message ${doc.id}: ${e.message}")
                            }
                        }
                        android.util.Log.d("MessageRepository", "Global listener processed ${snapshot.documents.size} messages")
                    } catch (e: Exception) {
                        android.util.Log.e("MessageRepository", "Error in global message listener: ${e.message}")
                    }
                }
            }
    }
    
    /**
     * Stop global message listener
     * Should be called when user logs out
     */
    fun stopGlobalMessageListener() {
        globalMessageListener?.remove()
        globalMessageListener = null
        android.util.Log.d("MessageRepository", "Global message listener stopped")
    }

    override fun observeQueuedMessageCount(): Flow<Result<Int>> = messageQueueDao.observePendingCount().map { Result.Success(it) }
}
