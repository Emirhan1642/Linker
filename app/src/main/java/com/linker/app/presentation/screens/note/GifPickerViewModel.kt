package com.linker.app.presentation.screens.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.GifItem
import com.linker.app.domain.repository.GifRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.Immutable

@Immutable
data class GifPickerUiState(
    val query: String = "",
    val gifs: List<GifItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GifPickerViewModel @Inject constructor(
    private val gifRepository: GifRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GifPickerUiState())
    val uiState: StateFlow<GifPickerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTrending()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            loadTrending()
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = gifRepository.searchGifs(query)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        gifs = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun loadTrending() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = gifRepository.getTrendingGifs()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        gifs = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }
}
