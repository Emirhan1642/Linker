package com.linker.app.domain.model

/**
 * Domain model for a Comment (supports nested replies)
 */
data class Comment(
    val commentId: String,
    val linkId: String,
    val author: User,
    val content: String,
    val gifUrl: String?,
    val parentCommentId: String?,
    val likesCount: Int,
    val repliesCount: Int,
    val isLiked: Boolean,
    val isPinned: Boolean,
    val isEdited: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Domain model for a Notification
 */
data class Notification(
    val notificationId: String,
    val notificationType: NotificationType,
    val actor: User,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val title: String,
    val message: String,
    val imageUrl: String?,
    val actionUrl: String?,
    val isRead: Boolean,
    val createdAt: Long
)

enum class NotificationType {
    LIKE, COMMENT, REPLY, FOLLOW, MENTION, RELINK, MESSAGE, STORY_VIEW, LIVE
}
