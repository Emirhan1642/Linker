package com.linker.app.domain.model

/**
 * Sealed class for message sending state with error handling
 */
sealed class MessageSendState {
    object Idle : MessageSendState()
    object Sending : MessageSendState()
    data class Success(val messageId: String) : MessageSendState()
    data class Error(
        val errorType: MessageErrorType,
        val message: String,
        val canRetry: Boolean = true
    ) : MessageSendState()
}

/**
 * Error types for messaging operations
 */
enum class MessageErrorType {
    NETWORK_ERROR,
    PERMISSION_DENIED,
    BLOCKED_USER,
    RATE_LIMIT,
    INVALID_CONTENT,
    MESSAGE_TOO_LONG,
    CHAT_NOT_FOUND,
    UNKNOWN
}

/**
 * Parse exception to determine error type
 */
fun parseErrorType(exception: Throwable?): MessageErrorType {
    return when {
        exception == null -> MessageErrorType.UNKNOWN
        exception.message?.contains("network", ignoreCase = true) == true ->
            MessageErrorType.NETWORK_ERROR
        exception.message?.contains("permission", ignoreCase = true) == true ->
            MessageErrorType.PERMISSION_DENIED
        exception.message?.contains("blocked", ignoreCase = true) == true ->
            MessageErrorType.BLOCKED_USER
        exception.message?.contains("rate", ignoreCase = true) == true ->
            MessageErrorType.RATE_LIMIT
        exception.message?.contains("too long", ignoreCase = true) == true ->
            MessageErrorType.MESSAGE_TOO_LONG
        exception.message?.contains("not found", ignoreCase = true) == true ->
            MessageErrorType.CHAT_NOT_FOUND
        else -> MessageErrorType.UNKNOWN
    }
}
