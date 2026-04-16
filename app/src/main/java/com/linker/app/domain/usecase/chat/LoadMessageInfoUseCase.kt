package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Message
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Load detailed message information including reactions, replies, and read receipts
 */
class LoadMessageInfoUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(messageId: String): Result<MessageInfo> {
        return try {
            val message = chatRepository.getMessageById(messageId)
            val reactions = chatRepository.getMessageReactions(messageId)
            val readReceipts = chatRepository.getReadReceipts(messageId)

            Result.Success(
                MessageInfo(
                    message = message,
                    reactions = reactions,
                    readReceipts = readReceipts
                )
            )
        } catch (e: Exception) {
            Result.Error("Failed to load message info: ${e.message}")
        }
    }
}

/**
 * Data class for message information
 */
data class MessageInfo(
    val message: Message,
    val reactions: Map<String, String>,
    val readReceipts: Map<String, Long>
)
