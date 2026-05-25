package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Notification Entity - In-app notifications
 * 
 * Stores notifications for likes, comments, follows, etc.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["isRead", "createdAt"], name = "idx_unread_notifications"),
        Index(value = ["notificationType", "createdAt"], name = "idx_notification_type"),
        Index(value = ["targetEntityId", "targetEntityType"], name = "idx_notification_target")
    ]
)
data class NotificationEntity(
    @PrimaryKey
    val notificationId: String,
    val notificationType: NotificationType,
    val actorId: String, // User who triggered the notification
    val targetEntityId: String?, // linkId, commentId, etc.
    val targetEntityType: String?, // "link", "comment", "story"
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val actionUrl: String? = null, // Deep link
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

enum class NotificationType {
    LIKE,           // Someone liked your post
    COMMENT,        // Someone commented on your post
    REPLY,          // Someone replied to your comment
    FOLLOW,         // Someone followed you
    MENTION,        // Someone mentioned you
    RELINK,         // Someone relinked your post
    MESSAGE,        // New message
    STORY_VIEW,     // Someone viewed your story
    LIVE            // Someone you follow went live
}
