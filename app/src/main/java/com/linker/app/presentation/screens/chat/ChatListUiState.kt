package com.linker.app.presentation.screens.chat

import com.linker.app.domain.model.Note

data class ChatListUiState(
    val isLoading: Boolean = true,
    val chats: List<ChatUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "All",
    val userNote: Note? = null,
    val otherNotes: List<Note> = emptyList(),
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
    val isBlocked: Boolean = false,
    val formattedTime: String = ""
)
