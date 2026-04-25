package com.linker.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.ChatRepository
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
import kotlinx.coroutines.Job
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
    val error: String? = null,
    val canManageGroup: Boolean = false,
    val groupAdminIds: List<String> = emptyList(),
    val groupCreatedBy: String? = null,
    val feedbackMessage: String? = null,
    val shouldCloseScreen: Boolean = false
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
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInfoUiState())
    val uiState: StateFlow<ChatInfoUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var currentChatId: String = ""
    private var mediaObserverJob: Job? = null

    override fun onCleared() {
        mediaObserverJob?.cancel()
        super.onCleared()
    }

    fun loadChatInfo(chatId: String) {
        val chatIdChanged = chatId != currentChatId
        currentChatId = chatId
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                feedbackMessage = if (chatIdChanged) null else it.feedbackMessage
            )
        }

        viewModelScope.launch {
            var actualChatId = chatId
            var chat: Chat? = null

            // Try to find existing chat
            when (val chatResult = chatRepository.getChatById(chatId)) {
                is Result.Success -> {
                    chat = chatResult.data
                }
                is Result.Error -> {
                    if (!isChatNotFoundError(chatResult)) {
                        _uiState.update {
                            it.copy(isLoading = false, error = chatResult.message)
                        }
                        return@launch
                    }
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
                is Result.Loading -> return@launch
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

            val adminIds = chat.groupAdminIds
            val createdBy = chat.groupCreatedBy
            val canManageGroup = isGroup && (
                adminIds.contains(currentUserId) ||
                    (adminIds.isEmpty() && createdBy == currentUserId)
                )

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
                    theme = chat.theme,
                    canManageGroup = canManageGroup,
                    groupAdminIds = adminIds,
                    groupCreatedBy = createdBy
                )
            }

            loadSharedMedia(actualChatId)
        }
    }

    private fun loadSharedMedia(chatId: String) {
        mediaObserverJob?.cancel()
        mediaObserverJob = chatRepository.observeMessages(chatId)
            .onEach { messages ->
                val mediaItems = messages
                    .filter { !it.isDeleted && it.mediaUrl != null }
                    .mapNotNull { msg ->
                        msg.mediaUrl?.let { url ->
                            SharedMediaItem(
                                mediaUrl = url,
                                mediaType = when (msg.messageType) {
                                    com.linker.app.domain.model.MessageType.VIDEO -> MediaType.VIDEO
                                    com.linker.app.domain.model.MessageType.GIF -> MediaType.GIF
                                    else -> MediaType.IMAGE
                                },
                                timestamp = msg.createdAt
                            )
                        }
                    }
                    .sortedByDescending { it.timestamp }

                val linkItems = messages
                    .filter { !it.isDeleted && it.sharedLink != null }
                    .mapNotNull { msg ->
                        msg.sharedLink?.let { link ->
                            SharedLinkItem(
                                linkId = link.linkId,
                                title = link.description ?: "Shared Link",
                                thumbnailUrl = link.thumbnailUrl,
                                senderName = msg.sender.displayName.ifBlank { msg.sender.username },
                                timestamp = msg.createdAt
                            )
                        }
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

    private fun isChatNotFoundError(error: Result.Error): Boolean {
        return error.code == com.linker.app.core.util.ErrorCodes.NOT_FOUND ||
            error.message.contains("Chat not found", ignoreCase = true)
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

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun consumeCloseScreen() {
        _uiState.update { it.copy(shouldCloseScreen = false) }
    }

    fun promoteMember(userId: String) {
        viewModelScope.launch {
            when (val r = chatRepository.promoteGroupAdmin(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = "Added as admin") }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = r.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun demoteMember(userId: String) {
        viewModelScope.launch {
            when (val r = chatRepository.demoteGroupAdmin(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = "Admin role removed") }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = r.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            when (val r = chatRepository.removeGroupMember(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = "Removed from group") }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = r.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun leaveGroup(removeFromList: Boolean) {
        viewModelScope.launch {
            if (removeFromList) {
                // Archive before leaving so it disappears from local list immediately.
                chatRepository.updateChatSettings(
                    chatId = currentChatId,
                    isArchived = true
                )
            }
            when (val r = chatRepository.leaveGroup(currentChatId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            feedbackMessage = "You left the group",
                            shouldCloseScreen = true
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = r.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun updateGroupName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val r = chatRepository.updateGroupProfile(currentChatId, trimmed, null)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = "Group name updated") }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = r.message) }
                is Result.Loading -> {}
            }
        }
    }
}
