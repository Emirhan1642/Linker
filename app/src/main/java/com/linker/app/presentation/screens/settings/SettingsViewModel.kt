package com.linker.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isPrivateAccount: Boolean = false,
    val hideFollowLists: Boolean = false,
    /** Hangi toggle kayıt yapılıyor? null = hiçbiri */
    val savingField: String? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        userRepository.getCurrentUser()
            .onEach { user ->
                _uiState.update {
                    it.copy(
                        isPrivateAccount = user?.isPrivate ?: false,
                        hideFollowLists  = user?.hideFollowLists ?: false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setPrivateAccount(isPrivate: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingField = "private") }
            when (val result = userRepository.setPrivateAccount(isPrivate)) {
                is Result.Success -> _uiState.update { it.copy(isPrivateAccount = isPrivate, savingField = null) }
                is Result.Error   -> _uiState.update { it.copy(savingField = null, snackbarMessage = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun setHideFollowLists(hide: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingField = "hideFollowLists") }
            when (val result = userRepository.setHideFollowLists(hide)) {
                is Result.Success -> _uiState.update { it.copy(hideFollowLists = hide, savingField = null) }
                is Result.Error   -> _uiState.update { it.copy(savingField = null, snackbarMessage = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
