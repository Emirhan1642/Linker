package com.linker.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class NewChatUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val suggested: List<User> = emptyList()
)

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
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
                val myUid = auth.currentUser?.uid
                val snapshot = firestore.collection("users")
                    .limit(40)
                    .get()
                    .await()
                val users = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val uid = doc.id
                    if (uid == myUid) return@mapNotNull null
                    User(
                        userId = uid,
                        username = data["username"] as? String ?: "",
                        displayName = data["displayName"] as? String ?: "User",
                        email = data["email"] as? String,
                        phoneNumber = data["phoneNumber"] as? String,
                        bio = data["bio"] as? String,
                        profileImageUrl = data["profileImageUrl"] as? String,
                        coverImageUrl = data["coverImageUrl"] as? String,
                        isVerified = data["isVerified"] as? Boolean ?: false,
                        followersCount = (data["followersCount"] as? Number)?.toInt() ?: 0,
                        followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
                        likesCount = (data["likesCount"] as? Number)?.toInt() ?: 0,
                        isFollowing = data["isFollowing"] as? Boolean ?: false,
                        isFollowedBy = data["isFollowedBy"] as? Boolean ?: false,
                        isBlocked = data["isBlocked"] as? Boolean ?: false,
                        isMuted = data["isMuted"] as? Boolean ?: false,
                        isPrivate = data["isPrivate"] as? Boolean ?: false,
                        followRequestSent = data["followRequestSent"] as? Boolean ?: false,
                        hideFollowLists = data["hideFollowLists"] as? Boolean ?: false,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false, suggested = users)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

