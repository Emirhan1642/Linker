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
        placeName: String
    ): Result<Note.Location> {
        if (latitude !in -90.0..90.0) return Result.Error("Latitude must be between -90 and 90")
        if (longitude !in -180.0..180.0) return Result.Error("Longitude must be between -180 and 180")
        if (placeName.isBlank()) return Result.Error("Place name cannot be empty")
        return noteRepository.postLocationNote(latitude, longitude, placeName)
    }
}

/**
 * Posts a countdown note with a target time and title.
 */
class PostCountdownNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(title: String, targetTime: Long): Result<Note.Countdown> {
        if (title.isBlank()) return Result.Error("Title cannot be empty")
        if (title.length > Note.Countdown.MAX_COUNTDOWN_CONTENT_LENGTH) {
            return Result.Error("Title exceeds maximum length of ${Note.Countdown.MAX_COUNTDOWN_CONTENT_LENGTH}")
        }
        if (targetTime <= System.currentTimeMillis()) {
            return Result.Error("Target time must be in the future")
        }
        return noteRepository.postCountdownNote(title, targetTime)
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
        caption: String = ""
    ): Result<Note.Music> {
        if (trackId.isBlank()) return Result.Error("Track ID cannot be empty")
        if (trackName.isBlank()) return Result.Error("Track name cannot be empty")
        if (artistName.isBlank()) return Result.Error("Artist name cannot be empty")
        if (caption.length > Note.Music.MAX_MUSIC_CONTENT_LENGTH) {
            return Result.Error("Caption exceeds maximum length of ${Note.Music.MAX_MUSIC_CONTENT_LENGTH}")
        }
        return noteRepository.postMusicNote(trackId, trackName, artistName, albumArtUrl, caption)
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

data class NoteInteractionUseCases @Inject constructor(
    val postNote: PostNoteUseCase,
    val postLocationNote: PostLocationNoteUseCase,
    val postCountdownNote: PostCountdownNoteUseCase,
    val postMusicNote: PostMusicNoteUseCase,
    val subscribeToCountdown: SubscribeToCountdownUseCase,
    val unsubscribeFromCountdown: UnsubscribeFromCountdownUseCase,
    val likeNote: LikeNoteUseCase,
    val replyToNote: ReplyToNoteUseCase
)
