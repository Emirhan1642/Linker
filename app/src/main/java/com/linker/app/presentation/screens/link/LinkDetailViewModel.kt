package com.linker.app.presentation.screens.link

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.usecase.link.LinkInteractionUseCases
import com.linker.app.domain.usecase.user.CurrentUserProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LinkDetailUiState(
    val link: Link? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LinkDetailViewModel @Inject constructor(
    private val linkRepository: com.linker.app.domain.repository.LinkRepository,
    private val linkInteractionUseCases: LinkInteractionUseCases,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val currentUserId: String
        get() = currentUserProvider.getCurrentUserId() ?: ""

    private val _uiState = MutableStateFlow(LinkDetailUiState())
    val uiState: StateFlow<LinkDetailUiState> = _uiState.asStateFlow()

    private var currentLinkId: String? = null

    fun loadLink(linkId: String) {
        currentLinkId = linkId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = linkRepository.getLinkById(linkId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        link = result.data,
                        error = null
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        link = null,
                        error = result.message
                    )
                }
                is Result.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    fun toggleLike() {
        val currentLink = _uiState.value.link ?: return
        val currentMetrics = currentLink.engagement
        val newIsLiked = !currentMetrics.isLiked
        val newLikesCount = if (newIsLiked) currentMetrics.likesCount + 1 else maxOf(0, currentMetrics.likesCount - 1)
        
        // Optimistic update
        _uiState.value = _uiState.value.copy(
            link = currentLink.copy(
                engagement = currentMetrics.copy(
                    isLiked = newIsLiked,
                    likesCount = newLikesCount
                )
            )
        )
        viewModelScope.launch {
            linkRepository.toggleLike(currentLink.linkId)
        }
    }

    fun toggleSave() {
        val currentLink = _uiState.value.link ?: return
        val currentMetrics = currentLink.engagement
        val newIsSaved = !currentMetrics.isSaved
        val newSavesCount = if (newIsSaved) currentMetrics.savesCount + 1 else maxOf(0, currentMetrics.savesCount - 1)

        // Optimistic update
        _uiState.value = _uiState.value.copy(
            link = currentLink.copy(
                engagement = currentMetrics.copy(
                    isSaved = newIsSaved,
                    savesCount = newSavesCount
                )
            )
        )
        viewModelScope.launch {
            linkRepository.toggleSave(currentLink.linkId)
        }
    }

    fun toggleRelink() {
        val currentLink = _uiState.value.link ?: return
        val currentMetrics = currentLink.engagement
        val newIsRelinked = !currentMetrics.isRelinked
        val newRelinksCount = if (newIsRelinked) currentMetrics.relinksCount + 1 else maxOf(0, currentMetrics.relinksCount - 1)

        // Optimistic update
        _uiState.value = _uiState.value.copy(
            link = currentLink.copy(
                engagement = currentMetrics.copy(
                    isRelinked = newIsRelinked,
                    relinksCount = newRelinksCount
                )
            )
        )
        viewModelScope.launch {
            linkRepository.toggleRelink(currentLink.linkId)
        }
    }

    fun reportLink(reason: ReportReason) {
        val linkId = currentLinkId ?: return
        viewModelScope.launch {
            linkInteractionUseCases.reportLink(linkId, reason)
        }
    }

    fun sendToDm(userId: String) {
        val linkId = currentLinkId ?: return
        viewModelScope.launch {
            linkInteractionUseCases.sendLinkToDm(linkId, userId)
        }
    }
}

