package com.linker.app.presentation.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.AudioPlayerManager
import com.linker.app.core.util.Result
import com.linker.app.core.util.SpotifyAppRemoteManager
import com.linker.app.core.util.SpotifyAuthManager
import com.linker.app.domain.model.SpotifyArtistProfile
import com.linker.app.domain.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import androidx.compose.runtime.Immutable

@Immutable
data class ArtistProfileUiState(
    val isLoading: Boolean = true,
    val profile: SpotifyArtistProfile? = null,
    val error: String? = null
)

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    val audioPlayerManager: AudioPlayerManager,
    val spotifyAuthManager: SpotifyAuthManager,
    val spotifyAppRemoteManager: SpotifyAppRemoteManager
) : ViewModel() {

    private val _loadingPreviewTrackId = MutableStateFlow<String?>(null)
    val loadingPreviewTrackId: StateFlow<String?> = _loadingPreviewTrackId.asStateFlow()
    
    private val scrapedPreviewsCache = android.util.LruCache<String, String>(50)

    private val _uiState = MutableStateFlow(ArtistProfileUiState())
    val uiState: StateFlow<ArtistProfileUiState> = _uiState.asStateFlow()

    private val _currentRemoteTrackId = MutableStateFlow<String?>(null)
    val currentRemoteTrackId: StateFlow<String?> = _currentRemoteTrackId.asStateFlow()

    fun saveRecentTrack(track: SpotifyTrack) {
        spotifyRepository.saveLocalRecentTrack(track)
    }

    fun loadProfile(artistId: String) {
        if (_uiState.value.profile?.artist?.id == artistId) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = spotifyRepository.getArtistProfile(artistId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profile = result.data
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is Result.Loading -> {
                    // Do nothing or keep isLoading = true
                }
            }
        }
    }

    fun playOrPausePreview(previewUrl: String?) {
        if (previewUrl == null) return
        audioPlayerManager.playPreview(previewUrl)
    }

    fun playTrack(context: Context, track: SpotifyTrack, onAuthRequired: () -> Unit, onError: (String) -> Unit) {
        val finalUrl = track.previewUrl ?: scrapedPreviewsCache.get(track.id)
        if (!finalUrl.isNullOrBlank()) {
            playOrPausePreview(finalUrl)
            return
        }
        
        viewModelScope.launch {
            _loadingPreviewTrackId.value = track.id
            val scrapedUrl = spotifyRepository.scrapeTrackPreviewUrl(track.id)
            _loadingPreviewTrackId.value = null
            
            if (!scrapedUrl.isNullOrBlank()) {
                scrapedPreviewsCache.put(track.id, scrapedUrl)
                playOrPausePreview(scrapedUrl)
            } else {
                playPremiumTrack(context, track, onAuthRequired, onError)
            }
        }
    }

    fun playPremiumTrack(context: Context, track: SpotifyTrack, onAuthRequired: () -> Unit, onError: (String) -> Unit) {
        audioPlayerManager.stop() // stop 30s preview if any
        if (spotifyAuthManager.accessToken.value == null) {
            onAuthRequired()
            return
        }
        spotifyAppRemoteManager.connect(
            context = context,
            clientId = com.linker.app.BuildConfig.SPOTIFY_CLIENT_ID,
            onConnected = {
                _currentRemoteTrackId.value = track.id
                spotifyAppRemoteManager.playTrack(track.id)
            },
            onError = { throwable ->
                if (throwable is com.spotify.android.appremote.api.error.NotLoggedInException) {
                    spotifyAuthManager.clearToken()
                    onAuthRequired()
                } else {
                    onError("Spotify App ile bağlantı kurulamadı: ${throwable.message}")
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
        spotifyAppRemoteManager.pauseAndDisconnect()
    }
}
