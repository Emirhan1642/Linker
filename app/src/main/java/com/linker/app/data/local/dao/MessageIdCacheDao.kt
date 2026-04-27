package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.MessageIdCacheEntity

/**
 * DAO for Message ID Cache operations
 * 
 * Used for BLE packet deduplication to prevent processing the same packet
 * multiple times when it arrives via different mesh routes.
 */
@Dao
interface MessageIdCacheDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageId(cache: MessageIdCacheEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIds(caches: List<MessageIdCacheEntity>)
    
    @Query("SELECT * FROM message_id_cache WHERE messageId = :messageId")
    suspend fun getMessageId(messageId: String): MessageIdCacheEntity?
    
    /**
     * Check if message ID exists in cache
     */
    @Query("SELECT EXISTS(SELECT 1 FROM message_id_cache WHERE messageId = :messageId)")
    suspend fun exists(messageId: String): Boolean
    
    /**
     * Get all cached message IDs
     */
    @Query("SELECT * FROM message_id_cache ORDER BY receivedAt DESC")
    suspend fun getAllMessageIds(): List<MessageIdCacheEntity>
    
    /**
     * Get message IDs received within the last N milliseconds
     */
    @Query("SELECT * FROM message_id_cache WHERE receivedAt > :sinceTimestamp ORDER BY receivedAt DESC")
    suspend fun getRecentMessageIds(sinceTimestamp: Long): List<MessageIdCacheEntity>
    
    /**
     * Delete message IDs older than the given timestamp
     * Used for cleanup to prevent unbounded growth
     */
    @Query("DELETE FROM message_id_cache WHERE receivedAt < :beforeTimestamp")
    suspend fun deleteOldMessageIds(beforeTimestamp: Long): Int
    
    /**
     * Delete specific message ID
     */
    @Query("DELETE FROM message_id_cache WHERE messageId = :messageId")
    suspend fun deleteMessageId(messageId: String)
    
    /**
     * Clear all cached message IDs
     */
    @Query("DELETE FROM message_id_cache")
    suspend fun clearAll()
    
    /**
     * Get cache size
     */
    @Query("SELECT COUNT(*) FROM message_id_cache")
    suspend fun getCacheSize(): Int
    
    /**
     * Delete oldest entries when cache exceeds limit
     * Keeps the most recent N entries
     */
    @Query("""
        DELETE FROM message_id_cache 
        WHERE messageId IN (
            SELECT messageId FROM message_id_cache 
            ORDER BY receivedAt ASC 
            LIMIT (SELECT COUNT(*) - :maxSize FROM message_id_cache)
        )
    """)
    suspend fun trimToSize(maxSize: Int): Int
}
