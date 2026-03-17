package com.linker.app.presentation.screens.followlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.linker.app.core.util.Result
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.UserRepository
import com.linker.app.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FollowListType { FOLLOWERS, FOLLOWING, PENDING_REQUESTS, SENT_REQUESTS }

data class FollowListUiState(
    val isLoading: Boolean = true,
    val users: List<User> = emptyList(),
    val listType: FollowListType = FollowListType.FOLLOWERS,
    val targetUserId: String = "",
    val error: String? = null
)

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.FollowList>()
    private val _uiState = MutableStateFlow(
        FollowListUiState(
            listType     = FollowListType.valueOf(route.listType),
            targetUserId = route.userId
        )
    )
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result: Result<List<User>> = when (_uiState.value.listType) {
                FollowListType.FOLLOWERS        -> userRepository.getFollowers(_uiState.value.targetUserId)
                FollowListType.FOLLOWING        -> userRepository.getFollowing(_uiState.value.targetUserId)
                FollowListType.PENDING_REQUESTS -> userRepository.getPendingRequests()
                FollowListType.SENT_REQUESTS    -> userRepository.getSentRequests()
            }
            when (result) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, users = result.data) }
                is Result.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun acceptRequest(fromUserId: String) {
        viewModelScope.launch {
            userRepository.acceptFollowRequest(fromUserId)
            load()
        }
    }

    fun declineRequest(fromUserId: String) {
        viewModelScope.launch {
            userRepository.declineFollowRequest(fromUserId)
            load()
        }
    }

    fun cancelSentRequest(toUserId: String) {
        viewModelScope.launch {
            userRepository.cancelFollowRequest(toUserId)
            load()
        }
    }

    fun removeFollower(userId: String) {
        viewModelScope.launch {
            // takipçiyi çıkarmak = onun bizden unfollowunu sağlamak → şimdilik decline gibi davran
            userRepository.declineFollowRequest(userId)
            load()
        }
    }

    fun unfollow(userId: String) {
        viewModelScope.launch {
            userRepository.unfollowUser(userId)
            load()
        }
    }
}
