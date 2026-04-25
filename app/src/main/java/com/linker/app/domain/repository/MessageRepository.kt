package com.linker.app.domain.repository

import com.linker.app.domain.model.Message
import com.linker.app.domain.model.MessageType
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for message-related operations
 * Separated from ChatRepository for single responsibility
 */
interface MessageRepository {

    /** Observe messages for a specific chat */
    fun observeMessages(chatId: String): Flow<List<Message>>

    /** Get single message by ID */
    suspend fun getMessageById(messageId: String): Result<Message>

    /** Get messages with pagination */
    suspend fun getMessagesPaged(
        chatId: String,
        beforeTimestamp: Long? = null,
        limit: Int = 50
    ): Result<List<Message>>

    /** Send a new message */
    suspend fun sendMessage(
        chatId: String,
        messageType: MessageType = MessageType.TEXT,
        content: String,
        mediaUrl: String? = null,
        replyToMessageId: String? = null
    ): Result<Message>

    /** Edit existing message */
    suspend fun editMessage(messageId: String, newContent: String): Result<Unit>

    /** Delete message */
    suspend fun deleteMessage(messageId: String, forEveryone: Boolean = false): Result<Unit>

    /** Add or remove an emoji reaction to a message */
    suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit>

    /** Mark a single message as read */
    suspend fun markMessageAsRead(messageId: String): Result<Unit>

    /** Mark all messages in a chat as read */
    suspend fun markChatAsRead(chatId: String): Result<Unit>

    /** Retry failed messages */
    suspend fun retryFailedMessages(): Result<Unit>

    /** Forward message to another chat */
    suspend fun forwardMessage(
        messageId: String,
        targetChatId: String
    ): Result<Message>

    /** Search messages in a chat */
    suspend fun searchMessages(chatId: String, query: String): Result<List<Message>>
}
