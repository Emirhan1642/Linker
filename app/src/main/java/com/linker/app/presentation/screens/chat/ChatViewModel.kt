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
import kotlinx.coroutines.launch
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
    val participantIds: List<String> = emptyList()
)

data class ChatMessageUiState(
    val isLoading: Boolean = true,
    val chatId: String = "",
    val recipientName: String = "User",
    val recipientImageUrl: String? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val error: String? = null
)

data class MessageUiModel(
    val messageId: String,
    val content: String?,
    val isSelf: Boolean,
    val timestamp: Long,
    val status: MessageStatus
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

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        observeChats()
        observeNotes()
    }

    // ── Chat List ──────────────────────────────────────────────────────────

    private fun observeChats() {
        viewModelScope.launch {
            chatRepository.observeChats().collect { chats ->
                val uiModels = chats.map { chat ->
                    val otherParticipant = chat.participants.firstOrNull { it.userId != currentUserId }
                    val displayName = chat.chatName
                        ?: otherParticipant?.displayName?.ifBlank { null }
                        ?: "Chat"

                    val resolvedName = if (displayName == "Chat" && otherParticipant != null) {
                        resolveUserDisplayName(otherParticipant.userId)
                    } else displayName

                    val lastMsgText = chatRepository.getChatLastMessageText(chat.chatId)
                    
                    ChatUiModel(
                        chatId = chat.chatId,
                        displayName = resolvedName,
                        imageUrl = otherParticipant?.profileImageUrl,
                        lastMessage = lastMsgText,
                        lastMessageTime = chat.updatedAt,
                        unreadCount = chat.unreadCount,
                        participantIds = chat.participants.map { it.userId }
                    )
                }
                _chatListState.value = _chatListState.value.copy(
                    isLoading = false,
                    chats = uiModels
                )
            }
        }
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

    // ── Messages ───────────────────────────────────────────────────────────

    fun openChat(chatId: String) {
        _messageState.value = ChatMessageUiState(isLoading = true, chatId = chatId)

        viewModelScope.launch {
            // Load chat info
            when (val chatResult = chatRepository.getChatById(chatId)) {
                is Result.Success -> {
                    val chat = chatResult.data
                    val otherUser = chat.participants.firstOrNull { it.userId != currentUserId }
                    val name = chat.chatName
                        ?: if (otherUser != null) resolveUserDisplayName(otherUser.userId) else "Chat"

                    _messageState.value = _messageState.value.copy(
                        recipientName = name,
                        recipientImageUrl = otherUser?.profileImageUrl
                    )
                }
                is Result.Error -> {
                    _messageState.value = _messageState.value.copy(error = chatResult.message)
                }
                is Result.Loading -> { /* no-op */ }
            }

            // Mark as read
            chatRepository.markChatAsRead(chatId)

            // Observe messages
            chatRepository.observeMessages(chatId).collect { messages ->
                val uiMessages = messages
                    .filter { !it.isDeleted }
                    .map { msg ->
                        MessageUiModel(
                            messageId = msg.messageId,
                            content = msg.content,
                            isSelf = msg.sender.userId == currentUserId,
                            timestamp = msg.createdAt,
                            status = msg.messageStatus
                        )
                    }
                _messageState.value = _messageState.value.copy(
                    isLoading = false,
                    messages = uiMessages
                )
            }
        }
    }

    fun sendMessage(content: String) {
        val chatId = _messageState.value.chatId
        if (chatId.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            chatRepository.sendMessage(
                chatId = chatId,
                messageType = MessageType.TEXT,
                content = content.trim()
            )
        }
    }
}
