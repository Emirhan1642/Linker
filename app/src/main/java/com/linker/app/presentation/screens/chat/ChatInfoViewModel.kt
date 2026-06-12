package com.linker.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.R
import com.linker.app.core.util.Result
import com.linker.app.core.util.UiText
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.ChatSettingsRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.ChatType
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatBasicInfo(
    val chat: Chat? = null,
    val chatName: String = "",
    val chatSubtitle: String? = null,
    val chatImageUrl: String? = null,
    val participants: List<User> = emptyList(),
    val otherParticipant: User? = null,
    val isGroupChat: Boolean = false,
    val canManageGroup: Boolean = false,
    val groupAdminIds: List<String> = emptyList(),
    val groupCreatedBy: String? = null
)

data class ChatSettingsState(
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorited: Boolean = false,
    val theme: String? = null
)

data class SharedMediaState(
    val sharedMedia: List<SharedMediaItem> = emptyList(),
    val sharedLinks: List<SharedLinkItem> = emptyList()
)

data class ChatInfoUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val basicInfo: ChatBasicInfo = ChatBasicInfo(),
    val settings: ChatSettingsState = ChatSettingsState(),
    val sharedMediaState: SharedMediaState = SharedMediaState(),
    val feedbackMessage: UiText? = null,
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
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val messageRepository: MessageRepository
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
            when (val chatResult = chatRepository.getChatById(chatId)) {
                is Result.Success -> {
                    processChatData(chatResult.data, chatId)
                }
                is Result.Error -> {
                    if (!isChatNotFoundError(chatResult)) {
                        _uiState.update {
                            it.copy(isLoading = false, error = UiText.DynamicString(chatResult.message))
                        }
                        return@launch
                    }
                    // Chat not found, try creating it
                    createAndLoadPrivateChat(chatId)
                }
                is Result.Loading -> return@launch
            }
        }
    }

    private suspend fun createAndLoadPrivateChat(recipientId: String) {
        when (val newChatResult = chatRepository.createPrivateChat(recipientId)) {
            is Result.Success -> {
                currentChatId = newChatResult.data.chatId
                processChatData(newChatResult.data, currentChatId)
            }
            is Result.Error -> {
                _uiState.update {
                    it.copy(isLoading = false, error = UiText.DynamicString(newChatResult.message))
                }
            }
            is Result.Loading -> {}
        }
    }

    private fun processChatData(chat: Chat, actualChatId: String) {
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

        val chatSubtitle = if (isGroup) {
            "${chat.participants.size} members"
        } else {
            other?.username?.let { "@$it" }
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                basicInfo = ChatBasicInfo(
                    chat = chat,
                    chatName = displayName,
                    chatSubtitle = chatSubtitle,
                    chatImageUrl = if (isGroup) chat.chatImageUrl else other?.profileImageUrl,
                    participants = chat.participants,
                    otherParticipant = other,
                    isGroupChat = isGroup,
                    canManageGroup = canManageGroup,
                    groupAdminIds = adminIds,
                    groupCreatedBy = createdBy
                ),
                settings = ChatSettingsState(
                    isMuted = chat.isMuted,
                    isPinned = chat.isPinned,
                    isArchived = chat.isArchived,
                    isBlocked = chat.isBlocked,
                    isFavorited = chat.isFavorited,
                    theme = chat.theme
                )
            )
        }

        loadSharedMedia(actualChatId)
    }

    private fun loadSharedMedia(chatId: String) {
        mediaObserverJob?.cancel()
        mediaObserverJob = messageRepository.observeMessages(chatId)
            .onEach { result ->
                if (result is Result.Success) {
                    val messages = result.data
                    
                    viewModelScope.launch(Dispatchers.Default) {
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
                                        thumbnailUrl = link.mediaItems.filterIsInstance<com.linker.app.domain.model.MediaItem.Video>().firstOrNull()?.thumbnailUrl,
                                        senderName = msg.sender.displayName.ifBlank { msg.sender.username },
                                        timestamp = msg.createdAt
                                    )
                                }
                            }
                            .sortedByDescending { it.timestamp }

                        _uiState.update {
                            it.copy(
                                sharedMediaState = SharedMediaState(
                                    sharedMedia = mediaItems,
                                    sharedLinks = linkItems
                                )
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun isChatNotFoundError(error: Result.Error): Boolean {
        return error.code == com.linker.app.core.util.ErrorCodes.NOT_FOUND ||
            error.message.contains("Chat not found", ignoreCase = true)
    }

    fun toggleMute() {
        val currentState = _uiState.value.settings
        val newMuted = !currentState.isMuted
        viewModelScope.launch {
            chatSettingsRepository.setMuted(chatId = currentChatId, isMuted = newMuted)
            _uiState.update { it.copy(settings = it.settings.copy(isMuted = newMuted)) }
        }
    }

    fun togglePin() {
        val currentState = _uiState.value.settings
        val newPinned = !currentState.isPinned
        viewModelScope.launch {
            chatSettingsRepository.setPinned(chatId = currentChatId, isPinned = newPinned)
            _uiState.update { it.copy(settings = it.settings.copy(isPinned = newPinned)) }
        }
    }

    fun toggleArchive() {
        val currentState = _uiState.value.settings
        val newArcived = !currentState.isArchived
        viewModelScope.launch {
            chatSettingsRepository.setArchived(chatId = currentChatId, isArchived = newArcived)
            _uiState.update { it.copy(settings = it.settings.copy(isArchived = newArcived)) }
        }
    }

    fun toggleBlock() {
        val currentState = _uiState.value.settings
        val newBlocked = !currentState.isBlocked
        viewModelScope.launch {
            chatSettingsRepository.setBlocked(chatId = currentChatId, isBlocked = newBlocked)
            _uiState.update { it.copy(settings = it.settings.copy(isBlocked = newBlocked)) }
        }
    }

    fun toggleFavorite() {
        // chatSettingsRepository doesn't have setFavorited, skipping for now
    }

    fun clearChat() {
        viewModelScope.launch {
            messageRepository.markChatAsRead(currentChatId)
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
            when (val r = chatSettingsRepository.promoteToAdmin(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = UiText.StringResource(R.string.chat_info_make_admin)) }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = UiText.DynamicString(r.message)) }
                is Result.Loading -> {}
            }
        }
    }

    fun demoteMember(userId: String) {
        viewModelScope.launch {
            when (val r = chatSettingsRepository.demoteAdmin(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = UiText.StringResource(R.string.chat_info_remove_admin_role)) }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = UiText.DynamicString(r.message)) }
                is Result.Loading -> {}
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            when (val r = chatSettingsRepository.removeParticipant(currentChatId, userId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = UiText.StringResource(R.string.chat_info_remove_from_group)) }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = UiText.DynamicString(r.message)) }
                is Result.Loading -> {}
            }
        }
    }

    fun leaveGroup(removeFromList: Boolean) {
        viewModelScope.launch {
            if (removeFromList) {
                chatSettingsRepository.setArchived(chatId = currentChatId, isArchived = true)
            }
            when (val r = chatSettingsRepository.leaveGroupChat(currentChatId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            feedbackMessage = UiText.StringResource(R.string.chat_info_leave_group),
                            shouldCloseScreen = true
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = UiText.DynamicString(r.message)) }
                is Result.Loading -> {}
            }
        }
    }

    fun updateGroupName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val r = chatSettingsRepository.updateGroupProfile(currentChatId, trimmed, null)) {
                is Result.Success -> {
                    _uiState.update { it.copy(feedbackMessage = UiText.StringResource(R.string.chat_info_save)) }
                    loadChatInfo(currentChatId)
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = UiText.DynamicString(r.message)) }
                is Result.Loading -> {}
            }
        }
    }
}
