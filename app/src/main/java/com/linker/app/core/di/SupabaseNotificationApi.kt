package com.linker.app.core.di

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Supabase Edge Function API for sending push notifications.
 * 
 * **Authentication:**
 * Authorization and API key headers are automatically added by
 * SupabaseAuthInterceptor configured in NetworkModule.
 * Do not pass authentication headers manually.
 * 
 * **Error Handling:**
 * All methods return Response<T> for manual error handling.
 * Check response.isSuccessful before accessing response.body().
 * 
 * **Rate Limiting:**
 * Supabase Edge Functions have rate limits. Implement exponential
 * backoff for failed requests.
 * 
 * @see com.linker.app.core.di.NetworkModule
 */
interface SupabaseNotificationApi {

    /**
     * Send a generic push notification to a user.
     * 
     * @param request Notification details including user ID, title, body, and data
     * @return Response with success status and optional message ID
     * @throws IOException if network request fails
     */
    @POST("functions/v1/send-notification")
    suspend fun sendPushNotification(
        @Body request: PushNotificationRequest
    ): Response<PushNotificationResponse>

    /**
     * Send a chat message notification to a recipient.
     * 
     * Optimized for chat notifications with sender information,
     * message preview, and chat context.
     * 
     * @param request Chat notification details
     * @return Response with success status
     * @throws IOException if network request fails
     */
    @POST("functions/v1/send-chat-notification")
    suspend fun sendChatNotification(
        @Body request: ChatNotificationRequest
    ): Response<PushNotificationResponse>

    /**
     * Register a device's FCM token for push notifications.
     * 
     * Should be called:
     * - On app first launch
     * - When FCM token is refreshed
     * - After user login
     * 
     * @param request Token registration details
     * @return Response with success status
     * @throws IOException if network request fails
     */
    @POST("functions/v1/register-push-token")
    suspend fun registerPushToken(
        @Body request: RegisterPushTokenRequest
    ): Response<PushNotificationResponse>

    /**
     * Delete a chat notification (e.g., when message is read).
     * 
     * Removes notification from system tray and marks as read
     * on the server.
     * 
     * @param request Notification deletion details
     * @return Response with success status
     * @throws IOException if network request fails
     */
    @POST("functions/v1/delete-chat-notification")
    suspend fun deleteChatNotification(
        @Body request: DeleteChatNotificationRequest
    ): Response<PushNotificationResponse>
}

@Serializable
data class PushNotificationRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("data") val data: Map<String, String> = emptyMap()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(userId.length <= 100) { "User ID too long (max 100 characters)" }
        require(title.isNotBlank()) { "Notification title cannot be blank" }
        require(title.length <= 100) { "Notification title too long (max 100 characters)" }
        require(body.isNotBlank()) { "Notification body cannot be blank" }
        require(body.length <= 500) { "Notification body too long (max 500 characters)" }
        require(data.size <= 20) { "Too many data fields (max 20)" }
        data.forEach { (key, value) ->
            require(key.length <= 50) { "Data key too long: $key (max 50 characters)" }
            require(value.length <= 200) { "Data value too long for key $key (max 200 characters)" }
        }
    }
}

@Serializable
data class ChatNotificationRequest(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("message") val message: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("chat_type") val chatType: String? = null,
    @SerialName("chat_name") val chatName: String? = null
) {
    init {
        require(recipientId.isNotBlank()) { "Recipient ID cannot be blank" }
        require(senderId.isNotBlank()) { "Sender ID cannot be blank" }
        require(senderName.isNotBlank()) { "Sender name cannot be blank" }
        require(senderName.length <= 100) { "Sender name too long (max 100 characters)" }
        require(message.isNotBlank()) { "Message cannot be blank" }
        require(message.length <= 200) { "Message preview too long (max 200 characters)" }
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        
        chatType?.let { type ->
            require(type in listOf("PRIVATE", "GROUP")) {
                "Chat type must be PRIVATE or GROUP, got: $type"
            }
        }
        
        chatName?.let { name ->
            require(name.isNotBlank()) { "Chat name cannot be blank if provided" }
            require(name.length <= 100) { "Chat name too long (max 100 characters)" }
        }
    }
}

@Serializable
data class RegisterPushTokenRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("platform") val platform: String? = "android"
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(fcmToken.isNotBlank()) { "FCM token cannot be blank" }
        require(fcmToken.length > 50) { "Invalid FCM token format (too short)" }
        require(fcmToken.length <= 500) { "Invalid FCM token format (too long)" }
        
        platform?.let { p ->
            require(p in listOf("android", "ios", "web")) {
                "Platform must be android, ios, or web, got: $p"
            }
        }
    }
}

@Serializable
data class DeleteChatNotificationRequest(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("chat_id") val chatId: String? = null
) {
    init {
        require(recipientId.isNotBlank()) { "Recipient ID cannot be blank" }
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        
        chatId?.let { id ->
            require(id.isNotBlank()) { "Chat ID cannot be blank if provided" }
        }
    }
}

@Serializable
data class PushNotificationResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String? = null,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("error_code") val errorCode: String? = null
) {
    /**
     * Check if the notification was sent successfully.
     */
    fun isSuccess(): Boolean = success && errorCode == null
    
    /**
     * Get error message if request failed.
     */
    fun getErrorMessage(): String? {
        return if (!success) {
            message ?: "Unknown error occurred"
        } else {
            null
        }
    }
    
    /**
     * Get the notification message ID if available.
     * Only available for successful requests.
     */
    fun getNotificationId(): String? {
        return if (success) messageId else null
    }
    
    /**
     * Check if error is retryable.
     */
    fun isRetryable(): Boolean {
        return errorCode in listOf(
            "RATE_LIMIT_EXCEEDED",
            "TEMPORARY_ERROR",
            "NETWORK_ERROR"
        )
    }
}

/**
 * Specific error types that can be returned by Supabase Edge Functions.
 */
enum class NotificationErrorType {
    INVALID_TOKEN,
    USER_NOT_FOUND,
    RATE_LIMIT_EXCEEDED,
    INVALID_REQUEST,
    SERVER_ERROR,
    NETWORK_ERROR,
    UNKNOWN
}

/**
 * Detailed error response from notification API.
 */
@Serializable
data class NotificationError(
    @SerialName("error_type") val errorType: String,
    @SerialName("error_message") val errorMessage: String,
    @SerialName("retry_after") val retryAfter: Long? = null,
    @SerialName("details") val details: Map<String, String>? = null
) {
    fun toErrorType(): NotificationErrorType {
        return when (errorType.uppercase()) {
            "INVALID_TOKEN" -> NotificationErrorType.INVALID_TOKEN
            "USER_NOT_FOUND" -> NotificationErrorType.USER_NOT_FOUND
            "RATE_LIMIT_EXCEEDED" -> NotificationErrorType.RATE_LIMIT_EXCEEDED
            "INVALID_REQUEST" -> NotificationErrorType.INVALID_REQUEST
            "SERVER_ERROR" -> NotificationErrorType.SERVER_ERROR
            "NETWORK_ERROR" -> NotificationErrorType.NETWORK_ERROR
            else -> NotificationErrorType.UNKNOWN
        }
    }
}
