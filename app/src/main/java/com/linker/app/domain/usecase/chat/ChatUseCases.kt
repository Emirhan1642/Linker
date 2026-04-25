package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ─── Observe Chats ────────────────────────────────────────────────────────────

class ObserveChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Chat>> = chatRepository.observeChats()
}

// ─── Observe Unread Count ─────────────────────────────────────────────────────

class ObserveTotalUnreadUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<Int> = chatRepository.observeTotalUnread()
}

// ─── Observe Messages ─────────────────────────────────────────────────────────

class ObserveMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(chatId: String): Flow<List<Message>> =
        chatRepository.observeMessages(chatId)
}

// ─── Send Message ─────────────────────────────────────────────────────────────

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        chatId: String,
        content: String,
        messageType: MessageType = MessageType.TEXT,
        mediaLocalPath: String? = null,
        replyToMessageId: String? = null
    ): Result<Message> {
        if (messageType == MessageType.TEXT && content.isBlank())
            return Result.Error("Message cannot be empty")
        return chatRepository.sendMessage(
            chatId, messageType, content, mediaLocalPath, replyToMessageId
        )
    }
}

// ─── Create Private Chat ──────────────────────────────────────────────────────

class CreatePrivateChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(recipientUserId: String): Result<Chat> =
        chatRepository.createPrivateChat(recipientUserId)
}

// ─── Create Group Chat ────────────────────────────────────────────────────────

class CreateGroupChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        name: String,
        participantIds: List<String>,
        permissions: Map<String, Any>? = null
    ): Result<Chat> {
        if (name.isBlank()) return Result.Error("Group name cannot be empty")
        if (participantIds.size < 2) return Result.Error("A group needs at least 2 other participants")
        return chatRepository.createGroupChat(name, participantIds, permissions)
    }
}

// ─── Edit Message ─────────────────────────────────────────────────────────────

class EditMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String, newContent: String): Result<Unit> {
        if (newContent.isBlank()) return Result.Error("Edited message cannot be empty")
        return chatRepository.editMessage(messageId, newContent)
    }
}

// ─── Delete Message ───────────────────────────────────────────────────────────

class DeleteMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        messageId: String,
        forEveryone: Boolean = false
    ): Result<Unit> = chatRepository.deleteMessage(messageId, forEveryone)
}

// ─── React to Message ─────────────────────────────────────────────────────────

class ReactToMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String, emoji: String?): Result<Unit> =
        chatRepository.reactToMessage(messageId, emoji)
}

// ─── Mark Chat as Read ────────────────────────────────────────────────────────

class MarkChatAsReadUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Unit> =
        chatRepository.markChatAsRead(chatId)
}

// ─── Mark Chat as Read Up To ──────────────────────────────────────────────────

class MarkChatAsReadUpToUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String, upToTimestamp: Long): Result<Unit> =
        chatRepository.markChatAsReadUpTo(chatId, upToTimestamp)
}

// ─── Search Messages ──────────────────────────────────────────────────────────

class SearchMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String, query: String): Result<List<Message>> {
        if (query.isBlank()) return Result.Success(emptyList())
        return chatRepository.searchMessages(chatId, query)
    }
}

// ─── Observe Queued Messages ──────────────────────────────────────────────────

class ObserveQueuedMessageCountUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<Int> = chatRepository.observeQueuedMessageCount()
}

// ─── Retry Failed Messages (BLE / Wi-Fi Direct) ───────────────────────────────

class RetryFailedMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        preferredMethod: DeliveryMethod = DeliveryMethod.BLE
    ): Result<Unit> = chatRepository.retryFailedMessages(preferredMethod)
}

// ─── Get Chat By Id ───────────────────────────────────────────────────────────

class GetChatByIdUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Chat> =
        chatRepository.getChatById(chatId)
}

// ─── Get Message By Id ────────────────────────────────────────────────────────

class GetMessageByIdUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String): Message =
        chatRepository.getMessageById(messageId)
}
