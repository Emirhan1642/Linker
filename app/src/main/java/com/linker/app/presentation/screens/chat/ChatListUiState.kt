package com.linker.app.presentation.screens.chat

import androidx.compose.runtime.Immutable
import com.linker.app.domain.model.Note

@Immutable
data class ChatListUiState(
    val isLoading: Boolean = true,
    val chats: List<ChatUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "All",
    val userNote: Note? = null,
    val otherNotes: List<Note> = emptyList(),
    val onlineUsers: List<com.linker.app.domain.model.User> = emptyList(),
    val suggestedUsers: List<com.linker.app.domain.model.User> = emptyList(),
    val error: String? = null
)

/** Lightweight model for the chat list screen. */
@Immutable
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
    val isBlocked: Boolean = false,
    val formattedTime: String = ""
)
