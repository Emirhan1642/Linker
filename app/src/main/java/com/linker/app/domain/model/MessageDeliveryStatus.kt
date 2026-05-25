package com.linker.app.domain.model

/**
 * Sealed class representing the delivery lifecycle of a message.
 *
 * ## State Machine
 * ```
 * Pending → Sending → Sent → Delivered → Read
 *                       ↓        ↓         ↓
 *                     Failed   Failed    Failed
 *                       ↓
 *                    Sending (retry)
 * ```
 *
 * Use [canTransitionTo] to check valid state transitions before applying them.
 * Use [validateTransition] to transition with an [IllegalStateException] on invalid moves.
 */
sealed class MessageDeliveryStatus {

    /** Message has been created but not yet sent. */
    object Pending : MessageDeliveryStatus()

    /** Message is currently being transmitted. */
    object Sending : MessageDeliveryStatus()

    /**
     * Message was successfully sent to the server.
     * @property sentAt Timestamp when the server acknowledged receipt (epoch ms).
     */
    data class Sent(val sentAt: Long) : MessageDeliveryStatus() {
        init {
            require(sentAt > 0) { "sentAt must be positive" }
        }
    }

    /**
     * Message was delivered to the recipient's device.
     * @property deliveredAt Timestamp of delivery confirmation (epoch ms).
     */
    data class Delivered(val deliveredAt: Long) : MessageDeliveryStatus() {
        init {
            require(deliveredAt > 0) { "deliveredAt must be positive" }
        }
    }

    /**
     * Message was read by the recipient.
     * @property readAt Timestamp when the recipient opened the message (epoch ms).
     */
    data class Read(val readAt: Long) : MessageDeliveryStatus() {
        init {
            require(readAt > 0) { "readAt must be positive" }
        }
    }

    /**
     * Message delivery failed.
     *
     * @property failedAt Timestamp of the failure (epoch ms).
     * @property reason Structured failure reason.
     * @property technicalDetails Optional technical details for debugging.
     */
    data class Failed(
        val failedAt: Long,
        val reason: FailureReason,
        val technicalDetails: String? = null
    ) : MessageDeliveryStatus() {
        init {
            require(failedAt > 0) { "failedAt must be positive" }
            technicalDetails?.let {
                require(it.length <= MAX_TECHNICAL_DETAILS_LENGTH) {
                    "technicalDetails exceeds maximum length of $MAX_TECHNICAL_DETAILS_LENGTH"
                }
            }
        }

        companion object {
            /** Maximum length for technical details string. */
            const val MAX_TECHNICAL_DETAILS_LENGTH = 500
        }
    }

    /**
     * Human-readable display text for the current status.
     */
    fun toDisplayText(): String = when (this) {
        is Pending -> "Pending"
        is Sending -> "Sending..."
        is Sent -> "Sent"
        is Delivered -> "Delivered"
        is Read -> "Read"
        is Failed -> "Failed"
    }

    /**
     * Whether this status represents a terminal state (no further transitions).
     */
    fun isTerminal(): Boolean = this is Read

    /**
     * Whether this status represents a failed delivery.
     */
    fun isFailed(): Boolean = this is Failed

    /**
     * Whether the message can be retried from this state.
     */
    fun canRetry(): Boolean = this is Failed && reason.isRetryable

    /**
     * Extracts the timestamp from status types that carry one.
     * Returns null for [Pending] and [Sending].
     */
    fun getTimestamp(): Long? = when (this) {
        is Pending -> null
        is Sending -> null
        is Sent -> sentAt
        is Delivered -> deliveredAt
        is Read -> readAt
        is Failed -> failedAt
    }

    /**
     * Checks whether a transition from this status to [target] is valid
     * according to the state machine rules.
     */
    fun canTransitionTo(target: MessageDeliveryStatus): Boolean = when (this) {
        is Pending -> target is Sending
        is Sending -> target is Sent || target is Failed
        is Sent -> target is Delivered || target is Failed
        is Delivered -> target is Read || target is Failed
        is Read -> false
        is Failed -> target is Sending
    }

