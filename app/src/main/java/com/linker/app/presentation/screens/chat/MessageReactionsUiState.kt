package com.linker.app.presentation.screens.chat

import androidx.compose.runtime.Immutable

@Immutable
data class MessageReactionsUiState(
    val isLoading: Boolean = false,
    val messageId: String = "",
    val reactions: List<ReactionUserInfo> = emptyList()
)

@Immutable
data class ReactionInfo(
    val userName: String,
    val emoji: String
)

@Immutable
data class ReactionUserInfo(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val emoji: String
)
