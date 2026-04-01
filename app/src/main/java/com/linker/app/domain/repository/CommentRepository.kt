package com.linker.app.domain.repository

import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.Notification
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface CommentRepository {

    /** Observes top-level comments for a link (most recent first). */
    fun observeComments(linkId: String): Flow<List<Comment>>

    /** Fetches a paginated list of top-level comments. */
    suspend fun getComments(linkId: String, limit: Int = 20, offset: Int = 0): Result<List<Comment>>

    /** Fetches replies to a comment. */
    suspend fun getReplies(parentCommentId: String): Result<List<Comment>>

    /** Adds a top-level comment or a reply. */
    suspend fun addComment(
        linkId: String,
        content: String,
        gifUrl: String? = null,
        parentCommentId: String? = null
    ): Result<Comment>

    /** Toggles like on a comment. */
    suspend fun toggleLike(commentId: String): Result<Boolean>

    /** Deletes a comment. */
    suspend fun deleteComment(commentId: String): Result<Unit>
}

interface NotificationRepository {

    /** Observes all notifications (newest first). */
    fun observeNotifications(): Flow<List<Notification>>

    /** Observes unread notification count. */
    fun observeUnreadCount(): Flow<Int>

    /** Marks a single notification as read. */
    suspend fun markAsRead(notificationId: String): Result<Unit>

    /** Marks all notifications as read. */
    suspend fun markAllAsRead(): Result<Unit>

    /** Deletes all notifications. */
    suspend fun clearAll(): Result<Unit>

    /** Inserts a local notification (used by chat repo for push notifications). */
    suspend fun insertNotification(notification: com.linker.app.data.local.entity.NotificationEntity)
}
