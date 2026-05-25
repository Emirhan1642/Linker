package com.linker.app.domain.model

/**
 * Sealed class representing the state of a message send operation.
 *
 * ## State Machine
 * ```
 * Idle → Sending → Success
 *           ↓
 *         Error
 *           ↓
 *        Sending (retry, up to MAX_RETRY_COUNT)
 * ```
 *
 * Use [canTransitionTo] to validate state transitions before applying them.
 */
sealed class MessageSendState {

    /** No send operation in progress. */
    object Idle : MessageSendState()

    /**
     * Message is being sent.
     * @property startedAt Timestamp when sending began (epoch ms).
     */
    data class Sending(
        val startedAt: Long = System.currentTimeMillis()
    ) : MessageSendState()

    /**
     * Message was sent successfully.
     * @property messageId Server-assigned message ID.
     * @property completedAt Timestamp when sending completed (epoch ms).
     */
    data class Success(
        val messageId: String,
        val completedAt: Long = System.currentTimeMillis()
    ) : MessageSendState() {
        init {
            require(messageId.isNotBlank()) { "messageId cannot be blank" }
            require(completedAt > 0) { "completedAt must be positive" }
        }
    }

    /**
     * Message send failed.
     *
     * @property errorType Categorized error type with metadata.
     * @property message User-facing error description.
     * @property technicalMessage Optional technical details for debugging.
     * @property occurredAt Timestamp when the error occurred (epoch ms).
     * @property retryCount Number of retry attempts so far.
     */
    data class Error(
        val errorType: MessageErrorType,
        val message: String,
        val technicalMessage: String? = null,
        val occurredAt: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    ) : MessageSendState() {
        init {
            require(message.isNotBlank()) { "Error message cannot be blank" }
            require(occurredAt > 0) { "occurredAt must be positive" }
            require(retryCount >= 0) { "retryCount cannot be negative" }
        }

        /**
         * Whether this error can be automatically retried.
         * Checks both the error type's retryability and the retry count limit.
         */
        val canRetry: Boolean
            get() = errorType.isRetryable && retryCount < MAX_RETRY_COUNT

        /**
         * Calculates the delay before the next retry using exponential backoff.
         * base delay × 2^retryCount, capped at 60 seconds.
         */
        fun getRetryDelayMs(): Long {
            if (!canRetry) return 0L
            val baseDelay = errorType.retryDelaySeconds * 1000L
            val exponentialDelay = baseDelay * (1L shl retryCount.coerceAtMost(5))
            return exponentialDelay.coerceAtMost(60_000L)
        }

        /**
         * Whether enough time has elapsed since the error to retry.
         */
        fun canRetryNow(): Boolean {
            if (!canRetry) return false
            val elapsed = System.currentTimeMillis() - occurredAt
            return elapsed >= getRetryDelayMs()
        }

        /**
         * Returns a copy with incremented [retryCount] and reset [occurredAt].
         */
        fun incrementRetry(): Error = copy(
            retryCount = retryCount + 1,
            occurredAt = System.currentTimeMillis()
        )

        companion object {
            /** Maximum number of automatic retries. */
            const val MAX_RETRY_COUNT = 3
        }
    }

    /**
     * Checks whether a transition from this state to [target] is valid.
     */
    fun canTransitionTo(target: MessageSendState): Boolean = when (this) {
        is Idle -> target is Sending
        is Sending -> target is Success || target is Error
        is Success -> false
        is Error -> target is Sending || target is Idle
    }

    /**
     * Transitions to [target] if valid, throwing [IllegalStateException] otherwise.
     */
    fun transitionTo(target: MessageSendState): MessageSendState {
        check(canTransitionTo(target)) {
            "Invalid transition from ${this::class.simpleName} to ${target::class.simpleName}"
        }
        return target
    }

    /** Whether this is a terminal state (no further transitions expected). */
    fun isTerminal(): Boolean = this is Success

    /** Whether a send operation is currently in progress. */
    fun isInProgress(): Boolean = this is Sending
}

/**
 * Error severity levels for message send failures.
 */
