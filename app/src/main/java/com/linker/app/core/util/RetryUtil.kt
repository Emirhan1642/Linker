package com.linker.app.core.util

import kotlinx.coroutines.delay
import kotlin.math.pow
import android.util.Log

/**
 * Retry utility with exponential backoff
 *
 * Provides retry mechanisms for network and IO operations with configurable
 * delay strategies.
 *
 * ✅ ARCHITECTURE: Use this for retrying network calls with exponential backoff
 */
object RetryUtil {

    private const val TAG = "RetryUtil"

    /**
     * Retry a suspending operation with exponential backoff
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 100ms)
     * @param maxDelay Maximum delay in milliseconds (default: 1000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param block The operation to retry
     * @return Result of the operation
     * @throws Exception if all retries fail
     */
    suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }

        // Last attempt
        try {
            return block()
        } catch (e: Exception) {
            Log.e(TAG, "Final attempt failed after $times tries")
            throw e
        }
    }

    /**
     * Retry with Result wrapper
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 100ms)
     * @param maxDelay Maximum delay in milliseconds (default: 1000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param block The operation to retry, returning a Result
     * @return Final Result after all retries
     */
    suspend fun <T> retryWithResult(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay

        repeat(times - 1) { attempt ->
            val result = block()
            if (result is Result.Success) return result

            if (result is Result.Error) {
                // Only retry if the error is retryable
                if (!result.isRetryable) {
                    Log.d(TAG, "Error not retryable, stopping: ${result.message}")
                    return result
                }
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${result.message}")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }

        // Last attempt
        return block()
    }

    /**
     * Retry a safeCall operation with exponential backoff
     *
     * Convenience wrapper that combines safeCall and retryWithResult
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 100ms)
     * @param maxDelay Maximum delay in milliseconds (default: 1000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param block The operation to retry
     * @return Result after all retries
     */
    suspend fun <T> retrySafeCall(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): Result<T> {
        return retryWithResult(times, initialDelay, maxDelay, factor) {
            safeCall(block)
        }
    }

    /**
     * Calculate exponential backoff delay
     *
     * @param attempt Current attempt number (0-indexed)
     * @param initialDelay Initial delay in milliseconds
     * @param maxDelay Maximum delay in milliseconds
     * @param factor Exponential backoff factor
     * @return Calculated delay in milliseconds
     */
    fun calculateDelay(
        attempt: Int,
        initialDelay: Long,
        maxDelay: Long,
        factor: Double
    ): Long {
        return (initialDelay * factor.pow(attempt)).toLong().coerceAtMost(maxDelay)
    }
}
