package com.linker.app.domain.repository

import com.linker.app.domain.model.Chat
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for chat lists and creation.
 * Follows Single Responsibility Principle (SRP).
 * 
 * For messages, see [MessageRepository].
 * For chat settings and group management, see [ChatSettingsRepository].
 */
interface ChatRepository {

    /** Observes all active (non-archived) chats ordered by last message. */
    fun observeChats(): Flow<Result<List<Chat>>>

    /** Observes archived chats. */
    fun observeArchivedChats(): Flow<Result<List<Chat>>>

    /** Observes total unread message count badge. */
    fun observeTotalUnread(): Flow<Result<Int>>

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

    /** Deletes a chat. */
    suspend fun deleteChat(chatId: String): Result<Unit>
}
