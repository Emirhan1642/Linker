package com.linker.app.presentation.screens.link

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentAuthor
import com.linker.app.domain.usecase.comment.CommentUseCases
import com.linker.app.domain.usecase.user.CurrentUserProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val commentUseCases: CommentUseCases = mockk(relaxed = true)
    private val currentUserProvider: CurrentUserProvider = mockk(relaxed = true)

    private val sampleAuthor = CommentAuthor(
        userId = "user_1",
        username = "alice",
        displayName = "Alice",
        profileImageUrl = null,
        isVerified = false
    )

    private val now = System.currentTimeMillis()

    private val sampleComment = Comment(
        commentId = "comment_abc",
        linkId = "target_post_1",
        author = sampleAuthor,
        content = "Original Comment Content",
        createdAt = now,
        updatedAt = now,
        likesCount = 10,
        isLiked = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { currentUserProvider.getCurrentUserId() } returns "user_1"
        every { commentUseCases.observeComments("target_post_1") } returns flowOf(Result.Success(listOf(sampleComment)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDeleteComment_optimisticRollbackOnFailure() = runTest(testDispatcher) {
        coEvery { commentUseCases.deleteComment("comment_abc") } returns Result.Error("Server deletion rejected")

        val viewModel = CommentSheetViewModel(commentUseCases, currentUserProvider)
        viewModel.observeComments("target_post_1")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.comments.size)

        // Trigger optimistic delete
        viewModel.deleteComment("comment_abc")

        // Immediately before repo returns, comment should be optimistically removed
        assertTrue("Comment should be optimistically removed from list", viewModel.uiState.value.comments.isEmpty())

        // Process repository failure
        advanceUntilIdle()

        // Comment must be restored and error message set
        assertEquals("Comment must be rolled back to list on failure", 1, viewModel.uiState.value.comments.size)
        assertEquals("comment_abc", viewModel.uiState.value.comments[0].commentId)
        assertEquals("Server deletion rejected", viewModel.uiState.value.error)
    }

    @Test
    fun testToggleLike_optimisticRollbackOnFailure() = runTest(testDispatcher) {
        coEvery { commentUseCases.likeComment("comment_abc") } returns Result.Error("Failed to like")

        val viewModel = CommentSheetViewModel(commentUseCases, currentUserProvider)
        viewModel.observeComments("target_post_1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.comments[0].isLiked)
        assertEquals(10, viewModel.uiState.value.comments[0].likesCount)

        // Trigger like
        viewModel.toggleLike("comment_abc")

        // Optimistically liked
        assertTrue(viewModel.uiState.value.comments[0].isLiked)
        assertEquals(11, viewModel.uiState.value.comments[0].likesCount)

        // Fail repository operation
        advanceUntilIdle()

        // Rolled back
        assertFalse("Like state should roll back on failure", viewModel.uiState.value.comments[0].isLiked)
        assertEquals(10, viewModel.uiState.value.comments[0].likesCount)
        assertEquals("Failed to like", viewModel.uiState.value.error)
    }

    @Test
    fun testEditComment_optimisticRollbackOnFailure() = runTest(testDispatcher) {
        coEvery { commentUseCases.editComment("comment_abc", any()) } returns Result.Error("Content rejected")

        val viewModel = CommentSheetViewModel(commentUseCases, currentUserProvider)
        viewModel.observeComments("target_post_1")
        advanceUntilIdle()

        viewModel.setEditComment(sampleComment)
        viewModel.submitComment("Modified Invalid Text")

        // Advance to error
        advanceUntilIdle()

        // Content must roll back to original content
        val currentComment = viewModel.uiState.value.comments.find { it.commentId == "comment_abc" }
        assertNotNull(currentComment)
        assertEquals("Original Comment Content", currentComment!!.content)
        assertEquals("Content rejected", viewModel.uiState.value.error)
    }
}
