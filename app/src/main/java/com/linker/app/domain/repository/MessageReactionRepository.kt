package com.linker.app.domain.repository

import com.linker.app.core.util.Result

interface MessageReactionRepository {
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit>
    suspend fun getMessageReactions(chatId: String, messageId: String): Result<Map<String, String>>
    suspend fun getReactionDetails(chatId: String, messageId: String): Result<List<ReactionDetail>>
}
