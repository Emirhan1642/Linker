package com.linker.app.core.session

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeout

/**
 * Background worker to cleanup expired passive sessions.
 *
 * Changes:
 *  - [4.2] doWork now outputs metrics (sessions cleaned, duration, active count)
 *  - [4.3] Cleanup operation wrapped in withTimeout(30s) to prevent hanging
 *  - [4.4] Retry limit (MAX_RETRY_ATTEMPTS = 3) — retryCount tracked via inputData
 *  - [4.5] logCleanupFailure() logs structured failure info (Crashlytics-ready logging)
 *  - [4.6] All log tags use companion object TAG constant
 */
@HiltWorker
class SessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hybridAccountManager: HybridAccountManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SessionCleanupWorker"  // [4.6]
        private const val CLEANUP_TIMEOUT_MS = 30_000L  // [4.3] 30 seconds
        private const val MAX_RETRY_ATTEMPTS = 3        // [4.4]

        // Output data keys
        const val KEY_SESSIONS_CLEANED = "sessions_cleaned"
        const val KEY_CLEANUP_DURATION_MS = "cleanup_duration_ms"
        const val KEY_ACTIVE_SESSIONS = "active_sessions"
        const val KEY_RETRY_COUNT = "retry_count"
    }

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()

        // [4.4] Read retry count from inputData
        val retryCount = inputData.getInt(KEY_RETRY_COUNT, 0)

        return try {
            Log.d(TAG, "Starting session cleanup (attempt ${retryCount + 1})")  // [4.6]

            // [4.2] Get metrics before cleanup
            val sessionsBefore = hybridAccountManager.getActiveSessionCount()

            // [4.3] Cleanup with timeout
            val sessionsCleaned = withTimeout(CLEANUP_TIMEOUT_MS) {
                hybridAccountManager.cleanupExpiredSessions()
            }

            val sessionsAfter = hybridAccountManager.getActiveSessionCount()
            val duration = System.currentTimeMillis() - startTime

            // [4.2] Log metrics
            Log.i(TAG, """
                Session cleanup completed:
                - Attempt: ${retryCount + 1}
                - Sessions before: $sessionsBefore
                - Sessions after: $sessionsAfter
                - Sessions cleaned: $sessionsCleaned
                - Duration: ${duration}ms
            """.trimIndent())

            hybridAccountManager.logSessionMetrics()

            // [4.2] Return output data for WorkManager observers
            val outputData = workDataOf(
                KEY_SESSIONS_CLEANED to sessionsCleaned,
                KEY_CLEANUP_DURATION_MS to duration,
                KEY_ACTIVE_SESSIONS to sessionsAfter,
                KEY_RETRY_COUNT to retryCount
            )

            Result.success(outputData)

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Session cleanup timeout after ${duration}ms (attempt ${retryCount + 1})")

            // [4.5] Log structured failure info
            logCleanupFailure("Timeout", duration, retryCount, e)

            val outputData = workDataOf(
                KEY_CLEANUP_DURATION_MS to duration,
                KEY_RETRY_COUNT to retryCount,
                "error_message" to "Cleanup timeout"
            )

            Result.failure(outputData)  // Don't retry on timeout

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to cleanup sessions after ${duration}ms (attempt ${retryCount + 1}): ${e.message}", e)

            // [4.4] Check retry limit
            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                Log.e(TAG, "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached, giving up")
                logCleanupFailure("Max retries exceeded", duration, retryCount, e)

                val outputData = workDataOf(
                    KEY_CLEANUP_DURATION_MS to duration,
                    KEY_RETRY_COUNT to retryCount,
                    "error_message" to "Max retries exceeded: ${e.message}"
                )

                return Result.failure(outputData)
            }

            val outputData = workDataOf(
                KEY_CLEANUP_DURATION_MS to duration,
                KEY_RETRY_COUNT to retryCount + 1,
                "error_message" to (e.message ?: "Unknown error")
            )

            Result.retry()
        }
    }

    /**
     * [4.5] Log structured failure info.
     * Emits structured Log.e for crash reporting integration (e.g., Crashlytics, Sentry)
     * if those dependencies are added later.
     */
    private fun logCleanupFailure(
        reason: String,
        duration: Long,
        retryCount: Int,
        exception: Exception
    ) {
        Log.e(TAG, "CLEANUP_FAILURE reason=$reason durationMs=$duration retryCount=$retryCount activeSessions=${hybridAccountManager.getActiveSessionCount()} error=${exception.message}", exception)
    }
}
