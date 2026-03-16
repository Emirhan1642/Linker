package com.linker.app.domain.usecase.feed

import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ─── Observe Feed ─────────────────────────────────────────────────────────────

class ObserveFeedUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<List<Link>> = linkRepository.observeFeed()
}

// ─── Refresh Feed ─────────────────────────────────────────────────────────────

class RefreshFeedUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(page: Int = 0, pageSize: Int = 20): Result<List<Link>> =
        linkRepository.refreshFeed(limit = pageSize, offset = page * pageSize)
}

// ─── Get Link ─────────────────────────────────────────────────────────────────

class GetLinkByIdUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Link> =
        linkRepository.getLinkById(linkId)
}

// ─── Observe Author's Links ────────────────────────────────────────────────────

class ObserveUserLinksUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(authorId: String): Flow<List<Link>> =
        linkRepository.observeLinksByAuthor(authorId)
}

// ─── Toggle Like ──────────────────────────────────────────────────────────────

class ToggleLikeUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> =
        linkRepository.toggleLike(linkId)
}

// ─── Toggle Save ──────────────────────────────────────────────────────────────

class ToggleSaveUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> =
        linkRepository.toggleSave(linkId)
}

// ─── Toggle Relink ────────────────────────────────────────────────────────────

class ToggleRelinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Boolean> =
        linkRepository.toggleRelink(linkId)
}

// ─── Observe Saved Links ──────────────────────────────────────────────────────

class ObserveSavedLinksUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    operator fun invoke(): Flow<List<Link>> = linkRepository.observeSavedLinks()
}

// ─── Create Link ──────────────────────────────────────────────────────────────

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
        return linkRepository.createLink(linkType, description, mediaLocalPaths, location)
    }
}

// ─── Delete Link ──────────────────────────────────────────────────────────────

class DeleteLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Unit> =
        linkRepository.deleteLink(linkId)
}

// ─── Record View ──────────────────────────────────────────────────────────────

class RecordViewUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<Unit> =
        linkRepository.recordView(linkId)
}
