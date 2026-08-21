package com.linker.app.domain.usecase.feed

import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.UserRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

class ObserveFeedUseCase @Inject constructor(
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider
) {
    operator fun invoke(limit: Int = 20): Flow<Result<List<Link>>> {
        return linkRepository.observeFeed().map { result ->
            if (result is Result.Success) {
                val sorted = result.data.sortedByDescending { it.engagement.likesCount + (it.engagement.commentsCount * 2) + (it.engagement.sharesCount * 3) }
                Result.Success(sorted)
            } else {
                result
            }
        }
    }
}

class LoadMoreFeedUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(beforeTimestamp: Long, limit: Int = 20): Result<List<Link>> {
        if (beforeTimestamp < 0) return Result.Error("Invalid timestamp")
        val validLimit = limit.coerceIn(1, 50)
        return linkRepository.loadMoreFeed(beforeTimestamp, validLimit)
    }
}

class RefreshFeedUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(limit: Int = 20): Result<List<Link>> {
        val validLimit = limit.coerceIn(1, 50)
        return linkRepository.refreshFeed(limit = validLimit)
    }
}

class GetLinkByIdUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Link> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.getLinkById(linkId)
    }
}

class ObserveUserLinksUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(authorId: String): Flow<Result<List<Link>>> {
        if (authorId.isBlank()) return kotlinx.coroutines.flow.flowOf(Result.Success(emptyList()))
        return linkRepository.observeLinksByAuthor(authorId)
    }
}

class ToggleLikeUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.toggleLike(linkId)
    }
}

class ToggleSaveUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.toggleSave(linkId)
    }
}

class ToggleRelinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.toggleRelink(linkId)
    }
}

class ObserveSavedLinksUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<Result<List<Link>>> = linkRepository.observeSavedLinks()
}

class CreateLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String? = null
    ): Result<Link> {
        if (mediaLocalPaths.isEmpty()) return Result.Error("At least one media file is required")
        if (mediaLocalPaths.size > 10) return Result.Error("Maximum 10 media files allowed")
        
        mediaLocalPaths.forEach { path ->
            val isValidUri = path.startsWith("content://") || path.startsWith("file://") || path.startsWith("/")
            if (path.isBlank() || path.contains("..") || path.contains("~") || !isValidUri) {
                return Result.Error("Invalid media path: $path")
            }
        }
        
        val sanitizedDescription = if (!description.isNullOrBlank()) {
            if (description.length > 500) return Result.Error("Description is too long")
            description.replace(Regex("<[^>]*>"), "").replace(Regex("(?i)<script[^>]*>.*?</script>"), "").trim()
        } else null
        
        val validatedLocation = if (!location.isNullOrBlank()) {
            if (location.length > 100) return Result.Error("Location is too long")
            location.trim()
        } else null

        return linkRepository.createLink(linkType, sanitizedDescription, mediaLocalPaths, validatedLocation)
    }
}

class DeleteLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Unit> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.deleteLink(linkId)
    }
}

@Singleton
class RecordViewUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    private val viewedLinks = mutableSetOf<String>()
    private val viewLock = Mutex()

    suspend operator fun invoke(linkId: String): Result<Unit> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        
        viewLock.withLock {
            if (linkId in viewedLinks) return Result.Success(Unit)
            viewedLinks.add(linkId)
        }
        
        linkRepository.recordView(linkId)
        return Result.Success(Unit)
    }
}
