package com.linker.app.data.queue

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exception types that should not be retried
 */
private val NON_RETRYABLE_EXCEPTIONS = setOf(
    IllegalArgumentException::class,
    IllegalStateException::class,
    UnsupportedOperationException::class
)

/**
 * Check if exception is retryable
 */
private fun isRetryable(exception: Exception): Boolean {
    return NON_RETRYABLE_EXCEPTIONS.none { it.isInstance(exception) }
}

/**
 * Retry strategy with exponential backoff for failed message transmissions.
 * 
 * Implements Requirements 14.1, 14.2, 14.3:
 * - INITIAL_DELAY = 5000ms (5 seconds)
 * - BACKOFF_MULTIPLIER = 3.0
 * - MAX_RETRIES = 3
 * - MAX_DELAY = 60000ms (60 seconds)
 */
object RetryStrategy {
    const val INITIAL_DELAY = 5000L // 5 seconds
    const val BACKOFF_MULTIPLIER = 3.0
    const val MAX_RETRIES = 3
    const val MAX_DELAY = 60_000L // 60 seconds
    
    /**
     * Calculate delay for a given retry attempt with jitter and max cap.
     * 
     * Jitter prevents thundering herd problem where multiple failed
     * messages retry at exactly the same time.
     * 
     * @param retryCount Current retry count (0-based)
     * @param useJitter Whether to add random jitter (default: true)
     * @return Delay in milliseconds (capped at MAX_DELAY)
     */
    fun calculateDelay(retryCount: Int, useJitter: Boolean = true): Long {
        if (retryCount < 0) return 0L
        if (retryCount >= MAX_RETRIES) return 0L
        
        val baseDelay = (INITIAL_DELAY * BACKOFF_MULTIPLIER.pow(retryCount)).toLong()
            .coerceAtMost(MAX_DELAY)
            
        if (!useJitter) {
            return baseDelay
        }
        
        // Add ±50% jitter
        val jitterRange = baseDelay / 2
        val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
        
        return (baseDelay + jitter).coerceIn(0L, MAX_DELAY)
    }
    
    /**
     * Execute a suspending operation with exponential backoff retry logic.
     * 
     * @param maxRetries Maximum number of retry attempts (default: MAX_RETRIES)
     * @param operation The suspending operation to execute
     * @param onRetry Optional callback called before each retry
     * @return Result of the operation
     * 
     * @throws Exception if all retries are exhausted or exception is non-retryable
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        onRetry: ((attempt: Int, exception: Exception, delayMs: Long) -> Unit)? = null,
        operation: suspend (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return operation(attempt)
            } catch (e: Exception) {
                lastException = e
                
                if (!isRetryable(e)) {
                    Log.e("RetryStrategy", "Non-retryable exception, aborting retry", e)
                    throw e
                }
                
                if (attempt < maxRetries) {
                    val delayMs = calculateDelay(attempt)
                    
                    Log.w(
                        "RetryStrategy",
                        "Retry attempt ${attempt + 1}/$maxRetries after ${delayMs}ms delay. Error: ${e.message}"
                    )
                    
                    onRetry?.invoke(attempt, e, delayMs)
                    delay(delayMs)
                } else {
                    Log.e(
                        "RetryStrategy",
                        "All $maxRetries retry attempts exhausted. Last error: ${e.message}"
                    )
                }
            }
        }
        
        throw lastException ?: Exception("Retry failed with unknown error")
    }
    
    /**
     * Check if a message should be retried based on retry count.
     * 
     * @param retryCount Current retry count (must be >= 0)
     * @return true if should retry, false if max retries reached
     * @throws IllegalArgumentException if retryCount is negative
     */
    fun shouldRetry(retryCount: Int): Boolean {
        require(retryCount >= 0) {
            "retryCount must be non-negative, got: $retryCount"
        }
        return retryCount < MAX_RETRIES
    }
}
