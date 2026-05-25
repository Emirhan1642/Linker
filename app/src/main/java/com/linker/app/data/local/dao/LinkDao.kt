package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.LinkEntity
import com.linker.app.data.local.entity.LinkType
import kotlinx.coroutines.flow.Flow

/**
 * Link DAO - Data Access Object for posts
 */
@Dao
interface LinkDao {
    
    @Query("SELECT * FROM links WHERE linkId = :linkId AND isDeleted = 0")
    suspend fun getLinkById(linkId: String): LinkEntity?
    
    @Query("SELECT * FROM links WHERE linkId = :linkId AND isDeleted = 0")
    fun observeLinkById(linkId: String): Flow<LinkEntity?>
    
    @Query("SELECT * FROM links WHERE authorId = :authorId AND isDeleted = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getLinksByAuthor(authorId: String, limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links WHERE authorId = :authorId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeLinksByAuthor(authorId: String): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE linkType = :linkType AND isDeleted = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getLinksByType(linkType: LinkType, limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links WHERE isDeleted = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllLinks(limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAllLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isLiked = 1 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeLikedLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isSaved = 1 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeSavedLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isRelinked = 1 AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeRelinkedLinks(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE isDeleted = 0 AND createdAt < :beforeTimestamp ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLinksBeforeTimestamp(beforeTimestamp: Long, limit: Int = 20): List<LinkEntity>

    @Query("SELECT * FROM links WHERE isDeleted = 0 ORDER BY likesCount DESC, commentsCount DESC LIMIT :limit")
    suspend fun getTrendingLinks(limit: Int = 20): List<LinkEntity>

    @Query("SELECT * FROM links WHERE isDeleted = 0 AND (description LIKE '%' || :query || '%' ESCAPE '\\') ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchLinks(query: String, limit: Int = 20): List<LinkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkEntity)
    
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)
    
    @Update
    suspend fun updateLink(link: LinkEntity)
    
    @Delete
    suspend fun deleteLink(link: LinkEntity)
    
    @Query("DELETE FROM links WHERE linkId = :linkId")
    suspend fun deleteLinkById(linkId: String)
    
    @Transaction
    @Query("UPDATE links SET isLiked = :isLiked, likesCount = MAX(0, likesCount + :delta), updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun updateLikeStatus(linkId: String, isLiked: Boolean, delta: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE links SET isSaved = :isSaved, updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun updateSaveStatus(linkId: String, isSaved: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Transaction
    @Query("UPDATE links SET isRelinked = :isRelinked, relinksCount = MAX(0, relinksCount + :delta), updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun updateRelinkStatus(linkId: String, isRelinked: Boolean, delta: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE links SET commentsCount = MAX(0, :count), updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun updateCommentsCount(linkId: String, count: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM links WHERE authorId = :authorId AND isDeleted = 0")
    suspend fun getLinkCountByAuthor(authorId: String): Int

    @Transaction
    @Query("UPDATE links SET isLiked = :isLiked, likesCount = MAX(0, likesCount + :delta), updatedAt = :timestamp WHERE linkId IN (:linkIds)")
    suspend fun batchUpdateLikeStatus(linkIds: List<String>, isLiked: Boolean, delta: Int, timestamp: Long = System.currentTimeMillis())

    @Transaction
    @Query("DELETE FROM links WHERE linkId IN (:linkIds)")
    suspend fun batchDeleteLinks(linkIds: List<String>)

    @Query("UPDATE links SET isDeleted = 1, updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun softDeleteLink(linkId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE links SET isDeleted = 0, updatedAt = :timestamp WHERE linkId = :linkId")
    suspend fun restoreLink(linkId: String, timestamp: Long = System.currentTimeMillis())
}
