package com.linker.app.presentation.screens.story

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.domain.repository.StoryPrivacy
import com.linker.app.domain.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StoryEditorUiState(
    val selectedMediaUri: Uri? = null,
    val mediaType: StoryMediaType = StoryMediaType.IMAGE,
    val caption: String = "",
    val privacy: StoryPrivacy = StoryPrivacy.PUBLIC,
    val isPublishing: Boolean = false,
    val isPublished: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StoryEditorViewModel @Inject constructor(
    private val storyRepository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryEditorUiState())
    val uiState: StateFlow<StoryEditorUiState> = _uiState.asStateFlow()

    fun onMediaSelected(uri: Uri, isVideo: Boolean) {
        _uiState.update {
            it.copy(
                selectedMediaUri = uri,
                mediaType = if (isVideo) StoryMediaType.VIDEO else StoryMediaType.IMAGE,
                error = null
            )
        }
    }

    fun onCaptionChange(caption: String) {
        _uiState.update { it.copy(caption = caption) }
    }

    fun onPrivacyChange(privacy: StoryPrivacy) {
        _uiState.update { it.copy(privacy = privacy) }
    }

    fun clearMedia() {
        _uiState.update {
            it.copy(
                selectedMediaUri = null,
                caption = "",
                error = null
            )
        }
    }

    fun publishStory() {
        val uri = _uiState.value.selectedMediaUri
        if (uri == null) {
            _uiState.update { it.copy(error = "Lütfen bir fotoğraf veya video seçin") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, error = null) }
            val result = storyRepository.createStory(
                mediaLocalPath = uri.toString(),
                mediaType = _uiState.value.mediaType,
                caption = _uiState.value.caption.ifBlank { null },
                privacy = _uiState.value.privacy
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isPublishing = false, isPublished = true) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isPublishing = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }
}
