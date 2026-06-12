package com.linker.app.domain.repository

import com.linker.app.domain.model.Comment
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

    /** Deletes a comment. */
    suspend fun deleteComment(commentId: String): Result<Unit>
}