    /**
     * Transitions to [target] if valid, throwing [IllegalStateException] otherwise.
     */
    fun validateTransition(target: MessageDeliveryStatus): MessageDeliveryStatus {
        check(canTransitionTo(target)) {
            "Invalid transition from ${this::class.simpleName} to ${target::class.simpleName}"
        }
        return target
    }

    companion object {
        /** Convenience factory for [Sent] with current timestamp. */
        fun sent(sentAt: Long = System.currentTimeMillis()) = Sent(sentAt)

        /** Convenience factory for [Delivered] with current timestamp. */
        fun delivered(deliveredAt: Long = System.currentTimeMillis()) = Delivered(deliveredAt)

        /** Convenience factory for [Read] with current timestamp. */
        fun read(readAt: Long = System.currentTimeMillis()) = Read(readAt)

        /** Convenience factory for [Failed]. */
        fun failed(
            reason: FailureReason,
            technicalDetails: String? = null,
            failedAt: Long = System.currentTimeMillis()
        ) = Failed(failedAt, reason, technicalDetails)
    }
}

/**
 * Structured failure reasons for message delivery.
 *
 * Replaces raw error strings with type-safe, retryable-aware error codes.
 *
 * @property displayMessage User-facing error description.
 * @property isRetryable Whether the operation can be retried automatically.
 */
enum class FailureReason(
    val displayMessage: String,
    val isRetryable: Boolean
) {
    /** Network connectivity issue. */
    NETWORK_ERROR("Network error. Please check your connection.", true),
    /** Request timed out. */
    TIMEOUT("Request timed out. Please try again.", true),
    /** User doesn't have permission. */
    PERMISSION_DENIED("You don't have permission to send messages.", false),
    /** Recipient has blocked the sender. */
    BLOCKED_USER("Unable to send message to this user.", false),
    /** Too many requests in a short period. */
    RATE_LIMIT("Too many messages. Please wait a moment.", true),
    /** Message content is invalid or violates policy. */
    INVALID_CONTENT("Message content is not allowed.", false),
    /** Message exceeds the size limit. */
    MESSAGE_TOO_LONG("Message is too long.", false),
    /** Target chat was not found. */
    CHAT_NOT_FOUND("Chat not found.", false),
    /** Internal server error. */
    SERVER_ERROR("Server error. Please try again later.", true),
    /** Unknown or unclassified error. */
    UNKNOWN("An unknown error occurred.", true)
}

/**
 * Converts a [MessageStatus] enum to [MessageDeliveryStatus] sealed class.
 *
 * @param timestamp Optional timestamp override (defaults to current time).
 * @param failureReason Failure reason for FAILED status.
 * @param technicalDetails Optional technical error details.
 * @return Corresponding [MessageDeliveryStatus] instance.
 */
fun MessageStatus.toDeliveryStatus(
    timestamp: Long? = null,
    failureReason: FailureReason = FailureReason.UNKNOWN,
    technicalDetails: String? = null
): MessageDeliveryStatus {
    return when (this) {
        MessageStatus.SENDING -> MessageDeliveryStatus.Sending
        MessageStatus.SENT -> MessageDeliveryStatus.Sent(timestamp ?: System.currentTimeMillis())
        MessageStatus.DELIVERED -> MessageDeliveryStatus.Delivered(timestamp ?: System.currentTimeMillis())
        MessageStatus.READ -> MessageDeliveryStatus.Read(timestamp ?: System.currentTimeMillis())
        MessageStatus.FAILED -> MessageDeliveryStatus.Failed(
            failedAt = timestamp ?: System.currentTimeMillis(),
            reason = failureReason,
            technicalDetails = technicalDetails
        )
    }
}
