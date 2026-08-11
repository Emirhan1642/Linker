package com.linker.app.presentation.screens.note

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.SyncedLyricLine
import com.linker.app.domain.repository.LyricsRepository
import com.linker.app.core.util.AudioPlayerManager
import com.linker.app.core.util.SpotifyAppRemoteManager
import com.linker.app.core.util.SpotifyAuthManager
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteDetailUiState(
    val isLoadingLyrics: Boolean = false,
    val lyrics: List<SyncedLyricLine> = emptyList(),
    val lyricsError: String? = null,
    val error: String? = null
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    val audioPlayerManager: AudioPlayerManager,
    val spotifyAppRemoteManager: SpotifyAppRemoteManager,
    val spotifyAuthManager: SpotifyAuthManager,
    private val noteRepository: NoteRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUserId: String?
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    private var currentNoteId: String? = null

    fun initNote(context: Context, note: Note) {
        if (note.noteId == currentNoteId) return
        currentNoteId = note.noteId
        
        // Reset state
        _uiState.value = NoteDetailUiState()
        audioPlayerManager.stop()
        spotifyAppRemoteManager.pause()

        if (note is Note.Music) {
            // Always try to connect to App Remote first for precise clipping
            spotifyAppRemoteManager.connect(
                context = context,
                clientId = com.linker.app.BuildConfig.SPOTIFY_CLIENT_ID,
                onConnected = {
                    spotifyAppRemoteManager.playTrack(
                        trackId = note.musicTrackId,
                        startTimeMs = note.clipStartTime,
                        endTimeMs = note.clipEndTime
                    )
                },
                onError = {
                    // Fallback to previewUrl if App Remote fails or user is Free tier,
                    // BUT only if this note is still active (prevents background play if sheet closed while connecting)
                    if (note.noteId == currentNoteId && note.previewUrl != null) {
                        audioPlayerManager.playPreview(
                            url = note.previewUrl,
                            startMs = 0L,
                            endMs = 30000L
                        )
                    }
                }
            )
            fetchLyrics(note.musicTrackName, note.musicArtistName)
        }
    }

    private fun fetchLyrics(trackName: String, artistName: String) {
        val cleanTrackName = trackName.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?\\]"), "").trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLyrics = true)
            when (val result = lyricsRepository.getSyncedLyrics(cleanTrackName, artistName)) {
                is com.linker.app.core.util.Result.Success -> {
                    val lines = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoadingLyrics = false,
                        lyrics = lines,
                        lyricsError = if (lines.isEmpty()) "Şarkı sözleri bulunamadı" else null
                    )
                }
                is com.linker.app.core.util.Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLyrics = false,
                        lyricsError = result.message
                    )
                }
                else -> {
                    // Handle Loading or any other state if needed
                }
            }
        }
    }

    fun deleteNote(noteId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = noteRepository.deleteNote(noteId)) {
                is com.linker.app.core.util.Result.Success -> onResult(true, null)
                is com.linker.app.core.util.Result.Error -> onResult(false, res.message)
                else -> {}
            }
        }
    }

    fun toggleLike(noteId: String) {
        viewModelScope.launch {
            val result = noteRepository.toggleLikeNote(noteId)
            if (result is com.linker.app.core.util.Result.Error) {
                // Hata mesajı kaydedilebilir veya loglanabilir
                _uiState.value = _uiState.value.copy(error = result.message)
            }
        }
    }

    fun replyToNote(note: com.linker.app.domain.model.Note, replyContent: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            // First create or get the private chat with the author
            when (val chatRes = chatRepository.createPrivateChat(note.author.userId)) {
                is com.linker.app.core.util.Result.Success -> {
                    val chatId = chatRes.data.chatId
                    val noteRef = com.linker.app.domain.model.NoteReference(
                        noteId = note.noteId,
                        authorId = note.author.userId,
                        authorName = note.author.displayName,
                        noteType = when (note) {
                            is com.linker.app.domain.model.Note.Text -> "TEXT"
                            is com.linker.app.domain.model.Note.Music -> "MUSIC"
                            is com.linker.app.domain.model.Note.Countdown -> "COUNTDOWN"
                            is com.linker.app.domain.model.Note.Location -> "LOCATION"
                            is com.linker.app.domain.model.Note.Gif -> "GIF"
                        },
                        content = when(note) {
                            is com.linker.app.domain.model.Note.Text -> note.content
                            is com.linker.app.domain.model.Note.Music -> note.content
                            is com.linker.app.domain.model.Note.Countdown -> note.countdownTitle
                            is com.linker.app.domain.model.Note.Location -> note.placeName
                            else -> null
                        },
                        musicTrackName = if (note is com.linker.app.domain.model.Note.Music) note.musicTrackName else null,
                        musicArtistName = if (note is com.linker.app.domain.model.Note.Music) note.musicArtistName else null,
                        musicAlbumArt = if (note is com.linker.app.domain.model.Note.Music) note.musicAlbumArt else null,
                        latitude = if (note is com.linker.app.domain.model.Note.Location) note.latitude else null,
                        longitude = if (note is com.linker.app.domain.model.Note.Location) note.longitude else null,
                        backgroundColor = note.backgroundColor,
                        textColor = note.textColor,
                        expiresAt = note.expiresAt
                    )
                    
                    when (val msgRes = messageRepository.sendMessage(chatId = chatId, content = replyContent, replyToNote = noteRef)) {
                        is com.linker.app.core.util.Result.Success -> onResult(true, null)
                        is com.linker.app.core.util.Result.Error -> onResult(false, msgRes.message)
                        else -> {}
                    }
                }
                is com.linker.app.core.util.Result.Error -> onResult(false, chatRes.message)
                else -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearNote()
    }

    fun clearNote() {
        currentNoteId = null
        audioPlayerManager.stop()
        spotifyAppRemoteManager.pauseAndDisconnect()
    }
}
