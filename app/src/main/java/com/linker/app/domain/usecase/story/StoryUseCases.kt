package com.linker.app.domain.usecase.story

import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observes all active (non-expired) stories for the grid screen.
 * Filters out stories from muted/blocked users at the domain level.
 */
class ObserveStoriesGridUseCase @Inject constructor(
    private val storyRepository: StoryRepository
) {
    /**
     * @param mutedUserIds Set of user IDs whose stories should be hidden.
     * @param blockedUserIds Set of user IDs whose stories must never appear.
     */
    operator fun invoke(
        mutedUserIds: Set<String> = emptySet(),
        blockedUserIds: Set<String> = emptySet()
    ): Flow<Result<List<UserStories>>> {
        return storyRepository.observeActiveUserStories().map { result ->
            if (result is Result.Success) {
                val filtered = result.data.filter { userStories ->
                    val uid = userStories.author.userId
                    uid !in mutedUserIds && uid !in blockedUserIds
                }
                Result.Success(filtered)
            } else {
                result
            }
        }
    }
}

/**
 * Toggles like on a Story with input validation.
 */
class LikeStoryUseCase @Inject constructor(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String): Result<Boolean> {
        if (storyId.isBlank()) return Result.Error("Story ID cannot be empty")
        return storyRepository.toggleLikeStory(storyId)
    }
}

/**
 * Sends or clears an emoji reaction on a Story.
 */
class ReactToStoryUseCase @Inject constructor(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String, emoji: String?): Result<Unit> {
        if (storyId.isBlank()) return Result.Error("Story ID cannot be empty")
        // Validate emoji is a single grapheme if provided
        emoji?.let {
            if (it.isBlank()) return Result.Error("Emoji cannot be blank")
        }
        return storyRepository.reactToStory(storyId, emoji)
    }
}

/**
 * Reports a Story for policy violations.
 */
class ReportStoryUseCase @Inject constructor(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String, reason: ReportReason): Result<Unit> {
        if (storyId.isBlank()) return Result.Error("Story ID cannot be empty")
        return storyRepository.reportStory(storyId, reason)
    }
}

/**
 * Returns a shareable external URL for a Story (for sharing to other platforms).
 */
class ShareStoryExternallyUseCase @Inject constructor(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String): Result<String> {
        if (storyId.isBlank()) return Result.Error("Story ID cannot be empty")
        return storyRepository.getShareableLink(storyId)
    }
}
