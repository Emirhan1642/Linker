package com.linker.app.core.di

import com.linker.app.BuildConfig
import com.linker.app.core.config.OfflineMessagingConfig
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.security.withCertificatePinning
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Network Module
 *
 * Provides Retrofit, OkHttp, and API services with security enhancements.
 * 
 * **Security Features:**
 * - Certificate pinning for Supabase and Cloudinary
 * - API keys from encrypted storage (SecurityManager)
 * - Logging only in debug builds
 * - Automatic retry with exponential backoff
 * 
 * **Performance:**
 * - Connection pooling
 * - Automatic retry on network failures
 * - Configurable timeouts
 * 
 * @see com.linker.app.core.security.SecurityManager
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class SupabaseRetrofit

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class CloudinaryRetrofit

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Provides OkHttpClient with security and reliability features
     * 
     * SECURITY:
     * - Certificate pinning for Supabase and Cloudinary
     * - Logging only in debug builds
     * 
     * RELIABILITY:
     * - Automatic retry with exponential backoff
     * - Configurable timeouts from OfflineMessagingConfig
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificatePinningInterceptor: com.linker.app.core.security.CertificatePinningInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(OfflineMessagingConfig.NETWORK_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(OfflineMessagingConfig.NETWORK_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(OfflineMessagingConfig.NETWORK_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        // ✅ SECURITY: Certificate pinning for Supabase, Cloudinary and Google APIs
        builder.withCertificatePinning(certificatePinningInterceptor)

        // ✅ RELIABILITY: Retry interceptor with exponential backoff
        builder.addInterceptor(RetryInterceptor(maxRetries = 3))

        // ✅ FIX: Only add logging interceptor in debug builds
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @SupabaseRetrofit
    fun provideSupabaseRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        securityManager: SecurityManager,
        supabaseAuthInterceptor: com.linker.app.core.security.SupabaseAuthInterceptor
    ): Retrofit {
        // Priority 1: BuildConfig (compile-time, from local.properties) — always available
        // Priority 2: SecurityManager encrypted prefs — populated after first Remote Config fetch
        // Priority 3: Fallback safe URL to ensure offline-first initialization never crashes Hilt
        val rawBaseUrl = when {
            BuildConfig.SUPABASE_URL.isNotEmpty() -> BuildConfig.SUPABASE_URL
            else -> when (val result = securityManager.getSupabaseUrl()) {
                is com.linker.app.core.security.ConfigResult.Success -> result.value
                is com.linker.app.core.security.ConfigResult.Error -> {
                    android.util.Log.w("NetworkModule", "Supabase URL unavailable: ${result.message}. Using offline fallback.")
                    "https://offline-placeholder.supabase.co"
                }
            }
        }

        // Retrofit strictly requires base URLs to be valid HTTP/HTTPS and end with a trailing slash ('/')
        val safeBaseUrl = if (rawBaseUrl.startsWith("http://") || rawBaseUrl.startsWith("https://")) {
            if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"
        } else {
            "https://offline-placeholder.supabase.co/"
        }

        val clientWithAuth = okHttpClient.newBuilder()
            .addInterceptor(supabaseAuthInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(safeBaseUrl)
            .client(clientWithAuth)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @CloudinaryRetrofit
    fun provideCloudinaryRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        securityManager: SecurityManager
    ): Retrofit {
        // Priority 1: BuildConfig (compile-time, from local.properties) — always available
        // Priority 2: SecurityManager encrypted prefs — populated after first Remote Config fetch
        val cloudName = when {
            BuildConfig.CLOUDINARY_CLOUD_NAME.isNotEmpty() -> BuildConfig.CLOUDINARY_CLOUD_NAME
            else -> when (val result = securityManager.getCloudinaryCloudName()) {
                is com.linker.app.core.security.ConfigResult.Success -> result.value
                is com.linker.app.core.security.ConfigResult.Error -> {
                    android.util.Log.w("NetworkModule", "Cloudinary cloud name unavailable: ${result.message}. Using offline fallback.")
                    "offline-placeholder"
                }
            }
        }

        val safeCloudName = if (cloudName.isBlank()) "offline-placeholder" else cloudName.trim().trim('/')

        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/v1_1/${safeCloudName}/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // ── API Services ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSupabaseNotificationApi(
        @SupabaseRetrofit retrofit: Retrofit
    ): SupabaseNotificationApi {
        return retrofit.create(SupabaseNotificationApi::class.java)
    }
}

/**
 * Retry Interceptor with Exponential Backoff
 * 
 * Automatically retries failed requests with increasing delays.
 * 
 * RETRY STRATEGY:
 * - Retry on network failures (IOException)
 * - Retry on 5xx server errors
 * - Exponential backoff: 1s, 2s, 4s
 * - Max 3 retries by default
 * 
 * NOT RETRIED:
 * - 4xx client errors (bad request, unauthorized, etc.)
 * - Successful responses (2xx, 3xx)
 */
private class RetryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Sadece idempotent (tekrarlanabilir) metodlar için retry yap (POST vb. tekrarlanmaz)
        val isIdempotent = request.method == "GET" || request.method == "HEAD" || request.method == "OPTIONS"
        val actualMaxRetries = if (isIdempotent) maxRetries else 0
        
        var response: Response? = null
        var exception: IOException? = null
        
        for (attempt in 0..actualMaxRetries) {
            try {
                // Clear previous response
                response?.close()
                
                // Execute request
                response = chain.proceed(request)
                
                // Check if retry is needed
                if (response.isSuccessful || !isRetryableStatusCode(response.code)) {
                    return response
                }
                
                // Server error - retry with backoff
                if (attempt < actualMaxRetries) {
                    val delayMs = calculateBackoffDelay(attempt)
                    android.util.Log.w("RetryInterceptor", "Request failed with ${response.code}, retrying in ${delayMs}ms (attempt ${attempt + 1}/$actualMaxRetries)")
                    Thread.sleep(delayMs)
                }
            } catch (e: IOException) {
                exception = e
                
                // Network failure - retry with backoff
                if (attempt < actualMaxRetries) {
                    val delayMs = calculateBackoffDelay(attempt)
                    android.util.Log.w("RetryInterceptor", "Network error: ${e.message}, retrying in ${delayMs}ms (attempt ${attempt + 1}/$actualMaxRetries)")
                    Thread.sleep(delayMs)
                }
            }
        }
        
        // All retries failed
        if (response != null) {
            return response
        }
        
        throw exception ?: IOException("Request failed after $actualMaxRetries retries")
    }
    
    /**
     * Check if HTTP status code is retryable
     * 
     * Retryable:
     * - 500 Internal Server Error
     * - 502 Bad Gateway
     * - 503 Service Unavailable
     * - 504 Gateway Timeout
     * - 408 Request Timeout
     * - 429 Too Many Requests
     */
    private fun isRetryableStatusCode(code: Int): Boolean {
        return code in 500..599 || code == 408 || code == 429
    }
    
    /**
     * Calculate exponential backoff delay
     * 
     * Formula: 1000 * 2^attempt
     * - Attempt 0: 1000ms (1s)
     * - Attempt 1: 2000ms (2s)
     * - Attempt 2: 4000ms (4s)
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val safeAttempt = attempt.coerceIn(0, 30)
        return (1000L * (1L shl safeAttempt)).coerceAtMost(60_000L) // Safe clamped exponential backoff (max 60s)
    }
}
