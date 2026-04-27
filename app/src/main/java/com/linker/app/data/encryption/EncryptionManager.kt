package com.linker.app.data.encryption

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
     * @return Result containing EncryptedMessage or error
     */
    suspend fun encryptMessage(recipientId: String, plaintext: String): Result<EncryptedMessage>
    
    /**
     * Decrypt a message from a sender
     * 
     * @param senderId Sender user ID
     * @param encrypted Encrypted message
     * @return Result containing decrypted plaintext or error
     */
    suspend fun decryptMessage(senderId: String, encrypted: EncryptedMessage): Result<String>
    
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
