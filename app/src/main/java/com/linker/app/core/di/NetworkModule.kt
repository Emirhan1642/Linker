package com.linker.app.core.di

import com.linker.app.BuildConfig
import com.linker.app.core.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Network Module
 *
 * Provides Retrofit, OkHttp, and API services
 * ✅ FIXED: Uses SecurityManager for dynamic API keys instead of BuildConfig
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

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

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
        // ✅ SECURITY: Get URL from encrypted storage instead of BuildConfig
        val baseUrl = try {
            securityManager.getSupabaseUrl()
        } catch (e: IllegalStateException) {
            // Fallback to BuildConfig during migration period
            BuildConfig.SUPABASE_URL
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
        // ✅ SECURITY: Get cloud name from encrypted storage
        val cloudName = try {
            securityManager.getCloudinaryCloudName()
        } catch (e: IllegalStateException) {
            BuildConfig.CLOUDINARY_CLOUD_NAME
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
