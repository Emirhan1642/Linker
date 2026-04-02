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
    private fun authHeader(): String = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"

    suspend fun registerCurrentToken() {
        val userId = auth.currentUser?.uid ?: return
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to fetch FCM token: ${e.message}")
            return
        }
        registerToken(userId, token)
    }

    suspend fun registerToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        registerToken(userId, token)
    }

    private suspend fun registerToken(userId: String, token: String) {
        try {
            supabaseNotificationApi.registerPushToken(
                auth = authHeader(),
                request = RegisterPushTokenRequest(
                    userId = userId,
                    fcmToken = token,
                    platform = "android"
                )
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to register push token: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PushTokenRegistrar"
    }
}
