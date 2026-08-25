package com.linker.app.presentation.screens.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.Story
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.usecase.story.LikeStoryUseCase
import com.linker.app.domain.usecase.story.ReactToStoryUseCase
import com.linker.app.domain.usecase.story.ReportStoryUseCase
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.Immutable

import com.linker.app.domain.usecase.user.CurrentUserProvider

@Immutable
data class StoryViewerUiState(
    val currentStories: List<Story> = emptyList(),
    val allUserStories: List<UserStories> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    private val likeStoryUseCase: LikeStoryUseCase,
    private val reactToStoryUseCase: ReactToStoryUseCase,
    private val reportStoryUseCase: ReportStoryUseCase,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val currentUserId: String
        get() = currentUserProvider.getCurrentUserId() ?: ""

    private val _uiState = MutableStateFlow(StoryViewerUiState())
    val uiState: StateFlow<StoryViewerUiState> = _uiState.asStateFlow()

    init {
        loadAllUserStories()
    }

    fun initialize(initialStories: List<UserStories>) {
        if (initialStories.isNotEmpty() && _uiState.value.allUserStories.isEmpty()) {
            _uiState.update { it.copy(allUserStories = initialStories) }
        }
    }

    private fun loadAllUserStories() {
        viewModelScope.launch {
            storyRepository.observeActiveUserStories().collect { result ->
                if (result is Result.Success) {
                    _uiState.update {
                        it.copy(allUserStories = result.data)
                    }
                }
            }
        }
    }

    fun loadStoriesForUser(userStories: UserStories) {
        _uiState.update {
            it.copy(currentStories = userStories.getActiveStories())
        }
    }

    fun markViewed(storyId: String) {
        viewModelScope.launch {
            storyRepository.markStoryAsViewed(storyId)
        }
    }

    fun likeStory(storyId: String) {
        viewModelScope.launch {
            // Optimistic update for both currentStories and allUserStories
            _uiState.update { state ->
                state.copy(
                    currentStories = state.currentStories.map { story ->
                        if (story.storyId == storyId) story.toggleLike() else story
                    },
                    allUserStories = state.allUserStories.map { group ->
                        group.copy(stories = group.stories.map { story ->
                            if (story.storyId == storyId) story.toggleLike() else story
                        })
                    }
                )
            }
            likeStoryUseCase(storyId)
        }
    }

    fun reactToStory(storyId: String, emoji: String?) {
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                state.copy(
                    currentStories = state.currentStories.map { story ->
                        if (story.storyId == storyId) story.withReaction(emoji) else story
                    },
                    allUserStories = state.allUserStories.map { group ->
                        group.copy(stories = group.stories.map { story ->
                            if (story.storyId == storyId) story.withReaction(emoji) else story
                        })
                    }
                )
            }
            reactToStoryUseCase(storyId, emoji)
        }
    }

    fun replyToStory(storyId: String, content: String) {
        viewModelScope.launch {
            storyRepository.replyToStory(storyId, content)
        }
    }

    fun deleteStory(storyId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = storyRepository.deleteStory(storyId)
            if (result is Result.Success) {
                _uiState.update { state ->
                    state.copy(
                        currentStories = state.currentStories.filter { it.storyId != storyId },
                        allUserStories = state.allUserStories.map { group ->
                            group.copy(stories = group.stories.filter { it.storyId != storyId })
                        }.filter { it.stories.isNotEmpty() }
                    )
                }
                onDeleted()
            }
        }
    }

    fun reportStory(storyId: String, reason: com.linker.app.domain.model.ReportReason) {
        viewModelScope.launch {
            reportStoryUseCase(storyId, reason)
        }
    }

    val storyViewers = MutableStateFlow<List<com.linker.app.domain.repository.StoryViewer>>(emptyList())
    val isLoadingViewers = MutableStateFlow(false)

    fun loadStoryViewers(storyId: String) {
        viewModelScope.launch {
            isLoadingViewers.value = true
            when (val result = storyRepository.getViewers(storyId)) {
                is Result.Success -> storyViewers.value = result.data
                else -> storyViewers.value = emptyList()
            }
            isLoadingViewers.value = false
        }
    }
}
