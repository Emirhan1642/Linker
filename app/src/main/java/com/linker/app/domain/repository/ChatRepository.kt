package com.linker.app.domain.repository

import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.model.MessageType
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    // ── Chat list ──────────────────────────────────────────────────────────

    /** Observes all active (non-archived) chats ordered by last message. */
    fun observeChats(): Flow<List<Chat>>

    /** Observes archived chats. */
    fun observeArchivedChats(): Flow<List<Chat>>

    /** Observes total unread message count badge. */
    fun observeTotalUnread(): Flow<Int>

    /** Fetches a single chat by ID. */
    suspend fun getChatById(chatId: String): Result<Chat>

    /** Creates a private chat with [recipientUserId]. */
    suspend fun createPrivateChat(recipientUserId: String): Result<Chat>

    /** Creates a group chat. */
    suspend fun createGroupChat(
        name: String,
        participantIds: List<String>,
        permissions: Map<String, Any>? = null
    ): Result<Chat>

    /** Updates pin / mute / archive status. */
    suspend fun updateChatSettings(
        chatId: String,
        isPinned: Boolean? = null,
        isMuted: Boolean? = null,
        isArchived: Boolean? = null,
        isBlocked: Boolean? = null,
        isFavorited: Boolean? = null
    ): Result<Unit>

    // ── Messages ───────────────────────────────────────────────────────────

    /** Observes messages in a chat (oldest → newest). */
    fun observeMessages(chatId: String): Flow<List<Message>>

    /**
     * Sends a message.
     *
     * If the device is online the message travels via Firebase/Supabase.
     * If offline it is put in the [MessageQueue] and advertised over BLE.
     */
    suspend fun sendMessage(
        chatId: String,
        messageType: MessageType,
        content: String? = null,
        mediaLocalPath: String? = null,
        replyToMessageId: String? = null
    ): Result<Message>

    /** Edits a text message. */
    suspend fun editMessage(messageId: String, newContent: String): Result<Unit>

    /** Deletes a message for the current user or for everyone. */
    suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit>

    /** Adds or removes an emoji reaction to a message. */
    suspend fun reactToMessage(messageId: String, emoji: String?): Result<Unit>

    /** Forwards a message to another chat. */
    suspend fun forwardMessage(messageId: String, targetChatId: String): Result<Unit>

    /** Marks all messages in a chat as read. */
    suspend fun markChatAsRead(chatId: String): Result<Unit>

    /** Marks messages up to [upToTimestamp] as read (inclusive). */
    suspend fun markChatAsReadUpTo(chatId: String, upToTimestamp: Long): Result<Unit>

    /** Searches messages within a chat. */
    suspend fun searchMessages(chatId: String, query: String): Result<List<Message>>

    /** Returns the number of items waiting in the offline queue. */
    fun observeQueuedMessageCount(): Flow<Int>

    /** Retries all failed messages for a specific delivery method. */
    suspend fun retryFailedMessages(preferredMethod: DeliveryMethod): Result<Unit>
}
