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
    private val reportStoryUseCase: ReportStoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryViewerUiState())
    val uiState: StateFlow<StoryViewerUiState> = _uiState.asStateFlow()

    init {
        loadAllUserStories()
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
            // Optimistic update
            _uiState.update { state ->
                state.copy(
                    currentStories = state.currentStories.map { story ->
                        if (story.storyId == storyId) story.toggleLike() else story
                    }
                )
            }
            // Fire-and-forget; on error we revert
            val result = likeStoryUseCase(storyId)
            if (result is Result.Error) {
                // Revert optimistic update
                _uiState.update { state ->
                    state.copy(
                        currentStories = state.currentStories.map { story ->
                            if (story.storyId == storyId) story.toggleLike() else story
                        }
                    )
                }
            }
        }
    }

    fun reactToStory(storyId: String, emoji: String?) {
        viewModelScope.launch {
            // Optimistic update
            _uiState.update { state ->
                state.copy(
                    currentStories = state.currentStories.map { story ->
                        if (story.storyId == storyId) story.withReaction(emoji) else story
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

    fun reportStory(storyId: String, reason: com.linker.app.domain.model.ReportReason) {
        viewModelScope.launch {
            reportStoryUseCase(storyId, reason)
        }
    }
}
