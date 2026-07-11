package com.linker.app.presentation.screens.note

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.AudioPlayerManager
import com.linker.app.core.util.SpotifyAppRemoteManager
import com.linker.app.core.util.SpotifyAuthManager
import com.linker.app.domain.repository.SpotifyRepository
import com.linker.app.domain.repository.LyricsRepository
import com.linker.app.domain.repository.SyncedLyricLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.linker.app.domain.model.*

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val albumArtUrl: String?,
    val previewUrl: String? = null,
    val durationMs: Long = 0L,
    val isExplicit: Boolean = false
)

data class SpotifySearchUiState(
    val query: String = "",
    val searchType: SpotifySearchType = SpotifySearchType.ALL,
    val isLoading: Boolean = false,
    val results: List<SpotifySearchResultItem> = emptyList(), // For search results
    
    // Dashboard fields
    val recentTracks: List<SpotifyTrack> = emptyList(),
    val recommendedTracks: List<SpotifyTrack> = emptyList(),
    val top50Turkey: List<SpotifyTrack> = emptyList(),
    val top50Global: List<SpotifyTrack> = emptyList(),
    val moodPlaylists: Map<String, List<SpotifyTrack>> = emptyMap(),

    val error: String? = null,
    val isLoadingMoreSearch: Boolean = false,
    val searchOffset: Int = 0,
    val loadingMoreCategory: String? = null,
    val isLoadingLyrics: Boolean = false,
    val lyrics: List<SyncedLyricLine> = emptyList(),
    val lyricsError: String? = null
)

