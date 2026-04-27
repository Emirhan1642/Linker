package com.linker.app.data.queue

import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Retry strategy with exponential backoff for failed message transmissions.
 * 
 * Implements Requirements 14.1, 14.2, 14.3:
 * - INITIAL_DELAY = 5000ms (5 seconds)
 * - BACKOFF_MULTIPLIER = 3.0
 * - MAX_RETRIES = 3
 * 
 * Retry delays: 5s, 15s, 45s
 */
object RetryStrategy {
    const val INITIAL_DELAY = 5000L // 5 seconds
    const val BACKOFF_MULTIPLIER = 3.0
    const val MAX_RETRIES = 3
    
    /**
     * Calculate delay for a given retry attempt.
     * 
     * @param retryCount Current retry count (0-based)
     * @return Delay in milliseconds
     * 
     * Examples:
     * - retryCount = 0 → 5000ms (5s)
     * - retryCount = 1 → 15000ms (15s)
     * - retryCount = 2 → 45000ms (45s)
     */
    fun calculateDelay(retryCount: Int): Long {
        if (retryCount < 0) return 0L
        if (retryCount >= MAX_RETRIES) return 0L
        
        return (INITIAL_DELAY * BACKOFF_MULTIPLIER.pow(retryCount)).toLong()
    }
    
    /**
     * Execute a suspending operation with exponential backoff retry logic.
     * 
     * @param maxRetries Maximum number of retry attempts (default: MAX_RETRIES)
     * @param operation The suspending operation to execute
     * @return Result of the operation
     * 
     * @throws Exception if all retries are exhausted
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = MAX_RETRIES,
        operation: suspend (attempt: Int) -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return operation(attempt)
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries) {
                    val delayMs = calculateDelay(attempt)
                    delay(delayMs)
                }
            }
        }
        
        throw lastException ?: Exception("Retry failed with unknown error")
    }
    
    /**
     * Check if a message should be retried based on retry count.
     * 
     * @param retryCount Current retry count
     * @return true if should retry, false if max retries reached
     */
    fun shouldRetry(retryCount: Int): Boolean {
        return retryCount < MAX_RETRIES
    }
}
