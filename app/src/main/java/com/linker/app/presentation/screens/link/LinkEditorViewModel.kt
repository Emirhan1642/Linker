package com.linker.app.presentation.screens.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.usecase.link.LinkInteractionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.net.Uri
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.model.LinkType

data class LinkEditorUiState(
    val description: String = "",
    val mediaUris: List<Uri> = emptyList(),
    val music: String? = null,
    val location: String? = null,
    val taggedUsers: List<String> = emptyList(),
    val aiLabelEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class LinkEditorViewModel @Inject constructor(
    private val linkInteractionUseCases: LinkInteractionUseCases,
    private val linkRepository: LinkRepository
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

    fun onAiLabelToggled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(aiLabelEnabled = enabled)
    }

    fun saveLink() {
        val linkId = currentLinkId
        val desc = _uiState.value.description

        if (desc.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Description cannot be empty")
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
                // Create new link
                val result = linkRepository.createLink(
                    linkType = LinkType.FEED,
                    description = desc,
                    mediaLocalPaths = _uiState.value.mediaUris.map { it.toString() },
                    location = _uiState.value.location
                )
                
                if (result is Result.Success) {
                    _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = result.message)
                }
            }
        }
    }

    private fun extractHashtags(desc: String): List<String> {
        val regex = Regex("#(\\w+)")
        return regex.findAll(desc).map { it.groupValues[1] }.toList()
    }
}
