package com.linker.app.core.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import com.linker.app.BuildConfig
// FirebaseCrashlytics import removed — dependency not available
import java.util.concurrent.ConcurrentHashMap

/**
 * Error handling utilities for reducing try-catch boilerplate
 * 
 * Provides extension functions and inline utilities for common error handling patterns.
 * 
 * ✅ ENHANCED: Added sanitization, crash reporting, and rate limiting
 */

/**
 * Sanitize exception message to prevent PII exposure in logs
 */
@PublishedApi
internal fun sanitizeExceptionMessage(e: Exception): String {
    return if (BuildConfig.DEBUG) {
        e.message ?: "Unknown error"
    } else {
        // ✅ Sanitize exception message in production
        when (e) {
            is SecurityException -> "Security error"
            is IllegalArgumentException -> "Invalid input"
            is IllegalStateException -> "Invalid state"
            is java.net.UnknownHostException -> "Network error"
            is java.net.SocketTimeoutException -> "Connection timeout"
            else -> "An error occurred"
        }
    }
}

/**
 * Report exception to crash reporting service
 */
@PublishedApi
internal fun reportException(
    tag: String,
    errorMessage: String,
    exception: Exception
) {
    if (!BuildConfig.DEBUG) {
        // Structured log for future crash reporting integration
        Log.e("ErrorHandling", "CRASH_REPORT tag=$tag error=$errorMessage type=${exception.javaClass.simpleName}", exception)
    }
}

/**
 * Rate limiter for error logging
 */
@PublishedApi
internal object ErrorRateLimiter {
    private val errorCounts = ConcurrentHashMap<String, ErrorCount>()
    private const val RATE_LIMIT_WINDOW_MS = 60_000L // 1 minute
    private const val MAX_ERRORS_PER_WINDOW = 10
    
    private data class ErrorCount(
        var count: Int = 0,
        var windowStart: Long = System.currentTimeMillis(),
        var lastLogged: Long = 0
    )
    
    fun shouldLog(tag: String, errorMessage: String): Boolean {
        val key = "$tag:$errorMessage"
        val now = System.currentTimeMillis()
        
        val errorCount = errorCounts.getOrPut(key) { ErrorCount() }
        
        // Reset window if expired
        if (now - errorCount.windowStart > RATE_LIMIT_WINDOW_MS) {
            errorCount.count = 0
            errorCount.windowStart = now
        }
        
        errorCount.count++
        
        // Check rate limit
        if (errorCount.count > MAX_ERRORS_PER_WINDOW) {
            // Log rate limit exceeded only once per window
            if (now - errorCount.lastLogged > RATE_LIMIT_WINDOW_MS) {
                Log.w("ErrorHandling", "Rate limit exceeded for $tag: ${errorCount.count} errors")
                errorCount.lastLogged = now
            }
            return false
        }
        
        errorCount.lastLogged = now
        return true
    }
}

/**
 * Execute a block with error handling and logging
 * 
 * @param tag Log tag for error messages
 * @param errorMessage Custom error message prefix
 * @param onError Optional error handler callback
 * @param block The code block to execute
 * @return Result of the block, or null if an error occurred
 */
inline fun <T> safeExecute(
    tag: String,
    errorMessage: String = "Error executing block",
    noinline onError: ((Exception) -> Unit)? = null,
    block: () -> T
): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: SecurityException) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        onError?.invoke(e)
        null
    } catch (e: Exception) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        onError?.invoke(e)
        null
    }
}

/**
 * Execute a suspend block with error handling and logging
 * 
 * @param tag Log tag for error messages
 * @param errorMessage Custom error message prefix
 * @param onError Optional error handler callback
 * @param block The suspend code block to execute
 * @return Result of the block, or null if an error occurred
 */
suspend inline fun <T> safeSuspendExecute(
    tag: String,
    errorMessage: String = "Error executing suspend block",
    noinline onError: ((Exception) -> Unit)? = null,
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: SecurityException) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        onError?.invoke(e)
        null
    } catch (e: Exception) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        onError?.invoke(e)
        null
    }
}

/**
 * Execute a block with error handling, returning a default value on error
 * 
 * @param tag Log tag for error messages
 * @param defaultValue Value to return if an error occurs
 * @param errorMessage Custom error message prefix
 * @param block The code block to execute
 * @return Result of the block, or defaultValue if an error occurred
 */
inline fun <T> safeExecuteOrDefault(
    tag: String,
    defaultValue: T,
    errorMessage: String = "Error executing block",
    block: () -> T
): T {
    return try {
        block()
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: SecurityException) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        defaultValue
    } catch (e: Exception) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        defaultValue
    }
}

/**
 * Execute a block with error handling, returning a Result
 * 
 * @param tag Log tag for error messages
 * @param errorMessage Custom error message prefix
 * @param block The code block to execute
 * @return Result.Success with the value, or Result.Error with the exception
 */
inline fun <T> safeExecuteResult(
    tag: String,
    errorMessage: String = "Error executing block",
    block: () -> T
): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: SecurityException) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.PERMISSION,
            cause = e
        )
    } catch (e: Exception) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.UNKNOWN,
            cause = e
        )
    }
}

/**
 * Execute a suspend block with error handling, returning a Result
 * 
 * @param tag Log tag for error messages
 * @param errorMessage Custom error message prefix
 * @param block The suspend code block to execute
 * @return Result.Success with the value, or Result.Error with the exception
 */
suspend inline fun <T> safeSuspendExecuteResult(
    tag: String,
    errorMessage: String = "Error executing suspend block",
    block: suspend () -> T
): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: SecurityException) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.PERMISSION,
            cause = e
        )
    } catch (e: Exception) {
        // ✅ Rate limit logging
        if (ErrorRateLimiter.shouldLog(tag, errorMessage)) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "$errorMessage: $sanitized")
            reportException(tag, errorMessage, e)
        }
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.UNKNOWN,
            cause = e
        )
    }
}

/**
 * Execute a block and ignore specific error types
 * 
 * ⚠️ Use sparingly! Only for truly non-critical operations.
 * 
 * @param tag Log tag for error messages (required for tracking)
 * @param allowedExceptions List of exception types to ignore (default: empty = ignore all)
 * @param block The code block to execute
 */
inline fun ignoreErrors(
    tag: String,
    allowedExceptions: List<Class<out Exception>> = emptyList(),
    block: () -> Unit
) {
    try {
        block()
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: Exception) {
        // ✅ Check if exception type is allowed to be ignored
        val shouldIgnore = allowedExceptions.isEmpty() || 
                          allowedExceptions.any { it.isInstance(e) }
        
        if (shouldIgnore) {
            val sanitized = sanitizeExceptionMessage(e)
            Log.w(tag, "Ignoring error: $sanitized")
            
            // Structured log for future crash reporting integration
            if (!BuildConfig.DEBUG) {
                Log.e("ErrorHandling", "IGNORED_ERROR tag=$tag type=${e.javaClass.simpleName} msg=$sanitized")
            }
        } else {
            // ✅ Don't ignore unexpected exceptions
            val sanitized = sanitizeExceptionMessage(e)
            Log.e(tag, "Unexpected error (not in allowed list): $sanitized")
            reportException(tag, "Unexpected error in ignoreErrors", e)
            throw e
        }
    }
}
