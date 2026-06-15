package com.linker.app.domain.usecase.user

import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.ReportableContentType
import com.linker.app.domain.model.UserPreference
import com.linker.app.domain.repository.UserPreferencesRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the current user's preferences in real-time.
 */
class ObserveUserPreferencesUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    operator fun invoke(): Flow<Result<UserPreference>> {
        return preferencesRepository.observePreferences()
    }
}

/**
 * Blocks a user. Removes any follow relationship and hides content bidirectionally.
 */
class BlockUserUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.Error("User ID cannot be empty")
        return preferencesRepository.blockUser(userId)
    }
}

/**
 * Unblocks a previously blocked user.
 */
class UnblockUserUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.Error("User ID cannot be empty")
        return preferencesRepository.unblockUser(userId)
    }
}

/**
 * Mutes a user's content (Stories + Links hidden, follow preserved).
 */
class MuteUserUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.Error("User ID cannot be empty")
        return preferencesRepository.muteUser(userId)
    }
}

/**
 * Unmutes a previously muted user.
 */
class UnmuteUserUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.Error("User ID cannot be empty")
        return preferencesRepository.unmuteUser(userId)
    }
}

/**
 * Signals positive interest in a content item (algorithm hint).
 */
class MarkInterestUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(contentId: String): Result<Unit> {
        if (contentId.isBlank()) return Result.Error("Content ID cannot be empty")
        return preferencesRepository.markInterest(contentId)
    }
}

/**
 * Signals disinterest in a content item (algorithm hint).
 */
class MarkDisinterestUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(contentId: String): Result<Unit> {
        if (contentId.isBlank()) return Result.Error("Content ID cannot be empty")
        return preferencesRepository.markDisinterest(contentId)
    }
}

/**
 * Reports a content item or user to the moderation system.
 */
class ReportContentUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(
        contentId: String,
        contentType: ReportableContentType,
        reason: ReportReason
    ): Result<Unit> {
        if (contentId.isBlank()) return Result.Error("Content ID cannot be empty")
        return preferencesRepository.reportContent(contentId, contentType, reason)
    }
}
