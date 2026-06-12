package com.linker.app.presentation.screens.chat

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

data class ReadReceiptInfo(
    val userId: String,
    val userName: String,
    val readAt: Long,
    val avatarUrl: String?
)
