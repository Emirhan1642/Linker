package com.linker.app.presentation.screens.chat

import androidx.compose.runtime.Immutable
import com.linker.app.domain.model.MessageStatus

@Immutable
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
    val sendError: String? = null,
    /** false when group permissions restrict sending to admins only and the current user is not admin */
    val canSendMessages: Boolean = true
)

@Immutable
data class MessageUiModel(
    val messageId: String,
    val chatId: String = "",
    val content: String?,
    val isSelf: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val replyToNote: com.linker.app.domain.model.NoteReference? = null,
    val readAt: Long? = null,
    val reactions: Map<String, String> = emptyMap(),
    val readReceipts: Map<String, Long> = emptyMap(),
    val seenByUsers: List<SeenByUserUi> = emptyList(),
    val senderId: String = "",
    val senderDisplayName: String = "User",
    val senderAvatarUrl: String? = null,
    val isDeleted: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val prevIsSelf: Boolean = false,
    val nextIsSelf: Boolean = false,
    val displayContent: String = "",
    val formattedReactions: List<String> = emptyList()
)

@Immutable
data class SeenByUserUi(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val seenAt: Long
)

@Immutable
data class MessageItem(
    val text: String,
    val isSelf: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val prevIsSelf: Boolean = false,
    val nextIsSelf: Boolean = false,
    val isDeleted: Boolean = false,
    val replyToNote: com.linker.app.domain.model.NoteReference? = null
)
