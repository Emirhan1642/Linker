package com.linker.app.domain.repository

import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentVersion
import com.linker.app.domain.model.ReportReason
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface CommentRepository {

    /** Observes top-level comments for a link (most recent first). */
    fun observeComments(linkId: String): Flow<Result<List<Comment>>>

    /** 
     * Fetches a paginated list of top-level comments.
     * Uses cursor-based pagination for performance.
     */
    suspend fun getComments(
        linkId: String, 
        limit: Int = 20, 
        beforeTimestamp: Long? = null
    ): Result<List<Comment>>

    /** Fetches replies to a comment. */
    suspend fun getReplies(parentCommentId: String): Result<List<Comment>>
    
    /** Returns the number of replies for a given comment. */
    suspend fun getReplyCount(parentCommentId: String): Result<Int>

    /** 
     * Adds a top-level comment or a reply.
     * 
     * @param gifUrl If provided, must be a valid Tenor or Giphy URL. 
     *               Validation is done before submission.
     */
    suspend fun addComment(
        linkId: String,
        content: String,
        gifUrl: String? = null,
        parentCommentId: String? = null
    ): Result<Comment>

    /** 
     * Toggles like on a comment. 
     * @return Result containing true if liked, false if unliked.
     */
    suspend fun toggleLike(commentId: String): Result<Boolean>

    /** 
     * Edits a comment's content.
     * 
     * Rules:
     * - Only the comment author can edit their comment.
     * - Maximum [Comment.MAX_EDITS] edits are allowed per comment.
     * - Previous content is saved to [getCommentEditHistory].
     * - The `isEdited` flag and `editCount` are incremented automatically.
     *
     * @return Error if edit limit is reached or user is not the author.
     */
    suspend fun editComment(commentId: String, newContent: String): Result<Unit>

    /**
     * Retrieves the full edit history of a comment.
     * Visible to all users — each version's content and timestamp are shown.
     * Returns an empty list if the comment has never been edited.
     */
    suspend fun getCommentEditHistory(commentId: String): Result<List<CommentVersion>>

    /** 
     * Deletes a comment.
     * 
     * Rules:
     * - The author can always delete their own comment.
     * - Post authors can delete any comment on their link.
     * - Deleted root comments display "[Silindi]" if they have replies;
     *   otherwise they are permanently removed.
     */
    suspend fun deleteComment(commentId: String): Result<Unit>

    /**
     * Reports a comment for policy violations.
     */
    suspend fun reportComment(commentId: String, reason: ReportReason): Result<Unit>
}
