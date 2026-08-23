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
                if (result is Result.Success) {
                    val allLinks = result.data
                    val followingPosts = allLinks.filter { followingUserIds.contains(it.author.userId) }
                    _uiState.update { 
                        it.copy(
                            links = allLinks,
                            followingLinks = followingPosts,
                            hasFollowingPosts = followingPosts.isNotEmpty()
                        )
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
