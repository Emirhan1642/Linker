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
        Index(value = ["linkId"]),
        Index(value = ["authorId"]),
        Index(value = ["parentCommentId"]),
        Index(value = ["createdAt"])
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
)
