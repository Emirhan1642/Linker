package com.linker.app.presentation.screens.chat

import com.linker.app.domain.model.MessageStatus
import com.linker.app.domain.model.Note

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
    val sendError: String? = null,
    /** false when group permissions restrict sending to admins only and the current user is not admin */
    val canSendMessages: Boolean = true
)

data class MessageUiModel(
    val messageId: String,
    val chatId: String = "",
    val content: String?,
    val isSelf: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val readAt: Long? = null,
    val reactions: Map<String, String> = emptyMap(),
    val readReceipts: Map<String, Long> = emptyMap(),
    val seenByUsers: List<SeenByUserUi> = emptyList(),
    val senderId: String = "",
    val senderDisplayName: String = "User",
    val senderAvatarUrl: String? = null,
    val isDeleted: Boolean = false,
    val deletedForEveryone: Boolean = false
)

data class SeenByUserUi(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val seenAt: Long
)

data class MessageInfoState(
    val isLoading: Boolean = false,
    val messageId: String = "",
    val replyToMessageId: String? = null,
    val replyPreview: ReplyPreview? = null,
    val content: String = "",
    val isSelf: Boolean = false,
    val fromTo: String = "",
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val deliveredReceipts: List<ParticipantReceiptInfo> = emptyList(),
    val readAt: Long? = null,
    val failedAt: Long? = null,
    val replies: List<ReplyInfo> = emptyList(),
    val reactions: List<ReactionUserInfo> = emptyList(),
    val readReceipts: List<ReadReceiptInfo> = emptyList()
)

data class ParticipantReceiptInfo(
    val userId: String,
    val userName: String,
    val atMillis: Long,
    val avatarUrl: String?
)

data class ReplyInfo(
    val messageId: String,
    val senderId: String,
    val preview: String,
    val avatarUrl: String?
)

data class ReplyPreview(
    val senderName: String,
    val previewText: String,
    val isSelf: Boolean
)

data class MessageItem(
    val text: String,
    val isSelf: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val prevIsSelf: Boolean = false,
    val nextIsSelf: Boolean = false,
    val isDeleted: Boolean = false
)

data class ReactionInfo(
    val userName: String,
    val emoji: String
)

data class ReadReceiptInfo(
    val userId: String,
    val userName: String,
    val readAt: Long,
    val avatarUrl: String?
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
