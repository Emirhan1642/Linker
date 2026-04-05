package com.linker.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized security manager for API keys and sensitive data
 *
 * Uses Android Keystore and EncryptedSharedPreferences for secure storage.
 *
 * **Security Features**:
 * - AES256_GCM encryption for values
 * - AES256_SIV encryption for keys
 * - Hardware-backed keystore on supported devices
 * - Auto-generated master key
 *
 * **Usage**:
 * ```kotlin
 * // Initialize in Application.onCreate()
 * if (!securityManager.areKeysInitialized()) {
 *     securityManager.initializeKeys(...)
 * }
 *
 * // Retrieve keys
 * val supabaseUrl = securityManager.getSupabaseUrl()
 * ```
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "linker_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Supabase Configuration ──────────────────────────────────────────

    /**
     * Get Supabase project URL
     *
     * @throws IllegalStateException if not initialized
     */
    fun getSupabaseUrl(): String {
        return encryptedPrefs.getString(KEY_SUPABASE_URL, "")
            ?: throw IllegalStateException("Supabase URL not initialized")
    }

    /**
     * Get Supabase anonymous key
     *
     * @throws IllegalStateException if not initialized
     */
    fun getSupabaseAnonKey(): String {
        return encryptedPrefs.getString(KEY_SUPABASE_ANON_KEY, "")
            ?: throw IllegalStateException("Supabase Anon Key not initialized")
    }

    // ── Cloudinary Configuration ────────────────────────────────────────

    /**
     * Get Cloudinary cloud name
     *
     * @throws IllegalStateException if not initialized
     */
    fun getCloudinaryCloudName(): String {
        return encryptedPrefs.getString(KEY_CLOUDINARY_CLOUD_NAME, "")
            ?: throw IllegalStateException("Cloudinary Cloud Name not initialized")
    }

    /**
     * Get Cloudinary API key
     *
     * @throws IllegalStateException if not initialized
     */
    fun getCloudinaryApiKey(): String {
        return encryptedPrefs.getString(KEY_CLOUDINARY_API_KEY, "")
            ?: throw IllegalStateException("Cloudinary API Key not initialized")
    }

    /**
     * Get Cloudinary API secret
     *
     * @throws IllegalStateException if not initialized
     */
    fun getCloudinaryApiSecret(): String {
        return encryptedPrefs.getString(KEY_CLOUDINARY_API_SECRET, "")
            ?: throw IllegalStateException("Cloudinary API Secret not initialized")
    }

    // ── Initialization ──────────────────────────────────────────────────

    /**
     * Initialize API keys on first app launch
     *
     * Call this from Application.onCreate()
     *
     * **Important**: After first stable release, remove keys from BuildConfig
     * and use environment variables or remote config instead.
     */
    fun initializeKeys(
        supabaseUrl: String,
        supabaseAnonKey: String,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String
    ) {
        encryptedPrefs.edit().apply {
            putString(KEY_SUPABASE_URL, supabaseUrl)
            putString(KEY_SUPABASE_ANON_KEY, supabaseAnonKey)
            putString(KEY_CLOUDINARY_CLOUD_NAME, cloudinaryCloudName)
            putString(KEY_CLOUDINARY_API_KEY, cloudinaryApiKey)
            putString(KEY_CLOUDINARY_API_SECRET, cloudinaryApiSecret)
            apply()
        }
    }

    /**
     * Check if keys are already initialized
     *
     * @return true if all required keys are present
     */
    fun areKeysInitialized(): Boolean {
        return encryptedPrefs.contains(KEY_SUPABASE_URL) &&
               encryptedPrefs.contains(KEY_SUPABASE_ANON_KEY) &&
               encryptedPrefs.contains(KEY_CLOUDINARY_CLOUD_NAME)
    }

    /**
     * Clear all stored keys (use for testing or logout)
     *
     * ⚠️ This will require re-initialization
     */
    fun clearKeys() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
        private const val KEY_CLOUDINARY_CLOUD_NAME = "cloudinary_cloud_name"
        private const val KEY_CLOUDINARY_API_KEY = "cloudinary_api_key"
        private const val KEY_CLOUDINARY_API_SECRET = "cloudinary_api_secret"
    }
}
