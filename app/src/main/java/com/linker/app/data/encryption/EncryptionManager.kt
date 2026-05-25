package com.linker.app.data.encryption

sealed class EncryptionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class KeyNotFound(val userId: String) : EncryptionError("Encryption keys not found for user: $userId")
    data class EncryptionFailed(val reason: String, override val cause: Throwable? = null) : EncryptionError(reason, cause)
    data class DecryptionFailed(val reason: String, override val cause: Throwable? = null) : EncryptionError(reason, cause)
    data class InvalidMessage(val reason: String) : EncryptionError(reason)
    object NotInitialized : EncryptionError("EncryptionManager is not initialized")
}

/**
 * Interface for end-to-end encryption using Signal Protocol
 * 
 * Handles encryption/decryption of messages for offline messaging.
 */
interface EncryptionManager {
    
    /**
     * Initialize the encryption manager
     * Generates identity key pair and pre-keys if not already present
     */
    suspend fun initialize()
    
    /**
     * Encrypt a message for a recipient
     * 
     * @param recipientId Recipient user ID
     * @param plaintext Message content to encrypt
     * @return Result containing EncryptedMessage or EncryptionError
     */
    suspend fun encryptMessage(recipientId: String, plaintext: String): Result<EncryptedMessage>
    
    /**
     * Decrypt a message from a sender
     * 
     * @param senderId Sender user ID
     * @param encrypted Encrypted message
     * @return Result containing decrypted plaintext or EncryptionError
     */
    suspend fun decryptMessage(senderId: String, encrypted: EncryptedMessage): Result<String>
    
    /**
     * Encrypt multiple messages
     */
    suspend fun encryptMessages(messages: Map<String, String>): Map<String, Result<EncryptedMessage>> {
        return messages.mapValues { (recipientId, plaintext) ->
            encryptMessage(recipientId, plaintext)
        }
    }
    
    /**
     * Decrypt multiple messages
     */
    suspend fun decryptMessages(messages: Map<String, EncryptedMessage>): Map<String, Result<String>> {
        return messages.mapValues { (senderId, encrypted) ->
            decryptMessage(senderId, encrypted)
        }
    }

    /**
     * Check if encryption keys exist for a user
     * 
     * @param userId User ID to check
     * @return true if keys exist, false otherwise
     */
    suspend fun hasKeysFor(userId: String): Boolean
    
    /**
     * Rotate encryption keys (should be done every 30 days)
     */
    suspend fun rotateKeys()
    
    /**
     * Get identity key fingerprint for verification
     * 
     * @return Hex-encoded fingerprint of local identity key
     */
    fun getIdentityKeyFingerprint(): String
}
