package com.linker.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.domain.repository.UserRepository
import com.linker.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.linker.app.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class NewChatUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val suggested: List<User> = emptyList()
)

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewChatUiState(isLoading = true))
    val uiState: StateFlow<NewChatUiState> = _uiState.asStateFlow()

    init {
        loadSuggested()
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    private fun loadSuggested() {
        viewModelScope.launch {
            try {
                when(val result = userRepository.searchUsers("", 40)) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, suggested = result.data)
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    else -> {}
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

