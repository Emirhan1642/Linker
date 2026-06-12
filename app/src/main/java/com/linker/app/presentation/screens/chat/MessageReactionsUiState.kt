package com.linker.app.presentation.screens.chat

data class MessageReactionsUiState(
    val isLoading: Boolean = false,
    val messageId: String = "",
    val reactions: List<ReactionUserInfo> = emptyList()
)

data class ReactionInfo(
    val userName: String,
    val emoji: String
)

data class ReactionUserInfo(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val emoji: String
)
