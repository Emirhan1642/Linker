package com.linker.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.linker.app.core.util.Result
import com.linker.app.presentation.components.StoryState

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val myPosts: List<Link> = emptyList(),
    val relinkedPosts: List<Link> = emptyList(),
    val storyState: StoryState = StoryState.NONE,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val linkRepository: LinkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))

    val uiState: StateFlow<ProfileUiState> = userRepository.observeCurrentUser()
        .flatMapLatest { userResult ->
            val user = (userResult as? Result.Success<User?>)?.data
            val uid = user?.userId ?: ""
            combine(
                _uiState,
                linkRepository.observeLinksByAuthor(uid),
                linkRepository.observeRelinkedLinks()
            ) { state, postsResult, relinkedResult ->
                val myPosts = (postsResult as? Result.Success<List<Link>>)?.data ?: emptyList()
                val relinked = (relinkedResult as? Result.Success<List<Link>>)?.data ?: emptyList()
                state.copy(
                    isLoading     = user == null && state.isLoading,
                    user          = user,
                    myPosts       = myPosts,
                    relinkedPosts = relinked
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileUiState(isLoading = true)
        )

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    val currentUid: String?
        get() = uiState.value.user?.userId
}
