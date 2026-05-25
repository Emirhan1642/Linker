package com.linker.app.data.queue

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deduplication statistics
 */
data class DeduplicationStatistics(
    val cacheSize: Int,
    val totalChecks: Long,
    val duplicateHits: Long,
    val totalMarked: Long,
    val hitRate: Double
)

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
 * - Automatic cleanup runs periodically to prevent memory leaks
 */
@Singleton
class MessageDeduplicationManager @Inject constructor() {
    
    private val processedMessageIds = ConcurrentHashMap<String, Long>()
    private val MESSAGE_DEDUP_WINDOW = 60_000L // 60 seconds
    private val MAX_CACHE_SIZE = 10_000 // Maximum 10,000 entries
    private val CLEANUP_INTERVAL = 5 * 60 * 1000L // 5 minutes
    
    // Statistics
    private var totalChecks = 0L
    private var duplicateHits = 0L
    private var totalMarked = 0L

    // Coroutine scope for automatic cleanup
    private val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private const val TAG = "MessageDeduplicationManager"
    }

    init {
        startAutomaticCleanup()
    }

    private fun startAutomaticCleanup() {
        cleanupScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL)
                try {
                    cleanupOldEntries()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during automatic cleanup", e)
                }
            }
        }
    }
    
    /**
     * Get deduplication statistics
     */
    fun getStatistics(): DeduplicationStatistics {
        return DeduplicationStatistics(
            cacheSize = processedMessageIds.size,
            totalChecks = totalChecks,
            duplicateHits = duplicateHits,
            totalMarked = totalMarked,
            hitRate = if (totalChecks > 0) (duplicateHits.toDouble() / totalChecks) else 0.0
        )
    }

    /**
     * Check if message was recently processed
     * 
     * @param messageId The MessageEntity.messageId (not BLE packet ID)
     * @return true if message is duplicate, false if new
     * @throws IllegalArgumentException if messageId is blank
     */
    fun isDuplicate(messageId: String): Boolean {
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        
        totalChecks++
        
        val lastProcessed = processedMessageIds[messageId]
        val isDuplicate = lastProcessed != null && 
               System.currentTimeMillis() - lastProcessed < MESSAGE_DEDUP_WINDOW
               
        if (isDuplicate) {
            duplicateHits++
            Log.d(TAG, "Duplicate message detected: $messageId (hit rate: ${getStatistics().hitRate})")
        }
        
        return isDuplicate
    }
    
    /**
     * Mark message as processed with cache size limit
     * 
     * If cache exceeds MAX_CACHE_SIZE, oldest entries are removed.
     * 
     * @param messageId The MessageEntity.messageId to mark
     * @throws IllegalArgumentException if messageId is blank
     */
    fun markAsProcessed(messageId: String) {
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        
        totalMarked++

        if (processedMessageIds.size >= MAX_CACHE_SIZE) {
            Log.w(TAG, "Cache size limit reached ($MAX_CACHE_SIZE), performing emergency cleanup")
            cleanupOldEntries()
            
            if (processedMessageIds.size >= MAX_CACHE_SIZE) {
                val entriesToRemove = processedMessageIds.size - (MAX_CACHE_SIZE * 3 / 4) // Remove 25%
                val sortedEntries = processedMessageIds.entries.sortedBy { it.value }
                
                sortedEntries.take(entriesToRemove).forEach { entry ->
                    processedMessageIds.remove(entry.key)
                }
                Log.w(TAG, "Emergency cleanup: removed $entriesToRemove oldest entries")
            }
        }

        processedMessageIds[messageId] = System.currentTimeMillis()
        Log.d(TAG, "Message marked as processed: $messageId (total: $totalMarked, cache size: ${processedMessageIds.size})")
    }
    
    /**
     * Remove old entries to prevent memory leak
     * 
     * This is called automatically every 5 minutes, but can also be called manually.
     */
    fun cleanupOldEntries() {
        val now = System.currentTimeMillis()
        val sizeBefore = processedMessageIds.size
        
        processedMessageIds.entries.removeIf { (_, timestamp) ->
            now - timestamp > MESSAGE_DEDUP_WINDOW
        }
        
        val sizeAfter = processedMessageIds.size
        val removed = sizeBefore - sizeAfter
        
        if (removed > 0) {
            Log.d(TAG, "Cleaned up $removed old entries (cache size: $sizeAfter)")
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
        val size = processedMessageIds.size
        processedMessageIds.clear()
        Log.w(TAG, "Cleared all $size entries from deduplication cache")
    }

    /**
     * Reset statistics
     */
    fun resetStatistics() {
        totalChecks = 0
        duplicateHits = 0
        totalMarked = 0
        Log.d(TAG, "Statistics reset")
    }

    /**
     * Stop automatic cleanup (for testing or cleanup)
     */
    fun shutdown() {
        cleanupScope.cancel()
        Log.d(TAG, "MessageDeduplicationManager shutdown")
    }
}
