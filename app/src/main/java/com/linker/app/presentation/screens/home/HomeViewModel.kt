package com.linker.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.Link
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeFeedUiState(
    val links: List<Link> = emptyList(),
    val followingLinks: List<Link> = emptyList(),
    val hasFollowingPosts: Boolean = false,
    val hasActiveStories: Boolean = false,
    val selectedTab: Int = 0, // 0: All, 1: Following
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val linkRepository: LinkRepository,
    private val storyRepository: StoryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeFeedUiState())
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    private var followingUserIds: Set<String> = emptySet()

    init {
        observeFeed()
        observeFollowing()
        observeStories()
        refreshFeed()
    }

    private fun observeFeed() {
        viewModelScope.launch {
            linkRepository.observeFeed().collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = it.links.isEmpty()) }
                    }
                    is Result.Success -> {
                        val allLinks = result.data
                        val followingPosts = allLinks.filter { followingUserIds.contains(it.author.userId) }
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                links = allLinks,
                                followingLinks = followingPosts,
                                hasFollowingPosts = followingPosts.isNotEmpty(),
                                error = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = result.message) }
                    }
                }
            }
        }
    }

    private fun observeFollowing() {
        viewModelScope.launch {
            userRepository.observeFollowing().collect { followingResult ->
                if (followingResult is Result.Success) {
                    followingUserIds = followingResult.data.map { it.userId }.toSet()
                    _uiState.update { state ->
                        val followingPosts = state.links.filter { followingUserIds.contains(it.author.userId) }
                        state.copy(
                            followingLinks = followingPosts,
                            hasFollowingPosts = followingPosts.isNotEmpty()
                        )
                    }
                }
            }
        }
    }

    private fun observeStories() {
        viewModelScope.launch {
            storyRepository.observeActiveUserStories().collect { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(hasActiveStories = result.data.isNotEmpty()) }
                }
            }
        }
    }

    fun onTabSelected(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            when (val result = linkRepository.refreshFeed(20)) {
                is Result.Success -> {
                    val allLinks = result.data
                    val followingPosts = allLinks.filter { followingUserIds.contains(it.author.userId) }
                    _uiState.update { 
                        it.copy(
                            isRefreshing = false, 
                            links = allLinks,
                            followingLinks = followingPosts,
                            hasFollowingPosts = followingPosts.isNotEmpty()
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isRefreshing = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun toggleLike(linkId: String) {
        _uiState.update { state ->
            val updateList = { list: List<com.linker.app.domain.model.Link> ->
                list.map { link ->
                    if (link.linkId == linkId) {
                        val newLiked = !link.engagement.isLiked
                        val delta = if (newLiked) 1 else -1
                        link.copy(
                            engagement = link.engagement.copy(
                                isLiked = newLiked,
                                likesCount = (link.engagement.likesCount + delta).coerceAtLeast(0)
                            )
                        )
                    } else link
                }
            }
            state.copy(
                links = updateList(state.links),
                followingLinks = updateList(state.followingLinks)
            )
        }
        viewModelScope.launch {
            linkRepository.toggleLike(linkId)
        }
    }

    fun toggleSave(linkId: String) {
        _uiState.update { state ->
            val updateList = { list: List<com.linker.app.domain.model.Link> ->
                list.map { link ->
                    if (link.linkId == linkId) {
                        val newSaved = !link.engagement.isSaved
                        val delta = if (newSaved) 1 else -1
                        link.copy(
                            engagement = link.engagement.copy(
                                isSaved = newSaved,
                                savesCount = (link.engagement.savesCount + delta).coerceAtLeast(0)
                            )
                        )
                    } else link
                }
            }
            state.copy(
                links = updateList(state.links),
                followingLinks = updateList(state.followingLinks)
            )
        }
        viewModelScope.launch {
            linkRepository.toggleSave(linkId)
        }
    }

    fun toggleRelink(linkId: String) {
        _uiState.update { state ->
            val updateList = { list: List<com.linker.app.domain.model.Link> ->
                list.map { link ->
                    if (link.linkId == linkId) {
                        val newRelinked = !link.engagement.isRelinked
                        val delta = if (newRelinked) 1 else -1
                        link.copy(
                            engagement = link.engagement.copy(
                                isRelinked = newRelinked,
                                relinksCount = (link.engagement.relinksCount + delta).coerceAtLeast(0)
                            )
                        )
                    } else link
                }
            }
            state.copy(
                links = updateList(state.links),
                followingLinks = updateList(state.followingLinks)
            )
        }
        viewModelScope.launch {
            linkRepository.toggleRelink(linkId)
        }
    }

    fun hidePost(linkId: String) {
        _uiState.update { state ->
            state.copy(
                links = state.links.filter { it.linkId != linkId },
                followingLinks = state.followingLinks.filter { it.linkId != linkId }
            )
        }
    }

    fun hideUserPosts(userId: String) {
        _uiState.update { state ->
            state.copy(
                links = state.links.filter { it.author.userId != userId },
                followingLinks = state.followingLinks.filter { it.author.userId != userId }
            )
        }
    }

    suspend fun getUserByUsername(username: String): Result<com.linker.app.domain.model.User> {
        return userRepository.getUserByUsername(username)
    }
}
