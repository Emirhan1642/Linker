package com.linker.app.domain.usecase.user

import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Get user display name with caching support
 * Returns "You" for current user, cached name for others
 */
class GetUserDisplayNameUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val currentUserProvider: CurrentUserProvider
) {
    suspend operator fun invoke(userId: String): String {
        if (userId == currentUserProvider.getCurrentUserId()) {
            return "You"
        }

        return when (val result = userRepository.getUserById(userId)) {
            is Result.Success -> result.data.displayName.ifBlank { result.data.username }.ifBlank { "User" }
            is Result.Error -> "User"
            is Result.Loading -> "Loading..."
            else -> "User"
        }
    }
}

/**
 * Provider for current user information
 */
interface CurrentUserProvider {
    fun getCurrentUserId(): String?
    fun getCurrentUserDisplayName(): String?
}
