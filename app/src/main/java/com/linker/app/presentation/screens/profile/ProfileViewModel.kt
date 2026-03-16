package com.linker.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.linker.app.presentation.components.StoryState

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val myPosts: List<Link> = emptyList(),
    val relinkedPosts: List<Link> = emptyList(),
    val storyState: StoryState = StoryState.NONE, // Derived from user's active stories later
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val linkRepository: LinkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    
    // We combine the base UI state with the flows from repositories
    val uiState: StateFlow<ProfileUiState> = combine(
        _uiState,
        userRepository.getCurrentUser(),
        linkRepository.observeRelinkedLinks()
    ) { state, user, relinked ->
        state.copy(
            isLoading = user == null && state.isLoading,
            user = user,
            relinkedPosts = relinked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState(isLoading = true))

    init {
        // Here we could refresh remote data or fetch exact user's authored links
        // But for now the combine operator handles merging the local reactive data
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
