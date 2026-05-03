package com.linker.app.core.util

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Error handling utilities for reducing try-catch boilerplate
 * 
 * Provides extension functions and inline utilities for common error handling patterns.
 */

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
        Log.e(tag, "$errorMessage: Security exception", e)
        onError?.invoke(e)
        null
    } catch (e: Exception) {
        Log.e(tag, "$errorMessage: ${e.message}", e)
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
        Log.e(tag, "$errorMessage: Security exception", e)
        onError?.invoke(e)
        null
    } catch (e: Exception) {
        Log.e(tag, "$errorMessage: ${e.message}", e)
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
        Log.e(tag, "$errorMessage: Security exception", e)
        defaultValue
    } catch (e: Exception) {
        Log.e(tag, "$errorMessage: ${e.message}", e)
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
        Log.e(tag, "$errorMessage: Security exception", e)
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.PERMISSION,
            cause = e
        )
    } catch (e: Exception) {
        Log.e(tag, "$errorMessage: ${e.message}", e)
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
        Log.e(tag, "$errorMessage: Security exception", e)
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.PERMISSION,
            cause = e
        )
    } catch (e: Exception) {
        Log.e(tag, "$errorMessage: ${e.message}", e)
        Result.Error(
            message = e.message ?: errorMessage,
            code = ErrorCodes.UNKNOWN,
            cause = e
        )
    }
}

/**
 * Execute a block and ignore all errors (use sparingly)
 * 
 * @param tag Log tag for error messages (optional)
 * @param block The code block to execute
 */
inline fun ignoreErrors(
    tag: String? = null,
    block: () -> Unit
) {
    try {
        block()
    } catch (e: CancellationException) {
        // Don't catch coroutine cancellation
        throw e
    } catch (e: Exception) {
        tag?.let { Log.w(it, "Ignoring error: ${e.message}") }
    }
}
