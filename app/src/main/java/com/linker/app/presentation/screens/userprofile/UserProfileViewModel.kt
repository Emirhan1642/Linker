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
import com.linker.app.domain.usecase.user.CurrentUserProvider
import com.linker.app.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.linker.app.core.util.UiText
import androidx.compose.runtime.Immutable

@Immutable
data class UserProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val posts: List<Link> = emptyList(),
    val selectedTab: Int = 0,
    val isActionLoading: Boolean = false,
    val isContentLocked: Boolean = false,
    val error: UiText? = null
)

sealed class UserProfileEffect {
    data class ShowSnackbar(val message: UiText) : UserProfileEffect()
    data object NavigateToChat : UserProfileEffect()
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val linkRepository: LinkRepository,
    private val currentUserProvider: CurrentUserProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = savedStateHandle.toRoute<Route.UserProfile>().userId

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UserProfileEffect>()
    val effects: SharedFlow<UserProfileEffect> = _effects.asSharedFlow()

    /** Aktif kullanıcının kendi profili mi görüntüleniyor? */
    val isOwnProfile: Boolean
        get() = currentUserProvider.getCurrentUserId() == userId

    init { loadUser() }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = userRepository.getUserById(userId)) {
                is Result.Success -> {
                    val user = result.data
                    val locked = user.privacy.isPrivate && !user.relationship.isFollowing
                    // isActionLoading'i de burada sıfırlıyoruz —
                    // follow işlemi tamamlanınca loadUser() çağrılır ve her şey temizlenir
                    _uiState.update {
                        it.copy(
                            isLoading       = false,
                            isActionLoading = false,
                            user            = user,
                            isContentLocked = locked
                        )
                    }
                    if (!locked) {
                        loadUserPosts()
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isActionLoading = false, error = UiText.DynamicString(result.message ?: "Unknown error"))
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun loadUserPosts() {
        linkRepository.observeLinksByAuthor(userId)
            .onEach { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(posts = result.data) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onTabSelected(tab: Int) = _uiState.update { it.copy(selectedTab = tab) }

    fun onFollowAction() {
        val user = _uiState.value.user ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }

            val result: Result<Unit> = when (user.followState()) {
                FollowState.FOLLOWING             -> userRepository.unfollowUser(user.userId)
                FollowState.REQUEST_SENT          -> userRepository.cancelFollowRequest(user.userId)
                FollowState.NOT_FOLLOWING,
                FollowState.NOT_FOLLOWING_PRIVATE -> userRepository.followUser(user.userId)
            }

            when (result) {
                is Result.Success -> loadUser()   // loadUser isActionLoading'i false yapıyor
                is Result.Error -> {
                    _uiState.update { it.copy(isActionLoading = false) }
                    _effects.emit(UserProfileEffect.ShowSnackbar(UiText.DynamicString(result.message ?: "Action failed")))
                }
                is Result.Loading -> {}
            }
        }
    }
}
