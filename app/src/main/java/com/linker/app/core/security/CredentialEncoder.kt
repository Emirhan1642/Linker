package com.linker.app.core.security

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Secure credential encoding without delimiters
 *
 * Format: [email_length(4 bytes)][email_bytes][password_bytes]
 *
 * This encoding is resistant to delimiter injection attacks since there are
 * no delimiters - the email length is stored as a fixed 4-byte integer.
 *
 * ✅ SECURITY: Use this instead of string concatenation with delimiters
 */
object CredentialEncoder {

    /**
     * Encode email and password into a single secure credential string
     *
     * @param email User's email address
     * @param password User's password
     * @return Base64-encoded credential string (safe for storage)
     */
    fun encode(email: String, password: String): String {
        val emailBytes = email.toByteArray(StandardCharsets.UTF_8)
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)

        val buffer = ByteBuffer.allocate(4 + emailBytes.size + passwordBytes.size)
        buffer.putInt(emailBytes.size)  // Email length (4 bytes, big-endian)
        buffer.put(emailBytes)           // Email bytes
        buffer.put(passwordBytes)        // Password bytes

        val combined = buffer.array()
        val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)

        // Clear sensitive data from memory
        combined.fill(0)
        buffer.clear()

        return base64
    }

    /**
     * Decode credential string back to email and password
     *
     * @param encoded Base64-encoded credential string
     * @return Pair of (email, password)
     * @throws IllegalArgumentException if format is invalid
     */
    fun decode(encoded: String): Pair<String, String> {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)

        try {
            if (combined.size < 4) {
                throw IllegalArgumentException("Invalid credential format: too short")
            }

            val buffer = ByteBuffer.wrap(combined)

            val emailLength = buffer.getInt()
            if (emailLength < 0 || emailLength > combined.size - 4) {
                throw IllegalArgumentException("Invalid credential format: invalid email length")
            }

            val emailBytes = ByteArray(emailLength)
            buffer.get(emailBytes)

            val passwordLength = buffer.remaining()
            if (passwordLength < 0) {
                throw IllegalArgumentException("Invalid credential format: invalid password length")
            }

            val passwordBytes = ByteArray(passwordLength)
            buffer.get(passwordBytes)

            val email = String(emailBytes, StandardCharsets.UTF_8)
            val password = String(passwordBytes, StandardCharsets.UTF_8)

            // Clear sensitive data
            combined.fill(0)
            emailBytes.fill(0)
            passwordBytes.fill(0)

            return Pair(email, password)
        } catch (e: Exception) {
            // Clear sensitive data even on error
            combined.fill(0)
            throw IllegalArgumentException("Failed to decode credentials: ${e.message}")
        }
    }

    /**
     * Validate if the encoded credential has a valid format
     *
     * @param encoded Base64-encoded credential string
     * @return true if format is valid, false otherwise
     */
    fun isValid(encoded: String): Boolean {
        return try {
            decode(encoded)
            true
        } catch (e: Exception) {
            false
        }
    }
}
