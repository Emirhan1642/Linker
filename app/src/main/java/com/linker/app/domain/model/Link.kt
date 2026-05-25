package com.linker.app.domain.model

/**
 * Engagement metrics for a Link post.
 *
 * Groups all engagement-related counts and user interaction flags.
 * All counts are guaranteed non-negative via [init] validation.
 *
 * @property likesCount Number of likes.
 * @property commentsCount Number of comments.
 * @property sharesCount Number of shares.
 * @property relinksCount Number of relinks (reposts).
 * @property savesCount Number of saves.
 * @property viewsCount Number of views.
 * @property isLiked Whether the current user has liked this post.
 * @property isSaved Whether the current user has saved this post.
 * @property isRelinked Whether the current user has relinked this post.
 */
data class EngagementMetrics(
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val relinksCount: Int = 0,
    val savesCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isRelinked: Boolean = false
) {
    init {
        require(likesCount >= 0) { "likesCount cannot be negative" }
        require(commentsCount >= 0) { "commentsCount cannot be negative" }
        require(sharesCount >= 0) { "sharesCount cannot be negative" }
        require(relinksCount >= 0) { "relinksCount cannot be negative" }
        require(savesCount >= 0) { "savesCount cannot be negative" }
        require(viewsCount >= 0) { "viewsCount cannot be negative" }
    }

    /** Total engagement count (likes + comments + shares + relinks + saves). */
    val totalEngagement: Int
        get() = likesCount + commentsCount + sharesCount + relinksCount + savesCount

    /** Whether the current user has interacted with this post in any way. */
    val hasUserEngaged: Boolean
        get() = isLiked || isSaved || isRelinked
}

/**
 * Represents a media item in a Link post.
 *
 * Supports images, videos, and GIFs with type-specific metadata.
 */
sealed class MediaItem {
    abstract val url: String
    abstract val aspectRatio: Float?

    /**
     * Static image media item.
     *
     * @property url Image URL.
     * @property aspectRatio Image aspect ratio (width/height).
     * @property width Image width in pixels.
     * @property height Image height in pixels.
     */
    data class Image(
        override val url: String,
        override val aspectRatio: Float?,
        val width: Int?,
        val height: Int?
    ) : MediaItem() {
        init {
            require(url.isNotBlank()) { "Image URL cannot be blank" }
            aspectRatio?.let {
                require(it > 0f) { "aspectRatio must be positive when set" }
            }
            width?.let {
                require(it > 0) { "width must be positive when set" }
            }
            height?.let {
                require(it > 0) { "height must be positive when set" }
            }
        }
    }

    /**
     * Video media item.
     *
     * @property url Video URL.
     * @property aspectRatio Video aspect ratio (width/height).
     * @property thumbnailUrl Preview thumbnail URL.
     * @property duration Video duration in seconds.
     * @property width Video width in pixels.
     * @property height Video height in pixels.
     */
    data class Video(
        override val url: String,
        override val aspectRatio: Float?,
        val thumbnailUrl: String?,
        val duration: Int,
        val width: Int?,
        val height: Int?
    ) : MediaItem() {
        init {
            require(url.isNotBlank()) { "Video URL cannot be blank" }
            require(duration > 0) { "Video duration must be positive" }
            aspectRatio?.let {
                require(it > 0f) { "aspectRatio must be positive when set" }
            }
            width?.let {
                require(it > 0) { "width must be positive when set" }
            }
            height?.let {
                require(it > 0) { "height must be positive when set" }
            }
        }
    }

    /**
     * Animated GIF media item.
     *
     * @property url GIF URL.
     * @property aspectRatio GIF aspect ratio (width/height).
     * @property width GIF width in pixels.
     * @property height GIF height in pixels.
     */
    data class Gif(
        override val url: String,
        override val aspectRatio: Float?,
        val width: Int?,
        val height: Int?
    ) : MediaItem() {
        init {
            require(url.isNotBlank()) { "GIF URL cannot be blank" }
            aspectRatio?.let {
                require(it > 0f) { "aspectRatio must be positive when set" }
            }
            width?.let {
                require(it > 0) { "width must be positive when set" }
            }
            height?.let {
                require(it > 0) { "height must be positive when set" }
            }
        }
    }
}

