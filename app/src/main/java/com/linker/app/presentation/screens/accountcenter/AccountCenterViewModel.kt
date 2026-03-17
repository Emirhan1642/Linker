package com.linker.app.presentation.screens.accountcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountCenterUiState(
    val sessions: List<AccountSession> = emptyList(),
    val activeUid: String? = null,
    val isSwitching: Boolean = false,
    val switchError: String? = null
)

@HiltViewModel
class AccountCenterViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountCenterUiState())
    val uiState: StateFlow<AccountCenterUiState> = _uiState.asStateFlow()

    init {
        accountRepository.observeSessions()
            .onEach { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                activeUid = accountRepository.getActiveUid()
            )
        }
    }

    fun switchAccount(uid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitching = true, switchError = null)
            when (val result = accountRepository.switchToAccount(uid)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSwitching = false,
                        activeUid = uid
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSwitching = false,
                        switchError = result.message
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSwitching = false)
                }
            }
        }
    }

    fun removeAccount(uid: String) {
        viewModelScope.launch {
            accountRepository.removeSession(uid)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(switchError = null)
    }
}
