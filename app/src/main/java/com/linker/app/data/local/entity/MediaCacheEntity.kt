package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Media Cache Entity - Local media cache management
 * 
 * Tracks downloaded media for offline viewing
 */
@Entity(
    tableName = "media_cache",
    indices = [
        Index(value = ["mediaUrl"], unique = true),
        Index(value = ["lastAccessedAt"]),
        Index(value = ["cacheType"])
    ]
)
data class MediaCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaUrl: String, // Original URL
    val localPath: String, // Local file path
    val cacheType: CacheType, // IMAGE, VIDEO, THUMBNAIL
    val fileSize: Long, // in bytes
    val mimeType: String,
    val associatedEntityId: String, // linkId, storyId, messageId, etc.
    val associatedEntityType: String, // "link", "story", "message"
    val downloadedAt: Long,
    val lastAccessedAt: Long,
    val expiresAt: Long? = null, // For stories/notes
    val isPermanent: Boolean = false // User saved for offline
)

enum class CacheType {
    IMAGE,
    VIDEO,
    THUMBNAIL,
    AUDIO
}
