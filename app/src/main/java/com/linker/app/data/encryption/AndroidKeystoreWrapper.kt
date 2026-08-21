package com.linker.app.data.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.linker.app.core.util.SecureLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.signal.libsignal.protocol.IdentityKeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Wrapper for Android Keystore operations
 * 
 * Provides hardware-backed secure storage for:
 * - Signal Protocol identity keys
 * - Registration IDs
 * - Encrypted data
 */
@Singleton
class AndroidKeystoreWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "AndroidKeystoreWrapper"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "signal_keystore_prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * Generate or retrieve AES key from Android Keystore
     */
    private fun getOrCreateSecretKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            return keyStore.getKey(alias, null) as SecretKey
        }
        
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Encrypt data using Android Keystore
     */
    fun encrypt(alias: String, data: ByteArray): Result<ByteArray> {
        return try {
            val secretKey = getOrCreateSecretKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)
            
            Result.success(iv + encrypted)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Encryption failed for alias: $alias", e)
            Result.failure(e)
        }
    }
    
    /**
     * Decrypt data using Android Keystore
     */
    fun decrypt(alias: String, encryptedData: ByteArray): Result<ByteArray> {
        return try {
            if (encryptedData.size < 12) {
                return Result.failure(IllegalArgumentException("Encrypted data too short"))
            }
            
            val secretKey = getOrCreateSecretKey(alias)
            val iv = encryptedData.copyOfRange(0, 12)
            val encrypted = encryptedData.copyOfRange(12, encryptedData.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            Result.success(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Decryption failed for alias: $alias", e)
            Result.failure(e)
        }
    }
    
    /**
     * Store Signal Protocol identity key pair
     */
    fun storeIdentityKeyPair(alias: String, identityKeyPair: IdentityKeyPair) {
        val serialized = identityKeyPair.serialize()
        encrypt(alias, serialized).onSuccess { encrypted ->
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit { putString(alias, encoded) }
        }.onFailure { e ->
            SecureLogger.e(TAG, "Failed to store identity key pair for alias: $alias", e)
        }
    }
    
    /**
     * Retrieve Signal Protocol identity key pair
     */
    fun getIdentityKeyPair(alias: String): IdentityKeyPair? {
        val encoded = prefs.getString(alias, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        
        return decrypt(alias, encrypted).getOrNull()?.let { decrypted ->
            IdentityKeyPair(decrypted)
        }
    }
    
    /**
     * Store registration ID
     */
    fun storeRegistrationId(key: String, registrationId: Int) {
        prefs.edit { putInt(key, registrationId) }
    }
    
    /**
     * Retrieve registration ID
     */
    fun getRegistrationId(key: String): Int {
        return prefs.getInt(key, 0)
    }
    
    /**
     * Store encrypted string
     */
    fun storeEncryptedString(alias: String, key: String, value: String) {
        encrypt(alias, value.toByteArray()).onSuccess { encrypted ->
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit { putString(key, encoded) }
        }.onFailure { e ->
            SecureLogger.e(TAG, "Failed to store encrypted string for key: $key", e)
        }
    }
    
    /**
     * Retrieve encrypted string
     */
    fun getEncryptedString(alias: String, key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        
        return decrypt(alias, encrypted).getOrNull()?.let { String(it) }
    }
    
    /**
     * Check if key exists
     */
    fun containsKey(key: String): Boolean {
        return prefs.contains(key)
    }
    
    /**
     * Remove key
     */
    fun removeKey(key: String) {
        prefs.edit { remove(key) }
    }
    
    private val clearMutex = Mutex()

    /**
     * Clear all stored data
     */
    suspend fun clearAll() {
        clearMutex.withLock {
            try {
                prefs.edit { clear() }
    
                val aliases = keyStore.aliases().toList()
                aliases.forEach { alias ->
                    // Do NOT delete the AndroidX Security MasterKey used by EncryptedSharedPreferences
                    if (!alias.startsWith("_androidx_security_master_key_")) {
                        try {
                            keyStore.deleteEntry(alias)
                        } catch (e: Exception) {
                            SecureLogger.w(TAG, "Failed to delete keystore entry: $alias", e)
                        }
                    }
                }
                
                SecureLogger.d(TAG, "All keystore data cleared")
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error clearing keystore", e)
                throw e
            }
        }
    }
}
