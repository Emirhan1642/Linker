package com.linker.app.core.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.linker.app.core.config.OfflineMessagingConfig
import com.linker.app.data.encryption.EncryptionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for automatic encryption key rotation.
 * 
 * Addresses Issue #38 (P3): Implement automatic key rotation
 * 
 * Rotates Signal Protocol encryption keys every 30 days per Requirement 6.6.
 * Scheduled as periodic work that runs in the background.
 * 
 * Usage:
 * ```
 * KeyRotationWorker.schedule(context)
 * ```
 */
@HiltWorker
class KeyRotationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val encryptionManager: EncryptionManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "KeyRotationWorker"
        private const val WORK_NAME = "key_rotation_work"
        
        /**
         * Schedule periodic key rotation.
         * 
         * Runs every 30 days per Requirement 6.6.
         * 
         * @param context Application context
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Require network for key exchange
                .setRequiresBatteryNotLow(true) // Don't rotate when battery is low
                .build()
            
            val rotationRequest = PeriodicWorkRequestBuilder<KeyRotationWorker>(
                repeatInterval = OfflineMessagingConfig.KEY_ROTATION_INTERVAL_DAYS.toLong(),
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(TAG)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule
                rotationRequest
            )
            
            Log.d(TAG, "Key rotation scheduled (every ${OfflineMessagingConfig.KEY_ROTATION_INTERVAL_DAYS} days)")
        }
        
        /**
         * Cancel scheduled key rotation.
         * 
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Key rotation cancelled")
        }
        
        /**
         * Trigger immediate key rotation (for testing or manual rotation).
         * 
         * @param context Application context
         */
        fun triggerNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val rotationRequest = OneTimeWorkRequestBuilder<KeyRotationWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()
            
            WorkManager.getInstance(context).enqueue(rotationRequest)
            Log.d(TAG, "Immediate key rotation triggered")
        }
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting key rotation")
        
        return try {
            // Rotate encryption keys
            encryptionManager.rotateKeys()
            
            Log.d(TAG, "Key rotation completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Key rotation failed: ${e.message}", e)
            
            // Retry on failure (WorkManager will use exponential backoff)
            if (runAttemptCount < 3) {
                Log.d(TAG, "Retrying key rotation (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Log.e(TAG, "Key rotation failed after 3 attempts")
                Result.failure()
            }
        }
    }
}
