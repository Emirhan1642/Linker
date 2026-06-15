package com.linker.app.presentation.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.linker.app.domain.repository.SpotifyRepository
import javax.inject.Inject

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val albumArtUrl: String?
)

data class SpotifySearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SpotifyTrack> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SpotifySearchViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifySearchUiState())
    val uiState: StateFlow<SpotifySearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = spotifyRepository.searchTracks(newQuery)
            
            if (result is com.linker.app.core.util.Result.Success) {
                _uiState.value = _uiState.value.copy(isLoading = false, results = result.data)
            } else if (result is com.linker.app.core.util.Result.Error) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }
}
