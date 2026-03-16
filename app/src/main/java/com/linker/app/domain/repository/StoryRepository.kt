package com.linker.app.domain.repository

import com.linker.app.domain.model.Story
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface StoryRepository {

    /** Observes the story bar — all active stories grouped per user. */
    fun observeActiveUserStories(): Flow<List<UserStories>>

    /** Returns stories for a specific user. */
    suspend fun getStoriesByUser(userId: String): Result<List<Story>>

    /** Creates a new story. */
    suspend fun createStory(
        mediaLocalPath: String,
        mediaType: StoryMediaType,
        caption: String?
    ): Result<Story>

    /** Marks a story as viewed. */
    suspend fun markStoryAsViewed(storyId: String): Result<Unit>

    /** Deletes a story. */
    suspend fun deleteStory(storyId: String): Result<Unit>

    /** Cleans up expired stories from the local cache. */
    suspend fun purgeExpiredStories(): Result<Unit>
}
