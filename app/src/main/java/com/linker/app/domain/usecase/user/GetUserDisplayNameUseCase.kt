package com.linker.app.domain.usecase.user

import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

class GetUserDisplayNameUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider
) {
    suspend operator fun invoke(userId: String): String {
        if (userId.isBlank()) return "User"
        
        val currentUserId = currentUserProvider.getCurrentUserId()
        if (currentUserId != null && userId == currentUserId) {
            return "You"
        }

        return when (val result = userRepository.getUserById(userId)) {
            is Result.Success -> {
                result.data.displayName.ifBlank { result.data.username }.ifBlank { "User" }
            }
            is Result.Error -> "User"
            else -> "User"
        }
    }
}

interface CurrentUserProvider {
    fun getCurrentUserId(): String?
    fun getCurrentUserDisplayName(): String?
}
