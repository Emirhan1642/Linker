package com.linker.app.presentation.screens.link

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentVersion
import com.linker.app.domain.usecase.comment.CommentUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CommentSheetUiState(
    val comments: List<Comment> = emptyList(),
    val repliesMap: Map<String, List<Comment>> = emptyMap(),
    val expandedReplyParentIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val replyToComment: Comment? = null,
    val editComment: Comment? = null,
    val commentHistory: List<CommentVersion>? = null
)

@HiltViewModel
class CommentSheetViewModel @Inject constructor(
    private val commentUseCases: CommentUseCases
) : ViewModel() {

    val currentUserId: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(CommentSheetUiState())
    val uiState: StateFlow<CommentSheetUiState> = _uiState.asStateFlow()

    private var currentTargetId: String? = null
    private var commentsJob: kotlinx.coroutines.Job? = null

    fun observeComments(targetId: String) {
        currentTargetId = targetId
        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.comments.isEmpty())
        
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            commentUseCases.observeComments(targetId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val allList = result.data
                        val rootComments = allList.filter { it.parentCommentId == null }
                        val repliesGrouped = allList.filter { it.parentCommentId != null }
                            .groupBy { it.parentCommentId!! }
                            .mapValues { (_, list) -> list.sortedBy { it.createdAt } }

                        val mergedReplies = _uiState.value.repliesMap.toMutableMap()
                        repliesGrouped.forEach { (parentId, replies) ->
                            mergedReplies[parentId] = replies
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            comments = rootComments,
                            repliesMap = mergedReplies,
                            error = null
                        )
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                    is Result.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.comments.isEmpty())
                    }
                }
            }
        }
    }

    fun toggleReplies(parentCommentId: String) {
        val currentExpanded = _uiState.value.expandedReplyParentIds
        if (currentExpanded.contains(parentCommentId)) {
            _uiState.value = _uiState.value.copy(
                expandedReplyParentIds = currentExpanded - parentCommentId
            )
        } else {
            _uiState.value = _uiState.value.copy(
                expandedReplyParentIds = currentExpanded + parentCommentId
            )
            loadReplies(parentCommentId)
        }
    }

    fun loadReplies(parentCommentId: String) {
        viewModelScope.launch {
            val result = commentUseCases.getReplies(parentCommentId)
            if (result is Result.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val repliesList = (result as Result.Success<List<Comment>>).data
                val currentMap = _uiState.value.repliesMap.toMutableMap()
                currentMap[parentCommentId] = repliesList
                _uiState.value = _uiState.value.copy(repliesMap = currentMap)
            }
        }
    }

    fun submitComment(content: String) {
        val targetId = currentTargetId ?: return
        val replyTo = _uiState.value.replyToComment
        val editComment = _uiState.value.editComment

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            if (editComment != null) {
                // Optimistic Edit
                _uiState.value = _uiState.value.copy(
                    editComment = null,
                    comments = _uiState.value.comments.map {
                        if (it.commentId == editComment.commentId) it.copy(content = content, isEdited = true) else it
                    },
                    repliesMap = _uiState.value.repliesMap.mapValues { (_, replies) ->
                        replies.map {
                            if (it.commentId == editComment.commentId) it.copy(content = content, isEdited = true) else it
                        }
                    }
                )
                val result = commentUseCases.editComment(editComment.commentId, content)
                _uiState.value = _uiState.value.copy(isSending = false)
                if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            } else if (replyTo != null) {
                // Reply
                val parentId = replyTo.parentCommentId ?: replyTo.commentId
                val result = commentUseCases.replyToComment(targetId, parentId, content)
                if (result is Result.Success<*>) {
                    _uiState.value = _uiState.value.copy(
                        replyToComment = null,
                        isSending = false,
                        expandedReplyParentIds = _uiState.value.expandedReplyParentIds + parentId
                    )
                    loadReplies(parentId)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message, isSending = false)
                }
            } else {
                // New top-level comment
                val result = commentUseCases.addComment(targetId, content)
                if (result is Result.Success<*>) {
                    _uiState.value = _uiState.value.copy(isSending = false)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message, isSending = false)
                }
            }
        }
    }

    fun setReplyTo(comment: Comment?) {
        _uiState.value = _uiState.value.copy(replyToComment = comment, editComment = null)
    }

    fun setEditComment(comment: Comment?) {
        _uiState.value = _uiState.value.copy(editComment = comment, replyToComment = null)
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            // Optimistic Delete
            _uiState.value = _uiState.value.copy(
                comments = _uiState.value.comments.filter { it.commentId != commentId },
                repliesMap = _uiState.value.repliesMap.mapValues { (_, replies) ->
                    replies.filter { it.commentId != commentId }
                }
            )
            commentUseCases.deleteComment(commentId)
        }
    }

    fun toggleLike(commentId: String) {
        viewModelScope.launch {
            // Optimistic Like Toggle
            _uiState.value = _uiState.value.copy(
                comments = _uiState.value.comments.map { comment ->
                    if (comment.commentId == commentId) {
                        val newLiked = !comment.isLiked
                        val newCount = if (newLiked) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
                        comment.copy(isLiked = newLiked, likesCount = newCount)
                    } else comment
                },
                repliesMap = _uiState.value.repliesMap.mapValues { (_, replies) ->
                    replies.map { reply ->
                        if (reply.commentId == commentId) {
                            val newLiked = !reply.isLiked
                            val newCount = if (newLiked) reply.likesCount + 1 else (reply.likesCount - 1).coerceAtLeast(0)
                            reply.copy(isLiked = newLiked, likesCount = newCount)
                        } else reply
                    }
                }
            )
            commentUseCases.likeComment(commentId)
        }
    }

    fun loadCommentHistory(commentId: String) {
        viewModelScope.launch {
            val result = commentUseCases.getCommentEditHistory(commentId)
            if (result is Result.Success<*>) {
                _uiState.value = _uiState.value.copy(commentHistory = (result as Result.Success<List<CommentVersion>>).data)
            }
        }
    }

    fun clearCommentHistory() {
        _uiState.value = _uiState.value.copy(commentHistory = null)
    }
}
