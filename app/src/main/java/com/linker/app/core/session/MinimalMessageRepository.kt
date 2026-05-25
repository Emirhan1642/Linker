package com.linker.app.core.session

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.BuildConfig
import com.linker.app.R
import com.linker.app.core.di.ChatNotificationRequest
import com.linker.app.core.di.SupabaseNotificationApi
import com.linker.app.core.security.ConfigResult
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Message Repository for passive accounts
 *
 * Provides basic messaging operations without full repository overhead.
 * Used only for notification actions from passive accounts.
 *
 * Changes:
 *  - [3.1] SecurityManager injected; API keys retrieved from secure storage
 *  - [3.2] Firestore runTransaction for atomic message send (instead of batch)
 *  - [3.3] sanitizeUserId/ChatId/MessageId mask PII in production logs
 *  - [3.4] withTimeout(15s) on all Firestore read/write operations
 *  - [3.5] notificationRateLimiter — max 10 notifications/min per recipient
 *  - [3.6] retryWithExponentialBackoff for transient network failures
 *  - [3.7] Input validation (chatId, content, emoji length checks)
 *  - [3.8] NotificationResult — failure reasons tracked and logged
 *  - [3.9] Concurrent notification dispatch via async/awaitAll
 *  - [3.10] Hardcoded Turkish text replaced with strings.xml R.string reference
 */
