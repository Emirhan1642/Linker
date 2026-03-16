package com.linker.app.domain.model

/**
 * Domain model for Note (24-hour expiring)
 */
data class Note(
    val noteId: String,
    val author: User,
    val noteType: NoteType,
    val content: String,
    val musicTrackId: String?,
    val musicTrackName: String?,
    val musicArtistName: String?,
    val musicAlbumArt: String?,
    val countdownTargetTime: Long?,
    val countdownTitle: String?,
    val backgroundColor: String?,
    val textColor: String?,
    val createdAt: Long,
    val expiresAt: Long
)

enum class NoteType { TEXT, MUSIC, COUNTDOWN }
