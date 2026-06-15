package com.linker.app.presentation.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.NoteType
import com.linker.app.domain.usecase.note.NoteInteractionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val selectedType: NoteType = NoteType.TEXT,
    val textContent: String = "",
    // Location Note
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String = "",
    // Countdown Note
    val countdownTitle: String = "",
    val targetTime: Long? = null,
    // Music Note
    val trackId: String = "",
    val trackName: String = "",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val musicCaption: String = "",
    
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteInteractionUseCases: NoteInteractionUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    fun selectType(type: NoteType) {
        _uiState.value = _uiState.value.copy(selectedType = type, error = null)
    }

    fun onTextChange(text: String) {
        _uiState.value = _uiState.value.copy(textContent = text, error = null)
    }

    fun onLocationChange(lat: Double, lng: Double, place: String) {
        _uiState.value = _uiState.value.copy(
            latitude = lat,
            longitude = lng,
            placeName = place,
            error = null
        )
    }

    fun onCountdownChange(title: String, timeMillis: Long) {
        _uiState.value = _uiState.value.copy(
            countdownTitle = title,
            targetTime = timeMillis,
            error = null
        )
    }

    fun onMusicChange(id: String, name: String, artist: String, artUrl: String?, caption: String) {
        _uiState.value = _uiState.value.copy(
            trackId = id,
            trackName = name,
            artistName = artist,
            albumArtUrl = artUrl,
            musicCaption = caption,
            error = null
        )
    }

    fun saveNote() {
        val state = _uiState.value
        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            val result = when (state.selectedType) {
                NoteType.TEXT -> {
                    noteInteractionUseCases.postNote(state.textContent)
                }
                NoteType.LOCATION -> {
                    if (state.latitude == null || state.longitude == null || state.placeName.isBlank()) {
                        Result.Error("Konum bilgisi eksik")
                    } else {
                        noteInteractionUseCases.postLocationNote(state.latitude, state.longitude, state.placeName)
                    }
                }
                NoteType.COUNTDOWN -> {
                    if (state.targetTime == null || state.countdownTitle.isBlank()) {
                        Result.Error("Geri sayım başlığı veya zamanı eksik")
                    } else {
                        noteInteractionUseCases.postCountdownNote(state.countdownTitle, state.targetTime)
                    }
                }
                NoteType.MUSIC -> {
                    if (state.trackId.isBlank()) {
                        Result.Error("Müzik seçimi eksik")
                    } else {
                        noteInteractionUseCases.postMusicNote(
                            trackId = state.trackId,
                            trackName = state.trackName,
                            artistName = state.artistName,
                            albumArtUrl = state.albumArtUrl,
                            caption = state.musicCaption
                        )
                    }
                }
                else -> Result.Error("Desteklenmeyen not türü")
            }

            if (result is Result.Success) {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } else if (result is Result.Error) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }
}
