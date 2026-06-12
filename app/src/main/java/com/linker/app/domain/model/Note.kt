package com.linker.app.domain.model

/**
 * Domain model for Note (24-hour expiring status).
 *
 * Notes are ephemeral status updates that appear in the chat list header.
 * They support text, music, and countdown content types. All notes expire
 * within 24 hours of creation.
 *
 * This is a sealed class hierarchy for type-safe note handling.
 */
sealed class Note {
    abstract val noteId: String
    abstract val author: NoteAuthor
    abstract val backgroundColor: String?
    abstract val textColor: String?
    abstract val createdAt: Long
    abstract val expiresAt: Long

    /**
     * Plain text note.
     *
     * @property noteId Unique note identifier.
     * @property author Lightweight author reference.
     * @property content Text content (up to 60 characters).
     * @property backgroundColor Background color hex code.
     * @property textColor Text color hex code.
     * @property createdAt Creation timestamp (epoch ms).
     * @property expiresAt Expiration timestamp (epoch ms).
     */
    data class Text(
        override val noteId: String,
        override val author: NoteAuthor,
        val content: String,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.isNotBlank()) { "content cannot be blank" }
            require(content.length <= MAX_TEXT_CONTENT_LENGTH) {
                "Text content exceeds maximum length of $MAX_TEXT_CONTENT_LENGTH"
            }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }

        companion object {
            const val MAX_TEXT_CONTENT_LENGTH = 60
        }
    }

    /**
     * Music note with track information.
     *
     * @property noteId Unique note identifier.
     * @property author Lightweight author reference.
     * @property content Text content (up to 40 characters).
     * @property musicTrackId Spotify/Apple Music track ID.
     * @property musicTrackName Track name.
     * @property musicArtistName Artist name.
     * @property musicAlbumArt Album art URL.
     * @property backgroundColor Background color hex code.
     * @property textColor Text color hex code.
     * @property createdAt Creation timestamp (epoch ms).
     * @property expiresAt Expiration timestamp (epoch ms).
     */
    data class Music(
        override val noteId: String,
        override val author: NoteAuthor,
        val content: String,
        val musicTrackId: String,
        val musicTrackName: String,
        val musicArtistName: String,
        val musicAlbumArt: String?,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.isNotBlank()) { "content cannot be blank" }
            require(content.length <= MAX_MUSIC_CONTENT_LENGTH) {
                "Music content exceeds maximum length of $MAX_MUSIC_CONTENT_LENGTH"
            }
            require(musicTrackId.isNotBlank()) { "musicTrackId cannot be blank" }
            require(musicTrackName.isNotBlank()) { "musicTrackName cannot be blank" }
            require(musicArtistName.isNotBlank()) { "musicArtistName cannot be blank" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }

        companion object {
            const val MAX_MUSIC_CONTENT_LENGTH = 40
        }
    }

    /**
     * Countdown note with target time.
     *
     * @property noteId Unique note identifier.
     * @property author Lightweight author reference.
     * @property content Text content (up to 40 characters).
     * @property countdownTargetTime Target timestamp for countdown.
     * @property countdownTitle Title of the countdown event.
     * @property backgroundColor Background color hex code.
     * @property textColor Text color hex code.
     * @property createdAt Creation timestamp (epoch ms).
     * @property expiresAt Expiration timestamp (epoch ms).
     */
    data class Countdown(
        override val noteId: String,
        override val author: NoteAuthor,
        val content: String,
        val countdownTargetTime: Long,
        val countdownTitle: String,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.isNotBlank()) { "content cannot be blank" }
            require(content.length <= MAX_COUNTDOWN_CONTENT_LENGTH) {
                "Countdown content exceeds maximum length of $MAX_COUNTDOWN_CONTENT_LENGTH"
            }
            require(countdownTargetTime > 0) { "countdownTargetTime must be positive" }
            require(countdownTitle.isNotBlank()) { "countdownTitle cannot be blank" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }

        /**
         * Returns the remaining time until the countdown target in milliseconds.
         * Returns 0 if the countdown has already passed.
         */
        fun getRemainingCountdownMs(): Long {
            val remaining = countdownTargetTime - System.currentTimeMillis()
            return if (remaining > 0) remaining else 0
        }

        /**
         * Whether the countdown target time has been reached.
         */
        fun hasCountdownExpired(): Boolean = System.currentTimeMillis() >= countdownTargetTime

        companion object {
            const val MAX_COUNTDOWN_CONTENT_LENGTH = 40
        }
    }

    /**
     * Whether this note has expired and should no longer be displayed.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    /**
     * Returns the remaining time before expiration in milliseconds.
     * Returns 0 if already expired.
     */
    fun getRemainingTimeMs(): Long {
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /**
     * Whether this note is expiring within the next hour.
     * Useful for showing urgency indicators in the UI.
     */
    fun isExpiringSoon(): Boolean {
        val remaining = getRemainingTimeMs()
        return remaining in 1..EXPIRING_SOON_THRESHOLD_MS
    }

    /**
     * Validates color format (if provided).
     */
    protected fun validateColors() {
        backgroundColor?.let {
            require(it.matches(COLOR_HEX_REGEX)) {
                "backgroundColor must be a valid hex color (e.g., #FF5733)"
            }
        }
        textColor?.let {
            require(it.matches(COLOR_HEX_REGEX)) {
                "textColor must be a valid hex color (e.g., #FFFFFF)"
            }
        }
    }

    companion object {
        /** Maximum expiration duration: 24 hours in milliseconds. */
        const val MAX_EXPIRATION_DURATION_MS = 24L * 60 * 60 * 1000

        /** Threshold for "expiring soon" indicator: 1 hour. */
        const val EXPIRING_SOON_THRESHOLD_MS = 60L * 60 * 1000

        /** Regex for hex color validation (#RGB, #RRGGBB, #AARRGGBB). */
        val COLOR_HEX_REGEX = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    }
}

/**
 * Note content type with display metadata.
 *
 * @property displayName Human-readable name.
 * @property iconName Icon resource name.
 * @property maxContentLength Maximum text content length for this type.
 */
enum class NoteType(
    val displayName: String,
    val iconName: String,
    val maxContentLength: Int
) {
    /** Plain text note. */
    TEXT("Text", "ic_text", 60),
    /** Note with music attachment. */
    MUSIC("Music", "ic_music", 40),
    /** Countdown note with target time. */
    COUNTDOWN("Countdown", "ic_timer", 40)
}