enum class ErrorSeverity {
    /** Transient issue, likely to resolve on retry (e.g., network hiccup). */
    LOW,
    /** Moderate issue, may need user attention (e.g., rate limiting). */
    MEDIUM,
    /** Critical issue, requires user action (e.g., blocked, permission denied). */
    HIGH
}

/**
 * Categorized error types for message send failures.
 *
 * Each type carries metadata about retry behavior, user action requirements,
 * and display information.
 *
 * @property displayMessageKey Localization key for user-facing error message.
 * @property isRetryable Whether the error can be retried automatically.
 * @property retryDelaySeconds Base delay in seconds before retry.
 * @property requiresUserAction Whether the user must take action to resolve.
 * @property severity Severity level of this error.
 */
enum class MessageErrorType(
    val displayMessageKey: String,
    val isRetryable: Boolean,
    val retryDelaySeconds: Int,
    val requiresUserAction: Boolean,
    val severity: ErrorSeverity
) {
    /** Network connectivity issue. */
    NETWORK_ERROR(
        displayMessageKey = "error_network",
        isRetryable = true,
        retryDelaySeconds = 2,
        requiresUserAction = false,
        severity = ErrorSeverity.LOW
    ),
    /** User doesn't have permission to send. */
    PERMISSION_DENIED(
        displayMessageKey = "error_permission",
        isRetryable = false,
        retryDelaySeconds = 0,
        requiresUserAction = true,
        severity = ErrorSeverity.HIGH
    ),
    /** Recipient has blocked the sender. */
    BLOCKED_USER(
        displayMessageKey = "error_blocked",
        isRetryable = false,
        retryDelaySeconds = 0,
        requiresUserAction = true,
        severity = ErrorSeverity.HIGH
    ),
    /** Too many requests in a short period. */
    RATE_LIMIT(
        displayMessageKey = "error_rate_limit",
        isRetryable = true,
        retryDelaySeconds = 10,
        requiresUserAction = false,
        severity = ErrorSeverity.MEDIUM
    ),
    /** Message content violates policies. */
    INVALID_CONTENT(
        displayMessageKey = "error_invalid_content",
        isRetryable = false,
        retryDelaySeconds = 0,
        requiresUserAction = true,
        severity = ErrorSeverity.HIGH
    ),
    /** Message exceeds the size limit. */
    MESSAGE_TOO_LONG(
        displayMessageKey = "error_too_long",
        isRetryable = false,
        retryDelaySeconds = 0,
        requiresUserAction = true,
        severity = ErrorSeverity.MEDIUM
    ),
    /** Target chat was not found. */
    CHAT_NOT_FOUND(
        displayMessageKey = "error_chat_not_found",
        isRetryable = false,
        retryDelaySeconds = 0,
        requiresUserAction = true,
        severity = ErrorSeverity.HIGH
    ),
    /** Unknown or unclassified error. */
    UNKNOWN(
        displayMessageKey = "error_unknown",
        isRetryable = true,
        retryDelaySeconds = 5,
        requiresUserAction = false,
        severity = ErrorSeverity.MEDIUM
    )
}

/**
 * Parses an [Exception] into the appropriate [MessageErrorType].
 *
 * Uses keyword matching on the exception message to classify the error.
 * Falls back to [MessageErrorType.UNKNOWN] if no match is found.
 */
fun parseErrorType(exception: Throwable?): MessageErrorType {
    return when {
        exception == null -> MessageErrorType.UNKNOWN
        exception.message?.contains("network", ignoreCase = true) == true -> MessageErrorType.NETWORK_ERROR
        exception.message?.contains("permission", ignoreCase = true) == true -> MessageErrorType.PERMISSION_DENIED
        exception.message?.contains("blocked", ignoreCase = true) == true -> MessageErrorType.BLOCKED_USER
        exception.message?.contains("rate", ignoreCase = true) == true -> MessageErrorType.RATE_LIMIT
        exception.message?.contains("too long", ignoreCase = true) == true -> MessageErrorType.MESSAGE_TOO_LONG
        exception.message?.contains("not found", ignoreCase = true) == true -> MessageErrorType.CHAT_NOT_FOUND
        else -> MessageErrorType.UNKNOWN
    }
}
