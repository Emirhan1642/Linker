package com.linker.app.domain.repository

import com.linker.app.domain.model.Notification
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user notifications.
 * 
 * Notification types can include:
 * - LIKE: Someone liked your post/comment
 * - COMMENT: Someone commented on your post
 * - REPLY: Someone replied to your comment
 * - FOLLOW: Someone started following you
 * - MENTION: Someone mentioned you
 */
interface NotificationRepository {

    /** Observes all notifications (newest first). */
    fun observeNotifications(): Flow<Result<List<Notification>>>

    /** Observes unread notification count. */
    fun observeUnreadCount(): Flow<Result<Int>>

    /** Marks a single notification as read. */
    suspend fun markAsRead(notificationId: String): Result<Unit>

    /** Marks all notifications as read. */
    suspend fun markAllAsRead(): Result<Unit>

    /** Deletes all notifications. */
    suspend fun clearAll(): Result<Unit>

    /** 
     * Inserts a local notification (used by chat repo for push notifications). 
     * Uses Domain model instead of Data layer Entity to respect Layered Architecture.
     */
    suspend fun insertNotification(notification: Notification): Result<Unit>
}
