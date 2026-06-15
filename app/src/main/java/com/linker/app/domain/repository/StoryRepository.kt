package com.linker.app.domain.repository

import com.linker.app.domain.model.Story
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.domain.model.ReportReason
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Privacy settings for a story.
 */
enum class StoryPrivacy {
    PUBLIC,
    FOLLOWERS_ONLY,
    CLOSE_FRIENDS
}

/**
 * Viewer details for a story.
 */
data class StoryViewer(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val viewedAt: Long,
    val hasLiked: Boolean
)

/**
 * A story highlight group.
 */
data class StoryHighlight(
    val id: String,
    val title: String,
    val coverImageUrl: String?,
    val stories: List<Story>
)

interface StoryRepository {

    // ── Feed & Creation ────────────────────────────────────────────────────

    /** 
     * Observes the story bar — all active stories grouped per user. 
     * Uses cursor-based pagination internally.
     */
    fun observeActiveUserStories(): Flow<Result<List<UserStories>>>

    /** Refresh the story feed. */
    suspend fun refreshStories(limit: Int = 20): Result<List<UserStories>>
    
    /** Load more stories for the feed using cursor-based pagination. */
    suspend fun loadMoreStories(beforeTimestamp: Long, limit: Int = 20): Result<List<UserStories>>

    /** Returns stories for a specific user. */
    suspend fun getStoriesByUser(userId: String): Result<List<Story>>

    /** 
     * Creates a new story. 
     * 
     * Security:
     * - Media files are scanned for malware before upload.
     * - EXIF data (including GPS coordinates) is stripped.
     * - Video length is restricted to 60 seconds maximum.
     */
    suspend fun createStory(
        mediaLocalPath: String,
        mediaType: StoryMediaType,
        caption: String? = null,
        privacy: StoryPrivacy = StoryPrivacy.FOLLOWERS_ONLY
    ): Result<Story>

    /** 
     * Deletes a story. 
     * 
     * Permissions:
     * - Only the author can delete their story.
     */
    suspend fun deleteStory(storyId: String): Result<Unit>

    /** Cleans up expired stories from the local cache. */
    suspend fun purgeExpiredStories(): Result<Unit>

    // ── Interaction ────────────────────────────────────────────────────────

    /** Marks a story as viewed. */
    suspend fun markStoryAsViewed(storyId: String): Result<Unit>

    /** Gets the total view count for a story. */
    suspend fun getViewCount(storyId: String): Result<Int>

    /** Gets the list of users who viewed the story (only visible to author). */
    suspend fun getViewers(storyId: String): Result<List<StoryViewer>>

    /** Reply to a story via direct message. */
    suspend fun replyToStory(storyId: String, content: String): Result<Unit>
    
    /** Get the number of replies a story received. */
    suspend fun getReplyCount(storyId: String): Result<Int>

    // ── Privacy & Close Friends ────────────────────────────────────────────

    /** Update the privacy setting of an existing story. */
    suspend fun updateStoryPrivacy(storyId: String, privacy: StoryPrivacy): Result<Unit>

    /** Update the user's close friends list. */
    suspend fun updateCloseFriendsList(userIds: List<String>): Result<Unit>
    
    /** Get the user's close friends list. */
    suspend fun getCloseFriendsList(): Result<List<String>>

    // ── Highlights ─────────────────────────────────────────────────────────

    /** Add a story to a highlight reel. */
    suspend fun addToHighlight(storyId: String, highlightId: String): Result<Unit>
    
    /** Remove a story from a highlight reel. */
    suspend fun removeFromHighlight(storyId: String, highlightId: String): Result<Unit>
    
    /** Get highlight reels for a specific user. */
    suspend fun getHighlights(userId: String): Result<List<StoryHighlight>>
    
    /** Create a new highlight reel. */
    suspend fun createHighlight(title: String, coverStoryId: String? = null): Result<StoryHighlight>

    // ── Engagement & Safety ────────────────────────────────────────────────

    /**
     * Toggles like on a Story.
     * Likes are visible to everyone; counts are public.
     * @return Result containing true if liked, false if unliked.
     */
    suspend fun toggleLikeStory(storyId: String): Result<Boolean>

    /**
     * Sends or clears an emoji reaction on a story.
     * @param emoji The emoji character, or null to clear the reaction.
     */
    suspend fun reactToStory(storyId: String, emoji: String?): Result<Unit>

    /**
     * Reports a story for policy violations.
     * @param reason The report reason selected by the user.
     */
    suspend fun reportStory(storyId: String, reason: ReportReason): Result<Unit>

    /**
     * Returns a shareable deep-link URL for the given story.
     * The link navigates back to the story viewer when opened on a device
     * with Linker installed, or falls back to a web preview.
     */
    suspend fun getShareableLink(storyId: String): Result<String>
}
