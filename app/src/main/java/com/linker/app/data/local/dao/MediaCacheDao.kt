package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.MediaCacheEntity
import com.linker.app.data.local.entity.CacheType
import kotlinx.coroutines.flow.Flow

/**
 * Media Cache DAO - Data Access Object for media cache
 */
@Dao
interface MediaCacheDao {
    
    @Query("SELECT * FROM media_cache WHERE id = :id")
    suspend fun getCacheById(id: Long): MediaCacheEntity?
    
    @Query("SELECT * FROM media_cache WHERE mediaUrl = :url")
    suspend fun getCacheByUrl(url: String): MediaCacheEntity?
    
    @Query("SELECT * FROM media_cache WHERE associatedEntityId = :entityId AND associatedEntityType = :entityType")
    suspend fun getCacheByEntity(entityId: String, entityType: String): List<MediaCacheEntity>
    
    @Query("SELECT * FROM media_cache WHERE cacheType = :type ORDER BY lastAccessedAt DESC")
    suspend fun getCacheByType(type: CacheType): List<MediaCacheEntity>
    
    @Query("SELECT * FROM media_cache WHERE isPermanent = 1")
    fun observePermanentCache(): Flow<List<MediaCacheEntity>>
    
    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM media_cache")
    suspend fun getTotalCacheSize(): Long
    
    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM media_cache WHERE isPermanent = 0")
    suspend fun getTemporaryCacheSize(): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: MediaCacheEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaches(caches: List<MediaCacheEntity>)
    
    @Update
    suspend fun updateCache(cache: MediaCacheEntity)
    
    @Delete
    suspend fun deleteCache(cache: MediaCacheEntity)
    
    @Query("DELETE FROM media_cache WHERE id = :id")
    suspend fun deleteCacheById(id: Long)
    
    @Query("DELETE FROM media_cache WHERE mediaUrl = :url")
    suspend fun deleteCacheByUrl(url: String)
    
    @Transaction
    @Query("DELETE FROM media_cache WHERE expiresAt < :currentTime AND expiresAt IS NOT NULL")
    suspend fun deleteExpiredCache(currentTime: Long = System.currentTimeMillis()): Int
    
    @Transaction
    @Query("DELETE FROM media_cache WHERE id IN (SELECT id FROM media_cache WHERE isPermanent = 0 ORDER BY lastAccessedAt ASC LIMIT :count)")
    suspend fun deleteOldestTemporaryCache(count: Int): Int
    
    @Query("UPDATE media_cache SET lastAccessedAt = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE media_cache SET isPermanent = :isPermanent WHERE mediaUrl = :url")
    suspend fun updatePermanentStatus(url: String, isPermanent: Boolean)

    @Transaction
    @Query("DELETE FROM media_cache WHERE mediaUrl IN (:urls)")
    suspend fun deleteCacheByUrls(urls: List<String>): Int

    @Transaction
    @Query("UPDATE media_cache SET isPermanent = :isPermanent WHERE mediaUrl IN (:urls)")
    suspend fun batchUpdatePermanentStatus(urls: List<String>, isPermanent: Boolean): Int

    @Query("SELECT * FROM media_cache WHERE mediaUrl = :url AND (expiresAt IS NULL OR expiresAt > :currentTime)")
    suspend fun getValidCacheByUrl(url: String, currentTime: Long = System.currentTimeMillis()): MediaCacheEntity?

    @Query("""
        SELECT 
            COALESCE(SUM(fileSize), 0) as totalSize,
            COALESCE(SUM(CASE WHEN isPermanent = 0 THEN fileSize ELSE 0 END), 0) as temporarySize,
            COALESCE(SUM(CASE WHEN isPermanent = 1 THEN fileSize ELSE 0 END), 0) as permanentSize,
            COUNT(*) as fileCount
        FROM media_cache
    """)
    suspend fun getCacheStats(): CacheStats
}

data class CacheStats(
    val totalSize: Long,
    val temporarySize: Long,
    val permanentSize: Long,
    val fileCount: Int
)
