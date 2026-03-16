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
    
    @Query("SELECT * FROM links WHERE linkId = :linkId")
    suspend fun getLinkById(linkId: String): LinkEntity?
    
    @Query("SELECT * FROM links WHERE linkId = :linkId")
    fun observeLinkById(linkId: String): Flow<LinkEntity?>
    
    @Query("SELECT * FROM links WHERE authorId = :authorId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getLinksByAuthor(authorId: String, limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links WHERE authorId = :authorId ORDER BY createdAt DESC")
    fun observeLinksByAuthor(authorId: String): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE linkType = :linkType ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getLinksByType(linkType: LinkType, limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllLinks(limit: Int = 20, offset: Int = 0): List<LinkEntity>
    
    @Query("SELECT * FROM links ORDER BY createdAt DESC")
    fun observeAllLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isLiked = 1 ORDER BY createdAt DESC")
    fun observeLikedLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isSaved = 1 ORDER BY createdAt DESC")
    fun observeSavedLinks(): Flow<List<LinkEntity>>
    
    @Query("SELECT * FROM links WHERE isRelinked = 1 ORDER BY createdAt DESC")
    fun observeRelinkedLinks(): Flow<List<LinkEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)
    
    @Update
    suspend fun updateLink(link: LinkEntity)
    
    @Delete
    suspend fun deleteLink(link: LinkEntity)
    
    @Query("DELETE FROM links WHERE linkId = :linkId")
    suspend fun deleteLinkById(linkId: String)
    
    @Query("UPDATE links SET isLiked = :isLiked, likesCount = likesCount + :delta WHERE linkId = :linkId")
    suspend fun updateLikeStatus(linkId: String, isLiked: Boolean, delta: Int)
    
    @Query("UPDATE links SET isSaved = :isSaved WHERE linkId = :linkId")
    suspend fun updateSaveStatus(linkId: String, isSaved: Boolean)
    
    @Query("UPDATE links SET isRelinked = :isRelinked, relinksCount = relinksCount + :delta WHERE linkId = :linkId")
    suspend fun updateRelinkStatus(linkId: String, isRelinked: Boolean, delta: Int)
    
    @Query("UPDATE links SET commentsCount = :count WHERE linkId = :linkId")
    suspend fun updateCommentsCount(linkId: String, count: Int)
    
    @Query("SELECT COUNT(*) FROM links WHERE authorId = :authorId")
    suspend fun getLinkCountByAuthor(authorId: String): Int
}
