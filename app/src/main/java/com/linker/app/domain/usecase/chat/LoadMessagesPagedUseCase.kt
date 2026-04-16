package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Message
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Load messages with pagination support
 * Reduces initial load time and memory usage
 */
class LoadMessagesPagedUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        chatId: String,
        beforeTimestamp: Long? = null,
        limit: Int = DEFAULT_PAGE_SIZE
    ): Result<List<Message>> {
        return chatRepository.getMessagesPaged(
            chatId = chatId,
            beforeTimestamp = beforeTimestamp,
            limit = limit
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
