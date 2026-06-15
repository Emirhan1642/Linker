package com.linker.app.domain.model

/**
 * Domain model for a Story (24-hour expiring media).
 *
 * Stories are ephemeral media items (image or video) that expire within
 * 24 hours of creation. They appear in the story bar above the chat list.
 *
 * @property storyId Unique story identifier.
 * @property author Lightweight author reference (use [StoryAuthor.from] to create from [User]).
 * @property mediaUrl URL to the media file (image or video).
 * @property mediaType Whether this is an IMAGE or VIDEO story.
 * @property thumbnailUrl Thumbnail preview URL (required for video stories).
 * @property duration Duration in seconds for video stories (null for images).
 * @property caption Optional text overlay on the story.
 * @property viewsCount Number of times this story has been viewed.
 * @property likesCount Number of likes on this story.
 * @property isViewed Whether the current user has viewed this story.
 * @property isLiked Whether the current user has liked this story.
 * @property reactionEmoji The emoji reaction the current user sent (null if none).
 * @property viewedAt Timestamp when the current user viewed this story (epoch ms, null if not viewed).
 * @property createdAt Creation timestamp (epoch ms).
 * @property expiresAt Expiration timestamp (epoch ms).
 */
data class Story(
    val storyId: String,
    val author: StoryAuthor,
    val mediaUrl: String,
    val mediaType: StoryMediaType,
    val thumbnailUrl: String?,
    val duration: Int?,
    val caption: String?,
    val viewsCount: Int,
    val likesCount: Int = 0,
    val isViewed: Boolean,
    val isLiked: Boolean = false,
    val reactionEmoji: String? = null,
    val viewedAt: Long? = null,
    val createdAt: Long,
    val expiresAt: Long
) {
    init {
        require(storyId.isNotBlank()) { "storyId cannot be blank" }
        require(mediaUrl.isNotBlank()) { "mediaUrl cannot be blank" }
        require(viewsCount >= 0) { "viewsCount cannot be negative" }
        require(likesCount >= 0) { "likesCount cannot be negative" }
        require(createdAt > 0) { "createdAt must be positive" }
        require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
        require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
            "Story cannot expire more than 24 hours after creation"
        }
        caption?.let {
            require(it.length <= MAX_CAPTION_LENGTH) {
                "Caption exceeds maximum length of $MAX_CAPTION_LENGTH"
            }
        }
        if (mediaType == StoryMediaType.VIDEO) {
            require(duration != null && duration > 0) {
                "Video stories must have a positive duration"
            }
        }
        viewedAt?.let {
            require(it > 0) { "viewedAt must be positive when set" }
        }
    }

    /** Whether this story has expired and should no longer be displayed. */
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    /**
     * Returns the remaining time before expiration in milliseconds.
     * Returns 0 if already expired.
     */
    fun getRemainingTimeMs(): Long {
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /** Whether this story is expiring within the next hour. */
    fun isExpiringSoon(): Boolean {
        val remaining = getRemainingTimeMs()
        return remaining in 1..EXPIRING_SOON_THRESHOLD_MS
    }

    /**
     * Returns a human-readable display duration for the story.
     * Images use [DEFAULT_IMAGE_DISPLAY_SECONDS], videos use their [duration].
     */
    fun getDisplayDuration(): Int = when (mediaType) {
        StoryMediaType.IMAGE -> DEFAULT_IMAGE_DISPLAY_SECONDS
        StoryMediaType.VIDEO -> duration ?: DEFAULT_IMAGE_DISPLAY_SECONDS
    }

    /**
     * Returns a copy with the story marked as liked/unliked.
     */
    fun toggleLike(): Story = copy(
        isLiked = !isLiked,
        likesCount = if (isLiked) maxOf(0, likesCount - 1) else likesCount + 1
    )

    /**
     * Returns a copy with the given emoji reaction set (or cleared if null).
     */
    fun withReaction(emoji: String?): Story = copy(reactionEmoji = emoji)

    /**
     * Returns a copy with the story marked as viewed now.
     */
    fun markAsViewed(): Story = copy(
        isViewed = true,
        viewedAt = System.currentTimeMillis()
    )

    /**
     * Returns milliseconds since the story was viewed, or null if not viewed.
     */
    fun getTimeSinceViewed(): Long? {
        return viewedAt?.let { System.currentTimeMillis() - it }
    }

    companion object {
        /** Maximum expiration duration: 24 hours in ms. */
        const val MAX_EXPIRATION_DURATION_MS = 24L * 60 * 60 * 1000

        /** Threshold for "expiring soon" indicator: 1 hour. */
        const val EXPIRING_SOON_THRESHOLD_MS = 60L * 60 * 1000

        /** Maximum caption length. */
        const val MAX_CAPTION_LENGTH = 200

        /** Default display duration for image stories in seconds. */
        const val DEFAULT_IMAGE_DISPLAY_SECONDS = 5
    }
}

/**
 * Story media type with display metadata.
 *
 * @property displayName Human-readable name.
 * @property iconName Icon resource name.
 * @property maxDurationSeconds Maximum allowed duration in seconds (null for images).
 * @property requiresThumbnail Whether a thumbnail must be provided.
 */
enum class StoryMediaType(
    val displayName: String,
    val iconName: String,
    val maxDurationSeconds: Int?,
    val requiresThumbnail: Boolean
) {
    /** Static image story. */
    IMAGE("Image", "ic_image", null, false),
    /** Video story. */
    VIDEO("Video", "ic_videocam", 60, true);

    /** Whether this type requires a duration field. */
    fun requiresDuration(): Boolean = this == VIDEO
}

/**
 * Supported emoji reactions for Stories.
 */
enum class StoryReaction(val emoji: String) {
    HEART("❤️"),
    LAUGH("😂"),
    WOW("😮"),
    SAD("😢"),
    CLAP("👏"),
    FIRE("🔥")
}

/**
 * Groups a user's stories together for display in the story bar.
 *
 * @property author Lightweight author reference for the story owner.
 * @property stories List of stories, typically ordered by creation time.
 */
data class UserStories(
    val author: StoryAuthor,
    val stories: List<Story>
) {
    init {
        require(stories.isNotEmpty()) { "UserStories must contain at least one story" }
    }

    /** Whether any story in this group is unviewed by the current user. */
    val hasUnviewed: Boolean
        get() = stories.any { !it.isViewed }

    /** Returns the first unviewed story, or null if all are viewed. */
    fun getFirstUnviewedStory(): Story? = stories.firstOrNull { !it.isViewed }

    /** Returns the most recently created story. */
    fun getMostRecentStory(): Story? = stories.maxByOrNull { it.createdAt }

    /** Whether all stories in this group have expired. */
    fun areAllExpired(): Boolean = stories.all { it.isExpired() }

    /** Returns only the non-expired stories. */
    fun getActiveStories(): List<Story> = stories.filter { !it.isExpired() }
}
