package com.linker.app.presentation.screens.home

import com.linker.app.core.util.Result
import com.linker.app.domain.model.EngagementMetrics
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkAuthor
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.model.MediaItem
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.domain.repository.UserRepository
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val linkRepository: LinkRepository = mockk(relaxed = true)
    private val storyRepository: StoryRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    private val sampleAuthor = LinkAuthor(
        userId = "author_1",
        username = "johndoe",
        displayName = "John Doe",
        profileImageUrl = null,
        isVerified = false
    )

    private val now = System.currentTimeMillis()

    private val sampleLink = Link(
        linkId = "link_123",
        author = sampleAuthor,
        linkType = LinkType.FEED,
        description = "Test Link",
        mediaItems = listOf(MediaItem.Image(url = "https://example.com/image.jpg", aspectRatio = 1.0f, width = 500, height = 500)),
        engagement = EngagementMetrics(
            likesCount = 5,
            commentsCount = 2,
            sharesCount = 1,
            relinksCount = 0,
            savesCount = 3,
            viewsCount = 100,
            isLiked = false,
            isSaved = false,
            isRelinked = false
        ),
        createdAt = now,
        updatedAt = now
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { linkRepository.observeFeed() } returns flowOf(Result.Success(listOf(sampleLink)))
        every { userRepository.observeFollowing() } returns flowOf(Result.Success(emptyList()))
        every { storyRepository.observeActiveUserStories() } returns flowOf(Result.Success(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testToggleLike_optimisticUpdateAndRollbackOnFailure() = runTest(testDispatcher) {
        // Setup repository failure for toggleLike
        coEvery { linkRepository.toggleLike("link_123") } returns Result.Error("Network failure")

        val viewModel = HomeViewModel(linkRepository, storyRepository, userRepository)
        advanceUntilIdle()

        // Verify initial state
        val initialLink = viewModel.uiState.value.links.find { it.linkId == "link_123" }
        assertNotNull(initialLink)
        assertFalse(initialLink!!.engagement.isLiked)
        assertEquals(5, initialLink.engagement.likesCount)

        // Trigger like
        viewModel.toggleLike("link_123")

        // Immediately after toggleLike (before coroutine completes), optimistic update must be applied
        val optimisticLink = viewModel.uiState.value.links.find { it.linkId == "link_123" }
        assertNotNull(optimisticLink)
        assertTrue("Optimistic like should be true immediately", optimisticLink!!.engagement.isLiked)
        assertEquals(6, optimisticLink.engagement.likesCount)

        // Advance coroutines to process the repository error response
        advanceUntilIdle()

        // State must roll back to original values due to failure
        val rolledBackLink = viewModel.uiState.value.links.find { it.linkId == "link_123" }
        assertNotNull(rolledBackLink)
        assertFalse("Rolled back like should be false", rolledBackLink!!.engagement.isLiked)
        assertEquals(5, rolledBackLink.engagement.likesCount)
        assertEquals("Network failure", viewModel.uiState.value.error)
    }

    @Test
    fun testToggleSave_optimisticUpdateAndRollbackOnFailure() = runTest(testDispatcher) {
        coEvery { linkRepository.toggleSave("link_123") } returns Result.Error("Save failed")

        val viewModel = HomeViewModel(linkRepository, storyRepository, userRepository)
        advanceUntilIdle()

        viewModel.toggleSave("link_123")

        // Optimistic check
        val optimisticLink = viewModel.uiState.value.links.find { it.linkId == "link_123" }
        assertTrue(optimisticLink!!.engagement.isSaved)
        assertEquals(4, optimisticLink.engagement.savesCount)

        // Advance to fail
        advanceUntilIdle()

        val rolledBackLink = viewModel.uiState.value.links.find { it.linkId == "link_123" }
        assertFalse(rolledBackLink!!.engagement.isSaved)
        assertEquals(3, rolledBackLink.engagement.savesCount)
        assertEquals("Save failed", viewModel.uiState.value.error)
    }
}
