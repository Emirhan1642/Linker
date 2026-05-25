package com.linker.app.core.util

import kotlinx.coroutines.delay
import kotlin.math.pow
import android.util.Log
import kotlin.random.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Retry utility with exponential backoff
 *
 * Provides retry mechanisms for network and IO operations with configurable
 * delay strategies.
 *
 * ✅ ENHANCED: Added jitter, circuit breaker, metrics tracking, configurable configs
 */

// ── Circuit Breaker ─────────────────────────────────────────────────────

enum class CircuitState {
    CLOSED,      // Normal operation
    OPEN,        // Circuit is open, fail fast
    HALF_OPEN    // Testing if service recovered
}

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 2,
    val timeout: Long = 60_000L  // 1 minute
)

class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private var state: CircuitState = CircuitState.CLOSED
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    
    fun canAttempt(): Boolean {
        return when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                // Check if timeout has passed
                val now = System.currentTimeMillis()
                if (now - lastFailureTime.get() > config.timeout) {
                    state = CircuitState.HALF_OPEN
                    successCount.set(0)
                    Log.d("CircuitBreaker", "$name: Transitioning to HALF_OPEN")
                    true
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> true
        }
    }
    
    fun recordSuccess() {
        when (state) {
            CircuitState.CLOSED -> {
                failureCount.set(0)
            }
            CircuitState.HALF_OPEN -> {
                val count = successCount.incrementAndGet()
                if (count >= config.successThreshold) {
                    state = CircuitState.CLOSED
                    failureCount.set(0)
                    Log.d("CircuitBreaker", "$name: Transitioning to CLOSED")
                }
            }
            CircuitState.OPEN -> {
                // Should not happen
            }
        }
    }
    
    fun recordFailure() {
        lastFailureTime.set(System.currentTimeMillis())
        
        when (state) {
            CircuitState.CLOSED -> {
                val count = failureCount.incrementAndGet()
                if (count >= config.failureThreshold) {
                    state = CircuitState.OPEN
                    Log.w("CircuitBreaker", "$name: Transitioning to OPEN after $count failures")
                }
            }
            CircuitState.HALF_OPEN -> {
                state = CircuitState.OPEN
                failureCount.set(config.failureThreshold)
                Log.w("CircuitBreaker", "$name: Transitioning back to OPEN")
            }
            CircuitState.OPEN -> {
                // Already open
            }
        }
    }
    
    fun getState(): CircuitState = state
}

class CircuitBreakerOpenException(message: String) : Exception(message)

// ── Retry Config ────────────────────────────────────────────────────────

data class RetryConfig(
    val times: Int = 3,
    val initialDelay: Long = 100,
    val maxDelay: Long = 1000,
    val factor: Double = 2.0,
    val jitterFactor: Double = 0.1,
    val circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    companion object {
        // ✅ Predefined configurations for common scenarios
        val FAST = RetryConfig(
            times = 2,
            initialDelay = 50,
            maxDelay = 500,
            factor = 2.0
        )
        
        val NORMAL = RetryConfig(
            times = 3,
            initialDelay = 100,
            maxDelay = 1000,
            factor = 2.0
        )
        
        val AGGRESSIVE = RetryConfig(
            times = 5,
            initialDelay = 200,
            maxDelay = 5000,
            factor = 2.0
        )
        
        val NETWORK = RetryConfig(
            times = 3,
            initialDelay = 1000,
            maxDelay = 10000,
            factor = 2.0,
            circuitBreakerConfig = CircuitBreakerConfig(
                failureThreshold = 5,
                timeout = 60_000L
            )
        )
    }
}
object RetryUtil {

    private const val TAG = "RetryUtil"
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    
    /**
     * ✅ Get or create circuit breaker for operation
     */
    private fun getCircuitBreaker(
        operationName: String,
        config: CircuitBreakerConfig = CircuitBreakerConfig()
    ): CircuitBreaker {
        return circuitBreakers.getOrPut(operationName) {
            CircuitBreaker(operationName, config)
        }
    }
    
