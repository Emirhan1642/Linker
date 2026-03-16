package com.linker.app.domain.model

/**
 * Domain model for Link (Post)
 * 
 * Represents a post in the feed: photo carousel, short video, or reel.
 */
data class Link(
    val linkId: String,
    val author: User,
    val linkType: LinkType,
    val description: String?,
    val mediaUrls: List<String>,
    val thumbnailUrl: String?,
    val videoDuration: Int?,
    val aspectRatio: Float?,
    val likesCount: Int,
    val commentsCount: Int,
    val sharesCount: Int,
    val relinksCount: Int,
    val savesCount: Int,
    val viewsCount: Int,
    val isLiked: Boolean,
    val isSaved: Boolean,
    val isRelinked: Boolean,
    val location: String?,
    val hashtags: List<String>,
    val mentions: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

enum class LinkType { FEED, VIDEO, REEL }
