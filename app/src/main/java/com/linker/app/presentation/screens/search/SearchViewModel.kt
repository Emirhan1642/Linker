package com.linker.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.core.util.Result
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val selectedTab: SearchTab = SearchTab.USERS,
    val error: String? = null
)

enum class SearchTab { LINKS, USERS }

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

    private var recentSearchesJob: Job? = null

    init {
        startListeningRecentSearches()
        observeQueryChanges()
    }

    /**
     * Hesap değişiminde çağrılır:
     * 1. Önceki arama geçmişi flow'unu durdur
     * 2. Query ve sonuçları temizle
     * 3. Yeni UID ile arama geçmişini dinlemeye başla
     */
    fun onAccountChanged() {
        // Önce state'i temizle — eski hesabın aramasını gösterme
        _uiState.update { SearchUiState() }
        startListeningRecentSearches()
    }

    fun startListeningRecentSearches() {
        recentSearchesJob?.cancel()
        recentSearchesJob = viewModelScope.launch {
            searchHistoryRepository.getRecentSearches(currentUid).collectLatest { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onSearchSubmit(query: String = _uiState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _uiState.update { it.copy(query = trimmed) }
        saveRecentSearch(trimmed)
        executeSearch(trimmed)
    }

    fun onRecentSearchClick(query: String) {
        _uiState.update { it.copy(query = query) }
        saveRecentSearch(query)
        executeSearch(query)
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { searchHistoryRepository.removeSearch(currentUid, query) }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch { searchHistoryRepository.clearAll(currentUid) }
    }

    private fun saveRecentSearch(query: String) {
        viewModelScope.launch { searchHistoryRepository.addSearch(currentUid, query) }
    }

    private fun observeQueryChanges() {
        viewModelScope.launch {
            _uiState
                .debounce(400)
                .distinctUntilChanged { old, new -> old.query == new.query }
                .filter { it.query.isNotBlank() }
                .collectLatest { state -> executeSearch(state.query) }
        }
    }

    private fun executeSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            when (val result = userRepository.searchUsers(query)) {
                is Result.Success -> _uiState.update { it.copy(searchResults = result.data, isSearching = false) }
                is Result.Error   -> _uiState.update { it.copy(isSearching = false, error = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun onTabSelected(tab: SearchTab) = _uiState.update { it.copy(selectedTab = tab) }
}
