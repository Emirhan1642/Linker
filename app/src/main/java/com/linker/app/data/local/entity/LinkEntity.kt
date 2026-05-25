package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Link Entity - Posts (Feed/Video/Reels)
 * 
 * Represents a post in the feed (similar to Instagram posts or TikTok videos)
 */
@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["createdAt"], name = "idx_link_feed"),
        Index(value = ["authorId", "createdAt"], name = "idx_link_author"),
        Index(value = ["linkType", "createdAt"], name = "idx_link_type"),
        Index(value = ["isLiked", "createdAt"], name = "idx_liked_links"),
        Index(value = ["isSaved", "createdAt"], name = "idx_saved_links")
    ]
)
data class LinkEntity(
    @PrimaryKey
    val linkId: String,
    val authorId: String,
    val linkType: LinkType, // FEED, VIDEO, REEL
    val description: String?,
    val mediaUrls: List<String>, // Image/Video URLs
    val thumbnailUrl: String?,
    val videoDuration: Int? = null, // in seconds
    val aspectRatio: Float? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val relinksCount: Int = 0, // Repost count
    val savesCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isRelinked: Boolean = false,
    val location: String? = null,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val isCached: Boolean = false, // For offline viewing
    val isDeleted: Boolean = false
) {
    init {
        require(linkId.isNotBlank()) { "Link ID cannot be blank" }
        require(authorId.isNotBlank()) { "Author ID cannot be blank" }
        require(mediaUrls.isNotEmpty()) { "Link must have at least one media URL" }
        require(mediaUrls.size <= MAX_MEDIA_COUNT) { "Maximum $MAX_MEDIA_COUNT media items allowed" }
        
        when (linkType) {
            LinkType.VIDEO, LinkType.REEL -> {
                require(videoDuration != null) { "Video/Reel must have duration" }
                require(videoDuration > 0) { "Video duration must be positive" }
                require(videoDuration <= MAX_VIDEO_DURATION) { "Video duration cannot exceed ${MAX_VIDEO_DURATION}s" }
                require(thumbnailUrl != null) { "Video/Reel must have thumbnail" }
            }
            LinkType.FEED -> {}
        }
        
        aspectRatio?.let { require(it > 0) { "Aspect ratio must be positive" } }
        description?.let { require(it.length <= MAX_DESCRIPTION_LENGTH) { "Description too long" } }
        require(hashtags.size <= MAX_HASHTAGS) { "Maximum $MAX_HASHTAGS hashtags allowed" }
        require(mentions.size <= MAX_MENTIONS) { "Maximum $MAX_MENTIONS mentions allowed" }
        require(updatedAt >= createdAt) { "Updated timestamp cannot be before created timestamp" }
    }

    fun isVideo(): Boolean = linkType in listOf(LinkType.VIDEO, LinkType.REEL)

    fun hasMultipleMedia(): Boolean = mediaUrls.size > 1

    fun getEngagementScore(): Int {
        return likesCount + (commentsCount * 2) + (relinksCount * 3) + (savesCount * 4)
    }

    fun canBeEdited(currentUserId: String, timeLimit: Long = 900000L): Boolean {
        return authorId == currentUserId && 
               System.currentTimeMillis() - createdAt < timeLimit
    }

    fun getDisplayDescription(): String {
        return description?.take(100) ?: ""
    }

    fun getFirstMediaUrl(): String? = mediaUrls.firstOrNull()

    companion object {
        const val MAX_MEDIA_COUNT = 10
        const val MAX_DESCRIPTION_LENGTH = 2200
        const val MAX_VIDEO_DURATION = 600
        const val MAX_HASHTAGS = 30
        const val MAX_MENTIONS = 20
    }
}

enum class LinkType {
    FEED,   // Instagram-style photo posts
    VIDEO,  // TikTok-style short videos
    REEL    // Instagram Reels
}
