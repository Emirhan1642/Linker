package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Message
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

class LoadMessagesPagedUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        chatId: String,
        beforeTimestamp: Long? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
        filterDeleted: Boolean = true
    ): Result<PaginatedMessages> {
        if (chatId.isBlank()) return Result.Error("Invalid chat ID")
        if (beforeTimestamp != null && beforeTimestamp < 0) return Result.Error("Invalid timestamp")
        
        val validatedLimit = when {
            limit < MIN_PAGE_SIZE -> MIN_PAGE_SIZE
            limit > MAX_PAGE_SIZE -> MAX_PAGE_SIZE
            else -> limit
        }
        
        val result = messageRepository.getMessagesPaged(chatId, beforeTimestamp, validatedLimit)
        
        return when (result) {
            is Result.Success -> {
                val messages = result.data
                val filteredMessages = if (filterDeleted) messages.filter { !it.isDeleted } else messages
                val hasMore = messages.size == validatedLimit
                val nextCursor = filteredMessages.lastOrNull()?.createdAt
                
                Result.Success(PaginatedMessages(filteredMessages, hasMore, nextCursor))
            }
            is Result.Error -> Result.Error(result.message)
            is Result.Loading -> Result.Loading()
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 100
    }
}

data class PaginatedMessages(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextCursor: Long?
)
