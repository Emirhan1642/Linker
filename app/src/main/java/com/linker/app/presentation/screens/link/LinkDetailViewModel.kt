package com.linker.app.presentation.screens.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.usecase.link.LinkInteractionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.linker.app.core.util.Result
import androidx.compose.runtime.Immutable

@Immutable
data class LinkDetailUiState(
    val link: Link? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LinkDetailViewModel @Inject constructor(
    private val linkRepository: com.linker.app.domain.repository.LinkRepository,
    private val linkInteractionUseCases: LinkInteractionUseCases
) : ViewModel() {

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
