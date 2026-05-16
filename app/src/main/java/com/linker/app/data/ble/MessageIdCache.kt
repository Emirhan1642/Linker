package com.linker.app.data.ble

import android.util.LruCache
import com.linker.app.data.local.dao.MessageIdCacheDao
import com.linker.app.data.local.entity.MessageIdCacheEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    
    // In-memory LRU cache for fast lookups
    private val memoryCache = LruCache<String, Long>(CACHE_SIZE)
    private val mutex = Mutex()
    
    // Track last trim time to avoid excessive database operations
    @Volatile
    private var lastTrimTime = 0L
    
    /**
     * Check if message ID exists in cache
     * 
     * @param messageId BLE packet message ID to check
     * @return true if message ID exists in cache, false otherwise
     */
    suspend fun contains(messageId: String): Boolean = mutex.withLock {
        // Check memory cache first
        if (memoryCache.get(messageId) != null) {
            return@withLock true
        }

        // Check database
        val exists = messageIdCacheDao.exists(messageId)

        // Update memory cache if found
        if (exists) {
            memoryCache.put(messageId, System.currentTimeMillis())
        }

        exists
    }
    
    /**
     * Add message ID to cache
     * 
     * @param messageId BLE packet message ID to add
     * @param sourceNodeId Node that sent the packet
     */
    suspend fun add(messageId: String, sourceNodeId: String) = mutex.withLock {
        val now = System.currentTimeMillis()
        
        // Add to memory cache
        memoryCache.put(messageId, now)
        
        // Add to database
        val entity = MessageIdCacheEntity(
            messageId = messageId,
            receivedAt = now,
            sourceNodeId = sourceNodeId
        )
        messageIdCacheDao.insertMessageId(entity)
        
        // Trim database if needed (but not too frequently)
        if (now - lastTrimTime > TRIM_INTERVAL) {
            val cacheSize = messageIdCacheDao.getCacheSize()
            if (cacheSize > CACHE_SIZE) {
                messageIdCacheDao.trimToSize(CACHE_SIZE)
                lastTrimTime = now
            }
        }
    }
    
    /**
     * Clean up old entries from cache
     * 
     * Removes entries older than 24 hours to prevent unbounded growth
     */
    suspend fun cleanup() = mutex.withLock {
        val cutoffTime = System.currentTimeMillis() - CACHE_RETENTION
        
        // Clean up database
        val removedCount = messageIdCacheDao.deleteOldMessageIds(cutoffTime)
        
        // Clean up memory cache
        val snapshot = memoryCache.snapshot()
        snapshot.forEach { (messageId, timestamp) ->
            if (timestamp < cutoffTime) {
                memoryCache.remove(messageId)
            }
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
    suspend fun clearAll() = mutex.withLock {
        memoryCache.evictAll()
        messageIdCacheDao.clearAll()
    }
    
    /**
     * Load recent entries from database into memory cache
     * 
     * Called on initialization to warm up the cache
     */
    suspend fun warmUpCache() = mutex.withLock {
        val recentEntries = messageIdCacheDao.getRecentMessageIds(
            System.currentTimeMillis() - CACHE_RETENTION
        )
        
        recentEntries.forEach { entity ->
            memoryCache.put(entity.messageId, entity.receivedAt)
        }
    }
}
