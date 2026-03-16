package com.linker.app.core.util

/**
 * A generic class that holds a value with its loading status.
 *
 * Used as the return type for every repository/use-case call so that the
 * ViewModel always knows whether to show a loader, display data, or surface
 * an error without dealing with exceptions directly.
 */
sealed class Result<out T> {

    /** Represents a successful operation with its returned data. */
    data class Success<T>(val data: T) : Result<T>()

    /** Represents a failed operation with an optional human-readable message. */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : Result<Nothing>()

    /** Represents an in-progress operation. */
    data object Loading : Result<Nothing>()

    // ── Convenience helpers ───────────────────────────────────────────────

    val isSuccess get() = this is Success
    val isError   get() = this is Error
    val isLoading get() = this is Loading

    /** Returns the encapsulated data or null if this is not [Success]. */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the encapsulated data, or throws if this is [Error]/[Loading]. */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error   -> throw cause ?: Exception(message)
        is Loading -> throw IllegalStateException("Result is still Loading")
    }

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
}

/** Wraps a suspending [block] in a try/catch, returning [Result.Success] or
 *  [Result.Error]. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e.message ?: "Unknown error", e)
}
