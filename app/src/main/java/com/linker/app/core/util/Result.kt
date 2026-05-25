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
    
    /**
     * ✅ Get error category from code
     */
    fun getCategoryFromCode(code: String): ErrorCategory {
        return when (code) {
            NETWORK, TIMEOUT -> ErrorCategory.NETWORK
            AUTH, PERMISSION -> ErrorCategory.AUTH
            VALIDATION -> ErrorCategory.VALIDATION
            DATABASE -> ErrorCategory.DATABASE
            CANCELLED -> ErrorCategory.SYSTEM
            else -> ErrorCategory.UNKNOWN
        }
    }
    
    /**
     * ✅ Get error severity from code
     */
    fun getSeverityFromCode(code: String): ErrorSeverity {
        return when (code) {
            AUTH, PERMISSION -> ErrorSeverity.CRITICAL
            DATABASE, NETWORK -> ErrorSeverity.HIGH
            VALIDATION, NOT_FOUND -> ErrorSeverity.MEDIUM
            CANCELLED -> ErrorSeverity.LOW
            else -> ErrorSeverity.MEDIUM
        }
    }
}

/**
 * ✅ Error severity levels
 */
enum class ErrorSeverity {
    LOW,      // Informational, can be ignored
    MEDIUM,   // Warning, should be addressed
    HIGH,     // Error, needs attention
    CRITICAL  // Critical error, immediate action required
}

/**
 * ✅ Error categories
 */
enum class ErrorCategory {
    NETWORK,      // Network-related errors
    AUTH,         // Authentication/authorization errors
    VALIDATION,   // Input validation errors
    DATABASE,     // Database errors
    PERMISSION,   // Permission errors
    BUSINESS,     // Business logic errors
    SYSTEM,       // System errors
    UNKNOWN       // Unknown errors
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
        val isRetryable: Boolean = true,
        val retryCount: Int = 0,  // ✅ Track retry attempts
        val timestamp: Long = System.currentTimeMillis(),  // ✅ Track when error occurred
        val originalError: Error? = null,  // ✅ Track original error before retries
        val userMessage: String? = null  // ✅ User-facing message
    ) : Result<Nothing>() {
        
        /**
         * ✅ Get error category
         */
        val category: ErrorCategory
            get() = ErrorCodes.getCategoryFromCode(code)
        
        /**
         * ✅ Get error severity
         */
        val severity: ErrorSeverity
            get() = ErrorCodes.getSeverityFromCode(code)
        
        /**
         * ✅ Create a new error with incremented retry count
         */
        fun withRetry(): Error {
            return copy(
                retryCount = retryCount + 1,
                timestamp = System.currentTimeMillis(),
                originalError = originalError ?: this
            )
        }
        
        /**
         * ✅ Check if max retries exceeded
         */
        fun hasExceededMaxRetries(maxRetries: Int): Boolean {
            return retryCount >= maxRetries
        }
        
        /**
         * ✅ Get time since first error
         */
        fun getTimeSinceFirstError(): Long {
            val firstError = originalError ?: this
            return System.currentTimeMillis() - firstError.timestamp
        }
        
        /**
         * ✅ Get user-friendly message
         */
        fun getUserFriendlyMessage(): String {
            return userMessage ?: when (category) {
                ErrorCategory.NETWORK -> "Network error. Please check your connection."
                ErrorCategory.AUTH -> "Authentication error. Please sign in again."
                ErrorCategory.VALIDATION -> "Invalid input. Please check your data."
                ErrorCategory.DATABASE -> "Database error. Please try again."
                ErrorCategory.PERMISSION -> "Permission denied. Please grant required permissions."
                ErrorCategory.BUSINESS -> message
                ErrorCategory.SYSTEM -> "System error. Please try again."
                ErrorCategory.UNKNOWN -> "An error occurred. Please try again."
            }
        }
    }

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
    val (code, isRetryable, userMessage) = when (e) {
        is java.net.UnknownHostException -> 
            Triple(ErrorCodes.NETWORK, true, "No internet connection")
        is java.net.SocketTimeoutException -> 
            Triple(ErrorCodes.TIMEOUT, true, "Connection timeout")
        is java.util.concurrent.CancellationException,
        is kotlinx.coroutines.CancellationException -> 
            Triple(ErrorCodes.CANCELLED, false, null)
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> Triple(ErrorCodes.AUTH, false, "Authentication failed")
            404 -> Triple(ErrorCodes.NOT_FOUND, false, "Resource not found")
            408, 504 -> Triple(ErrorCodes.TIMEOUT, true, "Request timeout")
            in 500..599 -> Triple(ErrorCodes.NETWORK, true, "Server error")
            else -> Triple(ErrorCodes.UNKNOWN, true, null)
        }
        else -> Triple(ErrorCodes.UNKNOWN, true, null)
    }
    
    Result.Error(
        message = e.message ?: "Unknown error",
        code = code,
        cause = e,
        isRetryable = isRetryable,
        userMessage = userMessage
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

