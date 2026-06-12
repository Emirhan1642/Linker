package com.linker.app.domain.repository

import com.linker.app.core.util.Result

interface ReadReceiptRepository {
    suspend fun markAsRead(messageId: String, chatId: String): Result<Unit>
    suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit>
    suspend fun getReadReceipts(chatId: String, messageId: String): Result<Map<String, Long>>
    suspend fun getDeliveryReceipts(chatId: String, messageId: String): Result<Map<String, Long>>
}