@HiltViewModel
class SpotifySearchViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val lyricsRepository: LyricsRepository,
    val audioPlayerManager: AudioPlayerManager,
    val spotifyAuthManager: SpotifyAuthManager,
    val spotifyAppRemoteManager: SpotifyAppRemoteManager
) : ViewModel() {

    private val _loadingPreviewTrackId = MutableStateFlow<String?>(null)
    val loadingPreviewTrackId: StateFlow<String?> = _loadingPreviewTrackId.asStateFlow()
    
    private val scrapedPreviewsCache = android.util.LruCache<String, String>(50)

    private val _uiState = MutableStateFlow(SpotifySearchUiState())
    val uiState: StateFlow<SpotifySearchUiState> = _uiState.asStateFlow()

    /** Tracks which premium (App Remote) track is currently loaded/playing. */
    private val _currentRemoteTrackId = MutableStateFlow<String?>(null)
    val currentRemoteTrackId: StateFlow<String?> = _currentRemoteTrackId.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadDashboard()
    }

    fun saveRecentTrack(track: SpotifyTrack) {
        spotifyRepository.saveLocalRecentTrack(track)
        // Update state
        _uiState.value = _uiState.value.copy(
            recentTracks = spotifyRepository.getLocalRecentTracks()
        )
    }

    fun fetchLyrics(trackName: String, artistName: String) {
        val cleanTrackName = trackName.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?\\]"), "").trim()
        
        // Fast synchronous cache check
        val cached = lyricsRepository.getCachedLyrics(cleanTrackName, artistName)
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                isLoadingLyrics = false,
                lyrics = cached,
                lyricsError = if (cached.isEmpty()) "Şarkı sözleri bulunamadı" else null
            )
            return
        }

        viewModelScope.launch {
            // Not cached, clear old lyrics to prevent flashing old track's lyrics and set loading
            _uiState.value = _uiState.value.copy(isLoadingLyrics = true, lyricsError = null, lyrics = emptyList())
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
                is com.linker.app.core.util.Result.Loading -> {
                    // Still loading, keep isLoadingLyrics = true
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
        // Step 1: Validate token
        if (spotifyAuthManager.accessToken.value == null) {
            Log.d("SpotifyViewModel", "No access token found. Triggering OAuth flow.")
            onAuthRequired()
            return
        }

        // Step 2: Token present → connect (or reuse existing connection) and play
        Log.d("SpotifyViewModel", "Access token present. Connecting via App Remote.")
        spotifyAppRemoteManager.connect(
            context = context,
            clientId = com.linker.app.BuildConfig.SPOTIFY_CLIENT_ID,
            onConnected = {
                val isSameTrack = _currentRemoteTrackId.value == track.id
                val isPlaying = spotifyAppRemoteManager.isPlaying.value

                when {
                    // Same track tapped while playing → pause
                    isSameTrack && isPlaying -> {
                        Log.d("SpotifyViewModel", "Same track playing → pausing.")
                        spotifyAppRemoteManager.pause()
                    }
                    // Same track tapped while paused → resume
                    isSameTrack && !isPlaying -> {
                        Log.d("SpotifyViewModel", "Same track paused → resuming.")
                        spotifyAppRemoteManager.resume()
                    }
                    // Different track tapped → always play the new one
                    else -> {
                        Log.d("SpotifyViewModel", "New track requested → playing: ${track.id}")
                        _currentRemoteTrackId.value = track.id
                        spotifyAppRemoteManager.playTrack(track.id)
                    }
                }
            },
            onError = { throwable ->
                Log.e("SpotifyAppRemoteManager", "App Remote Error: ", throwable)
                when (throwable) {
                    is com.spotify.android.appremote.api.error.NotLoggedInException -> {
                        Log.w("SpotifyViewModel", "NotLoggedInException: clearing token and requesting re-auth")
                        spotifyAuthManager.clearToken()
                        onAuthRequired()
                    }
                    is com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp ->
                        onError("Lütfen cihazınıza resmi Spotify uygulamasını yükleyin.")
                    is com.spotify.android.appremote.api.error.AuthenticationFailedException -> {
                        spotifyAuthManager.clearToken()
                        onError("Kimlik doğrulama başarısız oldu. Tekrar giriş yapın.")
                    }
                    else -> onError("Hata: ${throwable.message ?: "Spotify'a bağlanılamadı"}")
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        scrapedPreviewsCache.evictAll()
        audioPlayerManager.stop()
        spotifyAppRemoteManager.pauseAndDisconnect()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Fetch local recent tracks immediately
            val recent = spotifyRepository.getLocalRecentTracks()
            try {
                // Önerilenler: Today's Top Hits
                val recommendedResult = scrapePlaylistTracksWithOffset("37i9dQZF1DXcBWIGoYBM5M", 10)
                // TOP 50 Türkiye
                val top50TrResult = scrapePlaylistTracksWithOffset("37i9dQZEVXbIVYVBNw9D5K", 10)
                // TOP 50 Global
                val top50GlobalResult = scrapePlaylistTracksWithOffset("37i9dQZEVXbMDoHDwVN2tF", 10)
                // Enerjik
                val energeticResult = scrapePlaylistTracksWithOffset("37i9dQZF1EIeEZPgsd7pko", 10)
                // Hüzünlü
                val sadResult = scrapePlaylistTracksWithOffset("37i9dQZF1EIhNiJtmyKhaZ", 10)
                // Chill
                val chillResult = scrapePlaylistTracksWithOffset("37i9dQZF1EVHGWrwldPRtj", 10)

                val moods = mutableMapOf<String, List<SpotifyTrack>>()
                if (energeticResult is com.linker.app.core.util.Result.Success) moods["Enerjik"] = energeticResult.data
                if (sadResult is com.linker.app.core.util.Result.Success) moods["Hüzünlü"] = sadResult.data
                if (chillResult is com.linker.app.core.util.Result.Success) moods["Chill"] = chillResult.data

                // Gather the first error if any, to display to the user
                val firstError = listOf(recommendedResult, top50TrResult, top50GlobalResult)
                    .firstOrNull { it is com.linker.app.core.util.Result.Error } as? com.linker.app.core.util.Result.Error

                if (firstError != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Spotify API Hatası: ${firstError.message}",
                        recentTracks = recent
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        recentTracks = recent,
                        recommendedTracks = if (recommendedResult is com.linker.app.core.util.Result.Success) recommendedResult.data else emptyList(),
                        top50Turkey = if (top50TrResult is com.linker.app.core.util.Result.Success) top50TrResult.data else emptyList(),
                        top50Global = if (top50GlobalResult is com.linker.app.core.util.Result.Success) top50GlobalResult.data else emptyList(),
                        moodPlaylists = moods,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()
        audioPlayerManager.stop()
        
        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(500)
            
            // Guard against cancelled job
            if (!isActive) return@launch
            
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchOffset = 0)
            val result = spotifyRepository.search(newQuery, type = _uiState.value.searchType, limit = 10, offset = 0)
            
            // Check if still active after API call
            if (!isActive) return@launch
            
            when (result) {
                is com.linker.app.core.util.Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        results = result.data
                    )
                }
                is com.linker.app.core.util.Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is com.linker.app.core.util.Result.Loading -> {
                    // Do nothing
                }
            }
        }
    }

    fun setSearchType(type: SpotifySearchType) {
        _uiState.value = _uiState.value.copy(searchType = type)
        onQueryChange(_uiState.value.query)
    } 

    fun loadMoreSearchResults() {
        if (_uiState.value.isLoadingMoreSearch || _uiState.value.query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreSearch = true, error = null)
            val nextOffset = _uiState.value.searchOffset + 10
            
            val result = spotifyRepository.search(_uiState.value.query, type = _uiState.value.searchType, limit = 10, offset = nextOffset)
            
            when (result) {
                is com.linker.app.core.util.Result.Success -> {
                    val currentResults = _uiState.value.results.toMutableList()
                    
                    // Build set of existing IDs for efficient lookup
                    val existingIds = currentResults.mapNotNull { item ->
                        when(item) {
                            is com.linker.app.domain.model.SpotifySearchResultItem.Track -> item.track.id
                            is com.linker.app.domain.model.SpotifySearchResultItem.Artist -> item.artist.id
                            is com.linker.app.domain.model.SpotifySearchResultItem.Album -> item.album.id
                        }
                    }.toSet()
                    
                    // Filter duplicates using Set-based lookup
                    val newItems = result.data.filter { newItem ->
                        val newId = when(newItem) {
                            is com.linker.app.domain.model.SpotifySearchResultItem.Track -> newItem.track.id
                            is com.linker.app.domain.model.SpotifySearchResultItem.Artist -> newItem.artist.id
                            is com.linker.app.domain.model.SpotifySearchResultItem.Album -> newItem.album.id
                        }
                        newId !in existingIds
                    }
                    currentResults.addAll(newItems)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreSearch = false,
                        results = currentResults,
                        searchOffset = nextOffset
                    )
                }
                is com.linker.app.core.util.Result.Error -> {
                    _uiState.value = _uiState.value.copy(isLoadingMoreSearch = false, error = result.message)
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoadingMoreSearch = false)
                }
            }
        }
    }

    fun loadMore(categoryName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingMoreCategory = categoryName)
            
            val playlistId = when(categoryName) {
                "Önerilenler" -> "37i9dQZF1DXcBWIGoYBM5M"
                "TOP 50 Türkiye" -> "37i9dQZEVXbIVYVBNw9D5K"
                "TOP 50 Global" -> "37i9dQZEVXbMDoHDwVN2tF"
                "Enerjik" -> "37i9dQZF1EIeEZPgsd7pko"
                "Hüzünlü" -> "37i9dQZF1EIhNiJtmyKhaZ"
                "Chill" -> "37i9dQZF1EVHGWrwldPRtj"
                else -> return@launch
            }
            
            val currentList = when(categoryName) {
                "Önerilenler" -> _uiState.value.recommendedTracks
                "TOP 50 Türkiye" -> _uiState.value.top50Turkey
                "TOP 50 Global" -> _uiState.value.top50Global
                "Enerjik" -> _uiState.value.moodPlaylists["Enerjik"] ?: emptyList()
                "Hüzünlü" -> _uiState.value.moodPlaylists["Hüzünlü"] ?: emptyList()
                "Chill" -> _uiState.value.moodPlaylists["Chill"] ?: emptyList()
                else -> emptyList()
            }
            
            val offset = currentList.size
            
            // Fetch exactly 10 more tracks
            val result = scrapePlaylistTracksWithOffset(playlistId, 10, offset)
            
            if (result is com.linker.app.core.util.Result.Success) {
                val newList = currentList + result.data
                
                _uiState.value = when(categoryName) {
                    "Önerilenler" -> _uiState.value.copy(recommendedTracks = newList, loadingMoreCategory = null)
                    "TOP 50 Türkiye" -> _uiState.value.copy(top50Turkey = newList, loadingMoreCategory = null)
                    "TOP 50 Global" -> _uiState.value.copy(top50Global = newList, loadingMoreCategory = null)
                    "Enerjik", "Hüzünlü", "Chill" -> {
                        val newMoods = _uiState.value.moodPlaylists.toMutableMap()
                        newMoods[categoryName] = newList
                        _uiState.value.copy(moodPlaylists = newMoods, loadingMoreCategory = null)
                    }
                    else -> _uiState.value.copy(loadingMoreCategory = null)
                }
            } else {
                 // Stop loading state on error (silent fail for pagination is acceptable, or we could set a toast state)
                 _uiState.value = _uiState.value.copy(loadingMoreCategory = null)
            }
        }
    }

    private suspend fun scrapePlaylistTracksWithOffset(playlistId: String, totalWanted: Int = 10, startOffset: Int = 0): com.linker.app.core.util.Result<List<SpotifyTrack>> {
        val allTracks = mutableListOf<SpotifyTrack>()
        var offset = startOffset
        val maxLimitPerRequest = 10

        return try {
            kotlinx.coroutines.withTimeoutOrNull(30_000L) { // 30 second timeout
                while (offset - startOffset < totalWanted) {
                    val limit = minOf(maxLimitPerRequest, totalWanted - (offset - startOffset))
                    val result = spotifyRepository.scrapePlaylistTracks(playlistId, limit, offset)

                    if (result is com.linker.app.core.util.Result.Success) {
                        allTracks.addAll(result.data)
                        if (result.data.size < limit) break
                    } else if (result is com.linker.app.core.util.Result.Error) {
                        if (allTracks.isNotEmpty()) return@withTimeoutOrNull com.linker.app.core.util.Result.Success(allTracks)
                        return@withTimeoutOrNull result
                    }
                    offset += limit
                    delay(100)
                }
                com.linker.app.core.util.Result.Success(allTracks)
            } ?: com.linker.app.core.util.Result.Error("Playlist scraping timeout")
        } catch (e: Exception) {
            if (allTracks.isNotEmpty()) com.linker.app.core.util.Result.Success(allTracks)
            else com.linker.app.core.util.Result.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun fetchPlaylistTracksWithOffset(playlistId: String, totalWanted: Int = 10, startOffset: Int = 0): com.linker.app.core.util.Result<List<SpotifyTrack>> {
        val allTracks = mutableListOf<SpotifyTrack>()
        var offset = startOffset
        val maxLimitPerRequest = 10

        while (offset - startOffset < totalWanted) {
            val limit = minOf(maxLimitPerRequest, totalWanted - (offset - startOffset))
            val result = spotifyRepository.getPlaylistTracks(playlistId, limit, offset)

            if (result is com.linker.app.core.util.Result.Success) {
                allTracks.addAll(result.data)
                // Stop if Spotify returns fewer tracks than requested (end of results)
                if (result.data.size < limit) break
            } else if (result is com.linker.app.core.util.Result.Error) {
                // If we already fetched some tracks before the error occurred, just return them
                if (allTracks.isNotEmpty()) return com.linker.app.core.util.Result.Success(allTracks)
                return result
            }
            offset += limit
            // Tiny delay to avoid rate-limiting between chunk requests
            delay(100)
        }
        return com.linker.app.core.util.Result.Success(allTracks)
    }

    private suspend fun fetchTracksWithOffset(query: String, totalWanted: Int = 10, startOffset: Int = 0): com.linker.app.core.util.Result<List<SpotifyTrack>> {
        val allTracks = mutableListOf<SpotifyTrack>()
        var offset = startOffset
        val maxLimitPerRequest = 10

        while (offset - startOffset < totalWanted) {
            val limit = minOf(maxLimitPerRequest, totalWanted - (offset - startOffset))
            val result = spotifyRepository.searchTracks(query, limit, offset)

            if (result is com.linker.app.core.util.Result.Success) {
                allTracks.addAll(result.data)
                // Stop if Spotify returns fewer tracks than requested (end of results)
                if (result.data.size < limit) break
            } else if (result is com.linker.app.core.util.Result.Error) {
                // If we already fetched some tracks before the error occurred, just return them
                if (allTracks.isNotEmpty()) return com.linker.app.core.util.Result.Success(allTracks)
                return result
            }
            offset += limit
            // Tiny delay to avoid rate-limiting between chunk requests
            delay(100)
        }
        return com.linker.app.core.util.Result.Success(allTracks)
    }
}
