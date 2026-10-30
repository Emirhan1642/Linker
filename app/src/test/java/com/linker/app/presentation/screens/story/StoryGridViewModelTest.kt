package com.linker.app.presentation.screens.story

import com.linker.app.core.util.Result
import com.linker.app.domain.model.StoryAuthor
import com.linker.app.domain.model.UserPreference
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.usecase.story.ObserveStoriesGridUseCase
import com.linker.app.domain.usecase.user.ObserveUserPreferencesUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryGridViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val observeStoriesGridUseCase: ObserveStoriesGridUseCase = mockk(relaxed = true)
    private val observeUserPreferencesUseCase: ObserveUserPreferencesUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testObserveStories_success_updatesUiState() = runTest(testDispatcher) {
        val prefs = UserPreference(
            mutedUserIds = setOf("muted_user"),
            blockedUserIds = setOf("blocked_user")
        )
        val dummyAuthor = StoryAuthor(
            userId = "alice_id",
            username = "alice",
            displayName = "Alice",
            profileImageUrl = null
        )
        val dummyUserStories = mockk<UserStories>(relaxed = true) {
            every { author } returns dummyAuthor
        }

        every { observeUserPreferencesUseCase() } returns flowOf(Result.Success(prefs))
        every { observeStoriesGridUseCase(setOf("muted_user"), setOf("blocked_user")) } returns flowOf(
            Result.Success(listOf(dummyUserStories))
        )

        val viewModel = StoryGridViewModel(observeStoriesGridUseCase, observeUserPreferencesUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.userStories.size)
    }

    @Test
    fun testObserveStories_error_setsErrorMessage() = runTest(testDispatcher) {
        every { observeUserPreferencesUseCase() } returns flowOf(Result.Success(UserPreference.EMPTY))
        every { observeStoriesGridUseCase(emptySet(), emptySet()) } returns flowOf(
            Result.Error("Network failure loading stories")
        )

        val viewModel = StoryGridViewModel(observeStoriesGridUseCase, observeUserPreferencesUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Network failure loading stories", state.error)
        assertTrue(state.userStories.isEmpty())
    }
}
