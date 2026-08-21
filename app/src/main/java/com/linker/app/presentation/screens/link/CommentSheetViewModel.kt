package com.linker.app.presentation.screens.link

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

data class CommentSheetUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val replyToComment: Comment? = null,
    val editComment: Comment? = null,
    val commentHistory: List<CommentVersion>? = null
)

@HiltViewModel
class CommentSheetViewModel @Inject constructor(
    private val commentUseCases: CommentUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentSheetUiState())
    val uiState: StateFlow<CommentSheetUiState> = _uiState.asStateFlow()

    private var currentTargetId: String? = null
    private var commentsJob: kotlinx.coroutines.Job? = null

    fun observeComments(targetId: String) {
        currentTargetId = targetId
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            commentUseCases.observeComments(targetId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            comments = result.data,
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
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                }
            }
        }
    }

    fun submitComment(content: String) {
        val targetId = currentTargetId ?: return
        val replyTo = _uiState.value.replyToComment
        val editComment = _uiState.value.editComment

        viewModelScope.launch {
            if (editComment != null) {
                // Edit existing
                val result = commentUseCases.editComment(editComment.commentId, content)
                if (result is Result.Success) {
                    _uiState.value = _uiState.value.copy(editComment = null)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            } else if (replyTo != null) {
                // Reply
                val result = commentUseCases.replyToComment(targetId, replyTo.commentId, content)
                if (result is Result.Success) {
                    _uiState.value = _uiState.value.copy(replyToComment = null)
                } else if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            } else {
                // New top-level comment
                val result = commentUseCases.addComment(targetId, content)
                if (result is Result.Error) {
                    _uiState.value = _uiState.value.copy(error = result.message)
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
            commentUseCases.deleteComment(commentId)
        }
    }

    fun toggleLike(commentId: String) {
        viewModelScope.launch {
            commentUseCases.likeComment(commentId)
        }
    }

    fun loadCommentHistory(commentId: String) {
        viewModelScope.launch {
            val result = commentUseCases.getCommentEditHistory(commentId)
            if (result is Result.Success) {
                _uiState.value = _uiState.value.copy(commentHistory = result.data)
            }
        }
    }

    fun clearCommentHistory() {
        _uiState.value = _uiState.value.copy(commentHistory = null)
    }
}
