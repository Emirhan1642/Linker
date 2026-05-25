package com.linker.app.data.local.dao

import androidx.room.*
import com.linker.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Story DAO - Data Access Object for stories
 */
@Dao
interface StoryDao {
    
    @Query("SELECT * FROM stories WHERE storyId = :storyId")
    suspend fun getStoryById(storyId: String): StoryEntity?
    
    @Query("SELECT * FROM stories WHERE authorId = :authorId AND expiresAt > :currentTime ORDER BY createdAt DESC")
    suspend fun getStoriesByAuthor(authorId: String, currentTime: Long = System.currentTimeMillis()): List<StoryEntity>
    
    @Query("SELECT * FROM stories WHERE authorId IN (:authorIds) AND expiresAt > :currentTime ORDER BY createdAt DESC")
    suspend fun getStoriesByAuthors(authorIds: List<String>, currentTime: Long = System.currentTimeMillis()): List<StoryEntity>
    
    @Query("SELECT * FROM stories WHERE authorId = :authorId AND expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeStoriesByAuthor(authorId: String, currentTime: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>
    
    @Query("SELECT * FROM stories WHERE expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeActiveStories(currentTime: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>
    
    @Query("SELECT DISTINCT authorId FROM stories WHERE expiresAt > :currentTime")
    suspend fun getAuthorsWithActiveStories(currentTime: Long = System.currentTimeMillis()): List<String>
    
    @Query("SELECT DISTINCT authorId FROM stories WHERE expiresAt > :currentTime")
    fun observeAuthorsWithActiveStories(currentTime: Long = System.currentTimeMillis()): Flow<List<String>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)
    
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)
    
    @Update
    suspend fun updateStory(story: StoryEntity)
    
    @Delete
    suspend fun deleteStory(story: StoryEntity)
    
    @Query("DELETE FROM stories WHERE storyId = :storyId")
    suspend fun deleteStoryById(storyId: String)
    
    @Transaction
    @Query("DELETE FROM stories WHERE storyId IN (:storyIds)")
    suspend fun batchDeleteStories(storyIds: List<String>)
    
    @Transaction
    @Query("DELETE FROM stories WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long = System.currentTimeMillis()): Int
    
    @Transaction
    @Query("UPDATE stories SET isViewed = 1, viewsCount = MAX(0, viewsCount + 1) WHERE storyId = :storyId")
    suspend fun markAsViewed(storyId: String)
    
    @Transaction
    @Query("UPDATE stories SET isViewed = 1, viewsCount = MAX(0, viewsCount + 1) WHERE storyId IN (:storyIds)")
    suspend fun batchMarkAsViewed(storyIds: List<String>)
    
    @Query("SELECT COUNT(*) FROM stories WHERE authorId = :authorId AND expiresAt > :currentTime")
    suspend fun getActiveStoryCount(authorId: String, currentTime: Long = System.currentTimeMillis()): Int
}
