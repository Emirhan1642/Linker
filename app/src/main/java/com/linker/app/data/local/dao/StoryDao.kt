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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)
    
    @Update
    suspend fun updateStory(story: StoryEntity)
    
    @Delete
    suspend fun deleteStory(story: StoryEntity)
    
    @Query("DELETE FROM stories WHERE storyId = :storyId")
    suspend fun deleteStoryById(storyId: String)
    
    @Query("DELETE FROM stories WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long = System.currentTimeMillis())
    
    @Query("UPDATE stories SET isViewed = 1, viewsCount = viewsCount + 1 WHERE storyId = :storyId")
    suspend fun markAsViewed(storyId: String)
    
    @Query("SELECT COUNT(*) FROM stories WHERE authorId = :authorId AND expiresAt > :currentTime")
    suspend fun getActiveStoryCount(authorId: String, currentTime: Long = System.currentTimeMillis()): Int
}
