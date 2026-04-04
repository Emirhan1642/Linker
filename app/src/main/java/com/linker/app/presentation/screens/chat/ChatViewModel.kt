package com.linker.app.presentation.screens.chat


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.data.repository.ChatRepositoryImpl
import com.linker.app.data.repository.NoteRepositoryImpl
import com.linker.app.domain.model.*
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = true,
    val chats: List<ChatUiModel> = emptyList(),
    val notes: List<Note> = emptyList(),
    val error: String? = null
)

/** Lightweight model for the chat list screen. */
data class ChatUiModel(
    val chatId: String,
    val displayName: String,
    val imageUrl: String?,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isTyping: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val isGroupChat: Boolean = false,
    val isPinned: Boolean = false,
    val isFavorited: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false
)

data class ChatMessageUiState(
    val isLoading: Boolean = true,
    val chatId: String = "",
    val recipientId: String = "",
    val isGroupChat: Boolean = false,
    val recipientName: String = "User",
    val recipientUsername: String = "",
    val recipientImageUrl: String? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val error: String? = null,
    val isSending: Boolean = false,
    val sendError: String? = null
)

data class MessageUiModel(
    val messageId: String,
    val content: String?,
    val isSelf: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val readAt: Long? = null,
    val reactions: Map<String, String> = emptyMap(),
    val readReceipts: Map<String, Long> = emptyMap()
)

data class MessageInfoUiState(
    val isLoading: Boolean = false,
    val messageId: String = "",
    val replyToMessageId: String? = null,
    val content: String = "",
    val isSelf: Boolean = false,
    val fromTo: String = "",
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val failedAt: Long? = null,
    val replies: List<ReplyInfo> = emptyList(),
    val reactions: List<ReactionInfo> = emptyList(),
    val readReceipts: List<ReadReceiptInfo> = emptyList()
)

data class ReplyInfo(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val preview: String,
    val avatarUrl: String?
)

data class ReactionInfo(
    val userName: String,
    val emoji: String
)

data class ReadReceiptInfo(
    val userName: String,
    val readAt: Long
)

data class ReactionUserInfo(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val emoji: String
)

data class MessageReactionsUiState(
    val isLoading: Boolean = false,
    val messageId: String = "",
    val reactions: List<ReactionUserInfo> = emptyList()
)



