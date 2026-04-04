package com.linker.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.core.util.Result
import com.linker.app.data.repository.ChatRepositoryImpl
import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.ChatType
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatInfoUiState(
    val isLoading: Boolean = true,
    val chat: Chat? = null,
    val chatName: String = "",
    val chatImageUrl: String? = null,
    val participants: List<User> = emptyList(),
    val otherParticipant: User? = null,
    val isGroupChat: Boolean = false,
    val sharedMedia: List<SharedMediaItem> = emptyList(),
    val sharedLinks: List<SharedLinkItem> = emptyList(),
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorited: Boolean = false,
    val theme: String? = null,
    val error: String? = null
)

data class SharedMediaItem(
    val mediaUrl: String,
    val mediaType: MediaType,
    val timestamp: Long
)

enum class MediaType { IMAGE, VIDEO, GIF }

data class SharedLinkItem(
    val linkId: String,
    val title: String,
    val thumbnailUrl: String?,
    val senderName: String,
    val timestamp: Long
)

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInfoUiState())
    val uiState: StateFlow<ChatInfoUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var currentChatId: String = ""

    fun loadChatInfo(chatId: String) {
        currentChatId = chatId
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            var actualChatId = chatId
            var chat: Chat? = null

            // Try to find existing chat
            when (val chatResult = chatRepository.getChatById(chatId)) {
                is Result.Success -> {
                    chat = chatResult.data
                }
                is Result.Error -> {
                    // Chat doesn't exist — treat chatId as recipientUserId and create new chat
                    when (val newChatResult = chatRepository.createPrivateChat(chatId)) {
                        is Result.Success -> {
                            actualChatId = newChatResult.data.chatId
                            chat = newChatResult.data
                            currentChatId = actualChatId
                        }
                        is Result.Error -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = newChatResult.message)
                            }
                            return@launch
                        }
                        is Result.Loading -> { return@launch }
                    }
                }
                is Result.Loading -> {}
            }

            if (chat == null) {
                _uiState.update { it.copy(isLoading = false, error = "Chat not found") }
                return@launch
            }

            val isGroup = chat.chatType == ChatType.GROUP
            val other = if (!isGroup) {
                chat.participants.firstOrNull { it.userId != currentUserId }
            } else null

            val displayName = if (isGroup) {
                chat.chatName ?: "Group Chat"
            } else {
                other?.displayName?.ifBlank { null }
                    ?: other?.username?.ifBlank { null }
                    ?: "User"
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    chat = chat,
                    chatName = displayName,
                    chatImageUrl = if (isGroup) chat.chatImageUrl else other?.profileImageUrl,
                    participants = chat.participants,
                    otherParticipant = other,
                    isGroupChat = isGroup,
                    isMuted = chat.isMuted,
                    isPinned = chat.isPinned,
                    isArchived = chat.isArchived,
                    isBlocked = chat.isBlocked,
                    isFavorited = chat.isFavorited,
                    theme = chat.theme
                )
            }

            loadSharedMedia(actualChatId)
        }
    }

    private fun loadSharedMedia(chatId: String) {
        chatRepository.observeMessages(chatId)
            .onEach { messages ->
                val mediaItems = messages
                    .filter { !it.isDeleted && it.mediaUrl != null }
                    .map { msg ->
                        SharedMediaItem(
                            mediaUrl = msg.mediaUrl!!,
                            mediaType = when (msg.messageType) {
                                com.linker.app.domain.model.MessageType.VIDEO -> MediaType.VIDEO
                                com.linker.app.domain.model.MessageType.GIF -> MediaType.GIF
                                else -> MediaType.IMAGE
                            },
                            timestamp = msg.createdAt
                        )
                    }
                    .sortedByDescending { it.timestamp }

                val linkItems = messages
                    .filter { !it.isDeleted && it.sharedLink != null }
                    .map { msg ->
                        SharedLinkItem(
                            linkId = msg.sharedLink!!.linkId,
                            title = msg.sharedLink!!.description ?: "Shared Link",
                            thumbnailUrl = msg.sharedLink!!.thumbnailUrl,
                            senderName = msg.sender.displayName.ifBlank { msg.sender.username },
                            timestamp = msg.createdAt
                        )
                    }
                    .sortedByDescending { it.timestamp }

                _uiState.update {
                    it.copy(
                        sharedMedia = mediaItems,
                        sharedLinks = linkItems
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun toggleMute() {
        val currentState = _uiState.value
        val newMuted = !currentState.isMuted
        viewModelScope.launch {
            chatRepository.updateChatSettings(
                chatId = currentChatId,
                isMuted = newMuted
            )
            _uiState.update { it.copy(isMuted = newMuted) }
        }
    }

    fun togglePin() {
        val currentState = _uiState.value
        val newPinned = !currentState.isPinned
        viewModelScope.launch {
            chatRepository.updateChatSettings(
                chatId = currentChatId,
                isPinned = newPinned
            )
            _uiState.update { it.copy(isPinned = newPinned) }
        }
    }

    fun toggleArchive() {
        val currentState = _uiState.value
        val newArcived = !currentState.isArchived
        viewModelScope.launch {
            chatRepository.updateChatSettings(
                chatId = currentChatId,
                isArchived = newArcived
            )
            _uiState.update { it.copy(isArchived = newArcived) }
        }
    }

    fun toggleBlock() {
        val currentState = _uiState.value
        val newBlocked = !currentState.isBlocked
        viewModelScope.launch {
            chatRepository.updateChatSettings(
                chatId = currentChatId,
                isBlocked = newBlocked
            )
            _uiState.update { it.copy(isBlocked = newBlocked) }
        }
    }

    fun toggleFavorite() {
        val currentState = _uiState.value
        val newFavorited = !currentState.isFavorited
        viewModelScope.launch {
            chatRepository.updateChatSettings(
                chatId = currentChatId,
                isFavorited = newFavorited
            )
            _uiState.update { it.copy(isFavorited = newFavorited) }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.markChatAsRead(currentChatId)
        }
    }
}
