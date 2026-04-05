package com.linker.app.core.util

/**
 * Standardized error codes for application-wide error handling
 */
object ErrorCodes {
    const val UNKNOWN = "UNKNOWN"
    const val NETWORK = "NETWORK_ERROR"
    const val AUTH = "AUTH_ERROR"
    const val NOT_FOUND = "NOT_FOUND"
    const val VALIDATION = "VALIDATION_ERROR"
    const val DATABASE = "DATABASE_ERROR"
    const val TIMEOUT = "TIMEOUT"
    const val CANCELLED = "CANCELLED"
    const val PERMISSION = "PERMISSION_DENIED"
}

/**
 * A generic class that holds a value with its loading status.
 *
 * Used as the return type for every repository/use-case call so that the
 * ViewModel always knows whether to show a loader, display data, or surface
 * an error without dealing with exceptions directly.
 *
 * ✅ ENHANCED: Added error codes, retry tracking, and standardized helpers
 */
sealed class Result<out T> {

    /** Represents a successful operation with its returned data. */
    data class Success<T>(val data: T) : Result<T>()

    /** Represents a failed operation with standardized error info. */
    data class Error(
        val message: String,
        val code: String = ErrorCodes.UNKNOWN,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : Result<Nothing>()

    /** Represents an in-progress operation with optional progress value. */
    data class Loading(val progress: Float? = null) : Result<Nothing>()

    // ── Convenience helpers ───────────────────────────────────────────────

    val isSuccess get() = this is Success
    val isError   get() = this is Error
    val isLoading get() = this is Loading

    /** Returns the encapsulated data or null if this is not [Success]. */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the encapsulated data, or throws if this is [Error]/[Loading]. */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error   -> throw cause ?: Exception("[$code] $message")
        is Loading -> throw IllegalStateException("Result is still Loading")
    }

    /** Returns the data or a default value if not [Success]. */
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    /**
     * Transforms the [Success] data with [transform], leaving [Error] and
     * [Loading] unchanged.
     */
    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error   -> this
        is Loading -> this
    }

    /**
     * Maps an error to a different error, leaving [Success] and [Loading] unchanged.
     */
    fun mapError(transform: (Error) -> Error): Result<T> = when (this) {
        is Success -> this
        is Error   -> transform(this)
        is Loading -> this
    }

    /**
     * Calls [onSuccess], [onError], or [onLoading] depending on the state
     * and returns the result of whichever block was invoked.
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError:   (Error) -> R,
        onLoading: () -> R = { throw IllegalStateException("Unexpected Loading") }
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error   -> onError(this)
        is Loading -> onLoading()
    }

    /**
     * Execute [action] only if this is [Success].
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute [action] only if this is [Error].
     */
    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) action(this)
        return this
    }

    /**
     * Execute [action] only if this is [Loading].
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
}

/** Wraps a suspending [block] in a try/catch, returning [Result.Success] or
 *  [Result.Error] with standardized error classification.
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    val (code, isRetryable) = when (e) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException -> ErrorCodes.NETWORK to true
        is java.util.concurrent.CancellationException -> ErrorCodes.CANCELLED to false
        is kotlinx.coroutines.CancellationException -> ErrorCodes.CANCELLED to false
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> ErrorCodes.AUTH to false
            404 -> ErrorCodes.NOT_FOUND to false
            408, 504 -> ErrorCodes.TIMEOUT to true
            in 500..599 -> ErrorCodes.NETWORK to true
            else -> ErrorCodes.UNKNOWN to true
        }
        else -> ErrorCodes.UNKNOWN to true
    }
    Result.Error(
        message = e.message ?: "Unknown error",
        code = code,
        cause = e,
        isRetryable = isRetryable
    )
}

/** Wraps a suspending [block] in a try/catch with custom error mapper. */
suspend fun <T> safeCall(
    errorMapper: (Exception) -> Result.Error,
    block: suspend () -> T
): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    errorMapper(e)
}

