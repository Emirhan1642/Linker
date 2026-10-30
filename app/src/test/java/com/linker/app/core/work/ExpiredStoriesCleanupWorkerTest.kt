package com.linker.app.core.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.StoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiredStoriesCleanupWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var storyRepository: StoryRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        storyRepository = mockk(relaxed = true)
    }

    @Test
    fun testDoWork_success() = runTest {
        coEvery { storyRepository.purgeExpiredStories() } returns Result.Success(Unit)

        val worker = ExpiredStoriesCleanupWorker(context, params, storyRepository)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testDoWork_error_returnsRetry() = runTest {
        coEvery { storyRepository.purgeExpiredStories() } returns Result.Error("Network error while deleting")

        val worker = ExpiredStoriesCleanupWorker(context, params, storyRepository)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun testDoWork_exception_returnsFailure() = runTest {
        coEvery { storyRepository.purgeExpiredStories() } throws RuntimeException("Fatal DB error")

        val worker = ExpiredStoriesCleanupWorker(context, params, storyRepository)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