class MinimalMessageRepository(
    private val firestore: FirebaseFirestore,
    private val currentUserId: String,
    private val supabaseNotificationApi: SupabaseNotificationApi,
    private val securityManager: SecurityManager,
    private val context: Context   // [3.10] for R.string
) {

    // [3.5] Rate limit tracking
    private val notificationRateLimiter = ConcurrentHashMap<String, RateLimitInfo>()

    private data class RateLimitInfo(
        var count: Int = 0,
        var windowStart: Long = System.currentTimeMillis()
    )

    // [3.8] Structured notification result
    private data class NotificationResult(
        val recipientId: String,
        val success: Boolean,
        val error: String? = null
    )

    companion object {
        private const val TAG = "MinimalMessageRepo"
        private const val FIRESTORE_TIMEOUT_MS = 15_000L      // [3.4] 15 seconds
        private const val MAX_NOTIFICATIONS_PER_MINUTE = 10   // [3.5]
        private const val RATE_LIMIT_WINDOW_MS = 60_000L      // [3.5] 1 minute
        private const val MAX_MESSAGE_LENGTH = 10_000          // [3.7]
        private const val MAX_EMOJI_LENGTH = 10                // [3.7]

        // [3.3] PII sanitization helpers
        private fun sanitizeUserId(userId: String?): String = if (BuildConfig.DEBUG)
            userId ?: "null"
        else userId?.let { "user_${it.hashCode().toString(16)}" } ?: "null"

        private fun sanitizeChatId(chatId: String?): String = if (BuildConfig.DEBUG)
            chatId ?: "null"
        else chatId?.let { "chat_${it.hashCode().toString(16)}" } ?: "null"

        private fun sanitizeMessageId(messageId: String?): String = if (BuildConfig.DEBUG)
            messageId ?: "null"
        else messageId?.let { "msg_${it.hashCode().toString(16)}" } ?: "null"
    }

    // ── Input Validation [3.7] ────────────────────────────────────────────────

    private fun validateChatId(chatId: String) {
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(chatId.length <= 100) { "Chat ID too long" }
    }

    private fun validateMessageContent(content: String) {
        require(content.isNotBlank()) { "Message content cannot be blank" }
        require(content.length <= MAX_MESSAGE_LENGTH) { "Message too long (max $MAX_MESSAGE_LENGTH characters)" }
    }

    private fun validateMessageId(messageId: String) {
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        require(messageId.length <= 100) { "Message ID too long" }
    }

    private fun validateEmoji(emoji: String?) {
        if (emoji != null) {
            require(emoji.isNotBlank()) { "Emoji cannot be blank" }
            require(emoji.length <= MAX_EMOJI_LENGTH) { "Emoji too long" }
        }
    }

    // ── Rate Limiting [3.5] ───────────────────────────────────────────────────

    private fun canSendNotification(recipientId: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "${currentUserId}_$recipientId"

        val info = notificationRateLimiter.getOrPut(key) { RateLimitInfo() }

        if (now - info.windowStart > RATE_LIMIT_WINDOW_MS) {
            info.count = 0
            info.windowStart = now
        }

        if (info.count >= MAX_NOTIFICATIONS_PER_MINUTE) {
            android.util.Log.w(TAG, "Rate limit exceeded for ${sanitizeUserId(recipientId)}")
            return false
        }

        info.count++
        return true
    }

    // ── Retry Helper [3.6] ────────────────────────────────────────────────────

    private suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1_000L,
        maxDelay: Long = 10_000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}, retrying in ${currentDelay}ms")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block()
    }

    // ── Supabase Key Helper [3.1] ─────────────────────────────────────────────

    private fun getSupabaseKey(): String? {
        return when (val result = securityManager.getSupabaseAnonKey()) {
            is ConfigResult.Success -> result.value
            is ConfigResult.Error -> {
                android.util.Log.e(TAG, "Failed to get Supabase key: ${result.message}")
                null
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a text message from passive account.
     * [3.2] Uses Firestore transaction for atomicity.
     * [3.4] Timeout on all Firestore ops.
     * [3.6] Retry on transient failures.
     * [3.7] Input validation.
     */
    suspend fun sendMessage(chatId: String, content: String): Result<Unit> = safeCall {
        // [3.7] Validate
        validateChatId(chatId)
        validateMessageContent(content)

        android.util.Log.d(TAG, "Sending message to chat ${sanitizeChatId(chatId)}")

        // [3.6] Retry
        retryWithExponentialBackoff {
            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // [3.4] Fetch chat with timeout
            val chatDoc = try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    firestore.collection("chats").document(chatId).get().await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e(TAG, "Timeout fetching chat ${sanitizeChatId(chatId)}")
                throw Exception("Request timeout. Please check your connection.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Network error fetching chat: ${e.message}", e)
                throw Exception("Network error: Unable to fetch chat. Please check your connection.")
            }

            if (!chatDoc.exists()) throw Exception("Chat not found")

            val chatData = chatDoc.data ?: throw Exception("Chat data is null")
            val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val chatType = chatData["chatType"] as? String ?: "PRIVATE"

            val messageData = hashMapOf(
                "messageId" to messageId,
                "chatId" to chatId,
                "senderId" to currentUserId,
                "messageType" to "TEXT",
                "content" to content,
                "mediaUrl" to null,
                "thumbnailUrl" to null,
                "mediaWidth" to null,
                "mediaHeight" to null,
                "mediaDuration" to null,
                "sharedLinkId" to null,
                "replyToMessageId" to null,
                "forwardedFromMessageId" to null,
                "reactions" to emptyMap<String, String>(),
                "readReceipts" to emptyMap<String, Long>(),
                "deliveryReceipts" to mapOf(currentUserId to now),
                "isEdited" to false,
                "isDeleted" to false,
                "deletedForEveryone" to false,
                "messageStatus" to "SENT",
                "deliveryMethod" to "ONLINE",
                "participantIds" to participantIds,
                "createdAt" to now,
                "updatedAt" to now,
                "deliveredAt" to now,
                "readAt" to null
            )

            // [3.2] Atomic transaction instead of batch
            try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    firestore.runTransaction { transaction ->
                        val messageRef = firestore.collection("chats").document(chatId)
                            .collection("messages").document(messageId)
                        transaction.set(messageRef, messageData)

                        val chatRef = firestore.collection("chats").document(chatId)
                        val chatUpdates = mutableMapOf<String, Any>(
                            "lastMessageText" to content,
                            "lastMessageAt" to now,
                            "lastMessageId" to messageId,
                            "updatedAt" to now,
                            "unreadCounts.$currentUserId" to 0
                        )
                        participantIds.filter { it.isNotBlank() && it != currentUserId }
                            .forEach { uid -> chatUpdates["unreadCounts.$uid"] = FieldValue.increment(1) }

                        transaction.update(chatRef, chatUpdates)
                        null
                    }.await()
                }
                android.util.Log.d(TAG, "Message sent successfully from passive account")
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e(TAG, "Timeout committing message transaction")
                throw Exception("Request timeout. Please check your connection.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error committing message: ${e.message}", e)
                throw Exception("Network error: Unable to send message. Please check your connection.")
            }

            // [3.9] Concurrent notifications after successful transaction
            sendNotificationsIfNeeded(participantIds, chatType, content, chatId, messageId)
        }
    }

    /**
     * React to a message from passive account.
     * [3.4] Timeout on Firestore ops.
     * [3.6] Retry.
     * [3.7] Validation.
     */
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String?): Result<Unit> = safeCall {
        validateChatId(chatId)
        validateMessageId(messageId)
        validateEmoji(emoji)

        android.util.Log.d(TAG, "Reacting to message ${sanitizeMessageId(messageId)}")

        retryWithExponentialBackoff {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)

            val messageDoc = try {
                withTimeout(FIRESTORE_TIMEOUT_MS) { messageRef.get().await() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw Exception("Request timeout. Please check your connection.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Network error fetching message: ${e.message}", e)
                throw Exception("Network error: Unable to fetch message. Please check your connection.")
            }

            if (!messageDoc.exists()) throw Exception("Message not found")

            val reactions = (messageDoc.get("reactions") as? Map<*, *>)
                ?.mapKeys { it.key.toString() }?.toMutableMap() ?: mutableMapOf()
            val messageSenderId = messageDoc.getString("senderId") ?: ""

            if (emoji.isNullOrBlank()) {
                reactions.remove(currentUserId)
            } else {
                reactions[currentUserId] = emoji
                if (messageSenderId.isNotBlank() && messageSenderId != currentUserId) {
                    sendReactionNotification(messageSenderId, messageId, emoji)
                }
            }

            try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    messageRef.update("reactions", reactions).await()
                }
                android.util.Log.d(TAG, "Reaction updated for ${sanitizeMessageId(messageId)}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Network error updating reaction: ${e.message}", e)
                throw Exception("Network error: Unable to update reaction. Please check your connection.")
            }
        }
    }

    /**
     * Mark chat as read from passive account.
     * [3.4] Timeout on Firestore ops.
     * [3.6] Retry.
     * [3.7] Validation.
     */
    suspend fun markChatAsRead(chatId: String): Result<Unit> = safeCall {
        validateChatId(chatId)

        android.util.Log.d(TAG, "Marking chat ${sanitizeChatId(chatId)} as read")

        retryWithExponentialBackoff {
            val now = System.currentTimeMillis()

            val messagesSnapshot = try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    firestore.collection("chats").document(chatId)
                        .collection("messages")
                        .whereEqualTo("isDeleted", false)
                        .get()
                        .await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw Exception("Request timeout. Please check your connection.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Network error fetching messages: ${e.message}", e)
                throw Exception("Network error: Unable to fetch messages. Please check your connection.")
            }

            val batch = firestore.batch()
            var updateCount = 0

            for (doc in messagesSnapshot.documents) {
                val senderId = doc.getString("senderId")
                if (senderId == currentUserId) continue

                val readReceipts = doc.get("readReceipts") as? Map<*, *> ?: emptyMap<Any, Any>()
                if (readReceipts.containsKey(currentUserId)) continue

                batch.update(doc.reference, mapOf(
                    "readReceipts.$currentUserId" to now,
                    "readAt" to now,
                    "messageStatus" to "READ"
                ))
                updateCount++
            }

            batch.update(
                firestore.collection("chats").document(chatId),
                "unreadCounts.$currentUserId", 0
            )

            if (updateCount > 0 || true) { // Always commit to reset unread count
                try {
                    withTimeout(FIRESTORE_TIMEOUT_MS) { batch.commit().await() }
                    android.util.Log.d(TAG, "Chat ${sanitizeChatId(chatId)} marked as read ($updateCount messages)")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Network error marking as read: ${e.message}", e)
                    throw Exception("Network error: Unable to mark as read. Please check your connection.")
                }
            }
        }
    }

    // ── Private Notification Helpers ──────────────────────────────────────────

    /**
     * [3.9] Send notifications concurrently via async/awaitAll.
     * [3.5] Rate limited per recipient.
     * [3.8] Returns NotificationResult list.
     */
    private suspend fun sendNotificationsIfNeeded(
        participantIds: List<String>,
        chatType: String,
        content: String,
        chatId: String,
        messageId: String
    ) {
        val otherParticipants = participantIds.filter { it != currentUserId }
        if (otherParticipants.isEmpty()) return

        android.util.Log.d(TAG, "Sending notifications to ${otherParticipants.size} participants")

        // Get sender name
        val senderName = try {
            withTimeout(FIRESTORE_TIMEOUT_MS) {
                val userDoc = firestore.collection("users").document(currentUserId).get().await()
                userDoc.getString("displayName")?.ifBlank { userDoc.getString("username") }
                    ?: userDoc.getString("username")
                    ?: "User"
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to get sender name: ${e.message}")
            "User"
        }

        val chatName = if (chatType == "GROUP") {
            try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    firestore.collection("chats").document(chatId).get().await()
                        .getString("chatName")
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to get chat name: ${e.message}")
                null
            }
        } else null

        val displayText = content.take(50)
        val notificationMessage = when (chatType) {
            "PRIVATE" -> displayText
            "GROUP" -> "$senderName: $displayText"
            else -> displayText
        }

        // [3.9] Concurrent dispatch
        val results = coroutineScope {
            otherParticipants.mapNotNull { recipientId ->
                if (canSendNotification(recipientId)) {
                    async {
                        sendChatNotification(
                            recipientUserId = recipientId,
                            senderName = senderName,
                            messageText = notificationMessage,
                            chatId = chatId,
                            messageId = messageId,
                            chatType = chatType,
                            chatName = chatName
                        )
                    }
                } else null
            }.awaitAll()
        }

        // [3.8] Log summary
        val successCount = results.count { it.success }
        val failureCount = results.count { !it.success }
        android.util.Log.d(TAG, "Notification results: $successCount succeeded, $failureCount failed")
    }

    /**
     * [3.8] Structured notification result instead of silent failure.
     * [3.1] API key from SecurityManager.
     * [3.4] Timeout on HTTP call.
     */
    private suspend fun sendChatNotification(
        recipientUserId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        messageId: String,
        chatType: String,
        chatName: String? = null
    ): NotificationResult {
        return try {
            val key = getSupabaseKey()
                ?: return NotificationResult(recipientUserId, false, "Failed to get API key")

            android.util.Log.d(TAG, "Sending notification to ${sanitizeUserId(recipientUserId)}")

            val response = withTimeout(FIRESTORE_TIMEOUT_MS) {
                supabaseNotificationApi.sendChatNotification(
                    request = ChatNotificationRequest(
                        recipientId = recipientUserId,
                        senderId = currentUserId,
                        senderName = senderName,
                        message = messageText,
                        chatId = chatId,
                        messageId = messageId,
                        chatType = chatType,
                        chatName = chatName
                    )
                )
            }

            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Notification sent successfully")
                NotificationResult(recipientUserId, true)
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.w(TAG, "Failed to send notification: ${response.code()} - $errorBody")
                NotificationResult(recipientUserId, false, "HTTP ${response.code()}: $errorBody")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w(TAG, "Notification timeout for ${sanitizeUserId(recipientUserId)}")
            NotificationResult(recipientUserId, false, "Timeout")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to send notification: ${e.message}", e)
            NotificationResult(recipientUserId, false, e.message)
        }
    }

    /**
     * Send reaction notification.
     * [3.10] Uses localized string from strings.xml.
     * [3.1] API key from SecurityManager.
     */
    private suspend fun sendReactionNotification(recipientId: String, messageId: String, emoji: String) {
        try {
            val senderName = try {
                withTimeout(FIRESTORE_TIMEOUT_MS) {
                    val userDoc = firestore.collection("users").document(currentUserId).get().await()
                    userDoc.getString("displayName")?.ifBlank { userDoc.getString("username") }
                        ?: userDoc.getString("username")
                        ?: "User"
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to get sender name for reaction: ${e.message}")
                "User"
            }

            val key = getSupabaseKey() ?: run {
                android.util.Log.e(TAG, "Cannot send reaction notification: API key unavailable")
                return
            }

            // [3.10] Localized string instead of hardcoded Turkish
            val message = context.getString(R.string.reaction_notification_message, emoji)

            android.util.Log.d(TAG, "Sending reaction notification to ${sanitizeUserId(recipientId)}")

            val response = withTimeout(FIRESTORE_TIMEOUT_MS) {
                supabaseNotificationApi.sendChatNotification(
                    request = ChatNotificationRequest(
                        recipientId = recipientId,
                        senderId = currentUserId,
                        senderName = senderName,
                        message = message,
                        chatId = "",
                        messageId = messageId,
                        chatType = "REACTION"
                    )
                )
            }

            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Reaction notification sent successfully")
            } else {
                android.util.Log.w(TAG, "Failed to send reaction notification: ${response.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error sending reaction notification: ${e.message}", e)
        }
    }
}
