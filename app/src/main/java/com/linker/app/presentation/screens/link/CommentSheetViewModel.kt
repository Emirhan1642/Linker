package com.linker.app.presentation.screens.link

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentVersion
import com.linker.app.domain.usecase.comment.CommentUseCases
import com.linker.app.domain.usecase.user.CurrentUserProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val commentUseCases: CommentUseCases,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val currentUserId: String
        get() = currentUserProvider.getCurrentUserId() ?: ""

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
                val previousComments = _uiState.value.comments
                val previousRepliesMap = _uiState.value.repliesMap
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
                    _uiState.value = _uiState.value.copy(
                        comments = previousComments,
                        repliesMap = previousRepliesMap,
                        error = result.message ?: "Failed to edit comment"
                    )
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteComment(commentId: String) {
        var deletedRootComment: Comment? = null
        var deletedParentId: String? = null
        var deletedReplyComment: Comment? = null

        // Optimistic Delete applied immediately
        _uiState.update { state ->
            deletedRootComment = state.comments.find { it.commentId == commentId }
            state.repliesMap.forEach { (parentId, replies) ->
                val found = replies.find { it.commentId == commentId }
                if (found != null) {
                    deletedParentId = parentId
                    deletedReplyComment = found
                }
            }
            state.copy(
                comments = state.comments.filter { it.commentId != commentId },
                repliesMap = state.repliesMap.mapValues { (_, replies) ->
                    replies.filter { it.commentId != commentId }
                }
            )
        }
        viewModelScope.launch {
            val result = commentUseCases.deleteComment(commentId)
            if (result is Result.Error) {
                _uiState.update { state ->
                    val restoredComments = if (deletedRootComment != null) {
                        (state.comments + deletedRootComment!!).sortedBy { it.createdAt }
                    } else state.comments

                    val restoredRepliesMap = if (deletedReplyComment != null && deletedParentId != null) {
                        val currentReplies = state.repliesMap[deletedParentId] ?: emptyList()
                        state.repliesMap + (deletedParentId!! to (currentReplies + deletedReplyComment!!).sortedBy { it.createdAt })
                    } else state.repliesMap

                    state.copy(
                        comments = restoredComments,
                        repliesMap = restoredRepliesMap,
                        error = result.message ?: "Failed to delete comment"
                    )
                }
            }
        }
    }

    fun toggleLike(commentId: String) {
        // Optimistic Like Toggle applied immediately
        _uiState.update { state ->
            val updateList = { list: List<Comment> ->
                list.map { comment ->
                    if (comment.commentId == commentId) {
                        val newLiked = !comment.isLiked
                        val newCount = if (newLiked) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
                        comment.copy(isLiked = newLiked, likesCount = newCount)
                    } else comment
                }
            }
            state.copy(
                comments = updateList(state.comments),
                repliesMap = state.repliesMap.mapValues { (_, replies) -> updateList(replies) }
            )
        }
        viewModelScope.launch {
            val result = commentUseCases.likeComment(commentId)
            if (result is Result.Error) {
                _uiState.update { state ->
                    val revertList = { list: List<Comment> ->
                        list.map { comment ->
                            if (comment.commentId == commentId) {
                                val revertedLiked = !comment.isLiked
                                val newCount = if (revertedLiked) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
                                comment.copy(isLiked = revertedLiked, likesCount = newCount)
                            } else comment
                        }
                    }
                    state.copy(
                        comments = revertList(state.comments),
                        repliesMap = state.repliesMap.mapValues { (_, replies) -> revertList(replies) },
                        error = result.message ?: "Failed to update like"
                    )
                }
            }
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
