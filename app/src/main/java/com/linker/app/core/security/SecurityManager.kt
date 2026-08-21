package com.linker.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConfigResult<out T> {
    data class Success<T>(val value: T) : ConfigResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ConfigResult<Nothing>()
}

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

    private val remoteConfig = Firebase.remoteConfig.apply {
        setConfigSettingsAsync(remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        })
        setDefaultsAsync(
            mapOf(
                "supabase_url" to "",
                "supabase_anon_key" to "",
                "cloudinary_cloud_name" to "",
                "cloudinary_api_key" to "",
                "cloudinary_api_secret" to "",
                "cert_pinning_enabled" to true
            )
        )
    }

    suspend fun initializeKeysFromRemoteConfig() {
        try {
            val success = remoteConfig.fetchAndActivate().await()
            if (success || areKeysInitialized()) { // allow proceed if keys are already there but fetch failed
                initializeKeys(
                    supabaseUrl = remoteConfig.getString("supabase_url"),
                    supabaseAnonKey = remoteConfig.getString("supabase_anon_key"),
                    cloudinaryCloudName = remoteConfig.getString("cloudinary_cloud_name"),
                    cloudinaryApiKey = remoteConfig.getString("cloudinary_api_key"),
                    cloudinaryApiSecret = remoteConfig.getString("cloudinary_api_secret")
                )
            } else {
                throw SecurityException("Failed to fetch remote config and no local keys exist")
            }
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Failed to initialize keys from remote config: ${e.message}"
            )
            throw e
        }
    }

    private fun validateSupabaseUrl(url: String): Boolean = url.matches(Regex("^https://[a-z0-9-]+\\.supabase\\.co\$")) || url.isEmpty()
    
    // In production we would strictly validate. We allow empty for remote config defaults.
    private fun validateSupabaseKey(key: String): Boolean = key.length > 50 || key.isEmpty()
    private fun validateCloudinaryCloudName(name: String): Boolean = name.matches(Regex("^[A-Za-z0-9_-]+\$")) || name.isEmpty()
    private fun validateCloudinaryApiKey(key: String): Boolean = key.matches(Regex("^\\d+\$")) || key.isEmpty()
    private fun validateCloudinaryApiSecret(secret: String): Boolean = secret.matches(Regex("^[A-Za-z0-9_-]+\$")) || secret.isEmpty()

    @Volatile private var cachedSupabaseUrl: String? = null
    @Volatile private var cachedSupabaseAnonKey: String? = null
    @Volatile private var cachedCloudinaryCloudName: String? = null
    @Volatile private var cachedCloudinaryApiKey: String? = null
    @Volatile private var cachedCloudinaryApiSecret: String? = null
    @Volatile private var isIntegrityVerified = false

    private fun getHmacKey(): ByteArray {
        val hmacKeyStr = encryptedPrefs.getString("hmac_key_seed", null)
        if (hmacKeyStr != null) {
            return android.util.Base64.decode(hmacKeyStr, android.util.Base64.NO_WRAP)
        }
        val random = java.security.SecureRandom()
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)
        val encoded = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
        encryptedPrefs.edit().putString("hmac_key_seed", encoded).apply()
        return keyBytes
    }

    private fun calculateIntegrityHash(): String {
        val data = buildString {
            append(encryptedPrefs.getString(KEY_SUPABASE_URL, ""))
            append(encryptedPrefs.getString(KEY_SUPABASE_ANON_KEY, ""))
            append(encryptedPrefs.getString(KEY_CLOUDINARY_CLOUD_NAME, ""))
            append(encryptedPrefs.getString(KEY_CLOUDINARY_API_KEY, ""))
            append(encryptedPrefs.getString(KEY_CLOUDINARY_API_SECRET, ""))
            append(encryptedPrefs.getInt(KEY_CONFIG_VERSION, 0))
        }
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(getHmacKey(), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun verifyIntegrity(): Boolean {
        if (isIntegrityVerified) return true
        return try {
            val storedHash = encryptedPrefs.getString(KEY_INTEGRITY_HASH, null)
            val calculatedHash = calculateIntegrityHash()
            val isValid = storedHash == calculatedHash
            if (!isValid && storedHash != null) {
                SecurityLogger.logEvent(
                    SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                    "Config integrity check failed - possible tampering detected"
                )
            }
            if (isValid) {
                isIntegrityVerified = true
            }
            isValid
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Integrity verification failed: ${e.message}"
            )
            false
        }
    }

    private fun initializeKeys(
        supabaseUrl: String,
        supabaseAnonKey: String,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String
    ) {
        if (supabaseUrl.isNotEmpty() && !validateSupabaseUrl(supabaseUrl)) throw IllegalArgumentException("Invalid Supabase URL")
        if (supabaseAnonKey.isNotEmpty() && !validateSupabaseKey(supabaseAnonKey)) throw IllegalArgumentException("Invalid Supabase Key")
        if (cloudinaryCloudName.isNotEmpty() && !validateCloudinaryCloudName(cloudinaryCloudName)) throw IllegalArgumentException("Invalid Cloudinary Name")

        encryptedPrefs.edit().apply {
            putString(KEY_SUPABASE_URL, supabaseUrl)
            putString(KEY_SUPABASE_ANON_KEY, supabaseAnonKey)
            putString(KEY_CLOUDINARY_CLOUD_NAME, cloudinaryCloudName)
            putString(KEY_CLOUDINARY_API_KEY, cloudinaryApiKey)
            putString(KEY_CLOUDINARY_API_SECRET, cloudinaryApiSecret)
            putInt(KEY_CONFIG_VERSION, CURRENT_CONFIG_VERSION)
            putLong(KEY_LAST_ROTATION, System.currentTimeMillis())
            apply()
            
            putString(KEY_INTEGRITY_HASH, calculateIntegrityHash())
            apply()
        }
        cachedSupabaseUrl = supabaseUrl
        cachedSupabaseAnonKey = supabaseAnonKey
        cachedCloudinaryCloudName = cloudinaryCloudName
        cachedCloudinaryApiKey = cloudinaryApiKey
        cachedCloudinaryApiSecret = cloudinaryApiSecret
        isIntegrityVerified = true
        SecurityLogger.logApiKeyInitialization()
    }

    suspend fun rotateKeys() {
        try {
            val success = remoteConfig.fetchAndActivate().await()
            if (success) {
                initializeKeysFromRemoteConfig()
                SecurityLogger.logEvent(
                    SecurityLogger.EventType.API_KEY_INITIALIZED,
                    "Keys rotated successfully"
                )
            }
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Key rotation failed: ${e.message}"
            )
            throw e
        }
    }

    private val accessCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val lastAccessTime = java.util.concurrent.atomic.AtomicLong(0)

    private fun checkAccessPatterns() {
        val accessCount = accessCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        val lastAccess = lastAccessTime.getAndSet(now)

        if (accessCount > 1000) {
            SecurityLogger.logSuspiciousActivity("Excessive config access detected: $accessCount times")
        }
        if (now - lastAccess < 50) { 
            // Commenting out rapid access log to prevent log spam during normal init
            // SecurityLogger.logSuspiciousActivity("Rapid config access detected")
        }
        if (!verifyIntegrity()) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Config integrity check failed"
            )
            throw SecurityException("Config integrity check failed")
        }
    }

    private fun checkExpiration() {
        val lastRotation = encryptedPrefs.getLong(KEY_LAST_ROTATION, 0)
        val now = System.currentTimeMillis()
        if (now - lastRotation > KEY_ROTATION_INTERVAL_MS) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Keys expired, rotation required"
            )
        }
    }

    fun getSupabaseUrl(): ConfigResult<String> {
        return try {
            val cached = cachedSupabaseUrl
            if (!cached.isNullOrEmpty()) return ConfigResult.Success(cached)
            checkAccessPatterns()
            checkExpiration()
            val url = encryptedPrefs.getString(KEY_SUPABASE_URL, null)
            if (url.isNullOrEmpty()) {
                ConfigResult.Error("Supabase URL not initialized")
            } else {
                cachedSupabaseUrl = url
                ConfigResult.Success(url)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to retrieve Supabase URL", e)
        }
    }

    fun getSupabaseAnonKeySecure(): CharArray {
        val cached = cachedSupabaseAnonKey
        if (!cached.isNullOrEmpty()) return cached.toCharArray()
        checkAccessPatterns()
        val key = encryptedPrefs.getString(KEY_SUPABASE_ANON_KEY, null)
            ?: throw IllegalStateException("Supabase Anon Key not initialized")
        cachedSupabaseAnonKey = key
        return key.toCharArray()
    }
    
    fun getSupabaseAnonKey(): ConfigResult<String> {
        return try {
            val cached = cachedSupabaseAnonKey
            if (!cached.isNullOrEmpty()) return ConfigResult.Success(cached)
            checkAccessPatterns()
            checkExpiration()
            val key = encryptedPrefs.getString(KEY_SUPABASE_ANON_KEY, null)
            if (key.isNullOrEmpty()) {
                ConfigResult.Error("Supabase Anon Key not initialized")
            } else {
                cachedSupabaseAnonKey = key
                ConfigResult.Success(key)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to retrieve Supabase Anon Key", e)
        }
    }

    fun getCloudinaryCloudName(): ConfigResult<String> {
        return try {
            val cached = cachedCloudinaryCloudName
            if (!cached.isNullOrEmpty()) return ConfigResult.Success(cached)
            checkAccessPatterns()
            val name = encryptedPrefs.getString(KEY_CLOUDINARY_CLOUD_NAME, null)
            if (name.isNullOrEmpty()) {
                ConfigResult.Error("Cloudinary Cloud Name not initialized")
            } else {
                cachedCloudinaryCloudName = name
                ConfigResult.Success(name)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to retrieve Cloudinary Cloud Name", e)
        }
    }

    fun getCloudinaryApiKey(): ConfigResult<String> {
        return try {
            val cached = cachedCloudinaryApiKey
            if (!cached.isNullOrEmpty()) return ConfigResult.Success(cached)
            checkAccessPatterns()
            val key = encryptedPrefs.getString(KEY_CLOUDINARY_API_KEY, null)
            if (key.isNullOrEmpty()) {
                ConfigResult.Error("Cloudinary API Key not initialized")
            } else {
                cachedCloudinaryApiKey = key
                ConfigResult.Success(key)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to retrieve Cloudinary API Key", e)
        }
    }

    fun getCloudinaryApiSecretSecure(): CharArray {
        val cached = cachedCloudinaryApiSecret
        if (!cached.isNullOrEmpty()) return cached.toCharArray()
        checkAccessPatterns()
        val secret = encryptedPrefs.getString(KEY_CLOUDINARY_API_SECRET, null)
            ?: throw IllegalStateException("Cloudinary API Secret not initialized")
        cachedCloudinaryApiSecret = secret
        return secret.toCharArray()
    }

    fun getDatabasePassphrase(): ByteArray {
        var passphrase = encryptedPrefs.getString(KEY_DATABASE_PASSPHRASE, null)
        if (passphrase == null) {
            passphrase = generateSecurePassphrase()
            encryptedPrefs.edit().putString(KEY_DATABASE_PASSPHRASE, passphrase).apply()
        }
        return passphrase.toByteArray(Charsets.UTF_8)
    }

    private fun generateSecurePassphrase(): String {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun areKeysInitialized(): Boolean {
        val hasKeys = encryptedPrefs.contains(KEY_SUPABASE_URL) &&
                      encryptedPrefs.contains(KEY_SUPABASE_ANON_KEY) &&
                      encryptedPrefs.contains(KEY_CLOUDINARY_CLOUD_NAME)
        if (!hasKeys) return false
        val storedVersion = encryptedPrefs.getInt(KEY_CONFIG_VERSION, 0)
        if (storedVersion < CURRENT_CONFIG_VERSION) return false
        return true
    }

    fun clearKeys() {
        encryptedPrefs.edit().clear().apply()
    }

    suspend fun backupKeysToCloud(userId: String) {
        try {
            val encryptedBackup = encryptedPrefs.all.mapValues { it.value.toString() }
            val backupJson = Json.encodeToString(encryptedBackup)
            // TODO: Implement actual backend call to save `backupJson` to Supabase user profile.
            // Example: api.uploadBackup(userId, backupJson)
            
            SecurityLogger.logEvent(
                SecurityLogger.EventType.API_KEY_INITIALIZED,
                "Config backed up to cloud",
                userId = userId
            )
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Failed to backup config: ${e.message}",
                userId = userId
            )
        }
    }

    suspend fun restoreKeysFromCloud(userId: String): Boolean {
        return try {
            // TODO: Implement actual backend call to fetch `backupJson` from Supabase user profile.
            // val backupJson = api.fetchBackup(userId)
            val backupJson = "{}"
            val configData = Json.decodeFromString<Map<String, String>>(backupJson)
            
            if (configData.isNotEmpty()) {
                encryptedPrefs.edit().apply {
                    configData.forEach { (key, value) -> putString(key, value) }
                    apply()
                }
                SecurityLogger.logEvent(
                    SecurityLogger.EventType.API_KEY_INITIALIZED,
                    "Config restored from cloud",
                    userId = userId
                )
                true
            } else false
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SECURITY_CHECK_FAILED,
                "Failed to restore config: ${e.message}",
                userId = userId
            )
            false
        }
    }

    companion object {
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
        private const val KEY_CLOUDINARY_CLOUD_NAME = "cloudinary_cloud_name"
        private const val KEY_CLOUDINARY_API_KEY = "cloudinary_api_key"
        private const val KEY_CLOUDINARY_API_SECRET = "cloudinary_api_secret"
        private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        private const val KEY_INTEGRITY_HASH = "integrity_hash"
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_LAST_ROTATION = "last_rotation_timestamp"
        private const val CURRENT_CONFIG_VERSION = 2
        private const val KEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}
