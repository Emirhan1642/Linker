package com.linker.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialEncoder @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "linker_credential_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
        
        // Rate limiting
        private val rateLimitTimes = mutableListOf<Long>()
        private const val RATE_LIMIT_WINDOW = 60000L // 1 minute
        private const val MAX_ATTEMPTS = 30
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun checkRateLimit() {
        val now = System.currentTimeMillis()
        rateLimitTimes.add(now)
        rateLimitTimes.removeAll { now - it > RATE_LIMIT_WINDOW }
        
        if (rateLimitTimes.size > MAX_ATTEMPTS) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.SUSPICIOUS_ACTIVITY,
                "Credential encoder rate limit exceeded"
            )
            throw SecurityException("Rate limit exceeded")
        }
    }

    fun encode(email: String, password: CharArray): String {
        checkRateLimit()
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw IllegalArgumentException("Invalid email format")
        }
        if (password.isEmpty()) {
            throw IllegalArgumentException("Password cannot be empty")
        }

        SecurityLogger.logEvent(
            SecurityLogger.EventType.CREDENTIAL_ENCRYPTION_ATTEMPT,
            "Encoding credentials"
        )

        var emailBytes: ByteArray? = null
        var passwordBytes: ByteArray? = null
        var combined: ByteArray? = null

        try {
            emailBytes = email.toByteArray(StandardCharsets.UTF_8)
            val charBuffer = java.nio.CharBuffer.wrap(password)
            val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
            passwordBytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(passwordBytes)

            val buffer = ByteBuffer.allocate(4 + emailBytes.size + passwordBytes.size)
            buffer.putInt(emailBytes.size)
            buffer.put(emailBytes)
            buffer.put(passwordBytes)

            combined = buffer.array()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(combined)

            val outBuffer = ByteBuffer.allocate(4 + iv.size + encryptedBytes.size)
            outBuffer.putInt(iv.size)
            outBuffer.put(iv)
            outBuffer.put(encryptedBytes)
            
            SecurityLogger.logEvent(
                SecurityLogger.EventType.CREDENTIAL_ENCRYPTION_SUCCESS,
                "Credentials successfully encoded"
            )

            return Base64.encodeToString(outBuffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.CREDENTIAL_ENCRYPTION_FAILED,
                "Failed to encode credentials: ${e.message}"
            )
            throw SecurityException("Failed to encode credentials", e)
        } finally {
            emailBytes?.fill(0)
            passwordBytes?.fill(0)
            combined?.fill(0)
        }
    }

    fun encode(email: String, password: String): String {
        val chars = password.toCharArray()
        try {
            return encode(email, chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun decode(encoded: String): Pair<String, CharArray> {
        checkRateLimit()
        
        var combined: ByteArray? = null
        var emailBytes: ByteArray? = null
        var passwordBytes: ByteArray? = null
        
        try {
            val decodedData = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(decodedData)
            
            val ivLength = buffer.getInt()
            if (ivLength < 0 || ivLength > 16) {
                throw IllegalArgumentException("Invalid IV length")
            }
            
            val iv = ByteArray(ivLength)
            buffer.get(iv)
            
            val encryptedBytes = ByteArray(buffer.remaining())
            buffer.get(encryptedBytes)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            combined = cipher.doFinal(encryptedBytes)
            
            val plainBuffer = ByteBuffer.wrap(combined)
            val emailLength = plainBuffer.getInt()
            if (emailLength < 0 || emailLength > combined.size - 4) {
                throw IllegalArgumentException("Invalid email length")
            }
            
            emailBytes = ByteArray(emailLength)
            plainBuffer.get(emailBytes)
            
            val passwordLength = plainBuffer.remaining()
            if (passwordLength < 0) {
                throw IllegalArgumentException("Invalid password length")
            }
            
            passwordBytes = ByteArray(passwordLength)
            plainBuffer.get(passwordBytes)
            
            val email = String(emailBytes, StandardCharsets.UTF_8)
            val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(passwordBytes))
            val passwordChars = CharArray(charBuffer.remaining())
            charBuffer.get(passwordChars)
            
            return Pair(email, passwordChars)
        } catch (e: Exception) {
            SecurityLogger.logEvent(
                SecurityLogger.EventType.CREDENTIAL_ENCRYPTION_FAILED,
                "Failed to decode credentials: ${e.message}"
            )
            throw SecurityException("Failed to decode credentials", e)
        } finally {
            combined?.fill(0)
            emailBytes?.fill(0)
            passwordBytes?.fill(0)
        }
    }

    fun decodeToString(encoded: String): Pair<String, String> {
        val (email, passwordChars) = decode(encoded)
        val password = String(passwordChars)
        passwordChars.fill('\u0000')
        return Pair(email, password)
    }

    fun isValid(encoded: String): Boolean {
        return try {
            val decodedData = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(decodedData)
            val ivLength = buffer.getInt()
            ivLength in 0..16
        } catch (e: Exception) {
            false
        }
    }
}
