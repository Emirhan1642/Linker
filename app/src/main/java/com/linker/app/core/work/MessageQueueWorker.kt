package com.linker.app.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.linker.app.core.util.Result as LinkerResult
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.core.util.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Worker class responsible for processing pending offline messages in the background.
 * It periodically checks for failed or pending messages and attempts to send them
 * to Firestore when an internet connection is available.
 *
 * Implements security logging, batch processing, timeout control, and structured exception handling.
 */
@HiltWorker
class MessageQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        SecureLogger.d(TAG, "MessageQueueWorker started.")
        
        // Prevent concurrent overlapping executions
        if (!workerMutex.tryLock()) {
            SecureLogger.w(TAG, "MessageQueueWorker is already running. Skipping execution.")
            return Result.success()
        }

        return try {
            withTimeout(WORKER_TIMEOUT_MS) {
                var totalProcessed = 0
                var keepProcessing = true
                var hasErrors = false

                SecureLogger.d(TAG, "Starting batch processing loop. Batch size: $BATCH_SIZE")

                var batchCount = 0
                val maxBatches = 20

                while (keepProcessing && batchCount < maxBatches) {
                    batchCount++
                    val batchResult = messageRepository.retryFailedMessages(
                        batchSize = BATCH_SIZE
                    )

                    when (batchResult) {
                        is LinkerResult.Success -> {
                            val count = batchResult.data
                            totalProcessed += count
                            
                            SecureLogger.d(TAG, "Processed batch of $count messages. Total: $totalProcessed")

                            // If we processed fewer items than the batch size, the queue is exhausted
                            if (count < BATCH_SIZE) {
                                keepProcessing = false
                            }
                        }
                        is LinkerResult.Error -> {
                            SecureLogger.e(TAG, "Error during batch processing: ${batchResult.message}", null)
                            hasErrors = true
                            keepProcessing = false
                        }
                        is LinkerResult.Loading -> {
                            // Should not happen in synchronous suspended call, but handle defensively
                            keepProcessing = false
                        }
                    }
                }

                if (hasErrors) {
                    SecureLogger.w(TAG, "MessageQueueWorker finished with errors after processing $totalProcessed messages.")
                    Result.retry()
                } else {
                    SecureLogger.d(TAG, "MessageQueueWorker completed successfully. Processed $totalProcessed total messages.")
                    Result.success()
                }
            }
        } catch (e: CancellationException) {
            SecureLogger.w(TAG, "MessageQueueWorker was cancelled or timed out.")
            // Allow WorkManager to retry if timed out
            Result.retry()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Unexpected error in MessageQueueWorker: ${e.message}", e)
            Result.retry()
        } finally {
            workerMutex.unlock()
            SecureLogger.d(TAG, "MessageQueueWorker lock released.")
        }
    }

    companion object {
        private const val TAG = "MessageQueueWorker"
        const val WORK_NAME = "linker_message_queue_sync"
        private const val BATCH_SIZE = 50
        private const val WORKER_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        private val workerMutex = Mutex()

        /**
         * Schedules the periodic message queue worker.
         * Runs every 15 minutes, requires a network connection.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MessageQueueWorker>(
                15, TimeUnit.MINUTES, // Minimum allowed periodic interval
                5, TimeUnit.MINUTES   // Flex interval
            )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Cancels the scheduled message queue worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Triggers an immediate one-time execution of the message queue worker.
         */
        fun triggerNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MessageQueueWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_Immediate",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
