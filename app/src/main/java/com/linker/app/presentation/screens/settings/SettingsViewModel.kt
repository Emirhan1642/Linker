package com.linker.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.R
import com.linker.app.core.util.Result
import com.linker.app.core.util.UiText
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

enum class SettingField {
    PRIVATE_ACCOUNT,
    HIDE_FOLLOW_LISTS,
    NOTIFICATIONS_ENABLED,
    PUSH_STORIES,
    PUSH_MESSAGES,
    ACTIVITY_STATUS,
    READ_RECEIPTS,
    DATA_SAVER,
    AUTO_PLAY_VIDEOS
}

@androidx.compose.runtime.Immutable
data class SettingsUiState(
    val isPrivateAccount: Boolean = false,
    val hideFollowLists: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val pushStories: Boolean = true,
    val pushMessages: Boolean = true,
    val activityStatus: Boolean = true,
    val readReceipts: Boolean = true,
    val dataSaver: Boolean = false,
    val autoPlayVideos: Boolean = true,
    /** Hangi toggle kayıt yapılıyor? null = hiçbiri */
    val savingField: SettingField? = null,
    val snackbarMessage: UiText? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        userRepository.observeCurrentUser()
            .onEach { result ->
                val user = (result as? Result.Success)?.data
                _uiState.update {
                    it.copy(
                        isPrivateAccount = user?.privacy?.isPrivate ?: false,
                        hideFollowLists  = user?.privacy?.hideFollowLists ?: false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun setPrivateAccount(isPrivate: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingField = SettingField.PRIVATE_ACCOUNT) }
            when (val result = userRepository.setPrivateAccount(isPrivate)) {
                is Result.Success -> _uiState.update { it.copy(isPrivateAccount = isPrivate, savingField = null) }
                is Result.Error   -> _uiState.update { it.copy(savingField = null, snackbarMessage = UiText.DynamicString(result.message ?: "Error")) }
                is Result.Loading -> {}
            }
        }
    }

    fun setHideFollowLists(hide: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(savingField = SettingField.HIDE_FOLLOW_LISTS) }
            when (val result = userRepository.setHideFollowLists(hide)) {
                is Result.Success -> _uiState.update { it.copy(hideFollowLists = hide, savingField = null) }
                is Result.Error   -> _uiState.update { it.copy(savingField = null, snackbarMessage = UiText.DynamicString(result.message ?: "Error")) }
                is Result.Loading -> {}
            }
        }
    }
    
    // Note: These methods only update the UI state. In a real app, they should call a PreferencesRepository
    // to persist the user settings.
    fun setNotificationsEnabled(enabled: Boolean) = _uiState.update { it.copy(notificationsEnabled = enabled) }
    fun setPushStories(enabled: Boolean) = _uiState.update { it.copy(pushStories = enabled) }
    fun setPushMessages(enabled: Boolean) = _uiState.update { it.copy(pushMessages = enabled) }
    fun setActivityStatus(enabled: Boolean) = _uiState.update { it.copy(activityStatus = enabled) }
    fun setReadReceipts(enabled: Boolean) = _uiState.update { it.copy(readReceipts = enabled) }
    fun setDataSaver(enabled: Boolean) = _uiState.update { it.copy(dataSaver = enabled) }
    fun setAutoPlayVideos(enabled: Boolean) = _uiState.update { it.copy(autoPlayVideos = enabled) }

    fun showSnackbar(message: UiText) = _uiState.update { it.copy(snackbarMessage = message) }
    fun dismissSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
