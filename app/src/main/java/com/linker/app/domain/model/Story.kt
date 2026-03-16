package com.linker.app.domain.model

/**
 * Domain model for Story
 */
data class Story(
    val storyId: String,
    val author: User,
    val mediaUrl: String,
    val mediaType: StoryMediaType,
    val thumbnailUrl: String?,
    val duration: Int?,
    val caption: String?,
    val viewsCount: Int,
    val isViewed: Boolean,
    val createdAt: Long,
    val expiresAt: Long
)

enum class StoryMediaType { IMAGE, VIDEO }

/**
 * Grouped stories per user, for the top-bar story row
 */
data class UserStories(
    val author: User,
    val stories: List<Story>,
    val hasUnviewed: Boolean
)
