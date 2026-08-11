package com.linker.app.presentation.screens.note

import kotlinx.coroutines.isActive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.NoteType
import com.linker.app.domain.repository.LiveLocationRepository
import com.linker.app.domain.usecase.note.NoteInteractionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val locationCity: String = "",
    val locationDistrict: String = "",
    val locationUpdatedAt: Long? = null,
    val isLocationLoading: Boolean = false,
    val locationError: String? = null,
    val authorProfilePictureUrl: String? = null,
    // Countdown Note
    val countdownTitle: String = "",
    val targetTime: Long? = null,
    // Music Note
    val trackId: String = "",
    val trackName: String = "",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val previewUrl: String? = null,
    val musicCaption: String = "",
    val clipStartMs: Long = 0,
    val clipEndMs: Long = 30000,

    // GIF Note
    val gifUrl: String? = null,
    val gifAspectRatio: Float? = null,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    
    // Custom Colors
    val selectedBackgroundColor: String? = null,
    val selectedTextColor: String? = null,
    val isColorPickerVisible: Boolean = false
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteInteractionUseCases: NoteInteractionUseCases,
    private val liveLocationRepository: LiveLocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    /** Running location update job — cancelled when a new one starts or ViewModel is cleared. */
    private var locationUpdateJob: Job? = null

    fun selectType(type: NoteType) {
        _uiState.value = _uiState.value.copy(selectedType = type, error = null)
    }

    fun onTextChange(text: String) {
        val maxLength = when (_uiState.value.selectedType) {
            NoteType.TEXT -> com.linker.app.domain.model.Note.Text.MAX_TEXT_CONTENT_LENGTH
            NoteType.MUSIC -> com.linker.app.domain.model.Note.Music.MAX_MUSIC_CONTENT_LENGTH
            NoteType.COUNTDOWN -> com.linker.app.domain.model.Note.Countdown.MAX_COUNTDOWN_CONTENT_LENGTH
            NoteType.GIF -> com.linker.app.domain.model.Note.Gif.MAX_GIF_CONTENT_LENGTH
            NoteType.LOCATION -> NoteType.LOCATION.maxContentLength
        }
        
        val codePointCount = text.codePointCount(0, text.length)
        when {
            codePointCount <= maxLength -> {
                _uiState.value = _uiState.value.copy(
                    textContent = text,
                    error = null
                )
            }
            codePointCount > maxLength -> {
                // Truncate to max length
                val truncated = text.substring(0, text.length - (codePointCount - maxLength))
                _uiState.value = _uiState.value.copy(
                    textContent = truncated,
                    error = "Metin limite ulaştı ($maxLength karakter)"
                )
            }
        }
    }

    fun onLocationChange(lat: Double, lng: Double, place: String) {
        _uiState.value = _uiState.value.copy(
            latitude = lat,
            longitude = lng,
            placeName = place,
            error = null
        )
    }

    /**
     * Fetches the device's current location using FusedLocationProvider,
     * then reverse-geocodes it to obtain city + district names.
     *
     * Also starts a continuous update loop (every 5 s) that keeps
     * [locationUpdatedAt] fresh for as long as NoteEditor is open.
     */
    fun fetchCurrentLocation() {
        // Prevent duplicate fetch while loading
        if (_uiState.value.isLocationLoading) return

        _uiState.value = _uiState.value.copy(
            isLocationLoading = true,
            locationError = null,
            selectedType = NoteType.LOCATION
        )

        viewModelScope.launch {
            when (val locationResult = liveLocationRepository.getCurrentLocation()) {
                is Result.Success -> {
                    val loc = locationResult.data
                    // Resolve city / district
                    val placeResult = liveLocationRepository.reverseGeocode(loc.lat, loc.lon)
                    val place = when (placeResult) {
                        is Result.Success -> placeResult.data
                        is Result.Error -> {
                            // Log error but use coordinates as fallback
                            android.util.Log.w(
                                "LocationUpdate",
                                "Reverse geocoding failed: ${placeResult.message}"
                            )
                            com.linker.app.domain.repository.PlaceName(
                                city = String.format(java.util.Locale.US, "%.4f", loc.lat),
                                district = String.format(java.util.Locale.US, "%.4f", loc.lon)
                            )
                        }
                        else -> com.linker.app.domain.repository.PlaceName("", "")
                    }

                    val displayName = if (place.city.isBlank() && place.district.isBlank()) {
                        String.format(java.util.Locale.US, "%.4f°N, %.4f°E", loc.lat, loc.lon)
                    } else {
                        place.display()
                    }

                    _uiState.value = _uiState.value.copy(
                        latitude = loc.lat,
                        longitude = loc.lon,
                        placeName = displayName,
                        locationCity = place.city,
                        locationDistrict = place.district,
                        locationUpdatedAt = loc.updatedAt,
                        isLocationLoading = false,
                        locationError = null
                    )

                    // Start continuous updates
                    startLocationUpdates()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLocationLoading = false,
                        locationError = locationResult.message ?: "Konum alınamadı"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLocationLoading = false)
                }
            }
        }
    }

    /**
     * Observes live location updates every 5 seconds while the ViewModel is alive.
     * Each update reverse-geocodes to keep the city/district display fresh.
     */
    private fun startLocationUpdates() {
        locationUpdateJob?.cancel()
        locationUpdateJob = viewModelScope.launch {
            try {
                liveLocationRepository.observeLocationUpdates(intervalMs = 5_000L)
                    .collect { loc ->
                        // Check if job is still active before processing
                        if (!isActive) {
                            return@collect
                        }

                        val placeResult = liveLocationRepository.reverseGeocode(loc.lat, loc.lon)
                        val place = if (placeResult is Result.Success) placeResult.data
                                    else com.linker.app.domain.repository.PlaceName(
                                        _uiState.value.locationCity,
                                        _uiState.value.locationDistrict
                                    )

                        // Guard against post-cancellation state updates
                        if (isActive) {
                            _uiState.value = _uiState.value.copy(
                                latitude = loc.lat,
                                longitude = loc.lon,
                                placeName = place.display(),
                                locationCity = place.city,
                                locationDistrict = place.district,
                                locationUpdatedAt = loc.updatedAt
                            )
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when ViewModel is cleared or job is cancelled
                throw e
            }
        }
    }

    fun onCountdownChange(title: String, timeMillis: Long) {
        _uiState.value = _uiState.value.copy(
            countdownTitle = title,
            targetTime = timeMillis,
            error = null
        )
    }

    fun onMusicChange(id: String, name: String, artist: String, artUrl: String?, preview: String?, caption: String) {
        _uiState.value = _uiState.value.copy(
            trackId = id,
            trackName = name,
            artistName = artist,
            albumArtUrl = artUrl,
            previewUrl = preview,
            musicCaption = caption,
            error = null
        )
    }

    fun onMusicClipChange(startMs: Long, endMs: Long) {
        _uiState.value = _uiState.value.copy(clipStartMs = startMs, clipEndMs = endMs)
    }

    fun onGifSelected(url: String, aspectRatio: Float?) {
        _uiState.value = _uiState.value.copy(
            selectedType = NoteType.GIF,
            gifUrl = url,
            gifAspectRatio = aspectRatio,
            error = null
        )
    }

    fun clearGifSelection() {
        _uiState.value = _uiState.value.copy(
            gifUrl = null,
            gifAspectRatio = null,
            selectedType = NoteType.TEXT
        )
    }

    fun onColorSelected(bgHex: String, textHex: String) {
        _uiState.value = _uiState.value.copy(
            selectedBackgroundColor = bgHex,
            selectedTextColor = textHex
        )
    }

    fun toggleColorPicker() {
        _uiState.value = _uiState.value.copy(
            isColorPickerVisible = !_uiState.value.isColorPickerVisible
        )
    }

    fun saveNote() {
        val state = _uiState.value
        
        // Pre-save validation
        val validationError = validateNoteState(state)
        if (validationError != null) {
            _uiState.value = state.copy(error = validationError)
            return
        }
        
        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            val result = when (state.selectedType) {
                NoteType.TEXT -> {
                    noteInteractionUseCases.postNote(
                        content = state.textContent,
                        backgroundColor = state.selectedBackgroundColor,
                        textColor = state.selectedTextColor
                    )
                }
                NoteType.LOCATION -> {
                    if (state.latitude == null || state.longitude == null || state.placeName.isBlank()) {
                        Result.Error("Konum bilgisi eksik")
                    } else {
                        noteInteractionUseCases.postLocationNote(
                            latitude = state.latitude, 
                            longitude = state.longitude, 
                            placeName = state.placeName,
                            backgroundColor = state.selectedBackgroundColor,
                            textColor = state.selectedTextColor
                        )
                    }
                }
                NoteType.COUNTDOWN -> {
                    if (state.targetTime == null || state.countdownTitle.isBlank()) {
                        Result.Error("Geri sayım başlığı veya zamanı eksik")
                    } else {
                        noteInteractionUseCases.postCountdownNote(
                            title = state.countdownTitle, 
                            targetTime = state.targetTime, 
                            content = state.textContent,
                            backgroundColor = state.selectedBackgroundColor,
                            textColor = state.selectedTextColor
                        )
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
                            previewUrl = state.previewUrl,
                            clipStartMs = state.clipStartMs,
                            clipEndMs = state.clipEndMs,
                            caption = state.textContent,
                            backgroundColor = state.selectedBackgroundColor,
                            textColor = state.selectedTextColor
                        )
                    }
                }
                NoteType.GIF -> {
                    if (state.gifUrl == null) {
                        Result.Error("Lütfen bir GIF seçin")
                    } else {
                        noteInteractionUseCases.postGifNote(
                            gifUrl = state.gifUrl,
                            content = state.textContent,
                            aspectRatio = state.gifAspectRatio,
                            backgroundColor = state.selectedBackgroundColor,
                            textColor = state.selectedTextColor
                        )
                    }
                }
            }

            if (result is Result.Success) {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } else if (result is Result.Error) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
            }
        }
    }

    /**
     * Validates the current note state before saving.
     * Returns an error message if validation fails, or null if valid.
     */
    private fun validateNoteState(state: NoteEditorUiState): String? {
        return when (state.selectedType) {
            NoteType.TEXT -> {
                when {
                    state.textContent.isBlank() -> "Lütfen metin girin"
                    state.textContent.length > com.linker.app.domain.model.Note.Text.MAX_TEXT_CONTENT_LENGTH ->
                        "Metin çok uzun (Max: ${com.linker.app.domain.model.Note.Text.MAX_TEXT_CONTENT_LENGTH} karakter)"
                    else -> null
                }
            }
            NoteType.MUSIC -> {
                when {
                    state.trackId.isBlank() -> "Müzik seçimi eksik"
                    state.trackName.isBlank() -> "Müzik adı eksik"
                    state.artistName.isBlank() -> "Sanatçı adı eksik"
                    state.clipEndMs - state.clipStartMs < 1000 -> "Klip en az 1 saniye olmalı"
                    state.clipEndMs - state.clipStartMs > 30_000 -> "Klip maksimum 30 saniye olabilir"
                    else -> null
                }
            }
            NoteType.LOCATION -> {
                when {
                    state.latitude == null || state.longitude == null -> "Konum verisi eksik"
                    state.placeName.isBlank() -> "Konum adı eksik"
                    else -> null
                }
            }
            NoteType.GIF -> {
                when {
                    state.gifUrl.isNullOrBlank() -> "GIF seçimi eksik"
                    !state.gifUrl!!.startsWith("http") -> "GIF URL geçersiz"
                    state.gifAspectRatio == null || state.gifAspectRatio!! <= 0f ->
                        "GIF aspect ratio geçersiz"
                    else -> null
                }
            }
            NoteType.COUNTDOWN -> {
                when {
                    state.targetTime == null -> "Hedef zaman eksik"
                    state.targetTime!! <= System.currentTimeMillis() ->
                        "Hedef zaman geçmiş olmamalı"
                    state.countdownTitle.isBlank() -> "Başlık gerekli"
                    state.countdownTitle.length > 100 -> "Başlık çok uzun"
                    else -> null
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationUpdateJob?.cancel()
    }
}
