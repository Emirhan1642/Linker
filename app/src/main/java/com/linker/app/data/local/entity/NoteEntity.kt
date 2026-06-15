package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Note Entity - 24-hour expiring notes
 * 
 * Can be text, music (Spotify), or countdown
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["authorId", "expiresAt"], name = "idx_active_notes"),
        Index(value = ["expiresAt"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val noteId: String,
    val authorId: String,
    val noteType: NoteType, // TEXT, MUSIC, COUNTDOWN
    val content: String, // Text content or music track info
    val musicTrackId: String? = null, // Spotify track ID
    val musicTrackName: String? = null,
    val musicArtistName: String? = null,
    val musicAlbumArt: String? = null,
    val countdownTargetTime: Long? = null, // Unix timestamp for countdown
    val countdownTitle: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val createdAt: Long,
    val expiresAt: Long, // createdAt + 24 hours
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    init {
        require(noteId.isNotBlank()) { "Note ID cannot be blank" }
        require(authorId.isNotBlank()) { "Author ID cannot be blank" }
        require(content.length <= MAX_CONTENT_LENGTH) { "Content too long" }
        require(expiresAt > createdAt) { "Expiration must be after creation" }
    }
    companion object {
        const val MAX_CONTENT_LENGTH = 100
    }
}

enum class NoteType {
    TEXT,
    MUSIC,
    COUNTDOWN,
    LOCATION
}
