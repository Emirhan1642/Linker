package com.linker.app.domain.usecase.link

import com.linker.app.domain.model.DescriptionVersion
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Updates a Link's description, saving the previous version to history.
 * Enforces the [Link.MAX_DESCRIPTION_EDITS] limit.
 */
class UpdateLinkDescriptionUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String, newDescription: String): Result<Unit> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        if (newDescription.isBlank()) return Result.Error("Description cannot be empty")
        if (newDescription.length > Link.MAX_DESCRIPTION_LENGTH) {
            return Result.Error("Description exceeds maximum length of ${Link.MAX_DESCRIPTION_LENGTH}")
        }

        // Sanitize: strip HTML tags
        val sanitized = newDescription
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("(?i)<script[^>]*>.*?</script>"), "")
            .trim()

        return linkRepository.updateLinkDescription(linkId, sanitized)
    }
}

/**
 * Retrieves the description edit history for a Link.
 * Returns entries sorted newest-first.
 */
class GetLinkDescriptionHistoryUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<List<DescriptionVersion>> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return when (val result = linkRepository.getLinkDescriptionHistory(linkId)) {
            is Result.Success -> Result.Success(result.data.sortedByDescending { it.editedAt })
            is Result.Error -> result
            is Result.Loading -> Result.Loading(result.progress)
        }
    }
}

/**
 * Sends a Link to a user via DM using MessageType.LINK.
 */
class SendLinkToDmUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String, recipientUserId: String): Result<Unit> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        if (recipientUserId.isBlank()) return Result.Error("Recipient user ID cannot be empty")
        return linkRepository.sendLinkToDm(linkId, recipientUserId)
    }
}

/**
 * Reports a Link for policy violations.
 */
class ReportLinkUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String, reason: ReportReason): Result<Unit> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.reportLink(linkId, reason)
    }
}

/**
 * Returns a shareable deep-link URL for a Link (for sharing to other platforms).
 */
class ShareLinkExternallyUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(linkId: String): Result<String> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        return linkRepository.getShareableLink(linkId)
    }
}

data class LinkInteractionUseCases @Inject constructor(
    val updateLinkDescription: UpdateLinkDescriptionUseCase,
    val getLinkDescriptionHistory: GetLinkDescriptionHistoryUseCase,
    val sendLinkToDm: SendLinkToDmUseCase,
    val reportLink: ReportLinkUseCase,
    val shareLinkExternally: ShareLinkExternallyUseCase
)
