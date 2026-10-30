package com.linker.app.core.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.linker.app.domain.repository.StoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that periodically purges expired stories from the Room database and Firestore.
 */
@HiltWorker
class ExpiredStoriesCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storyRepository: StoryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i("ExpiredStoriesWorker", "Starting expired stories cleanup")
            val result = storyRepository.purgeExpiredStories()
            
            if (result is com.linker.app.core.util.Result.Success) {
                Log.i("ExpiredStoriesWorker", "Successfully completed expired stories cleanup")
                Result.success()
            } else {
                Log.w("ExpiredStoriesWorker", "Failed to purge expired stories")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("ExpiredStoriesWorker", "Exception during expired stories cleanup: ${e.message}", e)
            Result.failure()
        }
    }
}
