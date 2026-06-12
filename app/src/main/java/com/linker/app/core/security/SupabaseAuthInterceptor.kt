package com.linker.app.core.security

import com.google.firebase.auth.FirebaseAuth
import com.linker.app.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

class SupabaseAuthInterceptor @Inject constructor(
    private val securityManager: SecurityManager,
    private val auth: FirebaseAuth
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val anonKey = when (val result = securityManager.getSupabaseAnonKey()) {
            is ConfigResult.Success -> result.value
            else -> BuildConfig.SUPABASE_ANON_KEY
        }

        if (anonKey.isNotEmpty()) {
            builder.header("apikey", anonKey)
            
            // Default to anon key for Authorization
            var authHeader = "Bearer $anonKey"
            
            // Edge Functions expect Supabase JWT. Since we use Firebase Auth, sending Firebase JWT 
            // causes UNAUTHORIZED_ASYMMETRIC_JWT. We will just send the Anon Key for Edge Functions.
            builder.header("Authorization", authHeader)
        }

        return chain.proceed(builder.build())
    }
}
