package com.linker.app.presentation.screens.chat


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.domain.model.*
import com.linker.app.domain.usecase.chat.*
import com.linker.app.domain.usecase.note.ObserveActiveNotesUseCase
import com.linker.app.domain.usecase.note.PostNoteUseCase
import com.linker.app.domain.usecase.user.CurrentUserProvider
import com.linker.app.domain.usecase.user.GetUserByIdUseCase
import com.linker.app.domain.usecase.user.GetUserDisplayNameUseCase
import com.linker.app.core.util.InputValidator
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import javax.inject.Inject




@HiltViewModel
class ChatViewModel @Inject constructor(
    private val observeChatsUseCase: ObserveChatsUseCase,
    private val getChatByIdUseCase: GetChatByIdUseCase,
    private val createPrivateChatUseCase: CreatePrivateChatUseCase,
    private val createGroupChatUseCase: CreateGroupChatUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val markChatAsReadUseCase: MarkChatAsReadUseCase,
    private val markChatAsReadUpToUseCase: MarkChatAsReadUpToUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val reactToMessageUseCase: ReactToMessageUseCase,
    private val getMessageByIdUseCase: GetMessageByIdUseCase,
    private val observeActiveNotesUseCase: ObserveActiveNotesUseCase,
    private val postNoteUseCase: PostNoteUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val getUserDisplayNameUseCase: GetUserDisplayNameUseCase,
    private val loadMessageInfoUseCase: LoadMessageInfoUseCase,
    private val getUserByIdUseCase: GetUserByIdUseCase,
    private val syncMessagesFromFirestoreUseCase: com.linker.app.domain.usecase.chat.SyncMessagesFromFirestoreUseCase,
    private val userRepository: com.linker.app.domain.repository.UserRepository
) : ViewModel() {

    private val _chatListState = MutableStateFlow(ChatListUiState())
    val chatListState: StateFlow<ChatListUiState> = _chatListState.asStateFlow()

    private val allChatsFlow = MutableStateFlow<List<ChatUiModel>>(emptyList())
    private val searchQueryFlow = MutableStateFlow("")
    private val selectedFilterFlow = MutableStateFlow("All")

    private val _messageState = MutableStateFlow(ChatMessageUiState())
    val messageState: StateFlow<ChatMessageUiState> = _messageState.asStateFlow()

    private val _messageInfoState = MutableStateFlow(MessageInfoState())
    val messageInfoState: StateFlow<MessageInfoState> = _messageInfoState.asStateFlow()

    private val _messageReactionsState = MutableStateFlow(MessageReactionsUiState())
    val messageReactionsState: StateFlow<MessageReactionsUiState> = _messageReactionsState.asStateFlow()


    private val lastMarkedReadAtByChat = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var chatsJob: Job? = null
    private var messagesJob: Job? = null
    private var notesJob: Job? = null
    private var presenceJob: Job? = null
    
    private val displayNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val currentUserId: String
        get() = currentUserProvider.getCurrentUserId() ?: ""

    /** Track last known user to detect account switches */
    private var lastObservedUserId: String = currentUserId

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val newUid = auth.currentUser?.uid ?: ""
        if (newUid != lastObservedUserId) {
            lastObservedUserId = newUid
            displayNameCache.clear()
            avatarCache.clear()
            restartObserversAfterAccountSwitch()
        }
    }

    init {
        observeChats()
        observeNotes()
        observeFilters()
        startPresencePing()
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    private fun startPresencePing() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                userRepository.updatePresence()
                kotlinx.coroutines.delay(60_000L) // Ping every minute
            }
        }
    }

    private fun observeFilters() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.flow.combine(
                allChatsFlow,
                searchQueryFlow,
                selectedFilterFlow
            ) { chats, query, filter ->
                val normalizedQuery = query.trim()
                chats.filter { chat ->
                    when (filter) {
                        "Unreads" -> !chat.isArchived && chat.unreadCount > 0
                        "Favorites" -> !chat.isArchived && chat.isFavorited
                        "Groups" -> !chat.isArchived && chat.isGroupChat
                        "Archived" -> chat.isArchived
                        else -> !chat.isArchived
                    }
                }.filter { chat ->
                    normalizedQuery.isBlank() ||
                        chat.displayName.contains(normalizedQuery, ignoreCase = true) ||
                        (chat.lastMessage?.contains(normalizedQuery, ignoreCase = true) == true)
                }.sortedWith(
                    if (filter == "All") {
                        compareByDescending<ChatUiModel> { it.isPinned }
                            .thenByDescending { it.lastMessageTime }
                    } else {
                        compareByDescending { it.lastMessageTime }
                    }
                )
            }.collect { filteredChats ->
                _chatListState.update { it.copy(chats = filteredChats) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
        _chatListState.update { it.copy(searchQuery = query) }
    }

    fun updateSelectedFilter(filter: String) {
        selectedFilterFlow.value = filter
        _chatListState.update { it.copy(selectedFilter = filter) }
    }

    // ── Chat List ──────────────────────────────────────────────────────────

    private fun observeChats() {
        chatsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            observeChatsUseCase().collect { result ->
                if (result is Result.Success) {
                    val chats = result.data
                    val uiModels = chats.map { chat ->
                        val isGroup = chat.chatType == ChatType.GROUP
                        val otherParticipant = if (!isGroup) {
                            chat.participants.firstOrNull { it.userId != currentUserId }
                        } else null

                        val resolvedName = if (isGroup) {
                            chat.chatName ?: "Chat"
                        } else {
                            val otherId = otherParticipant?.userId
                            if (!otherId.isNullOrBlank()) {
                                resolveUserDisplayName(otherId)
                            } else {
                                "Chat"
                            }
                        }

                        val lastMsgText = chat.lastMessage?.content
                        val timeFormatted = formatTimestamp(chat.updatedAt)

                        ChatUiModel(
                            chatId = chat.chatId,
                            displayName = resolvedName,
                            imageUrl = if (isGroup) chat.chatImageUrl else otherParticipant?.profileImageUrl,
                            lastMessage = lastMsgText,
                            lastMessageTime = chat.updatedAt,
                            formattedTime = timeFormatted,
                            unreadCount = chat.unreadCount,
                            participantIds = chat.participants.map { it.userId },
                            isGroupChat = isGroup,
                            isPinned = chat.isPinned,
                            isFavorited = chat.isFavorited,
                            isArchived = chat.isArchived,
                            isMuted = chat.isMuted,
                            isBlocked = chat.isBlocked
                        )
                    }
                    allChatsFlow.value = uiModels
                    _chatListState.update { it.copy(isLoading = false) }
                } else if (result is Result.Error) {
                    _chatListState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val daysDiff = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            daysDiff == 0L -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
            daysDiff == 1L -> "Yesterday"
            daysDiff < 7 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
            else -> java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatsJob?.cancel()
        messagesJob?.cancel()
        notesJob?.cancel()
        presenceJob?.cancel()
        com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(authListener)
    }

    /** Re-observe data streams when user account changes (e.g. via AccountCenter). */
    private fun restartObserversAfterAccountSwitch() {
        chatsJob?.cancel()
        messagesJob?.cancel()
        notesJob?.cancel()
        presenceJob?.cancel()
        lastMarkedReadAtByChat.clear()
        _chatListState.value = ChatListUiState(isLoading = true)
        _messageState.value = ChatMessageUiState(isLoading = true)
        observeChats()
        observeNotes()
        startPresencePing()
    }

    private suspend fun resolveUserDisplayName(userId: String): String {
        return displayNameCache.getOrPut(userId) {
            getUserDisplayNameUseCase(userId)
        }
    }

    private suspend fun resolveUserAvatarUrl(userId: String): String? {
        avatarCache[userId]?.let { return it }
        val avatar = try {
            when (val result = getUserByIdUseCase(userId)) {
                is Result.Success -> result.data.profileImageUrl
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (!avatar.isNullOrBlank()) {
            avatarCache[userId] = avatar
        }
        return avatar
    }

    // ── Notes ──────────────────────────────────────────────────────────────

    private fun observeNotes() {
        notesJob?.cancel()
        notesJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.flow.combine(
                observeActiveNotesUseCase(),
                userRepository.observeFollowing()
            ) { notesResult, followingResult ->
                val allNotes = if (notesResult is Result.Success) notesResult.data else emptyList()
                val following = if (followingResult is Result.Success) followingResult.data else emptyList()
                
                val me = currentUserId
                val userNote = allNotes.firstOrNull { it.author.userId == me }
                val otherNotes = allNotes.filter { it.author.userId != me }
                
                // Get online users who DO NOT have a note
                val now = System.currentTimeMillis()
                val fiveMinsMs = 5 * 60 * 1000L
                val authorsWithNotes = otherNotes.map { it.author.userId }.toSet()
                
                val onlineUsers = following.filter { user ->
                    user.userId != me && 
                    !authorsWithNotes.contains(user.userId) &&
                    (now - user.lastSeen) < fiveMinsMs
                }
                
                Triple(userNote, otherNotes, onlineUsers)
            }.collect { (userNote, otherNotes, onlineUsers) ->
                _chatListState.update { 
                    it.copy(
                        userNote = userNote,
                        otherNotes = otherNotes,
                        onlineUsers = onlineUsers
                    )
                }
            }
        }
    }

    fun postNote(content: String) {
        viewModelScope.launch {
            postNoteUseCase(content)
        }
    }

    suspend fun createPrivateChat(recipientUserId: String): Result<Chat> {
        return createPrivateChatUseCase(recipientUserId)
    }

    suspend fun createGroupChat(
        name: String,
        participantIds: List<String>,
        permissions: Map<String, Any>? = null
    ): Result<Chat> {
        return createGroupChatUseCase(name, participantIds, permissions)
    }

    // ── Messages ───────────────────────────────────────────────────────────

    fun openChat(chatId: String) {
        if (_messageState.value.chatId == chatId && messagesJob?.isActive == true) {
            android.util.Log.d("ChatViewModel", "openChat ignored, already observing chatId: $chatId")
            return
        }
        
        android.util.Log.d("ChatViewModel", "openChat called with chatId: $chatId")
        _messageState.value = ChatMessageUiState(isLoading = true, chatId = chatId)

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Starting to load chat...")
                // Try to find existing chat
                val chatResult = getChatByIdUseCase(chatId)
                android.util.Log.d("ChatViewModel", "getChatByIdUseCase result: $chatResult")

                val actualChatId: String
                val actualChat: Chat?

            when (chatResult) {
                is Result.Success -> {
                    actualChatId = chatId
                    actualChat = chatResult.data
                }
                is Result.Error -> {
                    if (!isChatNotFoundError(chatResult)) {
                        _messageState.value = _messageState.value.copy(
                            isLoading = false,
                            error = "Failed to open chat: ${chatResult.message}"
                        )
                        return@launch
                    }
                    // Chat doesn't exist — treat chatId as recipientUserId and create new chat
                    val recipientUserId = chatId
                    when (val newChatResult = createPrivateChatUseCase(recipientUserId)) {
                        is Result.Success -> {
                            actualChatId = newChatResult.data.chatId
                            actualChat = newChatResult.data
                            _messageState.value = _messageState.value.copy(chatId = actualChatId)
                        }
                        is Result.Error -> {
                            _messageState.value = _messageState.value.copy(
                                isLoading = false,
                                error = "Failed to start chat: ${newChatResult.message}"
                            )
                            return@launch
                        }
                        is Result.Loading -> { return@launch }
                    }
                }
                is Result.Loading -> return@launch
            }

            val chat = actualChat ?: return@launch
            val isGroup = chat.chatType == ChatType.GROUP
            val otherUser = if (!isGroup) {
                chat.participants.firstOrNull { it.userId != currentUserId }
            } else null
            val name = if (isGroup) {
                chat.chatName ?: "Group Chat"
            } else if (otherUser != null) {
                try {
                    resolveUserDisplayName(otherUser.userId)
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Failed to resolve user display name: ${e.message}")
                    otherUser.username.ifBlank { otherUser.userId }
                }
            } else "Chat"

            // Compute canSendMessages from groupPermissions
            val canSend = if (isGroup) {
                if (!chat.groupPermissions.canSendMessages) {
                    // Only admins can send → check if current user is admin
                    chat.groupAdminIds.contains(currentUserId) || chat.groupCreatedBy == currentUserId
                } else {
                    true
                }
            } else true

            _messageState.value = _messageState.value.copy(
                recipientId = otherUser?.userId ?: "",
                isGroupChat = isGroup,
                recipientName = name,
                recipientUsername = otherUser?.username ?: "",
                recipientImageUrl = if (isGroup) chat.chatImageUrl else otherUser?.profileImageUrl,
                canSendMessages = canSend
            )

            // Sync messages from Firestore to local DB (for offline access)
            android.util.Log.d("ChatViewModel", "Syncing messages from Firestore for chat: $actualChatId")
            viewModelScope.launch {
                try {
                    syncMessagesFromFirestoreUseCase(actualChatId)
                    android.util.Log.d("ChatViewModel", "Message sync completed")
                } catch (e: Exception) {
                    android.util.Log.d("ChatViewModel", "Message sync failed (continuing anyway): ${e.message}")
                    // Continue even if sync fails (e.g., offline)
                }
            }

            // Mark as read (skip if offline to avoid blocking)
            android.util.Log.d("ChatViewModel", "About to mark chat as read for: $actualChatId")
            viewModelScope.launch {
                try {
                    markChatAsReadUseCase(actualChatId)
                    android.util.Log.d("ChatViewModel", "markChatAsReadUseCase completed")
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "markChatAsReadUseCase failed (continuing anyway): ${e.message}")
                    // Continue even if marking as read fails (e.g., offline)
                }
            }

            // Observe messages (don't wait for markChatAsRead to complete)
            android.util.Log.d("ChatViewModel", "About to call observeMessagesUseCase for chat: $actualChatId")
            observeMessagesUseCase(actualChatId).collect { result ->
                if (result is Result.Success) {
                    val messages = result.data
                    val uiMessages = coroutineScope {
                        val processed = messages.mapIndexed { index, msg ->
                            async {
                                val isSelf = msg.sender.userId == currentUserId
                                val prevIsSelf = if (index > 0) messages[index - 1].sender.userId == currentUserId else !isSelf
                                val nextIsSelf = if (index < messages.size - 1) messages[index + 1].sender.userId == currentUserId else !isSelf
                                
                                val displayContent = if (msg.isDeleted) {
                                    when {
                                        isSelf -> "You deleted this message"
                                        !isSelf && !msg.deletedForEveryone -> "You deleted this message"
                                        !isSelf && msg.deletedForEveryone -> "This message was deleted"
                                        else -> msg.content ?: ""
                                    }
                                } else {
                                    msg.content ?: ""
                                }

                                val formattedReactions = msg.reactions.values.groupBy { it }
                                    .map { (emoji, list) -> if (list.size > 1) "$emoji ${list.size}" else emoji }
                                    .take(3)

                                val seenByUsers = msg.readReceipts
                                    .filterKeys { uid -> uid != msg.sender.userId }
                                    .entries
                                    .sortedByDescending { it.value }
                                    .map { (uid, seenAt) ->
                                        SeenByUserUi(
                                            userId = uid,
                                            displayName = if (uid == currentUserId) "You" else resolveUserDisplayName(uid),
                                            avatarUrl = resolveUserAvatarUrl(uid),
                                            seenAt = seenAt
                                        )
                                    }
                                MessageUiModel(
                                    messageId = msg.messageId,
                                    content = msg.content,
                                    isSelf = isSelf,
                                    timestamp = msg.createdAt,
                                    status = msg.messageStatus,
                                    replyToMessageId = msg.replyToMessage?.messageId,
                                    replyToNote = msg.replyToNote,
                                    readAt = msg.readAt,
                                    reactions = msg.reactions,
                                    readReceipts = msg.readReceipts,
                                    seenByUsers = seenByUsers,
                                    senderId = msg.sender.userId,
                                    senderDisplayName = if (isSelf) {
                                        "You"
                                    } else {
                                        msg.sender.displayName.ifBlank { msg.sender.username }.ifBlank { "User" }
                                    },
                                    senderAvatarUrl = msg.sender.profileImageUrl,
                                    isDeleted = msg.isDeleted,
                                    deletedForEveryone = msg.deletedForEveryone,
                                    prevIsSelf = prevIsSelf,
                                    nextIsSelf = nextIsSelf,
                                    displayContent = displayContent,
                                    formattedReactions = formattedReactions
                                )
                            }
                        }
                        processed.awaitAll()
                    }
                    _messageState.value = _messageState.value.copy(
                        isLoading = false,
                        messages = uiMessages
                    )

                    val latestIncoming = uiMessages
                        .filter { !it.isSelf }
                        .maxOfOrNull { it.timestamp } ?: 0L

                    val lastMarkedForChat = lastMarkedReadAtByChat[actualChatId] ?: 0L
                    if (latestIncoming > lastMarkedForChat) {
                        lastMarkedReadAtByChat[actualChatId] = latestIncoming
                        markChatAsReadUpToUseCase(actualChatId, latestIncoming)
                    }
                }
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled (user navigated away) — this is normal, don't log as error
                android.util.Log.d("ChatViewModel", "Chat loading cancelled (user navigated away)")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in openChat", e)
                _messageState.value = _messageState.value.copy(
                    isLoading = false,
                    error = "Error loading chat: ${e.message}"
                )
            }
        }
    }

    private fun isChatNotFoundError(error: Result.Error): Boolean {
        return error.code == com.linker.app.core.util.ErrorCodes.NOT_FOUND ||
            error.message.contains("Chat not found", ignoreCase = true)
    }

    fun loadMessageInfo(messageId: String) {
        if (messageId.isBlank()) return
        _messageInfoState.value = MessageInfoState(isLoading = true, messageId = messageId)

        viewModelScope.launch {
            when (val result = loadMessageInfoUseCase(messageId)) {
                is Result.Success -> {
                    val info = result.data
                    val replyId = info.message.replyToMessage?.messageId
                    val replyPreview = if (!replyId.isNullOrBlank()) {
                        try {
                            val repliedResult = getMessageByIdUseCase(replyId)
                            if (repliedResult is Result.Success) {
                                val replied = repliedResult.data
                                val repliedSenderId = replied.sender.userId
                                val name = when {
                                    repliedSenderId.isBlank() -> "User"
                                    repliedSenderId == currentUserId -> "You"
                                    else -> replied.sender.displayName
                                        .ifBlank { replied.sender.username }
                                        .ifBlank { resolveUserDisplayName(repliedSenderId) }
                                }
                                ReplyPreview(
                                    senderName = name,
                                    previewText = replied.content ?: "[Media]",
                                    isSelf = repliedSenderId == currentUserId
                                )
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                    _messageInfoState.value = MessageInfoState(
                        isLoading = false,
                        messageId = messageId,
                        replyToMessageId = replyId,
                        replyPreview = replyPreview,
                        content = info.message.content ?: "[Media]",
                        isSelf = info.message.sender.userId == currentUserId,
                        sentAt = info.message.createdAt,
                        deliveredAt = info.message.deliveredAt,
                        deliveredReceipts = info.deliveryReceipts.map { (userId, timestamp) ->
                            ParticipantReceiptInfo(
                                userId = userId,
                                userName = if (userId == currentUserId) "You" else resolveUserDisplayName(userId),
                                atMillis = timestamp,
                                avatarUrl = null
                            )
                        },
                        readAt = info.message.readAt,
                        reactions = info.reactions.map { (userId, emoji) ->
                            ReactionUserInfo(
                                userId = userId,
                                userName = if (userId == currentUserId) "You" else resolveUserDisplayName(userId),
                                avatarUrl = null,
                                emoji = emoji
                            )
                        },
                        readReceipts = info.readReceipts.map { (userId, timestamp) ->
                            ReadReceiptInfo(
                                userId = userId,
                                userName = if (userId == currentUserId) "You" else resolveUserDisplayName(userId),
                                readAt = timestamp,
                                avatarUrl = null
                            )
                        }
                    )
                }
                is Result.Error -> {
                    _messageInfoState.value = MessageInfoState(isLoading = false, messageId = messageId)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun loadMessageReactions(messageId: String, reactions: Map<String, String>) {
        if (messageId.isBlank()) return
        _messageReactionsState.value = MessageReactionsUiState(isLoading = true, messageId = messageId)

        viewModelScope.launch {
            try {
                val list = reactions.map { (userId, emoji) ->
                    val name = if (userId == currentUserId) "You" else resolveUserDisplayName(userId)
                    val avatarUrl = try {
                        val result = getUserByIdUseCase(userId)
                        if (result is Result.Success) result.data.profileImageUrl else null
                    } catch (_: Exception) { null }
                    ReactionUserInfo(
                        userId = userId,
                        userName = name,
                        avatarUrl = avatarUrl,
                        emoji = emoji
                    )
                }
                _messageReactionsState.value = MessageReactionsUiState(
                    isLoading = false,
                    messageId = messageId,
                    reactions = list
                )
            } catch (_: Exception) {
                _messageReactionsState.value = MessageReactionsUiState(isLoading = false, messageId = messageId)
            }
        }
    }

    fun sendMessage(content: String, replyToMessageId: String? = null) {
        val chatId = _messageState.value.chatId
        if (chatId.isBlank() || content.isBlank()) return

        // SECURITY: Validate message content
        val validationResult = InputValidator.validateMessageContent(content.trim())
        if (!validationResult.isValid) {
            _messageState.update { it.copy(sendError = validationResult.message) }
            return
        }

        viewModelScope.launch {
            _messageState.update { it.copy(isSending = true, sendError = null) }

            when (val result = sendMessageUseCase(
                chatId = chatId,
                messageType = MessageType.TEXT,
                content = content.trim(),
                replyToMessageId = replyToMessageId
            )) {
                is Result.Success -> {
                    _messageState.update { it.copy(isSending = false, sendError = null) }
                }
                is Result.Error -> {
                    _messageState.update { it.copy(isSending = false, sendError = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean = false) {
        android.util.Log.d("ChatViewModel", "deleteMessage called: messageId=$messageId, forEveryone=$forEveryone")
        viewModelScope.launch {
            val result = deleteMessageUseCase(messageId, forEveryone = forEveryone)
            android.util.Log.d("ChatViewModel", "deleteMessage result: $result")
        }
    }

    fun reactToMessage(messageId: String, emoji: String?) {
        viewModelScope.launch {
            reactToMessageUseCase(messageId, emoji)
        }
    }
}
