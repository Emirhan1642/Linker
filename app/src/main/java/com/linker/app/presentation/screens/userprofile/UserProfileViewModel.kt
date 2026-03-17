package com.linker.app.presentation.screens.userprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.linker.app.core.util.Result
import com.linker.app.domain.model.FollowState
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.model.followState
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.UserRepository
import com.linker.app.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val posts: List<Link> = emptyList(),
    val selectedTab: Int = 0,
    val isActionLoading: Boolean = false,
    /** Private hesap ve takip etmiyorsak içerik kilitli */
    val isContentLocked: Boolean = false,
    val error: String? = null
)

sealed class UserProfileEffect {
    data class ShowSnackbar(val message: String) : UserProfileEffect()
    data object NavigateToChat : UserProfileEffect()
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val linkRepository: LinkRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = savedStateHandle.toRoute<Route.UserProfile>().userId

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UserProfileEffect>()
    val effects: SharedFlow<UserProfileEffect> = _effects.asSharedFlow()

    init { loadUser() }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = userRepository.getUserById(userId)) {
                is Result.Success -> {
                    val user = result.data
                    val locked = user.isPrivate && !user.isFollowing
                    _uiState.update {
                        it.copy(isLoading = false, user = user, isContentLocked = locked)
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun onTabSelected(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    /**
     * Tek buton, tüm follow state'lerini yönetir:
     *  FOLLOWING         → unfollow
     *  REQUEST_SENT      → cancel request
     *  NOT_FOLLOWING     → follow (direkt)
     *  NOT_FOLLOWING_PRIVATE → send follow request
     */
    fun onFollowAction() {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }

            val result: Result<Unit> = when (user.followState()) {
                FollowState.FOLLOWING              -> userRepository.unfollowUser(user.userId)
                FollowState.REQUEST_SENT           -> userRepository.cancelFollowRequest(user.userId)
                FollowState.NOT_FOLLOWING,
                FollowState.NOT_FOLLOWING_PRIVATE  -> userRepository.followUser(user.userId)
            }

            when (result) {
                is Result.Success -> loadUser()   // fresh state
                is Result.Error   -> {
                    _uiState.update { it.copy(isActionLoading = false) }
                    _effects.emit(UserProfileEffect.ShowSnackbar(result.message))
                }
                is Result.Loading -> {}
            }
        }
    }
}
