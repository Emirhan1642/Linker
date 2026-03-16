package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Story Entity - 24-hour expiring stories
 * 
 * Stories expire after 24 hours and are managed by Supabase Edge Functions
 */
@Entity(
    tableName = "stories",
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
        Index(value = ["expiresAt"])
    ]
)
data class StoryEntity(
    @PrimaryKey
    val storyId: String,
    val authorId: String,
    val mediaUrl: String,
    val mediaType: StoryMediaType, // IMAGE, VIDEO
    val thumbnailUrl: String?,
    val duration: Int? = null, // Video duration in seconds (max 30)
    val caption: String? = null,
    val viewsCount: Int = 0,
    val isViewed: Boolean = false,
    val createdAt: Long,
    val expiresAt: Long, // createdAt + 24 hours
    val lastSyncedAt: Long = System.currentTimeMillis()
)

enum class StoryMediaType {
    IMAGE,
    VIDEO
}
