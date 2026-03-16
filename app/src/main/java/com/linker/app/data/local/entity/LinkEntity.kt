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
        Index(value = ["authorId"]),
        Index(value = ["createdAt"]),
        Index(value = ["linkType"])
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
    val isCached: Boolean = false // For offline viewing
)

enum class LinkType {
    FEED,   // Instagram-style photo posts
    VIDEO,  // TikTok-style short videos
    REEL    // Instagram Reels
}
