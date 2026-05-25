package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Comment Entity - Comments on Links
 * 
 * Supports nested comments (parent/child threads)
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = LinkEntity::class,
            parentColumns = ["linkId"],
            childColumns = ["linkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["linkId", "parentCommentId", "isPinned", "createdAt"], name = "idx_top_level_comments"),
        Index(value = ["parentCommentId", "createdAt"], name = "idx_comment_replies"),
        Index(value = ["authorId", "createdAt"], name = "idx_author_comments")
    ]
)
data class CommentEntity(
    @PrimaryKey
    val commentId: String,
    val linkId: String,
    val authorId: String,
    val content: String,
    val gifUrl: String? = null, // Optional GIF
    val parentCommentId: String? = null, // For nested replies
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLiked: Boolean = false,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    init {
        require(commentId.isNotBlank()) { "Comment ID cannot be blank" }
        require(linkId.isNotBlank()) { "Link ID cannot be blank" }
        require(content.isNotBlank() || gifUrl != null) { "Comment must have content or GIF" }
        require(content.length <= MAX_CONTENT_LENGTH) { "Comment content too long" }
        require(updatedAt >= createdAt) { "Updated timestamp cannot be before created timestamp" }
        
        if (parentCommentId != null) {
            require(parentCommentId != commentId) { "Comment cannot be its own parent" }
        }
    }
    
    fun isTopLevel(): Boolean = parentCommentId == null

    fun isReply(): Boolean = parentCommentId != null

    fun canBeEdited(currentUserId: String, timeLimit: Long = 900000L): Boolean {
        return authorId == currentUserId && 
               System.currentTimeMillis() - createdAt < timeLimit
    }

    fun getDisplayContent(): String {
        return when {
            content.isBlank() && gifUrl != null -> "[GIF]"
            else -> content
        }
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 10000
        const val MAX_NESTED_DEPTH = 5
    }
}
