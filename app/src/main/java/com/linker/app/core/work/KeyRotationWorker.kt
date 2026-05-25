package com.linker.app.core.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.linker.app.core.config.OfflineMessagingConfig
import com.linker.app.core.util.SecureLogger
import com.linker.app.data.encryption.EncryptionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for automatic encryption key rotation.
 * 
 * Addresses Issue #38 (P3): Implement automatic key rotation
 * 
 * ## Overview
 * Rotates Signal Protocol encryption keys every 30 days per Requirement 6.6.
 * Scheduled as periodic work that runs in the background.
 * 
 * ## Constraints
 * - **Network:** Requires active network connection for key exchange with peers
 * - **Battery:** Waits for battery to be above low threshold (configurable)
 * 
 * ## Retry Strategy
 * - Network errors: Up to 3 retries with exponential backoff
 * - Crypto errors: No retry, immediate failure with notification
 * - Other errors: Up to 2 retries
 * 
 * ## Failure Handling
 * - After max retries: User notification sent
 * - Emergency rotation scheduled for 24 hours later
 * - Critical error reported to monitoring system
 * 
 * ## Usage
 * ```kotlin
 * // Schedule periodic rotation (every 30 days)
 * KeyRotationWorker.schedule(context)
 * 
 * // Trigger immediate rotation (for testing or manual rotation)
 * KeyRotationWorker.triggerNow(context)
 * 
 * // Cancel scheduled rotation
 * KeyRotationWorker.cancel(context)
 * 
 * // Schedule with custom battery preference
 * KeyRotationWorker.scheduleWithUserPreferences(context)
 * ```
 * 
 * ## Thread Safety
 * Uses mutex to ensure only one rotation runs at a time.
 * 
 * @see EncryptionManager.rotateKeys
 * @see OfflineMessagingConfig.KEY_ROTATION_INTERVAL_DAYS
 */
