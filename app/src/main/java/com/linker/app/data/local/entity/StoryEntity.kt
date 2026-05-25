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
        Index(value = ["authorId", "expiresAt"], name = "idx_active_stories"),
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
) {
    init {
        require(storyId.isNotBlank()) { "Story ID cannot be blank" }
        require(authorId.isNotBlank()) { "Author ID cannot be blank" }
        require(mediaUrl.isNotBlank()) { "Media URL cannot be blank" }
        require(expiresAt > createdAt) { "Expiration must be after creation" }
        caption?.let { require(it.length <= MAX_CAPTION_LENGTH) { "Caption too long" } }
        duration?.let { require(it <= MAX_VIDEO_DURATION) { "Duration too long" } }
    }
    companion object {
        const val MAX_CAPTION_LENGTH = 300
        const val MAX_VIDEO_DURATION = 30
    }
}

enum class StoryMediaType {
    IMAGE,
    VIDEO
}
