package com.linker.app.core.di

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Supabase Edge Function API for sending push notifications.
 * Calls a Supabase Edge Function that forwards to FCM.
 */
interface SupabaseNotificationApi {

    @POST("functions/v1/send-notification")
    suspend fun sendPushNotification(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String,
        @Body request: PushNotificationRequest
    ): Response<PushNotificationResponse>

    @POST("functions/v1/send-chat-notification")
    suspend fun sendChatNotification(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String,
        @Body request: ChatNotificationRequest
    ): Response<PushNotificationResponse>

    @POST("functions/v1/register-push-token")
    suspend fun registerPushToken(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String,
        @Body request: RegisterPushTokenRequest
    ): Response<PushNotificationResponse>
}

@Serializable
data class PushNotificationRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String,
    @SerialName("data") val data: Map<String, String> = emptyMap()
)

@Serializable
data class ChatNotificationRequest(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("message") val message: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("message_id") val messageId: String,
    /** Edge function FCM `data.chatType` olarak iletmeli: PRIVATE | GROUP */
    @SerialName("chat_type") val chatType: String? = null
)

@Serializable
data class RegisterPushTokenRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("platform") val platform: String? = null
)

@Serializable
data class PushNotificationResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String? = null,
    @SerialName("message_id") val messageId: String? = null
)