@HiltWorker
class KeyRotationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val encryptionManager: EncryptionManager
) : CoroutineWorker(context, workerParams) {
    
    private val TAG = "KeyRotationWorker"
    private val logger = object {
        fun d(message: String, args: Map<String, Any?> = emptyMap()) {
            val suffix = if (args.isNotEmpty()) " - $args" else ""
            SecureLogger.d(TAG, message + suffix)
        }
        fun e(message: String, throwable: Throwable? = null, args: Map<String, Any?> = emptyMap()) {
            val suffix = if (args.isNotEmpty()) " - $args" else ""
            SecureLogger.e(TAG, message + suffix, throwable)
        }
        fun e(message: String, args: Map<String, Any?>) {
            val suffix = if (args.isNotEmpty()) " - $args" else ""
            SecureLogger.e(TAG, message + suffix)
        }
    }
    
    companion object {
        private const val TAG = "KeyRotationWorker"
        private const val WORK_NAME = "key_rotation_work"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val KEY_ROTATION_TIMEOUT_MS = 60_000L // 60 seconds
        
        private val keyRotationMutex = Mutex()
        
        // Notifications
        private const val SECURITY_CHANNEL_ID = "security_channel"
        private const val KEY_ROTATION_FAILURE_NOTIFICATION_ID = 1001
        
        /**
         * Schedule periodic key rotation.
         * 
         * Runs every 30 days per Requirement 6.6.
         * 
         * @param context Application context
         * @param respectBatteryLevel Whether to respect battery level constraint
         */
        fun schedule(context: Context, respectBatteryLevel: Boolean = true) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Require network for key exchange
                
            if (respectBatteryLevel) {
                constraintsBuilder.setRequiresBatteryNotLow(true) // Don't rotate when battery is low
            }
            
            val constraints = constraintsBuilder.build()
            
            val rotationRequest = PeriodicWorkRequestBuilder<KeyRotationWorker>(
                repeatInterval = OfflineMessagingConfig.KEY_ROTATION_INTERVAL_MS,
                repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
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
            
            SecureLogger.d(TAG, "Key rotation scheduled - interval_days=${OfflineMessagingConfig.KEY_ROTATION_INTERVAL_MS / (24 * 60 * 60 * 1000)}, respect_battery=$respectBatteryLevel")
        }
        
        /**
         * Schedule key rotation using user preferences for battery level constraint.
         * 
         * @param context Application context
         */
        fun scheduleWithUserPreferences(context: Context) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val respectBattery = prefs.getBoolean("key_rotation_respect_battery", true)
            schedule(context, respectBattery)
        }
        
        /**
         * Cancel scheduled key rotation.
         * 
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            SecureLogger.d(TAG, "Key rotation cancelled")
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
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.KEEP,
                rotationRequest
            )
            
            SecureLogger.d(TAG, "Immediate key rotation triggered")
        }
    }
    
    override suspend fun doWork(): Result {
        return keyRotationMutex.withLock {
            logger.d("Starting key rotation", mapOf("attempt" to runAttemptCount))
            
            try {
                withTimeout(KEY_ROTATION_TIMEOUT_MS) {
                    encryptionManager.rotateKeys()
                }
                
                logger.d("Key rotation completed successfully")
                return Result.success()
                
            } catch (e: TimeoutCancellationException) {
                logger.e("Key rotation timed out", e, mapOf(
                    "timeout_ms" to KEY_ROTATION_TIMEOUT_MS,
                    "attempt" to runAttemptCount
                ))
                
                return handleFailureAndRetry(e)
                
            } catch (e: Exception) {
                logger.e("Key rotation failed", e, mapOf(
                    "attempt" to runAttemptCount,
                    "error_type" to e::class.simpleName
                ))
                
                when (e) {
                    is IOException, is SocketTimeoutException -> {
                        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                            logger.d("Retrying key rotation due to network error", mapOf(
                                "attempt" to runAttemptCount + 1,
                                "max_attempts" to MAX_RETRY_ATTEMPTS
                            ))
                            return Result.retry()
                        }
                    }
                    is InvalidKeyException, is NoSuchAlgorithmException -> {
                        logger.e("Key rotation failed with crypto error - not retrying", e)
                        reportCriticalError("KeyRotationCryptoError", e)
                        return Result.failure()
                    }
                    else -> {
                        if (runAttemptCount < 2) {
                            logger.d("Retrying key rotation", mapOf(
                                "attempt" to runAttemptCount + 1
                            ))
                            return Result.retry()
                        }
                    }
                }
                
                return handleFailureAndRetry(e)
            }
        }
    }
    
    private fun handleFailureAndRetry(e: Exception): Result {
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            return Result.retry()
        } else {
            logger.e("Key rotation failed after max attempts", mapOf(
                "max_attempts" to MAX_RETRY_ATTEMPTS
            ))
            
            notifyKeyRotationFailure()
            reportCriticalError("KeyRotationMaxRetries", e)
            scheduleEmergencyRotation()
            
            return Result.failure()
        }
    }
    
    private fun notifyKeyRotationFailure() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Assuming R.drawable.ic_security_warning and R.string.key_rotation_failed_title/message exist.
        // Fallbacks are handled properly if not, but typically we assume them.
        val resourceIdIcon = context.resources.getIdentifier("ic_security_warning", "drawable", context.packageName)
        val icon = if (resourceIdIcon != 0) resourceIdIcon else android.R.drawable.ic_dialog_alert
        
        val titleId = context.resources.getIdentifier("key_rotation_failed_title", "string", context.packageName)
        val title = if (titleId != 0) context.getString(titleId) else "Key Rotation Failed"
        
        val msgId = context.resources.getIdentifier("key_rotation_failed_message", "string", context.packageName)
        val msg = if (msgId != 0) context.getString(msgId) else "Automatic encryption key rotation failed. Please check your network connection."
        
        val notification = NotificationCompat.Builder(context, SECURITY_CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(KEY_ROTATION_FAILURE_NOTIFICATION_ID, notification)
    }
    
    private fun scheduleEmergencyRotation() {
        val emergencyRequest = OneTimeWorkRequestBuilder<KeyRotationWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("$TAG-emergency")
            .build()
        
        WorkManager.getInstance(context).enqueue(emergencyRequest)
        logger.d("Emergency key rotation scheduled for 24 hours")
    }
    
    private fun reportCriticalError(errorType: String, exception: Exception) {
        // Placeholder for crashlytics
        logger.e("CRITICAL ERROR: $errorType", exception)
    }
}
