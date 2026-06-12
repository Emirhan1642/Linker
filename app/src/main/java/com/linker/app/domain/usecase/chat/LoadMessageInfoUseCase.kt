package com.linker.app.domain.usecase.chat

import com.linker.app.domain.model.Message
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class LoadMessageInfoUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String): Result<MessageInfo> {
        if (messageId.isBlank()) return Result.Error("Message ID cannot be empty")
        
        return try {
            coroutineScope {
                val messageDeferred = async { messageRepository.getMessageById(messageId) }
                val reactionsDeferred = async { 
                    try { messageRepository.getMessageReactions(messageId) } catch (e: Exception) { Result.Success(emptyMap()) }
                }
                val readReceiptsDeferred = async { 
                    try { messageRepository.getReadReceipts(messageId) } catch (e: Exception) { Result.Success(emptyMap()) }
                }
                val deliveryReceiptsDeferred = async { 
                    try { messageRepository.getDeliveryReceipts(messageId) } catch (e: Exception) { Result.Success(emptyMap()) }
                }
                
                val messageResult = messageDeferred.await()
                if (messageResult !is Result.Success) return@coroutineScope Result.Error("Message not found")
                
                val reactionsResult = reactionsDeferred.await()
                val readReceiptsResult = readReceiptsDeferred.await()
                val deliveryReceiptsResult = deliveryReceiptsDeferred.await()
                
                Result.Success(
                    MessageInfo(
                        messageResult.data, 
                        if (reactionsResult is Result.Success) reactionsResult.data else emptyMap(), 
                        if (readReceiptsResult is Result.Success) readReceiptsResult.data else emptyMap(), 
                        if (deliveryReceiptsResult is Result.Success) deliveryReceiptsResult.data else emptyMap()
                    )
                )
            }
        } catch (e: Exception) {
            Result.Error("Failed to load message info: ${e.message}")
        }
    }
}

data class MessageInfo(
    val message: Message,
    val reactions: Map<String, String>,
    val readReceipts: Map<String, Long>,
    val deliveryReceipts: Map<String, Long> = emptyMap()
)