/**
 * Domain model for Link (Post).
 *
 * Represents a post in the feed: photo carousel, short video, or reel.
 * All engagement counts are guaranteed non-negative via [EngagementMetrics] validation.
 *
 * @property linkId Unique post identifier (Firestore document ID).
 * @property author Lightweight author reference (use [LinkAuthor.from] to create from [User]).
 * @property linkType Content type (FEED, VIDEO, or REEL).
 * @property description Post caption/description (nullable, up to [MAX_DESCRIPTION_LENGTH] chars).
 * @property mediaItems List of media items (images/videos/GIFs).
 * @property engagement Engagement metrics (likes, comments, shares, etc.).
 * @property location Location tag (nullable).
 * @property hashtags List of hashtags (up to [MAX_HASHTAGS]).
 * @property mentions List of mentioned usernames (up to [MAX_MENTIONS]).
 * @property createdAt Creation timestamp (epoch ms).
 * @property updatedAt Last update timestamp (epoch ms).
 */
data class Link(
    val linkId: String,
    val author: LinkAuthor,
    val linkType: LinkType = LinkType.FEED,
    val description: String? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val engagement: EngagementMetrics = EngagementMetrics(),
    val location: String? = null,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(linkId.isNotBlank()) { "linkId cannot be blank" }
        require(createdAt > 0) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot be before createdAt" }
        require(mediaItems.isNotEmpty()) { "Link must have at least one media item" }
        require(mediaItems.size <= MAX_MEDIA_COUNT) { "Media items exceed maximum of $MAX_MEDIA_COUNT" }
        require(hashtags.size <= MAX_HASHTAGS) { "Hashtags exceed maximum of $MAX_HASHTAGS" }
        require(mentions.size <= MAX_MENTIONS) { "Mentions exceed maximum of $MAX_MENTIONS" }
        description?.let {
            require(it.length <= MAX_DESCRIPTION_LENGTH) { "Description exceeds maximum length of $MAX_DESCRIPTION_LENGTH" }
        }
    }

    /** Primary media item (first in the list). */
    val primaryMedia: MediaItem
        get() = mediaItems.first()

    /** Whether this post contains video content. */
    val hasVideo: Boolean
        get() = mediaItems.any { it is MediaItem.Video }

    /** Whether this post contains only images. */
    val isImageOnly: Boolean
        get() = mediaItems.all { it is MediaItem.Image }

    /** Total video duration in seconds (sum of all video items). */
    val totalVideoDuration: Int
        get() = mediaItems.filterIsInstance<MediaItem.Video>().sumOf { it.duration }

    companion object {
        /** Maximum number of media items in a single post. */
        const val MAX_MEDIA_COUNT = 10

        /** Maximum description/caption length. */
        const val MAX_DESCRIPTION_LENGTH = 2200

        /** Maximum video duration in seconds (10 minutes). */
        const val MAX_VIDEO_DURATION_SECONDS = 600

        /** Maximum reel duration in seconds (90 seconds). */
        const val MAX_REEL_DURATION_SECONDS = 90

        /** Minimum allowed aspect ratio. */
        const val MIN_ASPECT_RATIO = 0.5f

        /** Maximum allowed aspect ratio. */
        const val MAX_ASPECT_RATIO = 2.0f

        /** Maximum hashtags per post. */
        const val MAX_HASHTAGS = 30

        /** Maximum mentions per post. */
        const val MAX_MENTIONS = 20

        /**
         * Creates a feed post (photo carousel or video).
         *
         * @param linkId Unique post identifier.
         * @param author Post author.
         * @param mediaItems List of media items (up to 10).
         * @param description Optional caption.
         * @param location Optional location tag.
         * @param hashtags List of hashtags.
         * @param mentions List of mentioned usernames.
         * @param createdAt Creation timestamp.
         * @return A [Link] configured as a FEED post.
         */
        fun createFeedPost(
            linkId: String,
            author: LinkAuthor,
            mediaItems: List<MediaItem>,
            description: String? = null,
            location: String? = null,
            hashtags: List<String> = emptyList(),
            mentions: List<String> = emptyList(),
            createdAt: Long = System.currentTimeMillis()
        ): Link {
            require(mediaItems.isNotEmpty()) { "Feed post must have at least one media item" }
            require(mediaItems.size <= MAX_MEDIA_COUNT) { "Feed post cannot have more than $MAX_MEDIA_COUNT media items" }

            // Validate video duration for feed posts
            val totalVideoDuration = mediaItems.filterIsInstance<MediaItem.Video>().sumOf { it.duration }
            if (totalVideoDuration > 0) {
                require(totalVideoDuration <= MAX_VIDEO_DURATION_SECONDS) {
                    "Total video duration ($totalVideoDuration s) exceeds maximum of $MAX_VIDEO_DURATION_SECONDS s"
                }
            }

            return Link(
                linkId = linkId,
                author = author,
                linkType = LinkType.FEED,
                description = description,
                mediaItems = mediaItems,
                engagement = EngagementMetrics(),
                location = location,
                hashtags = hashtags,
                mentions = mentions,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }

        /**
         * Creates a video post (single video up to 10 minutes).
         *
         * @param linkId Unique post identifier.
         * @param author Post author.
         * @param video Video media item.
         * @param description Optional caption.
         * @param location Optional location tag.
         * @param hashtags List of hashtags.
         * @param mentions List of mentioned usernames.
         * @param createdAt Creation timestamp.
         * @return A [Link] configured as a VIDEO post.
         */
        fun createVideoPost(
            linkId: String,
            author: LinkAuthor,
            video: MediaItem.Video,
            description: String? = null,
            location: String? = null,
            hashtags: List<String> = emptyList(),
            mentions: List<String> = emptyList(),
            createdAt: Long = System.currentTimeMillis()
        ): Link {
            require(video.duration <= MAX_VIDEO_DURATION_SECONDS) {
                "Video duration (${video.duration} s) exceeds maximum of $MAX_VIDEO_DURATION_SECONDS s"
            }

            return Link(
                linkId = linkId,
                author = author,
                linkType = LinkType.VIDEO,
                description = description,
                mediaItems = listOf(video),
                engagement = EngagementMetrics(),
                location = location,
                hashtags = hashtags,
                mentions = mentions,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }

        /**
         * Creates a reel (short-form video up to 90 seconds).
         *
         * @param linkId Unique post identifier.
         * @param author Post author.
         * @param video Video media item (must be ≤ 90 seconds).
         * @param description Optional caption.
         * @param location Optional location tag.
         * @param hashtags List of hashtags.
         * @param mentions List of mentioned usernames.
         * @param createdAt Creation timestamp.
         * @return A [Link] configured as a REEL.
         */
        fun createReel(
            linkId: String,
            author: LinkAuthor,
            video: MediaItem.Video,
            description: String? = null,
            location: String? = null,
            hashtags: List<String> = emptyList(),
            mentions: List<String> = emptyList(),
            createdAt: Long = System.currentTimeMillis()
        ): Link {
            require(video.duration <= MAX_REEL_DURATION_SECONDS) {
                "Reel duration (${video.duration} s) exceeds maximum of $MAX_REEL_DURATION_SECONDS s"
            }

            return Link(
                linkId = linkId,
                author = author,
                linkType = LinkType.REEL,
                description = description,
                mediaItems = listOf(video),
                engagement = EngagementMetrics(),
                location = location,
                hashtags = hashtags,
                mentions = mentions,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }
}

/**
 * Post content type discriminator with media constraints.
 *
 * @property displayName Human-readable name.
 * @property iconName Icon resource name.
 * @property maxMediaCount Maximum number of media items allowed.
 * @property maxDurationSeconds Maximum video duration in seconds (null for no video).
 * @property allowsMultipleMedia Whether multiple media items are supported.
 */
enum class LinkType(
    val displayName: String,
    val iconName: String,
    val maxMediaCount: Int,
    val maxDurationSeconds: Int?,
    val allowsMultipleMedia: Boolean
) {
    /** Standard feed post: supports photo carousel up to 10 images or a single video up to 10 min. */
    FEED("Post", "ic_grid", 10, 600, true),
    /** Video post: single video up to 10 minutes. */
    VIDEO("Video", "ic_play", 1, 600, false),
    /** Short-form reel: single video up to 90 seconds. */
    REEL("Reel", "ic_reel", 1, 90, false);

    /** Whether this type supports multiple media items. */
    fun supportsMultipleMedia(): Boolean = allowsMultipleMedia

    /** Whether this type requires a video file. */
    fun requiresVideo(): Boolean = this == VIDEO || this == REEL
}
