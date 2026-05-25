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
        
        // ✅ SECURITY: Encryption interceptor for sensitive data
        // Note: Only encrypts specific sensitive fields, not entire payload
        // builder.addInterceptor(EncryptionInterceptor(securityManager))

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
        securityManager: SecurityManager
    ): Retrofit {
        // ✅ SECURITY FIX: Get URL from encrypted storage instead of BuildConfig
        val baseUrl = try {
            when (val result = securityManager.getSupabaseUrl()) {
                is com.linker.app.core.security.ConfigResult.Success -> result.value
                is com.linker.app.core.security.ConfigResult.Error -> throw IllegalStateException(result.message, result.cause)
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkModule", "Failed to get Supabase URL from SecurityManager", e)
            throw IllegalStateException("Supabase URL not initialized in SecurityManager", e)
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
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
        // ✅ SECURITY FIX: Get cloud name from encrypted storage
        val cloudName = try {
            when (val result = securityManager.getCloudinaryCloudName()) {
                is com.linker.app.core.security.ConfigResult.Success -> result.value
                is com.linker.app.core.security.ConfigResult.Error -> throw IllegalStateException(result.message, result.cause)
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkModule", "Failed to get Cloudinary cloud name from SecurityManager", e)
            throw IllegalStateException("Cloudinary cloud name not initialized in SecurityManager", e)
        }

        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/v1_1/${cloudName}/")
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
        var response: Response? = null
        var exception: IOException? = null
        
        for (attempt in 0..maxRetries) {
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
                if (attempt < maxRetries) {
                    val delayMs = calculateBackoffDelay(attempt)
                    android.util.Log.w(
                        "RetryInterceptor",
                        "Request failed with ${response.code}, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)"
                    )
                    Thread.sleep(delayMs)
                }
                
            } catch (e: IOException) {
                exception = e
                
                // Network error - retry with backoff
                if (attempt < maxRetries) {
                    val delayMs = calculateBackoffDelay(attempt)
                    android.util.Log.w(
                        "RetryInterceptor",
                        "Network error: ${e.message}, retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)"
                    )
                    Thread.sleep(delayMs)
                } else {
                    // Max retries reached, throw exception
                    throw e
                }
            }
        }
        
        // Return last response or throw exception
        return response ?: throw (exception ?: IOException("Unknown error"))
    }
    
    /**
     * Check if HTTP status code is retryable
     * 
     * Retryable: 5xx server errors
     * Not retryable: 4xx client errors, 2xx success, 3xx redirects
     */
    private fun isRetryableStatusCode(code: Int): Boolean {
        return code in 500..599
    }
    
    /**
     * Calculate exponential backoff delay
     * 
     * Delay = 1000ms * 2^attempt
     * - Attempt 0: 1000ms (1s)
     * - Attempt 1: 2000ms (2s)
     * - Attempt 2: 4000ms (4s)
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        return 1000L * (1 shl attempt) // 2^attempt seconds
    }
}

/**
 * Encryption Interceptor for Sensitive Data
 * 
 * Encrypts sensitive fields in request/response bodies.
 * 
 * SECURITY:
 * - Only encrypts marked sensitive fields
 * - Uses AES-256-GCM encryption
 * - Adds encryption metadata to headers
 * 
 * USAGE:
 * Mark fields as sensitive in your data classes:
 * ```kotlin
 * data class UserData(
 *     val id: String,
 *     @Sensitive val password: String,
 *     @Sensitive val apiKey: String
 * )
 * ```
 * 
 * NOTE: Currently disabled. Enable by uncommenting in provideOkHttpClient()
 * and implementing field-level encryption logic.
 */
private class EncryptionInterceptor(
    private val securityManager: SecurityManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // TODO: Implement request body encryption for sensitive fields
        // 1. Parse request body JSON
        // 2. Identify @Sensitive annotated fields
        // 3. Encrypt those fields
        // 4. Rebuild request with encrypted data
        // 5. Add encryption metadata to headers
        
        val response = chain.proceed(request)
        
        // TODO: Implement response body decryption
        // 1. Check for encryption metadata in headers
        // 2. Parse response body JSON
        // 3. Decrypt encrypted fields
        // 4. Rebuild response with decrypted data
        
        return response
    }
    
    /**
     * Encrypt sensitive field value
     * 
     * @param value Plain text value
     * @return Encrypted value (base64 encoded)
     */
    private fun encryptField(value: String): String {
        // TODO: Implement AES-256-GCM encryption
        // 1. Generate random IV
        // 2. Encrypt with AES-256-GCM
        // 3. Combine IV + ciphertext
        // 4. Base64 encode
        return value // Placeholder
    }
    
    /**
     * Decrypt sensitive field value
     * 
     * @param encryptedValue Encrypted value (base64 encoded)
     * @return Plain text value
     */
    private fun decryptField(encryptedValue: String): String {
        // TODO: Implement AES-256-GCM decryption
        // 1. Base64 decode
        // 2. Extract IV and ciphertext
        // 3. Decrypt with AES-256-GCM
        // 4. Return plain text
        return encryptedValue // Placeholder
    }
}
