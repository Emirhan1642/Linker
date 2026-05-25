package com.linker.app.data.ble

import com.linker.app.core.util.SecureLogger
import com.linker.app.data.local.dao.MessageIdCacheDao
import com.linker.app.data.local.entity.MessageIdCacheEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache for BLE packet message IDs to prevent duplicate processing
 * 
 * Handles Requirements 13.1-13.6: Message deduplication for BLE packets
 * 
 * This cache prevents the same BLE packet from being processed multiple times
 * when it arrives via different mesh routes. This is separate from
 * MessageDeduplicationManager which handles race conditions when the same
 * logical message arrives via both BLE and online channels.
 */
@Singleton
class MessageIdCache @Inject constructor(
    private val messageIdCacheDao: MessageIdCacheDao
) {
    
    companion object {
        private const val CACHE_SIZE = 10_000 // Maximum entries in memory cache
        private const val CACHE_RETENTION = 24 * 60 * 60 * 1000L // 24 hours
        private const val TRIM_INTERVAL = 5 * 60 * 1000L // Trim every 5 minutes
    }
    
    private val logger = SecureLogger("MessageIdCache")
    
    // In-memory cache for fast lookups (thread-safe)
    private val memoryCache = ConcurrentHashMap<String, Long>(CACHE_SIZE)
    
    // Track last trim time to avoid excessive database operations
    @Volatile
    private var lastTrimTime = 0L
    
    /**
     * Check if message ID exists in cache
     * 
     * @param messageId BLE packet message ID to check
     * @return true if message ID exists in cache, false otherwise
     */
    suspend fun contains(messageId: String): Boolean {
        // Check memory cache first
        if (memoryCache.containsKey(messageId)) {
            return true
        }

        // Check database
        val exists = messageIdCacheDao.exists(messageId)

        // Update memory cache if found
        if (exists) {
            memoryCache[messageId] = System.currentTimeMillis()
        }

        return exists
    }
    
    /**
     * Add message ID to cache
     * 
     * @param messageId BLE packet message ID to add
     * @param sourceNodeId Node that sent the packet
     */
    suspend fun add(messageId: String, sourceNodeId: String) {
        val now = System.currentTimeMillis()
        
        // Add to memory cache
        memoryCache[messageId] = now
        
        // Add to database
        val entity = MessageIdCacheEntity(
            messageId = messageId,
            receivedAt = now,
            sourceNodeId = sourceNodeId
        )
        messageIdCacheDao.insertMessageId(entity)
        logger.d("Added message $messageId to cache")
        
        // Trim database and memory cache if needed (but not too frequently)
        if (now - lastTrimTime > TRIM_INTERVAL) {
            lastTrimTime = now
            val cacheSize = messageIdCacheDao.getCacheSize()
            if (cacheSize > CACHE_SIZE) {
                messageIdCacheDao.trimToSize(CACHE_SIZE)
                trimMemoryCache()
                logger.d("Trimmed message ID cache")
            }
        }
    }
    
    private fun trimMemoryCache() {
        if (memoryCache.size > CACHE_SIZE) {
            // Remove oldest entries
            val entriesToRemove = memoryCache.size - CACHE_SIZE
            val sortedEntries = memoryCache.entries.sortedBy { it.value }
            for (i in 0 until entriesToRemove) {
                if (i < sortedEntries.size) {
                    memoryCache.remove(sortedEntries[i].key)
                }
            }
        }
    }
    
    /**
     * Clean up old entries from cache
     * 
     * Removes entries older than 24 hours to prevent unbounded growth
     */
    suspend fun cleanup() {
        val cutoffTime = System.currentTimeMillis() - CACHE_RETENTION
        
        // Clean up database without holding a lock
        val removedCount = messageIdCacheDao.deleteOldMessageIds(cutoffTime)
        
        // Clean up memory cache concurrently
        memoryCache.entries.removeIf { it.value < cutoffTime }
        
        if (removedCount > 0) {
            logger.d("Cleaned up $removedCount old entries from message ID cache")
        }
    }
    
    /**
     * Get cache size
     * 
     * @return Number of entries in cache
     */
    suspend fun size(): Int {
        return messageIdCacheDao.getCacheSize()
    }
    
    /**
     * Clear all entries from cache
     */
    suspend fun clearAll() {
        memoryCache.clear()
        messageIdCacheDao.clearAll()
        logger.d("Cleared all message ID cache entries")
    }
    
    /**
     * Load recent entries from database into memory cache
     * 
     * Called on initialization to warm up the cache
     */
    suspend fun warmUpCache() {
        val recentEntries = messageIdCacheDao.getRecentMessageIds(
            System.currentTimeMillis() - CACHE_RETENTION
        )
        
        recentEntries.forEach { entity ->
            memoryCache[entity.messageId] = entity.receivedAt
        }
        logger.d("Warmed up message ID cache with ${recentEntries.size} entries")
    }
}
