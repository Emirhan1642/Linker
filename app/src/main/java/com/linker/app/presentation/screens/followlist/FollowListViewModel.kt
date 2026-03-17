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

enum class FollowListType { FOLLOWERS, FOLLOWING, PENDING_REQUESTS, SENT_REQUESTS }

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
    private val listTypeSafe = runCatching { FollowListType.valueOf(route.listType) }
        .getOrElse {
            Log.w("FollowListViewModel", "Unknown listType='${route.listType}', defaulting to FOLLOWERS")
            FollowListType.FOLLOWERS
        }
    private val myUid = FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow(
        FollowListUiState(
            listType     = listTypeSafe,
            targetUserId = route.userId,
            currentUid   = myUid
        )
    )
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    /** Sadece kendi takipçi/takip listesinde eylem butonları görünür */
    val isOwnList: Boolean get() = route.userId == myUid || route.userId == "me"

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isLocked = null) }

            when (_uiState.value.listType) {
                FollowListType.FOLLOWERS, FollowListType.FOLLOWING -> {
                    // Bu iki tip nullable döner: null = gizli, emptyList = açık ama boş
                    val result: Result<List<User>?> = if (_uiState.value.listType == FollowListType.FOLLOWERS)
                        userRepository.getFollowers(_uiState.value.targetUserId)
                    else
                        userRepository.getFollowing(_uiState.value.targetUserId)

                    when (result) {
                        is Result.Success -> {
                            val nullableList = result.data
                            if (nullableList == null) {
                                // Repository null döndürdü → liste gizli
                                _uiState.update { it.copy(isLoading = false, isLocked = true, users = emptyList()) }
                            } else {
                                // Liste açık (boş da olabilir)
                                var list = nullableList
                                if (_uiState.value.listType == FollowListType.FOLLOWERS && myUid != null) {
                                    val me = list.firstOrNull { it.userId == myUid }
                                    if (me != null) list = listOf(me) + list.filter { it.userId != myUid }
                                }
                                _uiState.update { it.copy(isLoading = false, isLocked = false, users = list) }
                            }
                        }
                        is Result.Error -> _uiState.update {
                            it.copy(isLoading = false, error = result.message)
                        }
                        is Result.Loading -> {}
                    }
                }

                FollowListType.PENDING_REQUESTS -> {
                    when (val result = userRepository.getPendingRequests()) {
                        is Result.Success -> _uiState.update {
                            it.copy(isLoading = false, isLocked = false, users = result.data)
                        }
                        is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                        is Result.Loading -> {}
                    }
                }

                FollowListType.SENT_REQUESTS -> {
                    when (val result = userRepository.getSentRequests()) {
                        is Result.Success -> _uiState.update {
                            it.copy(isLoading = false, isLocked = false, users = result.data)
                        }
                        is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                        is Result.Loading -> {}
                    }
                }
            }
        }
    }

    fun acceptRequest(fromUserId: String) = viewModelScope.launch {
        userRepository.acceptFollowRequest(fromUserId); load()
    }

    fun declineRequest(fromUserId: String) = viewModelScope.launch {
        userRepository.declineFollowRequest(fromUserId); load()
    }

    fun cancelSentRequest(toUserId: String) = viewModelScope.launch {
        userRepository.cancelFollowRequest(toUserId); load()
    }

    fun removeFollower(userId: String) = viewModelScope.launch {
        userRepository.declineFollowRequest(userId); load()
    }

    fun unfollow(userId: String) = viewModelScope.launch {
        userRepository.unfollowUser(userId); load()
    }
}
