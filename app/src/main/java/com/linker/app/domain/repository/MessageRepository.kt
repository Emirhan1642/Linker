package com.linker.app.domain.repository

import com.linker.app.domain.model.Message
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Reaction details for a specific user.
 */
data class ReactionDetail(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val emoji: String,
    val timestamp: Long
)

/**
 * Repository for message-related operations.
 * Consolidates Message, Reaction, and ReadReceipt functionalities for high cohesion.
 */
interface MessageRepository {

    // ── Messages ───────────────────────────────────────────────────────────

    /** Observe messages for a specific chat */
    fun observeMessages(chatId: String): Flow<Result<List<Message>>>

    /** Get single message by ID */
    suspend fun getMessageById(messageId: String): Result<Message>

    /** Get messages with cursor-based pagination */
    suspend fun getMessagesPaged(
        chatId: String,
        beforeTimestamp: Long? = null,
        limit: Int = 50
    ): Result<List<Message>>

    /** 
     * Send a new message.
     * 
     * Offline Behavior:
     * - If offline, the message is stored locally and queued for synchronization.
     * - Status becomes PENDING.
     * 
     * @param mediaLocalPath If provided, the media is uploaded before sending the message.
     */
    suspend fun sendMessage(
        chatId: String,
        messageType: MessageType = MessageType.TEXT,
        content: String? = null,
        mediaLocalPath: String? = null,
        replyToMessageId: String? = null,
        replyToNote: com.linker.app.domain.model.NoteReference? = null
    ): Result<Message>

    /** 
     * Edit existing text message. 
     * Only textual content can be edited. Media messages cannot be edited.
     */
    suspend fun editMessage(messageId: String, newContent: String): Result<Unit>

    /** Delete message for the current user. */
    suspend fun deleteMessageForMe(messageId: String): Result<Unit>
    
    /** 
     * Delete message for everyone. 
     * Only possible if the message was sent by the current user and within the allowed time window.
     */
    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit>

    /** Retry failed messages in batches */
    suspend fun retryFailedMessages(batchSize: Int = 50): Result<Int>
    
    /** Retry failed messages for a specific chat */
    suspend fun retryFailedMessagesForChat(chatId: String, batchSize: Int = 50): Result<Int>

    /** 
     * Forward message to another chat.
     * @return Result containing the newly created forwarded message.
     */
    suspend fun forwardMessage(
        messageId: String,
        targetChatId: String
    ): Result<Message>

    /** 
     * Search messages in a chat. 
     * Note: Search is performed locally if offline, falling back to server if online.
     */
    suspend fun searchMessages(chatId: String, query: String): Result<List<Message>>
    
    /** Returns the number of items waiting in the offline queue. */
    fun observeQueuedMessageCount(): Flow<Result<Int>>

    // ── Reactions ──────────────────────────────────────────────────────────

    /** Observe reactions for a specific message */
    fun observeMessageReactions(messageId: String): Flow<Result<Map<String, String>>>

    /** Add or remove an emoji reaction to a message (null to remove) */
    suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit>
    
    /** Get reactions for a message */
    suspend fun getMessageReactions(messageId: String): Result<Map<String, String>>

    /** Get detailed reaction info with user details */
    suspend fun getReactionDetails(messageId: String): Result<List<ReactionDetail>>
    
    /** Get reaction details for multiple messages (Batch Operation) */
    suspend fun getReactionDetailsBatch(messageIds: List<String>): Result<Map<String, List<ReactionDetail>>>

    // ── Receipts ───────────────────────────────────────────────────────────

    /** Observe read receipts for a specific message */
    fun observeReadReceipts(messageId: String): Flow<Result<Map<String, Long>>>
    
    /** Observe delivery receipts for a specific message */
    fun observeDeliveryReceipts(messageId: String): Flow<Result<Map<String, Long>>>

    /** 
     * Mark a single message as read.
     * Respects user's privacy settings (e.g., if Read Receipts are disabled, it only marks read locally).
     */
    suspend fun markMessageAsRead(messageId: String): Result<Unit>

    /** Mark all messages in a chat as read */
    suspend fun markChatAsRead(chatId: String): Result<Unit>
    
    /** Mark all messages in chat as read up to timestamp */
    suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit>

    /** Get read receipts for a message */
    suspend fun getReadReceipts(messageId: String): Result<Map<String, Long>>
    
    /** Get read receipts for multiple messages (Batch Operation) */
    suspend fun getReadReceiptsBatch(messageIds: List<String>): Result<Map<String, Map<String, Long>>>

    /** Get delivery receipts for a message */
    suspend fun getDeliveryReceipts(messageId: String): Result<Map<String, Long>>
    
    /** Get delivery receipts for multiple messages (Batch Operation) */
    suspend fun getDeliveryReceiptsBatch(messageIds: List<String>): Result<Map<String, Map<String, Long>>>
}
