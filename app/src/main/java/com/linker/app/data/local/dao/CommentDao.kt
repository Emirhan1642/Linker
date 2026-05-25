package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Comment DAO - Data Access Object for comments
 */
@Dao
interface CommentDao {
    
    @Query("SELECT * FROM comments WHERE commentId = :commentId")
    suspend fun getCommentById(commentId: String): CommentEntity?
    
    @Query("SELECT * FROM comments WHERE linkId = :linkId AND parentCommentId IS NULL ORDER BY isPinned DESC, createdAt DESC")
    fun observeTopLevelComments(linkId: String): Flow<List<CommentEntity>>
    
    @Query("SELECT * FROM comments WHERE linkId = :linkId AND parentCommentId IS NULL ORDER BY isPinned DESC, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getTopLevelComments(linkId: String, limit: Int = 20, offset: Int = 0): List<CommentEntity>
    
    @Query("SELECT * FROM comments WHERE parentCommentId = :parentId ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getRepliesPaginated(parentId: String, limit: Int = 20, offset: Int = 0): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE parentCommentId IN (:parentIds) ORDER BY parentCommentId, createdAt ASC")
    suspend fun getRepliesForComments(parentIds: List<String>): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE parentCommentId = :parentId ORDER BY createdAt ASC")
    suspend fun getReplies(parentId: String): List<CommentEntity>
    
    @Query("SELECT * FROM comments WHERE parentCommentId = :parentId ORDER BY createdAt ASC")
    fun observeReplies(parentId: String): Flow<List<CommentEntity>>
    
    @Query("SELECT * FROM comments WHERE authorId = :authorId ORDER BY createdAt DESC")
    suspend fun getCommentsByAuthor(authorId: String): List<CommentEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)
    
    @Update
    suspend fun updateComment(comment: CommentEntity)
    
    @Delete
    suspend fun deleteComment(comment: CommentEntity)
    
    @Query("DELETE FROM comments WHERE commentId = :commentId")
    suspend fun deleteCommentById(commentId: String)
    
    @Transaction
    @Query("DELETE FROM comments WHERE commentId = :commentId OR parentCommentId = :commentId")
    suspend fun deleteCommentAndReplies(commentId: String)
    
    @Transaction
    @Query("UPDATE comments SET isLiked = :isLiked, likesCount = MAX(0, likesCount + :delta), updatedAt = :timestamp WHERE commentId = :commentId")
    suspend fun updateLikeStatus(commentId: String, isLiked: Boolean, delta: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE comments SET repliesCount = MAX(0, :count), updatedAt = :timestamp WHERE commentId = :commentId")
    suspend fun updateRepliesCount(commentId: String, count: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM comments WHERE linkId = :linkId")
    suspend fun getCommentCount(linkId: String): Int
    
    @Query("SELECT COUNT(*) FROM comments WHERE parentCommentId = :parentId")
    suspend fun getReplyCount(parentId: String): Int
}
