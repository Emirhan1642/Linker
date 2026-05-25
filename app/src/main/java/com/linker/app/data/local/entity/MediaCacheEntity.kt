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
        Index(value = ["cacheType"]),
        Index(value = ["associatedEntityId", "associatedEntityType"], name = "idx_associated_entity"),
        Index(value = ["isPermanent", "lastAccessedAt"], name = "idx_cache_cleanup")
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
) {
    init {
        require(mediaUrl.isNotBlank()) { "Media URL cannot be blank" }
        require(localPath.isNotBlank()) { "Local path cannot be blank" }
        require(fileSize > 0) { "File size must be positive" }
        require(fileSize <= MAX_FILE_SIZE) { "File size exceeds maximum" }
        require(mimeType.isNotBlank()) { "MIME type cannot be blank" }
        require(associatedEntityId.isNotBlank()) { "Entity ID cannot be blank" }
        require(associatedEntityType.isNotBlank()) { "Entity type cannot be blank" }
        require(lastAccessedAt >= downloadedAt) { "Last accessed cannot be before download" }
        
        expiresAt?.let {
            require(it > downloadedAt) { "Expiration must be after download" }
        }
        
        // MIME type validation
        when (cacheType) {
            CacheType.IMAGE -> require(mimeType.startsWith("image/")) { "Invalid MIME type for image" }
            CacheType.VIDEO -> require(mimeType.startsWith("video/")) { "Invalid MIME type for video" }
            CacheType.AUDIO -> require(mimeType.startsWith("audio/")) { "Invalid MIME type for audio" }
            CacheType.THUMBNAIL -> require(mimeType.startsWith("image/")) { "Thumbnail must be image" }
        }
    }

    fun isExpired(): Boolean {
        return expiresAt?.let { it < System.currentTimeMillis() } ?: false
    }

    fun isStale(thresholdMillis: Long = 2592000000L): Boolean {
        return !isPermanent && 
               System.currentTimeMillis() - lastAccessedAt > thresholdMillis
    }

    fun getSizeInMB(): Double {
        return fileSize / (1024.0 * 1024.0)
    }

    companion object {
        const val MAX_FILE_SIZE = 100L * 1024 * 1024  // 100 MB
    }
}

enum class CacheType {
    IMAGE,
    VIDEO,
    THUMBNAIL,
    AUDIO
}
