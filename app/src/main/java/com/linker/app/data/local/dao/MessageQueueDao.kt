package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.MessageQueueEntity
import com.linker.app.data.local.entity.QueueStatus
import kotlinx.coroutines.flow.Flow

/**
 * Message Queue DAO - Data Access Object for message queue
 */
@Dao
interface MessageQueueDao {
    
    @Query("SELECT * FROM message_queue WHERE queueId = :queueId")
    suspend fun getQueueItemById(queueId: String): MessageQueueEntity?
    
    @Query("SELECT * FROM message_queue WHERE queueStatus = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getQueueItemsByStatus(status: QueueStatus): List<MessageQueueEntity>
    
    @Query("SELECT * FROM message_queue WHERE queueStatus IN ('PENDING', 'FAILED') ORDER BY priority DESC, createdAt ASC")
    fun observePendingQueueItems(): Flow<List<MessageQueueEntity>>
    
    @Query("SELECT * FROM message_queue WHERE messageId = :messageId")
    suspend fun getQueueItemByMessageId(messageId: String): MessageQueueEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: MessageQueueEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<MessageQueueEntity>)
    
    @Update
    suspend fun updateQueueItem(item: MessageQueueEntity)
    
    /**
     * Batch update multiple queue items
     * More efficient than updating one by one
     */
    @Transaction
    @Update
    suspend fun updateQueueItems(items: List<MessageQueueEntity>)
    
    @Delete
    suspend fun deleteQueueItem(item: MessageQueueEntity)
    
    @Query("DELETE FROM message_queue WHERE queueId = :queueId")
    suspend fun deleteQueueItemById(queueId: String)
    
    @Query("DELETE FROM message_queue WHERE queueStatus = 'SENT'")
    suspend fun deleteSentItems()
    
    @Query("UPDATE message_queue SET queueStatus = :status, lastAttemptAt = :timestamp WHERE queueId = :queueId")
    suspend fun updateQueueStatus(queueId: String, status: QueueStatus, timestamp: Long)
    
    @Transaction
    @Query("UPDATE message_queue SET retryCount = retryCount + 1, lastAttemptAt = :timestamp, errorMessage = :error WHERE queueId = :queueId")
    suspend fun incrementRetryCount(queueId: String, timestamp: Long, error: String?)
    
    @Transaction
    @Query("UPDATE message_queue SET queueStatus = :status, errorMessage = :error, lastAttemptAt = :timestamp WHERE queueId = :queueId")
    suspend fun updateStatus(queueId: String, status: QueueStatus, error: String? = null, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM message_queue WHERE queueStatus IN ('PENDING', 'SENDING')")
    fun observePendingCount(): Flow<Int>
    
    // Additional methods for SyncManager
    
    @Query("SELECT * FROM message_queue WHERE queueStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<MessageQueueEntity>
    
    @Query("SELECT * FROM message_queue WHERE queueStatus = 'FAILED' AND retryCount < maxRetries ORDER BY createdAt ASC")
    suspend fun getFailedMessages(): List<MessageQueueEntity>
    
    @Query("UPDATE message_queue SET queueStatus = :status, sentAt = :sentAt WHERE queueId = :queueId")
    suspend fun updateQueueStatus(queueId: String, status: QueueStatus, sentAt: Long?): Int
    
    @Query("UPDATE message_queue SET retryCount = retryCount + 1 WHERE queueId = :queueId")
    suspend fun incrementRetryCount(queueId: String)
    
    @Query("UPDATE message_queue SET errorMessage = :errorMessage WHERE queueId = :queueId")
    suspend fun updateErrorMessage(queueId: String, errorMessage: String)
    
    @Query("UPDATE message_queue SET lastAttemptAt = :timestamp WHERE queueId = :queueId")
    suspend fun updateLastAttempt(queueId: String, timestamp: Long)
    
    @Query("DELETE FROM message_queue WHERE queueStatus = 'SENT' AND sentAt < :cutoffTime")
    suspend fun deleteOldSentMessages(cutoffTime: Long): Int
    
    @Query("SELECT COUNT(*) FROM message_queue")
    suspend fun getQueueSize(): Int
    
    @Query("SELECT * FROM message_queue WHERE queueStatus = :status")
    suspend fun getMessagesByStatus(status: QueueStatus): List<MessageQueueEntity>
    
    @Query("DELETE FROM message_queue WHERE queueId = :queueId")
    suspend fun deleteQueueItem(queueId: String)
    
    /**
     * Batch delete multiple queue items by IDs
     * More efficient than deleting one by one
     */
    @Transaction
    @Query("DELETE FROM message_queue WHERE queueId IN (:queueIds)")
    suspend fun deleteQueueItems(queueIds: List<String>)
    
    /**
     * Batch update status for multiple queue items
     * More efficient than updating one by one
     */
    @Transaction
    @Query("UPDATE message_queue SET queueStatus = :status, lastAttemptAt = :timestamp WHERE queueId IN (:queueIds)")
    suspend fun batchUpdateStatus(queueIds: List<String>, status: QueueStatus, timestamp: Long)

    @Query("""
        SELECT queueStatus, COUNT(*) as count 
        FROM message_queue 
        GROUP BY queueStatus
    """)
    suspend fun getStatusCountsList(): List<StatusCountResult>

    suspend fun getStatusCounts(): Map<QueueStatus, Int> {
        return getStatusCountsList().associate { it.queueStatus to it.count }
    }

    @Query("""
        DELETE FROM message_queue 
        WHERE queueId IN (
            SELECT queueId 
            FROM message_queue 
            WHERE queueStatus = 'SENT' 
            ORDER BY sentAt ASC 
            LIMIT :limit
        )
    """)
    suspend fun deleteOldestSentMessages(limit: Int): Int
    
    /**
     * Atomically update queue status and message delivery method.
     * 
     * Addresses Issue #56 (P3): Add transaction support for queue updates
     * 
     * This ensures queue and message updates are atomic - either both succeed or both fail.
     * Prevents data inconsistency where queue shows SENT but message still shows BLE delivery.
     * 
     * NOTE: This method is not directly usable since DAOs cannot inject other DAOs.
     * The actual transaction is implemented in LinkerDatabase.updateQueueAndMessageAtomic()
     */
}

/**
 * Wrapper class for status counts query
 */
data class StatusCountResult(
    val queueStatus: QueueStatus,
    val count: Int
)