    /**
     * ✅ Calculate delay with jitter to prevent thundering herd
     */
    private fun calculateDelayWithJitter(
        baseDelay: Long,
        jitterFactor: Double = 0.1
    ): Long {
        val jitter = (baseDelay * jitterFactor * Random.nextDouble()).toLong()
        return baseDelay + jitter
    }

    /**
     * Retry a suspending operation with exponential backoff
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 100ms)
     * @param maxDelay Maximum delay in milliseconds (default: 1000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param jitterFactor Jitter factor to prevent thundering herd (default: 0.1)
     * @param block The operation to retry
     * @return Result of the operation
     * @throws Exception if all retries fail
     */
    suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        jitterFactor: Double = 0.1,
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
            
            // ✅ Add jitter to delay
            val delayWithJitter = calculateDelayWithJitter(currentDelay, jitterFactor)
            delay(delayWithJitter)
            
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
     * ✅ Retry with circuit breaker protection
     */
    suspend fun <T> retryWithCircuitBreaker(
        operationName: String,
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        jitterFactor: Double = 0.1,
        circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig(),
        block: suspend () -> T
    ): T {
        val circuitBreaker = getCircuitBreaker(operationName, circuitBreakerConfig)
        
        // ✅ Check circuit breaker before attempting
        if (!circuitBreaker.canAttempt()) {
            Log.w(TAG, "Circuit breaker is OPEN for $operationName, failing fast")
            throw CircuitBreakerOpenException("Circuit breaker is open for $operationName")
        }
        
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(times - 1) { attempt ->
            try {
                val result = block()
                // ✅ Record success
                circuitBreaker.recordSuccess()
                return result
            } catch (e: Exception) {
                lastException = e
                // ✅ Record failure
                circuitBreaker.recordFailure()
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
            }
            
            val delayWithJitter = calculateDelayWithJitter(currentDelay, jitterFactor)
            delay(delayWithJitter)
            
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }

        // Last attempt
        try {
            val result = block()
            circuitBreaker.recordSuccess()
            return result
        } catch (e: Exception) {
            circuitBreaker.recordFailure()
            Log.e(TAG, "Final attempt failed after $times tries")
            throw e
        }
    }
    
    /**
     * ✅ Retry with config
     */
    suspend fun <T> retryWithConfig(
        operationName: String,
        config: RetryConfig = RetryConfig.NORMAL,
        block: suspend () -> T
    ): T {
        return retryWithCircuitBreaker(
            operationName = operationName,
            times = config.times,
            initialDelay = config.initialDelay,
            maxDelay = config.maxDelay,
            factor = config.factor,
            jitterFactor = config.jitterFactor,
            circuitBreakerConfig = config.circuitBreakerConfig,
            block = block
        )
    }

    /**
     * Retry with Result wrapper
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 100ms)
     * @param maxDelay Maximum delay in milliseconds (default: 1000ms)
     * @param factor Exponential backoff factor (default: 2.0)
     * @param jitterFactor Jitter factor (default: 0.1)
     * @param block The operation to retry, returning a Result
     * @return Final Result after all retries
     */
    suspend fun <T> retryWithResult(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        jitterFactor: Double = 0.1,
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
            
            // ✅ Add jitter to delay
            val delayWithJitter = calculateDelayWithJitter(currentDelay, jitterFactor)
            delay(delayWithJitter)
            
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
     * @param jitterFactor Jitter factor (default: 0.1)
     * @param block The operation to retry
     * @return Result after all retries
     */
    suspend fun <T> retrySafeCall(
        times: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 1000,
        factor: Double = 2.0,
        jitterFactor: Double = 0.1,
        block: suspend () -> T
    ): Result<T> {
        return retryWithResult(times, initialDelay, maxDelay, factor, jitterFactor) {
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
