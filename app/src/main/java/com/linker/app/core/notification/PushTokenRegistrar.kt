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
    private fun resolveSupabaseKey(): String {
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { BuildConfig.SUPABASE_ANON_KEY }
    }

    private fun authHeader(): String = "Bearer ${resolveSupabaseKey()}"
    private fun apiKey(): String = resolveSupabaseKey()

    private fun logAnonKey() {
        val key = resolveSupabaseKey()
        val masked = if (key.length >= 8) {
            "${key.take(4)}...${key.takeLast(4)}"
        } else {
            "len=${key.length}"
        }
        val source = if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) "publishable" else "anon"
        android.util.Log.d(TAG, "SUPABASE_KEY[$source]=$masked (len=${key.length})")
    }

    suspend fun registerCurrentToken() {
        val userId = auth.currentUser?.uid ?: return
        android.util.Log.d(TAG, "registerCurrentToken: userId=$userId")
        logAnonKey()
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to fetch FCM token: ${e.message}")
            return
        }
        android.util.Log.d(TAG, "registerCurrentToken: token fetched")
        registerToken(userId, token)
    }

    suspend fun registerToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        registerToken(userId, token)
    }

    private suspend fun registerToken(userId: String, token: String) {
        try {
            android.util.Log.d(TAG, "Attempting to register token for userId=$userId, token=${token.take(20)}...")
            val response = supabaseNotificationApi.registerPushToken(
                auth = authHeader(),
                apiKey = apiKey(),
                request = RegisterPushTokenRequest(
                    userId = userId,
                    fcmToken = token,
                    platform = "android"
                )
            )
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "registerToken: success for $userId")
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.w(TAG, "registerToken: failed ${response.code()} - $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to register push token: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PushTokenRegistrar"
    }
}
