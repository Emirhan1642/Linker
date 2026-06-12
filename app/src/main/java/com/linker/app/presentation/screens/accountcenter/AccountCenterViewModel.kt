package com.linker.app.presentation.screens.accountcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountCenterUiState(
    val sessions: List<AccountSession> = emptyList(),
    val activeUid: String? = null,
    val isSwitching: Boolean = false,
    val switchError: String? = null
)

sealed class AccountCenterEffect {
    /** Hesap geçişi tamamlandı — Home'a git ve her şeyi sıfırla */
    data class SwitchComplete(val newUid: String) : AccountCenterEffect()
}

@HiltViewModel
class AccountCenterViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountCenterUiState())
    val uiState: StateFlow<AccountCenterUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AccountCenterEffect>()
    val effects: SharedFlow<AccountCenterEffect> = _effects.asSharedFlow()

    init {
        accountRepository.observeSessions()
            .onEach { sessions -> _uiState.update { it.copy(sessions = sessions) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val result = accountRepository.getActiveUid()
            if (result is Result.Success) {
                _uiState.update { it.copy(activeUid = result.data) }
            }
        }
    }

    fun switchAccount(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSwitching = true, switchError = null) }
            when (val result = accountRepository.switchToAccount(uid)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSwitching = false, activeUid = uid) }
                    _effects.emit(AccountCenterEffect.SwitchComplete(uid))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSwitching = false, switchError = result.message ?: "An unexpected error occurred") }
                }
                else -> _uiState.update { it.copy(isSwitching = false) }
            }
        }
    }

    fun removeAccount(uid: String) {
        viewModelScope.launch { accountRepository.removeSession(uid) }
    }

    fun dismissError() {
        _uiState.update { it.copy(switchError = null) }
    }
}
