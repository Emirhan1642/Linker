package com.linker.app.data.queue

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Race Condition Handler - Prevents duplicate message processing
 * 
 * Handles Requirement 18.8: "Message_Repository SHALL handle race conditions 
 * when same message arrives via both BLE and online"
 * 
 * This manager tracks MessageEntity.messageId to prevent the same logical message
 * from being inserted twice when it arrives via both BLE and online channels.
 * 
 * Note: This is separate from BLE packet deduplication (MessageIdCache), which
 * prevents the same BLE packet from being processed multiple times when it arrives
 * via different mesh routes.
 * 
 * Usage:
 * - When receiving a message (BLE or online), check isDuplicate(messageId)
 * - If not duplicate, process message and call markAsProcessed(messageId)
 * - Periodically call cleanupOldEntries() to prevent memory leaks
 */
@Singleton
class MessageDeduplicationManager @Inject constructor() {
    
    private val processedMessageIds = ConcurrentHashMap<String, Long>()
    private val MESSAGE_DEDUP_WINDOW = 60_000L // 60 seconds
    
    /**
     * Check if message was recently processed
     * 
     * @param messageId The MessageEntity.messageId (not BLE packet ID)
     * @return true if message is duplicate, false if new
     */
    fun isDuplicate(messageId: String): Boolean {
        val lastProcessed = processedMessageIds[messageId]
        return lastProcessed != null && 
               System.currentTimeMillis() - lastProcessed < MESSAGE_DEDUP_WINDOW
    }
    
    /**
     * Mark message as processed
     * 
     * @param messageId The MessageEntity.messageId to mark
     */
    fun markAsProcessed(messageId: String) {
        processedMessageIds[messageId] = System.currentTimeMillis()
    }
    
    /**
     * Remove old entries to prevent memory leak
     * 
     * Should be called periodically (e.g., every 5 minutes)
     */
    fun cleanupOldEntries() {
        val now = System.currentTimeMillis()
        processedMessageIds.entries.removeIf { (_, timestamp) ->
            now - timestamp > MESSAGE_DEDUP_WINDOW
        }
    }
    
    /**
     * Get current cache size
     */
    fun getCacheSize(): Int {
        return processedMessageIds.size
    }
    
    /**
     * Clear all entries
     */
    fun clearAll() {
        processedMessageIds.clear()
    }
}
