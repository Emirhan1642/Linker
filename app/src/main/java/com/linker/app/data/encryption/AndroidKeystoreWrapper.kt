package com.linker.app.data.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.IdentityKeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

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
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "signal_keystore_prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
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
    fun encrypt(alias: String, data: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Combine IV and encrypted data
        return iv + encrypted
    }
    
    /**
     * Decrypt data using Android Keystore
     */
    fun decrypt(alias: String, encryptedData: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey(alias)
        
        // Extract IV and encrypted data
        val iv = encryptedData.copyOfRange(0, 12) // GCM IV is 12 bytes
        val encrypted = encryptedData.copyOfRange(12, encryptedData.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encrypted)
    }
    
    /**
     * Store Signal Protocol identity key pair
     */
    fun storeIdentityKeyPair(alias: String, identityKeyPair: IdentityKeyPair) {
        val serialized = identityKeyPair.serialize()
        val encrypted = encrypt(alias, serialized)
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        
        prefs.edit().putString(alias, encoded).apply()
    }
    
    /**
     * Retrieve Signal Protocol identity key pair
     */
    fun getIdentityKeyPair(alias: String): IdentityKeyPair? {
        val encoded = prefs.getString(alias, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val decrypted = decrypt(alias, encrypted)
        
        return IdentityKeyPair(decrypted)
    }
    
    /**
     * Store registration ID
     */
    fun storeRegistrationId(key: String, registrationId: Int) {
        prefs.edit().putInt(key, registrationId).apply()
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
        val encrypted = encrypt(alias, value.toByteArray())
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        
        prefs.edit().putString(key, encoded).apply()
    }
    
    /**
     * Retrieve encrypted string
     */
    fun getEncryptedString(alias: String, key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val decrypted = decrypt(alias, encrypted)
        
        return String(decrypted)
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
        prefs.edit().remove(key).apply()
    }
    
    /**
     * Clear all stored data
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        
        // Delete all keys from keystore
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            keyStore.deleteEntry(alias)
        }
    }
}
