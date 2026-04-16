package com.linker.app.domain.repository

import com.linker.app.core.util.Result

/**
 * Repository for message reaction operations
 */
interface MessageReactionRepository {

    /** React to a message with emoji (null to remove reaction) */
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit>

    /** Get reactions for a message */
    suspend fun getMessageReactions(chatId: String, messageId: String): Result<Map<String, String>>

    /** Get detailed reaction info with user details */
    suspend fun getReactionDetails(chatId: String, messageId: String): Result<List<ReactionDetail>>
}

data class ReactionDetail(
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val emoji: String
)
