package com.linker.app.presentation.screens.link
 
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.R
import com.linker.app.core.util.MediaUtils
import com.linker.app.core.util.Result
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.usecase.link.LinkInteractionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LinkEditorUiState(
    val description: String = "",
    val mediaUris: List<Uri> = emptyList(),
    val music: String? = null,
    val location: String? = null,
    val taggedUsers: List<String> = emptyList(),
    val aiLabelEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDraftSaved: Boolean = false
)

@HiltViewModel
class LinkEditorViewModel @Inject constructor(
    private val linkInteractionUseCases: LinkInteractionUseCases,
    private val linkRepository: LinkRepository,
    val locationService: com.linker.app.core.location.LocationService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkEditorUiState())
    val uiState: StateFlow<LinkEditorUiState> = _uiState.asStateFlow()

    private var currentLinkId: String? = null

    fun initialize(linkId: String?, initialDescription: String?) {
        currentLinkId = linkId
        if (initialDescription != null && _uiState.value.description.isEmpty()) {
            _uiState.value = _uiState.value.copy(description = initialDescription)
        }
    }

    fun onDescriptionChange(newDescription: String) {
        _uiState.value = _uiState.value.copy(description = newDescription, error = null)
    }

    fun onMediaSelected(uris: List<Uri>) {
        _uiState.value = _uiState.value.copy(mediaUris = uris)
    }

    fun appendMedia(uris: List<Uri>) {
        val current = _uiState.value.mediaUris
        _uiState.value = _uiState.value.copy(mediaUris = current + uris)
    }

    fun removeMediaAt(index: Int) {
        val current = _uiState.value.mediaUris.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(mediaUris = current)
        }
    }

    fun setLocation(loc: String?) {
        _uiState.value = _uiState.value.copy(location = loc)
    }

    fun appendToDescription(text: String) {
        val current = _uiState.value.description
        val separator = if (current.isNotEmpty() && !current.endsWith(" ")) " " else ""
        _uiState.value = _uiState.value.copy(description = "$current$separator$text", error = null)
    }

    fun onAiLabelToggled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(aiLabelEnabled = enabled)
    }

    fun saveDraft() {
        // Mark draft saved and return feedback
        _uiState.value = _uiState.value.copy(isDraftSaved = true)
    }

    fun saveLink(mediaFitModes: Map<String, Boolean> = emptyMap()) {
        val linkId = currentLinkId
        val desc = _uiState.value.description

        if (desc.isBlank() && _uiState.value.mediaUris.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = context.getString(R.string.link_editor_error_empty))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            if (linkId != null) {
                // Edit existing link description
                val result = linkInteractionUseCases.updateLinkDescription(linkId, desc)
                if (result is Result.Success) {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                }
            } else {
                // Determine if media is video or standard image feed using MIME and URL checking
                val containsVideo = _uiState.value.mediaUris.any { uri ->
                    MediaUtils.isVideoUri(context, uri) || MediaUtils.isVideoUrl(uri.toString())
                }
                val determinedType = if (containsVideo) LinkType.VIDEO else LinkType.FEED

                val fitModesList = _uiState.value.mediaUris.map { uri ->
                    mediaFitModes[uri.toString()] ?: false
                }

                val hashtags = extractHashtags(desc)

                // Create new link
                val result = linkRepository.createLink(
                    linkType = determinedType,
                    description = desc,
                    mediaLocalPaths = _uiState.value.mediaUris.map { it.toString() },
                    location = _uiState.value.location,
                    isAiGenerated = _uiState.value.aiLabelEnabled,
                    mediaFitModes = fitModesList
                )
                
                if (result is Result.Success) {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    fun extractHashtags(desc: String): List<String> {
        return HASHTAG_REGEX.findAll(desc).map { it.groupValues[1] }.toList()
    }

    companion object {
        private val HASHTAG_REGEX = Regex("#(\\w+)")
    }
}

