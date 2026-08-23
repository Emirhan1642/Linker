package com.linker.app.domain.usecase.comment

import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentVersion
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.repository.CommentRepository
import com.linker.app.core.util.Result
import javax.inject.Inject

/**
 * Edits a comment's text content.
 * Enforces [Comment.MAX_EDITS] limit and sanitizes input.
 */
class EditCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(commentId: String, newContent: String): Result<Unit> {
        if (commentId.isBlank()) return Result.Error("Comment ID cannot be empty")
        if (newContent.isBlank()) return Result.Error("Comment content cannot be empty")
        if (newContent.length > Comment.MAX_CONTENT_LENGTH) {
            return Result.Error("Content exceeds maximum length of ${Comment.MAX_CONTENT_LENGTH}")
        }

        val sanitized = newContent
            .replace(Regex("<[^>]*>"), "")
            .trim()

        return commentRepository.editComment(commentId, sanitized)
    }
}

/**
 * Retrieves the edit history of a comment, sorted newest-first.
 */
class GetCommentEditHistoryUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(commentId: String): Result<List<CommentVersion>> {
        if (commentId.isBlank()) return Result.Error("Comment ID cannot be empty")
        return commentRepository.getCommentEditHistory(commentId)
    }
}

/**
 * Deletes a comment. Post authors can delete any comment on their link.
 */
class DeleteCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(commentId: String): Result<Unit> {
        if (commentId.isBlank()) return Result.Error("Comment ID cannot be empty")
        return commentRepository.deleteComment(commentId)
    }
}

/**
 * Toggles like on a Comment.
 */
class LikeCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(commentId: String): Result<Boolean> {
        if (commentId.isBlank()) return Result.Error("Comment ID cannot be empty")
        return commentRepository.toggleLike(commentId)
    }
}

/**
 * Adds a reply to an existing comment.
 */
class ReplyToCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(
        linkId: String,
        parentCommentId: String,
        content: String
    ): Result<Comment> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        if (parentCommentId.isBlank()) return Result.Error("Parent comment ID cannot be empty")
        if (content.isBlank()) return Result.Error("Reply content cannot be empty")
        if (content.length > Comment.MAX_CONTENT_LENGTH) {
            return Result.Error("Content exceeds maximum length of ${Comment.MAX_CONTENT_LENGTH}")
        }

        val sanitized = content.replace(Regex("<[^>]*>"), "").trim()

        return commentRepository.addComment(
            linkId = linkId,
            content = sanitized,
            gifUrl = null,
            parentCommentId = parentCommentId
        )
    }
}

/**
 * Adds a new top-level comment to a link.
 */
class AddCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(
        linkId: String,
        content: String,
        gifUrl: String? = null
    ): Result<Comment> {
        if (linkId.isBlank()) return Result.Error("Link ID cannot be empty")
        if (content.isBlank() && gifUrl.isNullOrBlank()) return Result.Error("Comment cannot be empty")
        if (content.length > Comment.MAX_CONTENT_LENGTH) {
            return Result.Error("Content exceeds maximum length of ${Comment.MAX_CONTENT_LENGTH}")
        }

        val sanitized = content.replace(Regex("<[^>]*>"), "").trim()

        return commentRepository.addComment(
            linkId = linkId,
            content = sanitized,
            gifUrl = gifUrl,
            parentCommentId = null
        )
    }
}

/**
 * Observes comments for a link.
 */
class ObserveCommentsUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    operator fun invoke(linkId: String): kotlinx.coroutines.flow.Flow<Result<List<Comment>>> {
        return commentRepository.observeComments(linkId)
    }
}

/**
 * Reports a comment for policy violations.
 */
class ReportCommentUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(commentId: String, reason: ReportReason): Result<Unit> {
        if (commentId.isBlank()) return Result.Error("Comment ID cannot be empty")
        return commentRepository.reportComment(commentId, reason)
    }
}

/**
 * Fetches replies for a parent comment.
 */
class GetRepliesUseCase @Inject constructor(
    private val commentRepository: CommentRepository
) {
    suspend operator fun invoke(parentCommentId: String): Result<List<Comment>> {
        if (parentCommentId.isBlank()) return Result.Error("Parent comment ID cannot be empty")
        return commentRepository.getReplies(parentCommentId)
    }
}

data class CommentUseCases @Inject constructor(
    val addComment: AddCommentUseCase,
    val observeComments: ObserveCommentsUseCase,
    val editComment: EditCommentUseCase,
    val getCommentEditHistory: GetCommentEditHistoryUseCase,
    val deleteComment: DeleteCommentUseCase,
    val likeComment: LikeCommentUseCase,
    val replyToComment: ReplyToCommentUseCase,
    val getReplies: GetRepliesUseCase,
    val reportComment: ReportCommentUseCase
)
