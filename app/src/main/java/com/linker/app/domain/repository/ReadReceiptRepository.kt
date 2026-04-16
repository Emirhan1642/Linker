package com.linker.app.domain.repository

import com.linker.app.core.util.Result

/**
 * Repository for read receipt operations
 */
interface ReadReceiptRepository {

    /** Mark message as read */
    suspend fun markAsRead(messageId: String, chatId: String): Result<Unit>

    /** Mark all messages in chat as read up to timestamp */
    suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit>

    /** Get read receipts for a message */
    suspend fun getReadReceipts(chatId: String, messageId: String): Result<Map<String, Long>>

    /** Get delivery receipts for a message */
    suspend fun getDeliveryReceipts(chatId: String, messageId: String): Result<Map<String, Long>>
}
