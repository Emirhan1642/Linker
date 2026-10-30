package com.linker.app.presentation.screens.search

import com.linker.app.core.util.Result
import com.linker.app.data.repository.SearchHistoryRepository
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.PaginatedUsers
import com.linker.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val linkRepository: LinkRepository = mockk(relaxed = true)
    private val searchHistoryRepository: SearchHistoryRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userRepository.observeCurrentUser() } returns flowOf(Result.Success(User(userId = "user_123")))
        every { searchHistoryRepository.getRecentSearches("user_123") } returns flowOf(listOf("android", "kotlin"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInit_loadsRecentSearchesForCurrentUser() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(userRepository, linkRepository, searchHistoryRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("android", "kotlin"), state.recentSearches)
    }

    @Test
    fun testOnQueryChange_blankClearsSearchResults() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(userRepository, linkRepository, searchHistoryRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("search_query")
        assertEquals("search_query", viewModel.uiState.value.query)

        viewModel.onQueryChange("")
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun testOnSearchSubmit_executesUserSearchAndSavesRecent() = runTest(testDispatcher) {
        val foundUser = User(userId = "alice_id", username = "alice", displayName = "Alice")
        coEvery { userRepository.searchUsers("alice") } returns Result.Success(
            PaginatedUsers(users = listOf(foundUser), nextCursor = null, hasMore = false)
        )

        val viewModel = SearchViewModel(userRepository, linkRepository, searchHistoryRepository)
        advanceUntilIdle()

        viewModel.onSearchSubmit("alice")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("alice", state.query)
        assertEquals(1, state.searchResults.size)
        assertEquals("alice_id", state.searchResults[0].userId)
        assertFalse(state.isSearching)

        coVerify(exactly = 1) { searchHistoryRepository.addSearch("user_123", "alice") }
    }

    @Test
    fun testClearAllRecentSearches_callsRepository() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(userRepository, linkRepository, searchHistoryRepository)
        advanceUntilIdle()

        viewModel.clearAllRecentSearches()
        advanceUntilIdle()

        coVerify(exactly = 1) { searchHistoryRepository.clearAll("user_123") }
    }

    @Test
    fun testOnTabSelected_switchesTab() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(userRepository, linkRepository, searchHistoryRepository)
        advanceUntilIdle()

        assertEquals(SearchTab.USERS, viewModel.uiState.value.selectedTab)

        viewModel.onTabSelected(SearchTab.LINKS)
        assertEquals(SearchTab.LINKS, viewModel.uiState.value.selectedTab)
    }
}
