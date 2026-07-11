package com.linker.app.domain.usecase.note

import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Posts a location note with GPS coordinates.
 */
class PostLocationNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        placeName: String,
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Location> {
        if (latitude !in -90.0..90.0) return Result.Error("Latitude must be between -90 and 90")
        if (longitude !in -180.0..180.0) return Result.Error("Longitude must be between -180 and 180")
        if (placeName.isBlank()) return Result.Error("Place name cannot be empty")
        return noteRepository.postLocationNote(latitude, longitude, placeName, backgroundColor, textColor)
    }
}

/**
 * Posts a countdown note with a target time and title.
 */
class PostCountdownNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(
        title: String, 
        targetTime: Long,
        content: String,
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Countdown> {
        if (title.isBlank()) {
            return Result.Error("Title cannot be empty")
        }
        if (content.codePointCount(0, content.length) > Note.Countdown.MAX_COUNTDOWN_CONTENT_LENGTH) {
            return Result.Error("Content exceeds maximum length of ${Note.Countdown.MAX_COUNTDOWN_CONTENT_LENGTH}")
        }
        // Note: Bu kontrol istemci taraflıdır. Asıl güvenlik için sunucu/Firebase kurallarında da "targetTime > now" kısıtlaması olmalıdır.
        if (targetTime <= System.currentTimeMillis()) {
            return Result.Error("Target time must be in the future")
        }
        return noteRepository.postCountdownNote(title, targetTime, content, backgroundColor, textColor)
    }
}

/**
 * Posts a music note with Spotify track metadata.
 */
class PostMusicNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(
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
    ): Result<Note.Music> {
        if (trackId.isBlank() || trackName.isBlank() || artistName.isBlank()) {
            return Result.Error("Track ID, Name, and Artist cannot be blank")
        }
        if (caption.codePointCount(0, caption.length) > Note.Music.MAX_MUSIC_CONTENT_LENGTH) {
            return Result.Error("Caption exceeds maximum length of ${Note.Music.MAX_MUSIC_CONTENT_LENGTH}")
        }
        return noteRepository.postMusicNote(trackId, trackName, artistName, albumArtUrl, previewUrl, clipStartMs, clipEndMs, caption, backgroundColor, textColor)
    }
}

/**
 * Subscribes the current user to a countdown note notification.
 */
class SubscribeToCountdownUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: String): Result<Unit> {
        if (noteId.isBlank()) return Result.Error("Note ID cannot be empty")
        return noteRepository.subscribeToCountdown(noteId)
    }
}

/**
 * Unsubscribes the current user from a countdown notification.
 */
class UnsubscribeFromCountdownUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: String): Result<Unit> {
        if (noteId.isBlank()) return Result.Error("Note ID cannot be empty")
        return noteRepository.unsubscribeFromCountdown(noteId)
    }
}

/**
 * Toggles like on a Note.
 */
class LikeNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: String): Result<Boolean> {
        if (noteId.isBlank()) return Result.Error("Note ID cannot be empty")
        return noteRepository.toggleLikeNote(noteId)
    }
}

/**
 * Sends a reply to a Note as a direct message to the author.
 */
class ReplyToNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: String, content: String): Result<Unit> {
        if (noteId.isBlank()) return Result.Error("Note ID cannot be empty")
        if (content.isBlank()) return Result.Error("Reply content cannot be empty")
        if (content.length > 500) return Result.Error("Reply is too long (max 500 characters)")
        return noteRepository.replyToNote(noteId, content)
    }
}

/**
 * Posts a GIF note.
 */
class PostGifNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(
        gifUrl: String,
        content: String = "",
        aspectRatio: Float? = null,
        backgroundColor: String? = null,
        textColor: String? = null
    ): Result<Note.Gif> {
        if (gifUrl.isBlank()) return Result.Error("GIF URL cannot be empty")
        if (content.codePointCount(0, content.length) > Note.Gif.MAX_GIF_CONTENT_LENGTH) {
            return Result.Error("Content exceeds maximum length of ${Note.Gif.MAX_GIF_CONTENT_LENGTH}")
        }
        return noteRepository.postGifNote(gifUrl, content, aspectRatio, backgroundColor, textColor)
    }
}

data class NoteInteractionUseCases @Inject constructor(
    val postNote: PostNoteUseCase,
    val postLocationNote: PostLocationNoteUseCase,
    val postCountdownNote: PostCountdownNoteUseCase,
    val postMusicNote: PostMusicNoteUseCase,
    val postGifNote: PostGifNoteUseCase,
    val subscribeToCountdown: SubscribeToCountdownUseCase,
    val unsubscribeFromCountdown: UnsubscribeFromCountdownUseCase,
    val likeNote: LikeNoteUseCase,
    val replyToNote: ReplyToNoteUseCase
)
