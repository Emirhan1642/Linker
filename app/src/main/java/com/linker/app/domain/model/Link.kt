package com.linker.app.domain.model

/**
 * Domain model for Link (Post)
 * 
 * Represents a post in the feed: photo carousel, short video, or reel.
 */
data class Link(
    val linkId: String = "",
    val author: User = User(),
    val linkType: LinkType = LinkType.FEED,
    val description: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val videoDuration: Int? = null,
    val aspectRatio: Float? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val relinksCount: Int = 0,
    val savesCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isRelinked: Boolean = false,
    val location: String? = null,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

enum class LinkType { FEED, VIDEO, REEL }
