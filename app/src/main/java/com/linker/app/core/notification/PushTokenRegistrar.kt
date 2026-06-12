package com.linker.app.core.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.linker.app.BuildConfig
import com.linker.app.core.di.RegisterPushTokenRequest
import com.linker.app.core.di.SupabaseNotificationApi
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRegistrar @Inject constructor(
    private val auth: FirebaseAuth,
    private val supabaseNotificationApi: SupabaseNotificationApi
) {
    suspend fun registerCurrentToken() {
        val userId = auth.currentUser?.uid ?: return
        
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            NotificationLogger.w("Failed to fetch FCM token: ${e.message}")
            return
        }
        registerToken(userId, token)
    }

    suspend fun registerToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        registerToken(userId, token)
    }

    private suspend fun registerToken(userId: String, token: String) {
        if (BuildConfig.DEBUG) {
            NotificationLogger.d("Registering token for user: ${userId.take(8)}...")
        }
        
        try {
            val response = supabaseNotificationApi.registerPushToken(
                request = RegisterPushTokenRequest(
                    userId = userId,
                    fcmToken = token,
                    platform = "android"
                )
            )
            if (response.isSuccessful) {
                NotificationLogger.d("Token registered successfully")
            } else {
                val errorBody = response.errorBody()?.string()
                NotificationLogger.w("Token registration failed: ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            NotificationLogger.e("Failed to register push token", e)
        }
    }
}
