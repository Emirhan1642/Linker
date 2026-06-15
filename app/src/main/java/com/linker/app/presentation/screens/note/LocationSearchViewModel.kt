package com.linker.app.presentation.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.LocationRepository
import com.linker.app.domain.repository.LocationSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<LocationSearchResult> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LocationSearchViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationSearchUiState())
    val uiState: StateFlow<LocationSearchUiState> = _uiState.asStateFlow()

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
            val result = locationRepository.searchLocation(newQuery)
            
            if (result is Result.Success) {
                _uiState.value = _uiState.value.copy(isLoading = false, results = result.data)
            } else if (result is Result.Error) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }
}
