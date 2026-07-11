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
    abstract val likesCount: Int
    abstract val isLiked: Boolean
    abstract val repliesCount: Int
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
        override val likesCount: Int = 0,
        override val isLiked: Boolean = false,
        override val repliesCount: Int = 0,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.isNotBlank()) { "content cannot be blank" }
            require(content.codePointCount(0, content.length) <= MAX_TEXT_CONTENT_LENGTH) {
                "Text content exceeds maximum length of $MAX_TEXT_CONTENT_LENGTH"
            }
            require(likesCount >= 0) { "likesCount cannot be negative" }
            require(repliesCount >= 0) { "repliesCount cannot be negative" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }

        companion object {
            const val MAX_TEXT_CONTENT_LENGTH = 100
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
     * @property previewUrl Audio preview URL.
     * @property clipStartTime Clip start time in milliseconds.
     * @property clipEndTime Clip end time in milliseconds.
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
        val previewUrl: String? = null,
        val clipStartTime: Long = 0,
        val clipEndTime: Long = 30000,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val likesCount: Int = 0,
        override val isLiked: Boolean = false,
        override val repliesCount: Int = 0,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.codePointCount(0, content.length) <= MAX_MUSIC_CONTENT_LENGTH) {
                "Music content exceeds maximum length of $MAX_MUSIC_CONTENT_LENGTH"
            }
            require(musicTrackId.isNotBlank()) { "musicTrackId cannot be blank" }
            require(musicTrackName.isNotBlank()) { "musicTrackName cannot be blank" }
            require(musicArtistName.isNotBlank()) { "musicArtistName cannot be blank" }
            require(clipStartTime >= 0) { "clipStartTime cannot be negative" }
            require(clipEndTime >= clipStartTime) { "clipEndTime must be >= clipStartTime" }
            require(likesCount >= 0) { "likesCount cannot be negative" }
            require(repliesCount >= 0) { "repliesCount cannot be negative" }
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
        val countdownSubscriberCount: Int = 0,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val likesCount: Int = 0,
        override val isLiked: Boolean = false,
        override val repliesCount: Int = 0,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.codePointCount(0, content.length) <= MAX_COUNTDOWN_CONTENT_LENGTH) {
                "Countdown content exceeds maximum length of $MAX_COUNTDOWN_CONTENT_LENGTH"
            }
            require(countdownTargetTime > 0) { "countdownTargetTime must be positive" }
            require(countdownTitle.isNotBlank()) { "countdownTitle cannot be blank" }
            require(countdownSubscriberCount >= 0) { "countdownSubscriberCount cannot be negative" }
            require(likesCount >= 0) { "likesCount cannot be negative" }
            require(repliesCount >= 0) { "repliesCount cannot be negative" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - countdownTargetTime <= MAX_EXPIRATION_DURATION_MS) {
                "Countdown note cannot expire more than 24 hours after target time"
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
            const val MAX_COUNTDOWN_CONTENT_LENGTH = 100
        }
    }

    /**
     * Location-sharing note.
     *
     * @property noteId Unique note identifier.
     * @property author Lightweight author reference.
     * @property latitude GPS latitude.
     * @property longitude GPS longitude.
     * @property placeName Human-readable place name (e.g. "Kadıköy, Istanbul").
     * @property mapPreviewUrl Google Maps Static API URL for the location preview image.
     * @property backgroundColor Background color hex code.
     * @property textColor Text color hex code.
     * @property createdAt Creation timestamp (epoch ms).
     * @property expiresAt Expiration timestamp (epoch ms).
     */
    data class Location(
        override val noteId: String,
        override val author: NoteAuthor,
        val latitude: Double,
        val longitude: Double,
        val placeName: String,
        val mapPreviewUrl: String?,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val likesCount: Int = 0,
        override val isLiked: Boolean = false,
        override val repliesCount: Int = 0,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(latitude in -90.0..90.0) { "latitude must be between -90 and 90" }
            require(longitude in -180.0..180.0) { "longitude must be between -180 and 180" }
            require(placeName.isNotBlank()) { "placeName cannot be blank" }
            require(likesCount >= 0) { "likesCount cannot be negative" }
            require(repliesCount >= 0) { "repliesCount cannot be negative" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }
    }

    /**
     * GIF note with optional text content.
     *
     * @property noteId Unique note identifier.
     * @property author Lightweight author reference.
     * @property content Optional text content (up to 40 characters).
     * @property gifUrl URL of the GIF.
     * @property aspectRatio Aspect ratio (width / height) of the GIF if known.
     * @property backgroundColor Background color hex code.
     * @property textColor Text color hex code.
     * @property createdAt Creation timestamp (epoch ms).
     * @property expiresAt Expiration timestamp (epoch ms).
     */
    data class Gif(
        override val noteId: String,
        override val author: NoteAuthor,
        val content: String,
        val gifUrl: String,
        val aspectRatio: Float?,
        override val backgroundColor: String? = null,
        override val textColor: String? = null,
        override val likesCount: Int = 0,
        override val isLiked: Boolean = false,
        override val repliesCount: Int = 0,
        override val createdAt: Long,
        override val expiresAt: Long
    ) : Note() {
        init {
            require(noteId.isNotBlank()) { "noteId cannot be blank" }
            require(content.codePointCount(0, content.length) <= MAX_GIF_CONTENT_LENGTH) {
                "Gif content exceeds maximum length of $MAX_GIF_CONTENT_LENGTH"
            }
            require(gifUrl.isNotBlank()) { "gifUrl cannot be blank" }
            require(likesCount >= 0) { "likesCount cannot be negative" }
            require(repliesCount >= 0) { "repliesCount cannot be negative" }
            require(createdAt > 0) { "createdAt must be positive" }
            require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
            require(expiresAt - createdAt <= MAX_EXPIRATION_DURATION_MS) {
                "Note cannot expire more than 24 hours after creation"
            }
            validateColors()
        }

        companion object {
            const val MAX_GIF_CONTENT_LENGTH = 40
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
    TEXT("Text", "ic_text", 100),
    /** Note with music attachment. */
    MUSIC("Music", "ic_music", 40),
    /** Countdown note with target time. */
    COUNTDOWN("Countdown", "ic_timer", 40),
    /** Location-sharing note. */
    LOCATION("Location", "ic_location", 40),
    /** GIF note with optional text. */
    GIF("GIF", "ic_gif", 40)
}
