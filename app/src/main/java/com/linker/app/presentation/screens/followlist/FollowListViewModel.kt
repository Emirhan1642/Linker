package com.linker.app.presentation.screens.followlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
import androidx.compose.runtime.Immutable

enum class FollowListType { FOLLOWERS, FOLLOWING, PENDING_REQUESTS, SENT_REQUESTS }

@Immutable
data class FollowListUiState(
    val isLoading: Boolean = true,
    val users: List<User> = emptyList(),
    val listType: FollowListType = FollowListType.FOLLOWERS,
    val targetUserId: String = "",
    val currentUid: String? = null,
    /**
     * null  = henüz yüklenmedi
     * true  = liste gizli (private hesap veya hideFollowLists)
     * false = liste açık (boş da olabilir)
     */
    val isLocked: Boolean? = null,
    val error: String? = null
)

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.FollowList>()
    private val listTypeSafe = route.listType
    private val myUid = FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow(
        FollowListUiState(
            listType     = listTypeSafe,
            targetUserId = route.userId,
            currentUid   = myUid
        )
    )
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    companion object {
        const val CURRENT_USER_ID_PARAM = "me"
    }

    /** Sadece kendi takipçi/takip listesinde eylem butonları görünür */
    val isOwnList: Boolean get() = route.userId == myUid || route.userId == CURRENT_USER_ID_PARAM

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isLocked = null) }

            when (_uiState.value.listType) {
                FollowListType.FOLLOWERS, FollowListType.FOLLOWING -> {
                    val result = if (_uiState.value.listType == FollowListType.FOLLOWERS)
                        userRepository.getFollowers(_uiState.value.targetUserId)
                    else
                        userRepository.getFollowing(_uiState.value.targetUserId)

                    when (result) {
                        is Result.Success -> {
                            val paginatedUsers = result.data
                            var list = paginatedUsers.users
                            if (_uiState.value.listType == FollowListType.FOLLOWERS && myUid != null) {
                                list = list.sortedByDescending { it.userId == myUid }
                            }
                            _uiState.update { it.copy(isLoading = false, isLocked = false, users = list) }
                        }
                        is Result.Error -> {
                            if (result.message?.contains("PrivateAccountLocked") == true || result.message?.contains("Not authorized to view") == true) {
                                _uiState.update { it.copy(isLoading = false, isLocked = true, users = emptyList()) }
                            } else {
                                _uiState.update { it.copy(isLoading = false, error = result.message) }
                            }
                        }
                        is Result.Loading -> {}
                    }
                }

                FollowListType.PENDING_REQUESTS -> {
                    when (val result = userRepository.getPendingRequests()) {
                        is Result.Success -> _uiState.update {
                            it.copy(isLoading = false, isLocked = false, users = result.data.users)
                        }
                        is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                        is Result.Loading -> {}
                    }
                }

                FollowListType.SENT_REQUESTS -> {
                    when (val result = userRepository.getSentRequests()) {
                        is Result.Success -> _uiState.update {
                            it.copy(isLoading = false, isLocked = false, users = result.data.users)
                        }
                        is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                        is Result.Loading -> {}
                    }
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun acceptRequest(fromUserId: String) {
        val previousUsers = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filter { u -> u.userId != fromUserId }) }
        viewModelScope.launch {
            when (val res = userRepository.acceptFollowRequest(fromUserId)) {
                is Result.Success -> {}
                is Result.Error -> _uiState.update {
                    it.copy(users = previousUsers, error = res.message ?: "Failed to accept request")
                }
                is Result.Loading -> {}
            }
        }
    }

    fun declineRequest(fromUserId: String) {
        val previousUsers = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filter { u -> u.userId != fromUserId }) }
        viewModelScope.launch {
            when (val res = userRepository.declineFollowRequest(fromUserId)) {
                is Result.Success -> {}
                is Result.Error -> _uiState.update {
                    it.copy(users = previousUsers, error = res.message ?: "Failed to decline request")
                }
                is Result.Loading -> {}
            }
        }
    }

    fun cancelSentRequest(toUserId: String) {
        val previousUsers = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filter { u -> u.userId != toUserId }) }
        viewModelScope.launch {
            when (val res = userRepository.cancelFollowRequest(toUserId)) {
                is Result.Success -> {}
                is Result.Error -> _uiState.update {
                    it.copy(users = previousUsers, error = res.message ?: "Failed to cancel request")
                }
                is Result.Loading -> {}
            }
        }
    }

    fun removeFollower(userId: String) {
        val previousUsers = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filter { u -> u.userId != userId }) }
        viewModelScope.launch {
            when (val res = userRepository.removeFollower(userId)) {
                is Result.Success -> {}
                is Result.Error -> _uiState.update {
                    it.copy(users = previousUsers, error = res.message ?: "Failed to remove follower")
                }
                is Result.Loading -> {}
            }
        }
    }

    fun unfollow(userId: String) {
        val previousUsers = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filter { u -> u.userId != userId }) }
        viewModelScope.launch {
            when (val res = userRepository.unfollowUser(userId)) {
                is Result.Success -> {}
                is Result.Error -> _uiState.update {
                    it.copy(users = previousUsers, error = res.message ?: "Failed to unfollow user")
                }
                is Result.Loading -> {}
            }
        }
    }
}
