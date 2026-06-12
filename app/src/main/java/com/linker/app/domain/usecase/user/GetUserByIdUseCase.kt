package com.linker.app.domain.usecase.user

import com.linker.app.core.util.Result
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.UserRepository
import javax.inject.Inject

class GetUserByIdUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): Result<User> {
        if (userId.isBlank()) return Result.Error("User ID cannot be empty")
        return userRepository.getUserById(userId)
    }
}
