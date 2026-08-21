package com.linker.app.domain.repository

import com.linker.app.domain.model.Note
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

import com.linker.app.domain.model.NoteViewer

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
    suspend fun postNote(content: String, backgroundColor: String? = null, textColor: String? = null): Result<Note>

    // Not: getMediaNote özelliği iptal edilmiştir ancak gerekirse eklenebilir.

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

    /** 
     * Get reactions for a specific note. 
     * @return Result containing a Map where Key is UserId and Value is Emoji String
     */
    suspend fun getNoteReactions(noteId: String): Result<Map<String, String>>

    /** Reply to a note (creates a private message to the author). */
    suspend fun replyToNote(noteId: String, content: String): Result<Unit>

    // ── Likes ──────────────────────────────────────────────────────

    /**
     * Toggles like on a Note.
     * @return Result containing true if liked, false if unliked.
     */
    suspend fun toggleLikeNote(noteId: String): Result<Boolean>

    // ── Specific Note Type Creation ──────────────────────────────────

    /**
     * Posts a new GIF note.
     */
    suspend fun postGifNote(gifUrl: String, content: String, aspectRatio: Float?, backgroundColor: String? = null, textColor: String? = null): Result<Note.Gif>

    /**
     * Posts a location note using GPS coordinates.
     * A Google Maps Static API thumbnail is generated server-side.
     * Requires ACCESS_FINE_LOCATION permission granted before calling.
     *
     * @param latitude GPS latitude (-90 to 90).
     * @param longitude GPS longitude (-180 to 180).
     * @param placeName Human-readable place name (e.g. "Kadıköy, Istanbul").
     */
    suspend fun postLocationNote(
        latitude: Double,
        longitude: Double,
        placeName: String,
        caption: String = "",
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Location>

    /**
     * Posts a countdown note that notifies subscribers when it reaches zero.
     *
     * @param title Short label for the countdown event (e.g. "Concert 🎸").
     * @param targetTime Epoch milliseconds of the target moment.
     */
    suspend fun postCountdownNote(
        title: String,
        targetTime: Long,
        content: String,
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Countdown>

    /**
     * Posts a music note with Spotify track metadata.
     *
     * @param trackId Spotify track ID.
     * @param trackName Track name.
     * @param artistName Artist name.
     * @param albumArtUrl Spotify album artwork URL.
     * @param caption Optional short caption (max [Note.Text.MAX_TEXT_CONTENT_LENGTH] chars).
     */
    suspend fun postMusicNote(
        trackId: String,
        trackName: String,
        artistName: String,
        albumArtUrl: String?,
        previewUrl: String?,
        clipStartMs: Long,
        clipEndMs: Long,
        caption: String = "",
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Music>

    // ── Countdown Subscriptions ────────────────────────────────────────

    /**
     * Subscribes the current user to a countdown note.
     * They will receive an FCM push notification when the countdown reaches zero.
     */
    suspend fun subscribeToCountdown(noteId: String): Result<Unit>

    /**
     * Unsubscribes the current user from a countdown notification.
     */
    suspend fun unsubscribeFromCountdown(noteId: String): Result<Unit>

    /**
     * Returns whether the current user is subscribed to the given countdown.
     */
    suspend fun isSubscribedToCountdown(noteId: String): Result<Boolean>
}