@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepositoryImpl,
    private val noteRepository: NoteRepositoryImpl,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _chatListState = MutableStateFlow(ChatListUiState())
    val chatListState: StateFlow<ChatListUiState> = _chatListState.asStateFlow()

    private val _messageState = MutableStateFlow(ChatMessageUiState())
    val messageState: StateFlow<ChatMessageUiState> = _messageState.asStateFlow()

    private val _messageInfoState = MutableStateFlow(MessageInfoUiState())
    val messageInfoState: StateFlow<MessageInfoUiState> = _messageInfoState.asStateFlow()

    private val _messageReactionsState = MutableStateFlow(MessageReactionsUiState())
    val messageReactionsState: StateFlow<MessageReactionsUiState> = _messageReactionsState.asStateFlow()

    private var lastMarkedReadAt: Long = 0L
    private var chatsJob: Job? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        observeChats()
        observeNotes()
        authListener = FirebaseAuth.AuthStateListener {
            // Reset and reload chats when account changes
            chatsJob?.cancel()
            _chatListState.value = ChatListUiState(isLoading = true, chats = emptyList(), notes = _chatListState.value.notes)
            observeChats()
        }
        auth.addAuthStateListener(authListener!!)
    }

    // ── Chat List ──────────────────────────────────────────────────────────

    private fun observeChats() {
        chatsJob = viewModelScope.launch {
            chatRepository.observeChats().collect { chats ->
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

                    val lastMsgText = chatRepository.getChatLastMessageText(chat.chatId)
                    
                    ChatUiModel(
                        chatId = chat.chatId,
                        displayName = resolvedName,
                        imageUrl = if (isGroup) chat.chatImageUrl else otherParticipant?.profileImageUrl,
                        lastMessage = lastMsgText,
                        lastMessageTime = chat.updatedAt,
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
                _chatListState.value = _chatListState.value.copy(
                    isLoading = false,
                    chats = uiModels
                )
            }
        }
    }

    override fun onCleared() {
        authListener?.let { auth.removeAuthStateListener(it) }
        super.onCleared()
    }

    private suspend fun resolveUserDisplayName(userId: String): String {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getString("displayName") ?: doc.getString("username") ?: "User"
        } catch (_: Exception) {
            "User"
        }
    }

    // ── Notes ──────────────────────────────────────────────────────────────

    private fun observeNotes() {
        viewModelScope.launch {
            noteRepository.observeActiveNotes().collect { notes ->
                _chatListState.value = _chatListState.value.copy(notes = notes)
            }
        }
    }

    fun postNote(content: String) {
        viewModelScope.launch {
            noteRepository.postNote(content)
        }
    }

    suspend fun createPrivateChat(recipientUserId: String): Result<Chat> {
        return chatRepository.createPrivateChat(recipientUserId)
    }

    suspend fun createGroupChat(
        name: String,
        participantIds: List<String>,
        permissions: Map<String, Any>? = null
    ): Result<Chat> {
        return chatRepository.createGroupChat(name, participantIds, permissions)
    }

    // ── Messages ───────────────────────────────────────────────────────────

    fun openChat(chatId: String) {
        _messageState.value = ChatMessageUiState(isLoading = true, chatId = chatId)

        viewModelScope.launch {
            // Try to find existing chat
            val chatResult = chatRepository.getChatById(chatId)

            val actualChatId: String
            val actualChat: Chat?

            if (chatResult is Result.Success) {
                actualChatId = chatId
                actualChat = chatResult.data
            } else {
                // Chat doesn't exist — treat chatId as recipientUserId and create new chat
                val recipientUserId = chatId
                when (val newChatResult = chatRepository.createPrivateChat(recipientUserId)) {
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

            val chat = actualChat!!
            val isGroup = chat.chatType == ChatType.GROUP
            val otherUser = if (!isGroup) {
                chat.participants.firstOrNull { it.userId != currentUserId }
            } else null
            val name = if (isGroup) {
                chat.chatName ?: "Group Chat"
            } else if (otherUser != null) {
                resolveUserDisplayName(otherUser.userId)
            } else "Chat"

            _messageState.value = _messageState.value.copy(
                recipientId = otherUser?.userId ?: "",
                isGroupChat = isGroup,
                recipientName = name,
                recipientUsername = otherUser?.username ?: "",
                recipientImageUrl = if (isGroup) chat.chatImageUrl else otherUser?.profileImageUrl
            )

            // Mark as read
            chatRepository.markChatAsRead(actualChatId)

            // Observe messages
            chatRepository.observeMessages(actualChatId).collect { messages ->
                val uiMessages = messages
                    .filter { !it.isDeleted }
                    .map { msg ->
                        MessageUiModel(
                            messageId = msg.messageId,
                            content = msg.content,
                            isSelf = msg.sender.userId == currentUserId,
                            timestamp = msg.createdAt,
                            status = msg.messageStatus,
                            replyToMessageId = msg.replyToMessage?.messageId,
                            readAt = msg.readAt,
                            reactions = msg.reactions,
                            readReceipts = emptyMap()
                        )
                    }
                _messageState.value = _messageState.value.copy(
                    isLoading = false,
                    messages = uiMessages
                )

                val latestIncoming = uiMessages
                    .filter { !it.isSelf }
                    .maxOfOrNull { it.timestamp } ?: 0L

                if (latestIncoming > lastMarkedReadAt) {
                    lastMarkedReadAt = latestIncoming
                    chatRepository.markChatAsReadUpTo(actualChatId, latestIncoming)
                }
            }
        }
    }

    fun loadMessageInfo(messageId: String) {
        if (messageId.isBlank()) return
        _messageInfoState.value = MessageInfoUiState(isLoading = true, messageId = messageId)

        viewModelScope.launch {
            try {
                val doc = firestore.collection("messages").document(messageId).get().await()
                val data = doc.data ?: run {
                    _messageInfoState.value = MessageInfoUiState(isLoading = false)
                    return@launch
                }

                val senderId = data["senderId"] as? String ?: ""
                val isSelf = senderId == currentUserId
                val replyToMessageId = data["replyToMessageId"] as? String
                val content = data["content"] as? String ?: "[Media]"
                val createdAt = (data["createdAt"] as? Number)?.toLong()
                val deliveredAt = (data["deliveredAt"] as? Number)?.toLong()
                val readAt = (data["readAt"] as? Number)?.toLong()
                val statusStr = data["messageStatus"] as? String ?: "SENT"
                val isFailed = statusStr == "FAILED"
                val failedAt = if (isFailed) (data["updatedAt"] as? Number)?.toLong() else null

                val senderName = if (senderId == currentUserId) {
                    "You"
                } else {
                    resolveUserDisplayName(senderId)
                }
                val toName = if (_messageState.value.recipientName.isNotBlank()) _messageState.value.recipientName else "Chat"
                val fromTo = if (senderId == currentUserId) {
                    "You \u2192 $toName"
                } else {
                    "$senderName \u2192 You"
                }

                val reactionsMap = (data["reactions"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val userId = k as? String ?: return@mapNotNull null
                    val emoji = v as? String ?: return@mapNotNull null
                    userId to emoji
                }?.toMap() ?: emptyMap()

                val reactions = reactionsMap.map { (userId, emoji) ->
                    val name = if (userId == currentUserId) "You" else resolveUserDisplayName(userId)
                    ReactionInfo(userName = name, emoji = emoji)
                }

                val readReceiptsMap = (data["readReceipts"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val userId = k as? String ?: return@mapNotNull null
                    val ts = (v as? Number)?.toLong() ?: return@mapNotNull null
                    userId to ts
                }?.toMap() ?: emptyMap()

                val readReceipts = readReceiptsMap.map { (userId, ts) ->
                    val name = if (userId == currentUserId) "You" else resolveUserDisplayName(userId)
                    ReadReceiptInfo(userName = name, readAt = ts)
                }

                val repliesSnapshot = firestore.collection("messages")
                    .whereEqualTo("replyToMessageId", messageId)
                    .get()
                    .await()

                val replies = repliesSnapshot.documents.mapNotNull { replyDoc ->
                    val replyData = replyDoc.data ?: return@mapNotNull null
                    val replySenderId = replyData["senderId"] as? String ?: ""
                    val replySenderName = if (replySenderId == currentUserId) {
                        "You"
                    } else {
                        resolveUserDisplayName(replySenderId)
                    }
                    val replyAvatar = try {
                        firestore.collection("users").document(replySenderId).get().await().getString("profileImageUrl")
                    } catch (_: Exception) { null }
                    val preview = (replyData["content"] as? String)?.take(80) ?: "[Media]"
                    ReplyInfo(
                        messageId = replyDoc.id,
                        senderId = replySenderId,
                        senderName = replySenderName,
                        preview = preview,
                        avatarUrl = replyAvatar
                    )
                }

                _messageInfoState.value = MessageInfoUiState(
                    isLoading = false,
                    messageId = messageId,
                    replyToMessageId = replyToMessageId,
                    content = content,
                    isSelf = isSelf,
                    fromTo = fromTo,
                    sentAt = createdAt,
                    deliveredAt = deliveredAt,
                    readAt = readAt,
                    failedAt = failedAt,
                    replies = replies,
                    reactions = reactions,
                    readReceipts = readReceipts
                )
            } catch (_: Exception) {
                _messageInfoState.value = MessageInfoUiState(isLoading = false)
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
                        firestore.collection("users").document(userId).get().await().getString("profileImageUrl")
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

        viewModelScope.launch {
            _messageState.update { it.copy(isSending = true, sendError = null) }

            when (val result = chatRepository.sendMessage(
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

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId, forEveryone = false)
        }
    }

    fun reactToMessage(messageId: String, emoji: String?) {
        viewModelScope.launch {
            chatRepository.reactToMessage(messageId, emoji)
        }
    }
}
