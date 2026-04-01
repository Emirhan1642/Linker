package com.linker.app.core.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.linker.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Network Module
 * 
 * Provides Retrofit, OkHttp, and API services
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
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    @SupabaseRetrofit
    fun provideSupabaseRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    
    @Provides
    @Singleton
    @CloudinaryRetrofit
    fun provideCloudinaryRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/v1_1/${BuildConfig.CLOUDINARY_CLOUD_NAME}/")
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
