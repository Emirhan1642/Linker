package com.linker.app.domain.repository

import com.linker.app.domain.model.Note
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Type of media attached to a note.
 */
enum class NoteMediaType {
    IMAGE, VIDEO, AUDIO, NONE
}

/**
 * Viewer details for a Note.
 */
data class NoteViewer(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val viewedAt: Long
)

/**
 * Repository interface for Note operations.
 * Notes are temporary content that expires after 24 hours.
 */
interface NoteRepository {

    /**
     * Observe all active (non-expired) notes in real-time.
     * Uses cursor-based pagination internally.
     * 
     * @return Flow of note list, ordered by expiration time
     */
    fun observeActiveNotes(): Flow<Result<List<Note>>>

    /**
     * Refresh the active notes list.
     */
    suspend fun refreshNotes(limit: Int = 20): Result<List<Note>>

    /**
     * Load older active notes for pagination.
     */
    suspend fun loadMoreNotes(beforeTimestamp: Long, limit: Int = 20): Result<List<Note>>

    /**
     * Post a new text note that expires in 24 hours.
     * 
     * Security:
     * - Content is validated for prohibited words and spam.
     * - Maximum length is 280 characters.
     */
    suspend fun postNote(content: String): Result<Note>

    /**
     * Post a new media note that expires in 24 hours.
     * 
     * Security:
     * - Media files are scanned for malware.
     * - EXIF data is stripped before upload.
     */
    suspend fun postMediaNote(
        mediaLocalPath: String,
        mediaType: NoteMediaType,
        caption: String? = null
    ): Result<Note>

    /**
     * Delete a note by ID.
     * 
     * Permissions:
     * - A user can only delete their own notes.
     * - Admin/Moderators can delete any note.
     */
    suspend fun deleteNote(noteId: String): Result<Unit>

    /**
     * Purge all expired notes from the database.
     * Scheduled via WorkManager.
     */
    suspend fun purgeExpiredNotes(): Result<Unit>

    // ── View Tracking ──────────────────────────────────────────────────────

    /** Records a view for a specific note. Fire-and-forget. */
    fun recordView(noteId: String)

    /** Gets the total view count for a note. */
    suspend fun getViewCount(noteId: String): Result<Int>

    /** Gets the list of users who viewed the note (only visible to author). */
    suspend fun getViewers(noteId: String): Result<List<NoteViewer>>

    // ── Reactions & Replies ────────────────────────────────────────────────

    /** Add or remove an emoji reaction to a note. */
    suspend fun reactToNote(noteId: String, emoji: String?): Result<Unit>

    /** Get reactions for a specific note. */
    suspend fun getNoteReactions(noteId: String): Result<Map<String, String>>

    /** Reply to a note (creates a private message to the author). */
    suspend fun replyToNote(noteId: String, content: String): Result<Unit>
}
