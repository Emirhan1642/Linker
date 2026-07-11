package com.linker.app.domain.model

/**
 * Viewer details for a Note.
 */
data class NoteViewer(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val viewedAt: Long
)
