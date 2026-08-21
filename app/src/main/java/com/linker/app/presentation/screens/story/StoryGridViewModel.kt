package com.linker.app.presentation.screens.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.domain.usecase.story.ObserveStoriesGridUseCase
import com.linker.app.domain.usecase.user.ObserveUserPreferencesUseCase
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.Immutable

@Immutable
data class StoryGridUiState(
    val userStories: List<UserStories> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class StoryGridViewModel @Inject constructor(
    private val observeStoriesGridUseCase: ObserveStoriesGridUseCase,
    private val observeUserPreferencesUseCase: ObserveUserPreferencesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryGridUiState())
    val uiState: StateFlow<StoryGridUiState> = _uiState.asStateFlow()

    init {
        observeStories()
    }

    private fun observeStories() {
        viewModelScope.launch {
            observeUserPreferencesUseCase().collect { prefsResult ->
                val mutedIds = if (prefsResult is Result.Success) prefsResult.data.mutedUserIds else emptySet()
                val blockedIds = if (prefsResult is Result.Success) prefsResult.data.blockedUserIds else emptySet()

                observeStoriesGridUseCase(mutedIds, blockedIds).collect { result ->
                    when (result) {
                        is Result.Success -> _uiState.update {
                            it.copy(userStories = result.data, isLoading = false, error = null)
                        }
                        is Result.Error -> _uiState.update {
                            it.copy(isLoading = false, error = result.message)
                        }
                        is Result.Loading -> _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }
}
