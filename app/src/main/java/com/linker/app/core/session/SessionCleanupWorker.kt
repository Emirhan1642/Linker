package com.linker.app.core.session

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker to cleanup expired passive sessions
 * 
 * Runs periodically (every 15 minutes) to free up resources.
 */
@HiltWorker
class SessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hybridAccountManager: HybridAccountManager
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            hybridAccountManager.cleanupExpiredSessions()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SessionCleanupWorker", "Failed to cleanup sessions: ${e.message}", e)
            Result.retry()
        }
    }
}
