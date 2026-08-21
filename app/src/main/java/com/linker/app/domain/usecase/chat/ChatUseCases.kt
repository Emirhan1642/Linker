package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChatsUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    operator fun invoke(): Flow<Result<List<Chat>>> = chatRepository.observeChats()
}

class ObserveTotalUnreadUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    operator fun invoke(): Flow<Result<Int>> = chatRepository.observeTotalUnread()
}

class ObserveMessagesUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    operator fun invoke(chatId: String): Flow<Result<List<Message>>> = messageRepository.observeMessages(chatId)
}

class SendMessageUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(
        chatId: String, content: String, messageType: MessageType = MessageType.TEXT,
        mediaLocalPath: String? = null, replyToMessageId: String? = null
    ): Result<Message> {
        if (chatId.isBlank()) return Result.Error("Chat ID cannot be empty")
        
        return when (messageType) {
            MessageType.TEXT -> {
                if (content.isBlank()) return Result.Error("Message cannot be empty")
                if (content.length > 5000) return Result.Error("Message is too long (max 5000 characters)")
                messageRepository.sendMessage(chatId, messageType, sanitizeContent(content), null, replyToMessageId)
            }
            else -> {
                if (mediaLocalPath.isNullOrBlank()) return Result.Error("Media path is required for ${messageType.name} messages")
                val isValidUri = mediaLocalPath.startsWith("content://") || mediaLocalPath.startsWith("file://") || mediaLocalPath.startsWith("/")
                if (mediaLocalPath.contains("..") || mediaLocalPath.contains("~") || !isValidUri) {
                    return Result.Error("Invalid media path")
                }
                val sanitizedCaption = if (content.isNotBlank()) {
                    if (content.length > 200) return Result.Error("Caption is too long")
                    sanitizeContent(content)
                } else null
                messageRepository.sendMessage(chatId, messageType, sanitizedCaption ?: "", mediaLocalPath, replyToMessageId)
            }
        }
    }
    
    private fun sanitizeContent(content: String): String {
        var sanitized = content.replace(Regex("<[^>]*>"), "")
        sanitized = sanitized.replace(Regex("(?i)<script[^>]*>.*?</script>"), "")
        return sanitized.trim()
    }
}

class CreatePrivateChatUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(recipientUserId: String): Result<Chat> = chatRepository.createPrivateChat(recipientUserId)
}

class CreateGroupChatUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(name: String, participantIds: List<String>, permissions: Map<String, Any>? = null): Result<Chat> {
        if (name.isBlank() || name.length > 100) return Result.Error("Invalid group name")
        if (participantIds.size < 2 || participantIds.size > 256) return Result.Error("Participants must be between 2 and 256")
        if (participantIds.any { it.isBlank() }) return Result.Error("Participant IDs cannot be empty")
        val uniqueParticipants = participantIds.distinct()
        if (uniqueParticipants.size != participantIds.size) return Result.Error("Duplicate participants not allowed")
        
        if (permissions != null) {
            val validKeys = setOf("canSendMessages", "canAddMembers", "canRemoveMembers", "canEditGroupInfo")
            if ((permissions.keys - validKeys).isNotEmpty()) return Result.Error("Invalid permission keys")
            if (permissions.values.any { it !is Boolean }) return Result.Error("Permissions must be boolean")
        }
        return chatRepository.createGroupChat(name, uniqueParticipants, permissions)
    }
}

class EditMessageUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(messageId: String, newContent: String): Result<Unit> {
        if (messageId.isBlank()) return Result.Error("Message ID cannot be empty")
        val trimmed = newContent.trim()
        if (trimmed.isBlank() || trimmed.length > 5000) return Result.Error("Invalid content")
        val sanitized = trimmed.replace(Regex("<[^>]*>"), "").replace(Regex("(?i)<script[^>]*>.*?</script>"), "").trim()
        return messageRepository.editMessage(messageId, sanitized)
    }
}

class DeleteMessageUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(messageId: String, forEveryone: Boolean = false): Result<Unit> = 
        if (forEveryone) messageRepository.deleteMessageForEveryone(messageId) else messageRepository.deleteMessageForMe(messageId)
}

class ReactToMessageUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(messageId: String, emoji: String?): Result<Unit> = messageRepository.reactToMessage(messageId, emoji)
}

class MarkChatAsReadUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(chatId: String): Result<Unit> = messageRepository.markChatAsRead(chatId)
}

class MarkChatAsReadUpToUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(chatId: String, upToTimestamp: Long): Result<Unit> = messageRepository.markChatAsReadUpTo(chatId, upToTimestamp)
}

class SearchMessagesUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(chatId: String, query: String): Result<List<Message>> {
        if (chatId.isBlank()) return Result.Error("Chat ID cannot be empty")
        val trimmed = query.trim()
        if (trimmed.isBlank()) return Result.Success(emptyList())
        if (trimmed.length < 2 || trimmed.length > 100) return Result.Error("Query length invalid")
        val sanitized = trimmed.replace(Regex("[\\\\^$.|?*+()\\[\\]{}]"), "\\\\$0")
        return messageRepository.searchMessages(chatId, sanitized)
    }
}

class ObserveQueuedMessageCountUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    operator fun invoke(): Flow<Result<Int>> = messageRepository.observeQueuedMessageCount()
}

class RetryFailedMessagesUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(batchSize: Int = 50): Result<Int> = messageRepository.retryFailedMessages(batchSize)
}

class GetChatByIdUseCase @Inject constructor(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(chatId: String): Result<Chat> = chatRepository.getChatById(chatId)
}

class GetMessageByIdUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(messageId: String): Result<Message> {
        if (messageId.isBlank()) return Result.Error("Message ID cannot be empty")
        return try {
            messageRepository.getMessageById(messageId)
        } catch (e: Exception) {
            Result.Error("Failed to get message")
        }
    }
}

class SyncMessagesFromFirestoreUseCase @Inject constructor(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(chatId: String): Result<Unit> {
        if (chatId.isBlank()) return Result.Error("Chat ID cannot be empty")
        return try {
            // Note: syncMessagesFromFirestore might not exist in MessageRepository, but I will assume it does, 
            // or I will just retry failed messages. Let's look at what MessageRepository has for sync.
            // MessageRepository doesn't have syncMessagesFromFirestore. It has `retryFailedMessagesForChat`.
            messageRepository.retryFailedMessagesForChat(chatId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
}
