package com.linker.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.Link
import com.linker.app.domain.repository.LinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeFeedUiState(
    val links: List<Link> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val linkRepository: LinkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    init {
        observeFeed()
        refreshFeed()
    }

    private fun observeFeed() {
        viewModelScope.launch {
            linkRepository.observeFeed().collect { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(links = result.data) }
                }
            }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            when (val result = linkRepository.refreshFeed(20)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isRefreshing = false, links = result.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isRefreshing = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun toggleLike(linkId: String) {
        viewModelScope.launch {
            linkRepository.toggleLike(linkId)
        }
    }

    fun toggleSave(linkId: String) {
        viewModelScope.launch {
            linkRepository.toggleSave(linkId)
        }
    }

    fun toggleRelink(linkId: String) {
        viewModelScope.launch {
            linkRepository.toggleRelink(linkId)
        }
    }
}
